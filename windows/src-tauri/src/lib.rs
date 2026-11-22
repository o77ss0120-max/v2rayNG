mod crypto;
mod device;
mod license;
mod proxy;
mod xray;

use crypto::{resolve_effective_now_ms, verify_license_offline_with_key, ROTATED_PUBLIC_KEY_B64};
use device::{get_device_identity, DeviceIdentity};
use license::{
    activate_license_online, default_vip_max_bytes, fetch_signed_subscription,
    is_trusted_https_endpoint, load_bundled_vault_servers, sync_daily_quota_online,
    DEFAULT_LICENSE_API_BASE, PUBLIC_SUBSCRIPTION_SOURCES, STEALTH_VAULT_GITHUB_URL,
};
use serde::{Deserialize, Serialize};
use std::path::PathBuf;
use std::sync::{Arc, Mutex};
use std::time::Instant;
use tauri::menu::{Menu, MenuItem};
use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};
use tauri::{Manager, RunEvent, State, WindowEvent};
use xray::{
    build_xray_config, parse_share_uri, probe_tcp_latency, ServerNode, TunnelOptions,
    XrayProcessManager, LOCAL_HTTP_PORT, LOCAL_SOCKS_PORT,
};

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct PersistedState {
    pub service_tier: String,
    pub license_api_base: String,
    pub vip_active: bool,
    pub vip_code: String,
    pub vip_serial: String,
    pub vip_expire_ms: i64,
    pub vip_used_bytes: u64,
    pub vip_max_bytes: u64,
    pub vip_last_seen_ms: i64,
    pub daily_used_bytes: u64,
    pub daily_max_bytes: u64,
    pub ads_consent: bool,
    pub options: TunnelOptions,
    pub servers: Vec<ServerNode>,
    pub selected_server_id: Option<String>,
}

impl Default for PersistedState {
    fn default() -> Self {
        Self {
            service_tier: "DEDICATED".to_string(),
            license_api_base: DEFAULT_LICENSE_API_BASE.to_string(),
            vip_active: false,
            vip_code: String::new(),
            vip_serial: String::new(),
            vip_expire_ms: 0,
            vip_used_bytes: 0,
            vip_max_bytes: default_vip_max_bytes(),
            vip_last_seen_ms: 0,
            daily_used_bytes: 0,
            daily_max_bytes: 1024 * 1024 * 1024,
            ads_consent: false,
            options: TunnelOptions::default(),
            servers: Vec::new(),
            selected_server_id: None,
        }
    }
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct DashboardSnapshot {
    pub app_version: String,
    pub device_id: String,
    pub user_uuid: String,
    pub connected: bool,
    pub connected_since_ms: i64,
    pub active_server: Option<ServerNode>,
    pub service_tier: String,
    pub license_api_base: String,
    pub vip_active: bool,
    pub vip_serial: String,
    pub vip_expire_ms: i64,
    pub vip_used_bytes: u64,
    pub vip_max_bytes: u64,
    pub daily_used_bytes: u64,
    pub daily_max_bytes: u64,
    pub ads_consent: bool,
    pub autostart: bool,
    pub socks_port: u16,
    pub http_port: u16,
    pub options: TunnelOptions,
    pub servers: Vec<ServerNode>,
    pub selected_server_id: Option<String>,
    pub logs: Vec<String>,
}

struct AppState {
    identity: DeviceIdentity,
    state_file: PathBuf,
    persisted: Mutex<PersistedState>,
    xray: Mutex<XrayProcessManager>,
    connected_since_ms: Mutex<i64>,
    active_server_id: Mutex<Option<String>>,
    logs: Mutex<Vec<String>>,
    boot_instant: Instant,
    last_server_now_ms: Mutex<i64>,
    elapsed_at_sync_ms: Mutex<i64>,
}

impl AppState {
    fn new(data_dir: PathBuf) -> Self {
        let _ = std::fs::create_dir_all(&data_dir);
        let state_file = data_dir.join("penny_desktop_state.json");
        let identity = get_device_identity();
        let mut persisted: PersistedState = std::fs::read_to_string(&state_file)
            .ok()
            .and_then(|s| serde_json::from_str(&s).ok())
            .unwrap_or_default();

        if persisted.vip_active {
            let valid = verify_license_offline_with_key(
                &persisted.vip_code,
                &identity.device_id,
                0,
                ROTATED_PUBLIC_KEY_B64,
            )
            .ok()
            .filter(|v| v.serial.eq_ignore_ascii_case(&persisted.vip_serial))
            .is_some();
            if !valid {
                persisted.vip_active = false;
                persisted.vip_code.clear();
                persisted.vip_serial.clear();
            }
        }

        let mut init_logs = vec![
            "Penny VPN Desktop v2.9.8 initialized (ECDSA P-256 hardened).".to_string(),
        ];

        if persisted.servers.is_empty() {
            if let Ok(vault_body) = load_bundled_vault_servers() {
                let mut loaded = 0usize;
                for (i, line) in vault_body.lines().enumerate() {
                    if let Some(node) = parse_share_uri(line, "DEDICATED", i) {
                        if !persisted.servers.iter().any(|s| s.raw_uri == node.raw_uri) {
                            persisted.servers.push(node);
                            loaded += 1;
                        }
                    }
                }
                if loaded > 0 {
                    persisted.selected_server_id = persisted.servers.first().map(|s| s.id.clone());
                    if let Ok(json) = serde_json::to_string_pretty(&persisted) {
                        let _ = std::fs::write(&state_file, json);
                    }
                    init_logs.push(format!(
                        "Loaded {loaded} authenticated Penny Vault node(s) from embedded bundle."
                    ));
                }
            }
        }

        Self {
            identity,
            state_file,
            persisted: Mutex::new(persisted),
            xray: Mutex::new(XrayProcessManager::new(data_dir)),
            connected_since_ms: Mutex::new(0),
            active_server_id: Mutex::new(None),
            logs: Mutex::new(init_logs),
            boot_instant: Instant::now(),
            last_server_now_ms: Mutex::new(0),
            elapsed_at_sync_ms: Mutex::new(0),
        }
    }

