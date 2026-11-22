use crate::crypto::decode_base64_flexible;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::collections::HashMap;
use std::path::PathBuf;
use std::process::{Child, Command, Stdio};
use std::time::{Duration, Instant};
use tokio::net::TcpStream;
use url::Url;

pub const LOCAL_SOCKS_PORT: u16 = 10808;
pub const LOCAL_HTTP_PORT: u16 = 10809;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerNode {
    pub id: String,
    pub label: String,
    pub protocol: String,
    pub host: String,
    pub port: u16,
    pub raw_uri: String,
    pub latency_ms: Option<u64>,
    pub tier: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TunnelOptions {
    pub tun_mode: bool,
    pub clean_dns: bool,
    pub split_tunnel_local: bool,
    pub fragment_mode: String, // "NORMAL" | "SMART" | "GHOST"
}

impl Default for TunnelOptions {
    fn default() -> Self {
        Self {
            tun_mode: false,
            clean_dns: true,
            split_tunnel_local: true,
            fragment_mode: "NORMAL".to_string(),
        }
    }
}

pub fn parse_share_uri(raw: &str, tier: &str, idx: usize) -> Option<ServerNode> {
    let trimmed = raw.trim();
    if trimmed.is_empty() || trimmed.starts_with('#') {
        return None;
    }

    if trimmed.starts_with("vmess://") {
        let b64 = trimmed.strip_prefix("vmess://")?;
        let bytes = decode_base64_flexible(b64).ok()?;
        let obj: Value = serde_json::from_slice(&bytes).ok()?;
        let host = obj.get("add")?.as_str()?.trim().to_string();
        let port = obj
            .get("port")
            .and_then(|v| v.as_u64().or_else(|| v.as_str().and_then(|s| s.parse().ok())))?
            as u16;
        if host.is_empty() || port == 0 {
            return None;
        }
        let ps = obj
            .get("ps")
            .and_then(|v| v.as_str())
            .map(|s| s.trim().to_string())
            .filter(|s| !s.is_empty())
            .unwrap_or_else(|| format!("Penny VMess #{}", idx + 1));
        return Some(ServerNode {
            id: format!("vmess-{idx}-{port}"),
            label: sanitize_label(&ps, idx),
            protocol: "vmess".to_string(),
            host,
            port,
            raw_uri: trimmed.to_string(),
            latency_ms: None,
            tier: tier.to_string(),
        });
    }

    let parsed = Url::parse(trimmed).ok()?;
    let scheme = parsed.scheme().to_ascii_lowercase();
    if !matches!(scheme.as_str(), "vless" | "trojan" | "ss") {
        return None;
    }
    let host = parsed.host_str()?.trim().to_string();
    let port = parsed.port().unwrap_or(443);
    let fragment = parsed
        .fragment()
        .map(|f| url_decode_lossy(f))
        .filter(|s| !s.trim().is_empty())
        .unwrap_or_else(|| format!("Penny {} #{}", scheme.to_ascii_uppercase(), idx + 1));

    Some(ServerNode {
        id: format!("{scheme}-{idx}-{port}"),
        label: sanitize_label(&fragment, idx),
        protocol: scheme,
        host,
        port,
        raw_uri: trimmed.to_string(),
        latency_ms: None,
        tier: tier.to_string(),
    })
}

fn sanitize_label(raw: &str, idx: usize) -> String {
    let cleaned: String = raw
        .chars()
        .filter(|c| !c.is_control())
        .take(48)
        .collect::<String>()
        .trim()
        .to_string();
    if cleaned.is_empty() {
        format!("Penny Node #{}", idx + 1)
    } else {
        cleaned
    }
}

fn url_decode_lossy(input: &str) -> String {
    let mut out = Vec::with_capacity(input.len());
    let bytes = input.as_bytes();
    let mut i = 0;
    while i < bytes.len() {
        if bytes[i] == b'%' && i + 2 < bytes.len() {
            if let Ok(hex) = std::str::from_utf8(&bytes[i + 1..i + 3]) {
                if let Ok(val) = u8::from_str_radix(hex, 16) {
                    out.push(val);
                    i += 3;
                    continue;
                }
            }
        }
        out.push(if bytes[i] == b'+' { b' ' } else { bytes[i] });
        i += 1;
    }
    String::from_utf8_lossy(&out).into_owned()
}

pub fn build_xray_config(node: &ServerNode, opts: &TunnelOptions) -> Result<Value, String> {
    let proxy_outbound = build_proxy_outbound(node, opts)?;
    let (frag_len, frag_int) = match opts.fragment_mode.to_ascii_uppercase().as_str() {
        "GHOST" => ("5-12", "1-2"),
        "SMART" => ("10-30", "1"),
        _ => ("100-200", "1"),
    };

    let mut inbounds = vec![
        json!({
            "tag": "socks-in",
            "port": LOCAL_SOCKS_PORT,
            "listen": "127.0.0.1",
            "protocol": "socks",
            "settings": { "auth": "noauth", "udp": true },
            "sniffing": { "enabled": true, "destOverride": ["http", "tls", "quic"] }
        }),
        json!({
            "tag": "http-in",
            "port": LOCAL_HTTP_PORT,
            "listen": "127.0.0.1",
            "protocol": "http",
            "settings": { "allowTransparent": false }
        }),
    ];

    if opts.tun_mode {
        inbounds.push(json!({
            "tag": "tun-in",
            "protocol": "tun",
            "settings": {
                "name": "PennyTun",
                "mtu": 1500,
                "userLevel": 0
            },
            "sniffing": { "enabled": true, "destOverride": ["http", "tls", "quic"] }
        }));
    }

    let dns_server = if opts.clean_dns { "1.1.1.2" } else { "1.1.1.1" };
    let mut rules = vec![json!({
        "type": "field",
        "ip": [
            "10.0.0.0/8",
            "127.0.0.0/8",
            "169.254.0.0/16",
            "172.16.0.0/12",
            "192.168.0.0/16",
            "::1/128",
            "fc00::/7",
            "fe80::/10"
        ],
        "outboundTag": "direct"
    })];
    if opts.split_tunnel_local {
        rules.push(json!({
            "type": "field",
            "domain": [
                "domain:ir",
                "domain:local",
                "domain:localhost",
                "domain:lan",
                "domain:home.arpa"
            ],
            "outboundTag": "direct"
        }));
    }

    Ok(json!({
        "log": { "loglevel": "warning" },
        "dns": {
            "servers": [
                { "address": format!("https://{dns_server}/dns-query") },
                "localhost"
            ]
        },
        "inbounds": inbounds,
        "outbounds": [
            proxy_outbound,
            {
                "tag": "fragment",
                "protocol": "freedom",
                "settings": {
                    "domainStrategy": "AsIs",
                    "fragment": {
                        "packets": "tlshello",
                        "length": frag_len,
                        "interval": frag_int
                    }
                }
            },
            {
                "tag": "direct",
                "protocol": "freedom",
                "settings": {}
            },
            {
                "tag": "block",
                "protocol": "blackhole",
                "settings": {}
            }
        ],
        "routing": {
            "domainStrategy": "IPIfNonMatch",
            "rules": rules
        }
    }))
}

fn build_proxy_outbound(node: &ServerNode, opts: &TunnelOptions) -> Result<Value, String> {
    let use_fragment = opts.fragment_mode != "OFF";
    match node.protocol.as_str() {
        "vless" => {
            let parsed = Url::parse(&node.raw_uri).map_err(|e| format!("Invalid VLESS URI: {e}"))?;
            let uuid = parsed.username();
            if uuid.is_empty() {
                return Err("VLESS URI missing UUID".to_string());
            }
            let params: HashMap<String, String> = parsed.query_pairs().into_owned().collect();
            let encryption = params.get("encryption").cloned().unwrap_or_else(|| "none".to_string());
            let flow = params.get("flow").cloned().unwrap_or_default();
            let stream = build_stream_settings(&params, &node.host, use_fragment);

            let mut user = json!({ "id": uuid, "encryption": encryption });
            if !flow.is_empty() {
                user["flow"] = json!(flow);
            }
            Ok(json!({
                "tag": "proxy",
                "protocol": "vless",
                "settings": {
                    "vnext": [{
                        "address": node.host,
                        "port": node.port,
                        "users": [user]
                    }]
                },
                "streamSettings": stream
            }))
        }
        "trojan" => {
            let parsed = Url::parse(&node.raw_uri).map_err(|e| format!("Invalid Trojan URI: {e}"))?;
            let password = url_decode_lossy(parsed.username());
            if password.is_empty() {
                return Err("Trojan URI missing password".to_string());
            }
            let params: HashMap<String, String> = parsed.query_pairs().into_owned().collect();
            let stream = build_stream_settings(&params, &node.host, use_fragment);
            Ok(json!({
                "tag": "proxy",
                "protocol": "trojan",
                "settings": {
                    "servers": [{
                        "address": node.host,
                        "port": node.port,
                        "password": password
                    }]
                },
                "streamSettings": stream
            }))
        }
        "vmess" => {
            let b64 = node.raw_uri.strip_prefix("vmess://").ok_or("Invalid VMess prefix")?;
            let bytes = decode_base64_flexible(b64)?;
            let obj: Value = serde_json::from_slice(&bytes).map_err(|e| format!("Invalid VMess JSON: {e}"))?;
            let id = obj.get("id").and_then(|v| v.as_str()).ok_or("VMess missing id")?;
            let security = obj.get("scy").and_then(|v| v.as_str()).unwrap_or("auto");
            let mut params = HashMap::new();
            if let Some(net) = obj.get("net").and_then(|v| v.as_str()) {
                params.insert("type".to_string(), net.to_string());
            }
            if let Some(tls) = obj.get("tls").and_then(|v| v.as_str()) {
                params.insert("security".to_string(), tls.to_string());
            }
            if let Some(sni) = obj.get("sni").and_then(|v| v.as_str()) {
                params.insert("sni".to_string(), sni.to_string());
            }
            if let Some(host) = obj.get("host").and_then(|v| v.as_str()) {
                params.insert("host".to_string(), host.to_string());
            }
            if let Some(path) = obj.get("path").and_then(|v| v.as_str()) {
                params.insert("path".to_string(), path.to_string());
            }
            let stream = build_stream_settings(&params, &node.host, use_fragment);
            Ok(json!({
                "tag": "proxy",
                "protocol": "vmess",
                "settings": {
                    "vnext": [{
                        "address": node.host,
                        "port": node.port,
                        "users": [{ "id": id, "security": security }]
                    }]
                },
                "streamSettings": stream
            }))
        }
        "ss" => {
            let parsed = Url::parse(&node.raw_uri).map_err(|e| format!("Invalid SS URI: {e}"))?;
            let raw_user = url_decode_lossy(parsed.username());
            let decoded_cred = if raw_user.contains(':') {
                raw_user
            } else {
                let bytes = decode_base64_flexible(&raw_user)?;
                String::from_utf8(bytes).map_err(|_| "Invalid SS credentials".to_string())?
            };
            let (method, password) = decoded_cred
                .split_once(':')
                .ok_or("Invalid Shadowsocks method:password")?;
            Ok(json!({
                "tag": "proxy",
                "protocol": "shadowsocks",
                "settings": {
                    "servers": [{
                        "address": node.host,
                        "port": node.port,
                        "method": method,
                        "password": password
                    }]
                }
            }))
        }
        other => Err(format!("Unsupported protocol: {other}")),
    }
}

fn build_stream_settings(
    params: &HashMap<String, String>,
    fallback_host: &str,
    use_fragment: bool,
) -> Value {
    let network = params
        .get("type")
        .map(|s| s.to_ascii_lowercase())
        .unwrap_or_else(|| "tcp".to_string());
    let security = params
        .get("security")
        .map(|s| s.to_ascii_lowercase())
        .unwrap_or_else(|| "none".to_string());
    let sni = params
        .get("sni")
        .filter(|s| !s.is_empty())
        .or_else(|| params.get("host").filter(|s| !s.is_empty()))
        .cloned()
        .unwrap_or_else(|| fallback_host.to_string());
    let fp = params
        .get("fp")
        .filter(|s| !s.is_empty())
        .cloned()
        .unwrap_or_else(|| "chrome".to_string());

    let mut stream = json!({
        "network": network,
        "security": security
    });

    if security == "tls" {
        stream["tlsSettings"] = json!({
            "serverName": sni,
            "fingerprint": fp,
            "allowInsecure": false
        });
        if use_fragment {
            stream["sockopt"] = json!({ "dialerProxy": "fragment" });
        }
    } else if security == "reality" {
        let pbk = params.get("pbk").cloned().unwrap_or_default();
        let sid = params.get("sid").cloned().unwrap_or_default();
        let spx = params.get("spx").cloned().unwrap_or_else(|| "/".to_string());
        stream["realitySettings"] = json!({
            "serverName": sni,
            "fingerprint": fp,
            "publicKey": pbk,
            "shortId": sid,
            "spiderX": spx
        });
    }

    match network.as_str() {
        "ws" => {
            let path = params.get("path").cloned().unwrap_or_else(|| "/".to_string());
            let host = params.get("host").cloned().unwrap_or_else(|| sni.clone());
            stream["wsSettings"] = json!({
                "path": path,
                "headers": { "Host": host }
            });
        }
        "grpc" => {
            let service_name = params.get("serviceName").cloned().unwrap_or_default();
            stream["grpcSettings"] = json!({ "serviceName": service_name });
        }
        "httpupgrade" => {
            let path = params.get("path").cloned().unwrap_or_else(|| "/".to_string());
            let host = params.get("host").cloned().unwrap_or_else(|| sni.clone());
            stream["httpupgradeSettings"] = json!({ "path": path, "host": host });
        }
        "xhttp" | "splithttp" => {
            let path = params.get("path").cloned().unwrap_or_else(|| "/".to_string());
            let host = params.get("host").cloned().unwrap_or_else(|| sni.clone());
            stream["xhttpSettings"] = json!({ "path": path, "host": host });
        }
        _ => {}
    }

    stream
}

pub async fn probe_tcp_latency(host: &str, port: u16) -> Option<u64> {
    let started = Instant::now();
    let addr = format!("{host}:{port}");
    match tokio::time::timeout(Duration::from_millis(2200), TcpStream::connect(addr)).await {
        Ok(Ok(_stream)) => Some(started.elapsed().as_millis().max(1) as u64),
        _ => None,
    }
}

pub struct XrayProcessManager {
    child: Option<Child>,
    config_path: PathBuf,
    stderr_path: PathBuf,
}

impl XrayProcessManager {
    pub fn new(app_data_dir: PathBuf) -> Self {
        let _ = std::fs::create_dir_all(&app_data_dir);
        Self {
            child: None,
            config_path: app_data_dir.join("runtime_xray_config.json"),
            stderr_path: app_data_dir.join("xray_stderr.log"),
        }
    }

    fn resolve_xray_binary() -> Result<PathBuf, String> {
        let exe_dir = std::env::current_exe()
            .ok()
            .and_then(|p| p.parent().map(|d| d.to_path_buf()))
            .unwrap_or_else(|| PathBuf::from("."));

        let candidates = [
            exe_dir.join("xray.exe"),
            exe_dir.join("xray-x86_64-pc-windows-msvc.exe"),
            exe_dir.join("binaries").join("xray-x86_64-pc-windows-msvc.exe"),
            exe_dir.join("binaries").join("xray.exe"),
            exe_dir.join("xray"),
        ];

        for c in candidates {
            if c.exists() {
                return Ok(c);
            }
        }
        Err("Xray-core binary (xray.exe) not found next to application executable.".to_string())
    }

    pub fn start(&mut self, config: &Value, tun_mode: bool) -> Result<(), String> {
        self.stop();
        let serialized = serde_json::to_string_pretty(config)
            .map_err(|e| format!("Failed to serialize Xray config: {e}"))?;
        std::fs::write(&self.config_path, serialized)
            .map_err(|e| format!("Failed to write Xray config: {e}"))?;

        let bin = Self::resolve_xray_binary()?;
        let stderr_cfg = std::fs::File::create(&self.stderr_path)
            .map(Stdio::from)
            .unwrap_or_else(|_| Stdio::null());

        let mut cmd = Command::new(&bin);
        cmd.arg("run")
            .arg("-c")
            .arg(&self.config_path)
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(stderr_cfg);

        #[cfg(windows)]
        {
            use std::os::windows::process::CommandExt;
            const CREATE_NO_WINDOW: u32 = 0x0800_0000;
            cmd.creation_flags(CREATE_NO_WINDOW);
        }

        let mut child = cmd
            .spawn()
            .map_err(|e| format!("Failed to launch xray.exe: {e}"))?;

        std::thread::sleep(Duration::from_millis(300));
        if let Ok(Some(status)) = child.try_wait() {
            let err_text = std::fs::read_to_string(&self.stderr_path).unwrap_or_default();
            let detail = err_text
                .lines()
                .rev()
                .find(|l| !l.trim().is_empty())
                .unwrap_or("unknown startup error")
                .trim();
            if tun_mode {
                return Err(format!(
                    "TUN Mode requires Administrator privileges (or switch to System Proxy mode). Xray exited ({status}): {detail}"
                ));
            }
            return Err(format!("Xray-core exited immediately ({status}): {detail}"));
        }

        self.child = Some(child);
        Ok(())
    }

    pub fn stop(&mut self) {
        if let Some(mut c) = self.child.take() {
            let _ = c.kill();
            let _ = c.wait();
        }
    }

    pub fn is_running(&mut self) -> bool {
        if let Some(c) = self.child.as_mut() {
            match c.try_wait() {
                Ok(None) => true,
                _ => {
                    self.child = None;
                    false
                }
            }
        } else {
            false
        }
    }
}

impl Drop for XrayProcessManager {
    fn drop(&mut self) {
        self.stop();
    }
}
