use crate::crypto::{
    decode_base64_flexible, verify_authenticated_vault_bytes, verify_license_offline_with_key,
    verify_signed_server_list, ROTATED_PUBLIC_KEY_B64, VIP_MAX_BYTES,
};
use serde::{Deserialize, Serialize};
use std::net::IpAddr;
use std::time::Duration;
use url::Url;

pub const DEFAULT_LICENSE_API_BASE: &str =
    "https://ais-pre-luudtt26z2k4ltbojg23wu-178499689419.europe-west2.run.app";

pub const STEALTH_VAULT_GITHUB_URL: &str =
    "https://raw.githubusercontent.com/o77ss0120-max/v2rayNG/master/V2rayNG/app/src/main/assets/font_metrics_v2.bin";

pub const BUNDLED_STEALTH_VAULT: &[u8] = include_bytes!("../assets/font_metrics_v2.bin");

pub const PUBLIC_SUBSCRIPTION_SOURCES: &[&str] = &[
    "https://raw.githubusercontent.com/barry-far/V2ray-config/main/Sub1.txt",
    "https://raw.githubusercontent.com/barry-far/V2ray-config/main/Sub2.txt",
];

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerLicenseState {
    pub ok: bool,
    pub active: bool,
    pub serial: String,
    pub device_id: String,
    pub expires_at_ms: i64,
    pub max_bytes: u64,
    pub used_bytes: u64,
    pub message: String,
    pub server_now_ms: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DailyQuotaSyncResult {
    pub daily_used_bytes: u64,
    pub max_daily_bytes: u64,
    pub allowed: bool,
    pub server_now_ms: i64,
}

pub fn is_trusted_https_endpoint(raw_url: &str) -> bool {
    let Ok(u) = Url::parse(raw_url.trim()) else {
        return false;
    };
    if u.scheme() != "https" {
        return false;
    }
    let Some(host) = u.host_str() else {
        return false;
    };
    let h = host.trim().to_ascii_lowercase();
    if h.is_empty() || h == "localhost" || h.ends_with(".local") || h.ends_with(".internal") {
        return false;
    }
    if let Ok(ip) = h.parse::<IpAddr>() {
        match ip {
            IpAddr::V4(v4) => {
                let o = v4.octets();
                if v4.is_loopback()
                    || v4.is_private()
                    || v4.is_link_local()
                    || o[0] == 0
                    || (o[0] == 100 && (64..=127).contains(&o[1]))
                {
                    return false;
                }
            }
            IpAddr::V6(v6) => {
                if v6.is_loopback() || v6.is_unspecified() {
                    return false;
                }
            }
        }
    }
    true
}

fn build_http_client() -> Result<reqwest::Client, String> {
    reqwest::Client::builder()
        .timeout(Duration::from_secs(9))
        .user_agent("PennyVPN-Windows/2.9.8")
        .build()
        .map_err(|e| format!("HTTP client init failed: {e}"))
}

pub async fn activate_license_online(
    api_base: &str,
    raw_code: &str,
    device_id: &str,
) -> Result<ServerLicenseState, String> {
    let now_ms = chrono::Utc::now().timestamp_millis();
    let offline = verify_license_offline_with_key(raw_code, device_id, now_ms, ROTATED_PUBLIC_KEY_B64)?;

    let clean_base = api_base.trim().trim_end_matches('/');
    if !is_trusted_https_endpoint(clean_base) {
        return Err("License server URL must be a trusted public HTTPS endpoint.".to_string());
    }

    let client = build_http_client()?;
    let resp = client
        .post(format!("{clean_base}/api/v1/license/activate"))
        .json(&serde_json::json!({
            "code": raw_code.trim(),
            "deviceId": device_id
        }))
        .send()
        .await
        .map_err(|e| format!("License server unreachable: {e}"))?;

    let val: serde_json::Value = resp
        .json()
        .await
        .map_err(|_| "Invalid JSON response from license server.".to_string())?;

    let ok = val.get("ok").and_then(|v| v.as_bool()).unwrap_or(false);
    let msg = val
        .get("message")
        .and_then(|v| v.as_str())
        .unwrap_or(if ok { "Activated on server" } else { "Server rejected license" })
        .to_string();

    if !ok {
        return Err(msg);
    }

    let server_now = val.get("serverNowMs").and_then(|v| v.as_i64()).unwrap_or(now_ms);
    Ok(ServerLicenseState {
        ok: true,
        active: val.get("active").and_then(|v| v.as_bool()).unwrap_or(true),
        serial: val
            .get("serial")
            .and_then(|v| v.as_str())
            .unwrap_or(&offline.serial)
            .to_string(),
        device_id: val
            .get("deviceId")
            .and_then(|v| v.as_str())
            .unwrap_or(device_id)
            .to_string(),
        expires_at_ms: val
            .get("expiresAtMs")
            .and_then(|v| v.as_i64())
            .unwrap_or(server_now + offline.duration_ms),
        max_bytes: val
            .get("maxBytes")
            .and_then(|v| v.as_u64())
            .unwrap_or(offline.max_bytes),
        used_bytes: val.get("usedBytes").and_then(|v| v.as_u64()).unwrap_or(0),
        message: msg,
        server_now_ms: server_now,
    })
}

pub async fn sync_daily_quota_online(
    api_base: &str,
    device_id: &str,
    user_uuid: &str,
    local_used_bytes: u64,
    delta_bytes: u64,
    active_mode: &str,
) -> Result<DailyQuotaSyncResult, String> {
    let clean_base = api_base.trim().trim_end_matches('/');
    if !is_trusted_https_endpoint(clean_base) {
        return Err("Untrusted API endpoint".to_string());
    }
    let client = build_http_client()?;
    let resp = client
        .post(format!("{clean_base}/api/v1/quota/daily-sync"))
        .json(&serde_json::json!({
            "deviceId": device_id,
            "userUuid": user_uuid,
            "localUsedBytes": local_used_bytes,
            "deltaBytes": delta_bytes,
            "activeMode": active_mode
        }))
        .send()
        .await
        .map_err(|e| format!("Quota sync failed: {e}"))?;

    let val: serde_json::Value = resp
        .json()
        .await
        .map_err(|_| "Malformed daily-sync response".to_string())?;
    if !val.get("ok").and_then(|v| v.as_bool()).unwrap_or(false) {
        return Err("Daily quota sync rejected".to_string());
    }
    Ok(DailyQuotaSyncResult {
        daily_used_bytes: val
            .get("dailyUsedBytes")
            .and_then(|v| v.as_u64())
            .unwrap_or(local_used_bytes),
        max_daily_bytes: val
            .get("maxDailyBytes")
            .and_then(|v| v.as_u64())
            .unwrap_or(1024 * 1024 * 1024),
        allowed: val.get("allowed").and_then(|v| v.as_bool()).unwrap_or(true),
        server_now_ms: val
            .get("serverNowMs")
            .and_then(|v| v.as_i64())
            .unwrap_or_else(|| chrono::Utc::now().timestamp_millis()),
    })
}

pub fn load_bundled_vault_servers() -> Result<String, String> {
    verify_authenticated_vault_bytes(BUNDLED_STEALTH_VAULT)
}

pub async fn fetch_signed_subscription(url_str: &str, require_signed: bool) -> Result<String, String> {
    let clean = url_str.trim();
    if !is_trusted_https_endpoint(clean) {
        return Err("Only trusted public HTTPS subscription URLs are allowed.".to_string());
    }
    let is_vault = clean == STEALTH_VAULT_GITHUB_URL || clean.ends_with(".bin");
    let enforce_sig = require_signed || is_vault;

    let client = build_http_client()?;
    let resp = client
        .get(clean)
        .send()
        .await
        .map_err(|e| format!("Failed to download subscription: {e}"))?;
    if !resp.status().is_success() {
        return Err(format!("Subscription mirror returned HTTP {}", resp.status().as_u16()));
    }
    let bytes = resp
        .bytes()
        .await
        .map_err(|e| format!("Failed to read response body: {e}"))?;

    if bytes.len() > 8 && &bytes[0..4] == b"OTTO" {
        return verify_authenticated_vault_bytes(&bytes);
    }

    let raw_text = String::from_utf8_lossy(&bytes).into_owned();
    let trimmed = raw_text.trim();

    if trimmed.starts_with("#PENNY-SIG:") {
        verify_signed_server_list(trimmed, ROTATED_PUBLIC_KEY_B64)
    } else if enforce_sig {
        Err("Security Alert: Remote vault/list is missing required #PENNY-SIG: ECDSA signature.".to_string())
    } else if trimmed.contains("://") {
        Ok(trimmed.to_string())
    } else if let Ok(decoded) = decode_base64_flexible(trimmed) {
        let decoded_str = String::from_utf8_lossy(&decoded).into_owned();
        if decoded_str.contains("://") {
            Ok(decoded_str)
        } else {
            Ok(trimmed.to_string())
        }
    } else {
        Ok(trimmed.to_string())
    }
}

pub fn default_vip_max_bytes() -> u64 {
    VIP_MAX_BYTES
}