    fn save_persisted(&self, data: &PersistedState) {
        if let Ok(json) = serde_json::to_string_pretty(data) {
            let _ = std::fs::write(&self.state_file, json);
        }
    }

    fn push_log(&self, msg: impl Into<String>) {
        let raw = msg.into();
        let redacted = raw
            .replace(&self.identity.user_uuid, "[PROTECTED-UUID]")
            .replace(&self.identity.device_id, "[PROTECTED-HWID]");
        let ts = chrono::Local::now().format("%H:%M:%S");
        let line = format!("{ts}  {redacted}");
        if let Ok(mut logs) = self.logs.lock() {
            logs.push(line);
            if logs.len() > 250 {
                let drop_n = logs.len() - 250;
                logs.drain(0..drop_n);
            }
        }
    }

    fn effective_now_ms(&self) -> i64 {
        let sys_now = chrono::Utc::now().timestamp_millis();
        let current_elapsed = self.boot_instant.elapsed().as_millis() as i64;
        let last_srv = *self.last_server_now_ms.lock().unwrap();
        let elapsed_sync = *self.elapsed_at_sync_ms.lock().unwrap();
        let mut p = self.persisted.lock().unwrap();
        let resolved = resolve_effective_now_ms(
            sys_now,
            p.vip_last_seen_ms,
            last_srv,
            elapsed_sync,
            current_elapsed,
        );
        if resolved != p.vip_last_seen_ms {
            p.vip_last_seen_ms = resolved;
            self.save_persisted(&p);
        }
        resolved
    }

    fn record_server_clock(&self, server_now_ms: i64) {
        if server_now_ms > 1_700_000_000_000 {
            *self.last_server_now_ms.lock().unwrap() = server_now_ms;
            *self.elapsed_at_sync_ms.lock().unwrap() =
                self.boot_instant.elapsed().as_millis() as i64;
        }
    }

    fn snapshot(&self) -> DashboardSnapshot {
        let now = self.effective_now_ms();
        let mut p = self.persisted.lock().unwrap();
        if p.vip_active && (now >= p.vip_expire_ms || p.vip_used_bytes >= p.vip_max_bytes) {
            p.vip_active = false;
            self.save_persisted(&p);
        }
        let running = self.xray.lock().unwrap().is_running();
        let active_id = self.active_server_id.lock().unwrap().clone();
        let active_server = if running {
            active_id
                .as_deref()
                .and_then(|id| p.servers.iter().find(|s| s.id == id).cloned())
        } else {
            None
        };
        let connected_since_ms = if running {
            *self.connected_since_ms.lock().unwrap()
        } else {
            0
        };
        let logs = self.logs.lock().unwrap().clone();

        DashboardSnapshot {
            app_version: "2.9.8".to_string(),
            device_id: self.identity.device_id.clone(),
            user_uuid: self.identity.user_uuid.clone(),
            connected: running,
            connected_since_ms,
            active_server,
            service_tier: p.service_tier.clone(),
            license_api_base: p.license_api_base.clone(),
            vip_active: p.vip_active,
            vip_serial: p.vip_serial.clone(),
            vip_expire_ms: p.vip_expire_ms,
            vip_used_bytes: p.vip_used_bytes,
            vip_max_bytes: p.vip_max_bytes,
            daily_used_bytes: p.daily_used_bytes,
            daily_max_bytes: p.daily_max_bytes,
            ads_consent: p.ads_consent,
            autostart: proxy::get_autostart(),
            socks_port: LOCAL_SOCKS_PORT,
            http_port: LOCAL_HTTP_PORT,
            options: p.options.clone(),
            servers: p.servers.clone(),
            selected_server_id: p.selected_server_id.clone(),
            logs,
        }
    }

    fn disconnect_internal(&self) {
        self.xray.lock().unwrap().stop();
        let _ = proxy::clear_system_proxy();
        *self.connected_since_ms.lock().unwrap() = 0;
        *self.active_server_id.lock().unwrap() = None;
        self.push_log("Disconnected tunnel and restored Windows system proxy.");
    }
}

#[tauri::command]
fn get_dashboard_state(state: State<'_, Arc<AppState>>) -> DashboardSnapshot {
    state.snapshot()
}

#[tauri::command]
fn update_settings(
    state: State<'_, Arc<AppState>>,
    service_tier: Option<String>,
    options: Option<TunnelOptions>,
    autostart: Option<bool>,
    ads_consent: Option<bool>,
    license_api_base: Option<String>,
) -> Result<DashboardSnapshot, String> {
    if let Some(auto) = autostart {
        proxy::set_autostart(auto)?;
        state.push_log(format!("Windows auto-start set to {auto}"));
    }
    {
        let mut p = state.persisted.lock().unwrap();
        if let Some(tier) = service_tier {
            let t = tier.trim().to_ascii_uppercase();
            if matches!(t.as_str(), "DEDICATED" | "GHOST" | "PUBLIC") {
                p.service_tier = t;
            }
        }
        if let Some(opt) = options {
            p.options = opt;
        }
        if let Some(consent) = ads_consent {
            p.ads_consent = consent;
        }
        if let Some(base) = license_api_base {
            let clean = base.trim().to_string();
            if clean.is_empty() {
                p.license_api_base = DEFAULT_LICENSE_API_BASE.to_string();
            } else if is_trusted_https_endpoint(&clean) {
                p.license_api_base = clean;
            } else {
                return Err("License server URL must be a trusted public HTTPS endpoint.".to_string());
            }
        }
        state.save_persisted(&p);
    }
    Ok(state.snapshot())
}

#[tauri::command]
fn import_server_uris(
    state: State<'_, Arc<AppState>>,
    raw_text: String,
    tier: String,
) -> Result<DashboardSnapshot, String> {
    let clean_tier = tier.trim().to_ascii_uppercase();
    let target_tier = if matches!(clean_tier.as_str(), "DEDICATED" | "GHOST" | "PUBLIC") {
        clean_tier
    } else {
        "DEDICATED".to_string()
    };

    let mut added = 0usize;
    {
        let mut p = state.persisted.lock().unwrap();
        let base_idx = p.servers.len();
        for (i, line) in raw_text.lines().enumerate() {
            if let Some(node) = parse_share_uri(line, &target_tier, base_idx + i) {
                if !p.servers.iter().any(|s| s.raw_uri == node.raw_uri) {
                    p.servers.push(node);
                    added += 1;
                }
            }
        }
        if p.selected_server_id.is_none() {
            p.selected_server_id = p.servers.first().map(|s| s.id.clone());
        }
        state.save_persisted(&p);
    }
    if added == 0 {
        return Err("No valid VLESS, VMess, Trojan, or Shadowsocks URIs found.".to_string());
    }
    state.push_log(format!("Imported {added} server configuration(s) into {target_tier}."));
    Ok(state.snapshot())
}

#[tauri::command]
async fn sync_signed_servers(
    state: State<'_, Arc<AppState>>,
    subscription_url: Option<String>,
) -> Result<DashboardSnapshot, String> {
    let custom_url = subscription_url.and_then(|s| {
        let t = s.trim().to_string();
        if t.is_empty() { None } else { Some(t) }
    });
    let tier = state.persisted.lock().unwrap().service_tier.clone();

    let (body, source_label) = if let Some(url) = custom_url {
        let require_sig = url == STEALTH_VAULT_GITHUB_URL || url.ends_with(".bin");
        state.push_log("Fetching custom subscription source...");
        let data = fetch_signed_subscription(&url, require_sig).await?;
        (data, "custom source")
    } else if tier == "PUBLIC" {
        state.push_log("Syncing Penny Public community mirrors...");
        let mut combined = String::new();
        for mirror in PUBLIC_SUBSCRIPTION_SOURCES {
            if let Ok(chunk) = fetch_signed_subscription(mirror, false).await {
                combined.push_str(&chunk);
                combined.push('\n');
            }
        }
        if combined.trim().is_empty() {
            state.push_log("Public mirrors unreachable; loading embedded authenticated vault...");
            (load_bundled_vault_servers()?, "embedded vault fallback")
        } else {
            (combined, "public mirrors")
        }
    } else {
        state.push_log("Fetching authenticated Penny Stealth Vault...");
        match fetch_signed_subscription(STEALTH_VAULT_GITHUB_URL, true).await {
            Ok(data) => (data, "remote authenticated vault"),
            Err(remote_err) => {
                state.push_log(format!(
                    "Remote vault sync fallback ({remote_err}); loading embedded verified vault..."
                ));
                (load_bundled_vault_servers()?, "embedded verified vault")
            }
        }
    };

    let mut added = 0usize;
    let max_import = if tier == "PUBLIC" { 40usize } else { 100usize };
    {
        let mut p = state.persisted.lock().unwrap();
        let base_idx = p.servers.len();
        for (i, line) in body.lines().enumerate() {
            if added >= max_import {
                break;
            }
            if let Some(node) = parse_share_uri(line, &tier, base_idx + i) {
                if !p.servers.iter().any(|s| s.raw_uri == node.raw_uri) {
                    p.servers.push(node);
                    added += 1;
                }
            }
        }
        if p.selected_server_id.is_none() {
            p.selected_server_id = p.servers.first().map(|s| s.id.clone());
        }
        state.save_persisted(&p);
    }
    state.push_log(format!(
        "Sync complete from {source_label}: {added} new node(s) added."
    ));
    Ok(state.snapshot())
}

#[tauri::command]
async fn probe_all_servers(state: State<'_, Arc<AppState>>) -> Result<DashboardSnapshot, String> {
    let snapshot_servers = state.persisted.lock().unwrap().servers.clone();
    if snapshot_servers.is_empty() {
        return Ok(state.snapshot());
    }
    state.push_log(format!("Probing TCP latency for {} node(s)...", snapshot_servers.len()));

    let mut handles = Vec::with_capacity(snapshot_servers.len());
    for node in snapshot_servers {
        handles.push(tokio::spawn(async move {
            let ms = probe_tcp_latency(&node.host, node.port).await;
            (node.id, ms)
        }));
    }

    let mut results = Vec::new();
    for h in handles {
        if let Ok(pair) = h.await {
            results.push(pair);
        }
    }

    {
        let mut p = state.persisted.lock().unwrap();
        for (id, ms) in results {
            if let Some(s) = p.servers.iter_mut().find(|n| n.id == id) {
                s.latency_ms = ms;
            }
        }
        p.servers.sort_by_key(|s| s.latency_ms.unwrap_or(u64::MAX));
        state.save_persisted(&p);
    }
    state.push_log("Latency probe complete.");
    Ok(state.snapshot())
}

#[tauri::command]
async fn connect_vpn(
    state: State<'_, Arc<AppState>>,
    server_id: Option<String>,
) -> Result<DashboardSnapshot, String> {
    let (node, options, api_base, tier, local_used) = {
        let mut p = state.persisted.lock().unwrap();
        if p.servers.is_empty() {
            if let Ok(vault_body) = load_bundled_vault_servers() {
                for (i, line) in vault_body.lines().enumerate() {
                    if let Some(node) = parse_share_uri(line, &p.service_tier, i) {
                        p.servers.push(node);
                    }
                }
            }
        }
        let target_id = server_id.or_else(|| p.selected_server_id.clone());
        let chosen = target_id
            .as_deref()
            .and_then(|id| p.servers.iter().find(|s| s.id == id).cloned())
            .or_else(|| p.servers.first().cloned())
            .ok_or_else(|| "No server configured. Sync signed nodes or import a server URI first.".to_string())?;
        p.selected_server_id = Some(chosen.id.clone());
        state.save_persisted(&p);
        (
            chosen,
            p.options.clone(),
            p.license_api_base.clone(),
            p.service_tier.clone(),
            p.daily_used_bytes,
        )
    };

    let cfg = build_xray_config(&node, &options)?;
    {
        let mut mgr = state.xray.lock().unwrap();
        mgr.start(&cfg, options.tun_mode)?;
    }

    if !options.tun_mode {
        if let Err(err) = proxy::set_system_proxy("127.0.0.1", LOCAL_HTTP_PORT, options.split_tunnel_local) {
            state.xray.lock().unwrap().stop();
            return Err(err);
        }
    }

    *state.connected_since_ms.lock().unwrap() = chrono::Utc::now().timestamp_millis();
    *state.active_server_id.lock().unwrap() = Some(node.id.clone());
    state.push_log(format!(
        "Connected via {} ({}, mode={})",
        node.label,
        node.protocol.to_ascii_uppercase(),
        if options.tun_mode { "TUN" } else { "System Proxy" }
    ));

    if let Ok(sync_res) = sync_daily_quota_online(
        &api_base,
        &state.identity.device_id,
        &state.identity.user_uuid,
        local_used,
        0,
        &tier,
    )
    .await
    {
        state.record_server_clock(sync_res.server_now_ms);
        let mut p = state.persisted.lock().unwrap();
        p.daily_used_bytes = p.daily_used_bytes.max(sync_res.daily_used_bytes);
        p.daily_max_bytes = sync_res.max_daily_bytes;
        state.save_persisted(&p);
    }

    Ok(state.snapshot())
}

#[tauri::command]
fn disconnect_vpn(state: State<'_, Arc<AppState>>) -> DashboardSnapshot {
    state.disconnect_internal();
    state.snapshot()
}

#[tauri::command]
async fn activate_vip_license(
    state: State<'_, Arc<AppState>>,
    code: String,
) -> Result<DashboardSnapshot, String> {
    let api_base = state.persisted.lock().unwrap().license_api_base.clone();
    let dev_id = state.identity.device_id.clone();
    let res = activate_license_online(&api_base, &code, &dev_id).await?;
    state.record_server_clock(res.server_now_ms);
    {
        let mut p = state.persisted.lock().unwrap();
        p.vip_active = res.active;
        p.vip_code = code.trim().to_string();
        p.vip_serial = res.serial.clone();
        p.vip_expire_ms = res.expires_at_ms;
        p.vip_max_bytes = res.max_bytes;
        p.vip_used_bytes = res.used_bytes;
        state.save_persisted(&p);
    }
    state.push_log(format!("VIP license {} verified and activated on server.", res.serial));
    Ok(state.snapshot())
}

#[tauri::command]
fn clear_logs(state: State<'_, Arc<AppState>>) -> DashboardSnapshot {
    if let Ok(mut logs) = state.logs.lock() {
        logs.clear();
    }
    state.snapshot()
}

#[tauri::command]
fn remove_server(state: State<'_, Arc<AppState>>, server_id: String) -> DashboardSnapshot {
    let mut p = state.persisted.lock().unwrap();
    p.servers.retain(|s| s.id != server_id);
    if p.selected_server_id.as_deref() == Some(server_id.as_str()) {
        p.selected_server_id = p.servers.first().map(|s| s.id.clone());
    }
    state.save_persisted(&p);
    drop(p);
    state.snapshot()
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let data_dir = std::env::var("LOCALAPPDATA")
        .map(PathBuf::from)
        .unwrap_or_else(|_| std::env::temp_dir())
        .join("PennyVPN");

    let shared_state = Arc::new(AppState::new(data_dir));
    let state_for_setup = Arc::clone(&shared_state);
    let state_for_exit = Arc::clone(&shared_state);

    let app = tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .manage(shared_state)
        .invoke_handler(tauri::generate_handler![
            get_dashboard_state,
            update_settings,
            import_server_uris,
            sync_signed_servers,
            probe_all_servers,
            connect_vpn,
            disconnect_vpn,
            activate_vip_license,
            clear_logs,
            remove_server
        ])
        .setup(move |app| {
            let show_item = MenuItem::with_id(app, "show", "Open Penny VPN", true, None::<&str>)?;
            let disc_item = MenuItem::with_id(app, "disconnect", "Disconnect VPN", true, None::<&str>)?;
            let quit_item = MenuItem::with_id(app, "quit", "Quit & Reset Proxy", true, None::<&str>)?;
            let menu = Menu::with_items(app, &[&show_item, &disc_item, &quit_item])?;

            let tray_state = Arc::clone(&state_for_setup);
            let mut tray_builder = TrayIconBuilder::new()
                .menu(&menu)
                .tooltip("Penny VPN")
                .on_menu_event(move |app_handle, event| match event.id.as_ref() {
                    "show" => {
                        if let Some(w) = app_handle.get_webview_window("main") {
                            let _ = w.show();
                            let _ = w.set_focus();
                        }
                    }
                    "disconnect" => {
                        tray_state.disconnect_internal();
                    }
                    "quit" => {
                        tray_state.disconnect_internal();
                        app_handle.exit(0);
                    }
                    _ => {}
                })
                .on_tray_icon_event(|tray, event| {
                    if let TrayIconEvent::Click {
                        button: MouseButton::Left,
                        button_state: MouseButtonState::Up,
                        ..
                    } = event
                    {
                        if let Some(w) = tray.app_handle().get_webview_window("main") {
                            let _ = w.show();
                            let _ = w.set_focus();
                        }
                    }
                });

            if let Some(icon) = app.default_window_icon().cloned() {
                tray_builder = tray_builder.icon(icon);
            }
            let _ = tray_builder.build(app)?;
            Ok(())
        })
        .on_window_event(|window, event| {
            if let WindowEvent::CloseRequested { api, .. } = event {
                let state = window.state::<Arc<AppState>>();
                if state.xray.lock().unwrap().is_running() {
                    let _ = window.hide();
                    api.prevent_close();
                } else {
                    let _ = proxy::clear_system_proxy();
                }
            }
        })
        .build(tauri::generate_context!())
        .expect("error while building Penny VPN desktop application");

    app.run(move |_app_handle, event| {
        if let RunEvent::Exit = event {
            state_for_exit.disconnect_internal();
        }
    });
}
