use base64::engine::general_purpose::{STANDARD, URL_SAFE_NO_PAD};
use base64::Engine;
use hmac::{Hmac, Mac};
use p256::ecdsa::{signature::Verifier, Signature, VerifyingKey};
use p256::pkcs8::DecodePublicKey;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};

type HmacSha256 = Hmac<Sha256>;

pub const VIP_DURATION_MS: i64 = 30 * 24 * 60 * 60 * 1000;
pub const VIP_MAX_BYTES: u64 = 50 * 1024 * 1024 * 1024;
pub const MIN_VIP_BYTES: u64 = 1 * 1024 * 1024 * 1024;
pub const MAX_VIP_BYTES: u64 = 1000 * 1024 * 1024 * 1024;

pub const ROTATED_PUBLIC_KEY_B64: &str =
    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEf1YXmwm/4ojudflyQNF470nc0kSAIdSM+K5OUIWqHHhxNCusYcvAygnbn9OFs6CGEpxTRB7AK6xfyyStgOE11Q==";

const STEALTH_VAULT_KEY_PREFIX: &str = "PENNY_STEALTH_VAULT_KEY_2026_V3:";
const STEALTH_VAULT_MAC_KEY: &str = "PENNY_STEALTH_VAULT_MAC_2026_V3";
const MAX_LOCAL_CLOCK_DRIFT_MS: i64 = 7 * 24 * 60 * 60 * 1000;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct VerifiedLicense {
    pub serial: String,
    pub device_id: String,
    pub duration_ms: i64,
    pub max_bytes: u64,
}

#[derive(Debug, Deserialize)]
struct JsonLicensePayload {
    s: Option<String>,
    d: Option<String>,
    exp: Option<i64>,
    max: Option<u64>,
}

pub fn decode_base64_flexible(input: &str) -> Result<Vec<u8>, String> {
    let cleaned: String = input
        .chars()
        .filter(|c| !c.is_whitespace())
        .map(|c| match c {
            '-' => '+',
            '_' => '/',
            other => other,
        })
        .collect();
    if cleaned.is_empty() {
        return Err("Empty base64 input".to_string());
    }
    let rem = cleaned.len() % 4;
    let padded = if rem == 0 {
        cleaned
    } else {
        format!("{}{}", cleaned, "=".repeat(4 - rem))
    };
    STANDARD
        .decode(padded.as_bytes())
        .map_err(|e| format!("Invalid base64: {e}"))
}

pub fn encode_base64_url_no_pad(bytes: &[u8]) -> String {
    URL_SAFE_NO_PAD.encode(bytes)
}

pub fn compute_device_id_hex(hw_digest: &[u8], bytes_count: usize) -> String {
    let count = bytes_count.clamp(6, 16);
    if hw_digest.len() < count {
        return "0000-0000-0000-0000".to_string();
    }
    let mut out = String::with_capacity(count * 2 + (count / 2));
    for (i, b) in hw_digest.iter().take(count).enumerate() {
        if i > 0 && i % 2 == 0 {
            out.push('-');
        }
        out.push_str(&format!("{:02X}", b));
    }
    out
}

pub fn compute_user_uuid_v4(hw_digest: &[u8]) -> String {
    if hw_digest.len() < 16 {
        return "00000000-0000-4000-8000-000000000000".to_string();
    }
    let mut d = [0u8; 16];
    d.copy_from_slice(&hw_digest[..16]);
    d[6] = (d[6] & 0x0F) | 0x40;
    d[8] = (d[8] & 0x3F) | 0x80;
    format!(
        "{:02x}{:02x}{:02x}{:02x}-{:02x}{:02x}-{:02x}{:02x}-{:02x}{:02x}-{:02x}{:02x}{:02x}{:02x}{:02x}{:02x}",
        d[0], d[1], d[2], d[3],
        d[4], d[5],
        d[6], d[7],
        d[8], d[9],
        d[10], d[11], d[12], d[13], d[14], d[15]
    )
}

pub fn resolve_effective_now_ms(
    system_wall_ms: i64,
    stored_last_seen_ms: i64,
    last_server_now_ms: i64,
    elapsed_at_sync_ms: i64,
    current_elapsed_ms: i64,
) -> i64 {
    if last_server_now_ms > 0 && elapsed_at_sync_ms > 0 && current_elapsed_ms >= elapsed_at_sync_ms {
        let server_derived = last_server_now_ms + (current_elapsed_ms - elapsed_at_sync_ms);
        return server_derived.max(system_wall_ms.min(server_derived + MAX_LOCAL_CLOCK_DRIFT_MS));
    }
    if stored_last_seen_ms > system_wall_ms
        && (stored_last_seen_ms - system_wall_ms) <= MAX_LOCAL_CLOCK_DRIFT_MS
    {
        stored_last_seen_ms
    } else {
        system_wall_ms
    }
}

pub fn verify_ecdsa_signature(data: &[u8], sig_der: &[u8], pub_key_b64: &str) -> bool {
    let Ok(key_der) = decode_base64_flexible(pub_key_b64) else {
        return false;
    };
    let Ok(verifying_key) = VerifyingKey::from_public_key_der(&key_der) else {
        return false;
    };
    let Ok(sig) = Signature::from_der(sig_der) else {
        return false;
    };
    verifying_key.verify(data, &sig).is_ok()
}

pub fn verify_license_offline_with_key(
    raw_code: &str,
    current_device_id: &str,
    effective_now_ms: i64,
    pub_key_b64: &str,
) -> Result<VerifiedLicense, String> {
    let trimmed: String = raw_code.chars().filter(|c| !c.is_whitespace()).collect();
    let upper_prefix = trimmed.get(0..4).unwrap_or("").to_ascii_uppercase();
    if upper_prefix != "PL1." && upper_prefix != "PV1." {
        return Err("Invalid license format. Use a signed ECDSA P-256 license (PL1....).".to_string());
    }
    let parts: Vec<&str> = trimmed.split('.').collect();
    if parts.len() != 3 {
        return Err("Malformed ECDSA license token.".to_string());
    }

    let payload_bytes = decode_base64_flexible(parts[1])?;
    let sig_bytes = decode_base64_flexible(parts[2])?;
    if !verify_ecdsa_signature(&payload_bytes, &sig_bytes, pub_key_b64) {
        return Err("Invalid or revoked ECDSA P-256 signature!".to_string());
    }

    let payload_str = String::from_utf8(payload_bytes)
        .map_err(|_| "License payload is not valid UTF-8.".to_string())?
        .trim()
        .to_string();
    let current_dev_upper = current_device_id.trim().to_ascii_uppercase();

    if payload_str.starts_with('{') {
        let json: JsonLicensePayload = serde_json::from_str(&payload_str)
            .map_err(|_| "Corrupted JSON license payload.".to_string())?;
        let serial = json.s.unwrap_or_default().trim().to_ascii_uppercase();
        if serial.is_empty() {
            return Err("Missing license serial in token.".to_string());
        }
        let target_device = json
            .d
            .unwrap_or_else(|| "ANY".to_string())
            .trim()
            .to_ascii_uppercase();
        if target_device != "ANY" && target_device != "*" && target_device != current_dev_upper {
            return Err("License is locked to a different Device ID.".to_string());
        }
        let raw_exp = json.exp.unwrap_or(0);
        let exp_ms = if raw_exp > 0 && raw_exp < 10_000_000_000 {
            raw_exp * 1000
        } else {
            raw_exp
        };
        let max_bytes = json
            .max
            .unwrap_or(VIP_MAX_BYTES)
            .clamp(MIN_VIP_BYTES, MAX_VIP_BYTES);
        let duration_ms = if exp_ms > 0 {
            let rem = exp_ms - effective_now_ms;
            if rem <= 0 {
                return Err("License token has expired.".to_string());
            }
            rem
        } else {
            VIP_DURATION_MS
        };
        return Ok(VerifiedLicense {
            serial,
            device_id: target_device,
            duration_ms,
            max_bytes,
        });
    }

    let sep = if payload_str.contains('|') { '|' } else { ':' };
    let fields: Vec<&str> = payload_str.split(sep).collect();
    if fields.len() < 3 {
        return Err("Corrupted license payload.".to_string());
    }
    let serial = fields[0].trim().to_ascii_uppercase();
    if serial.is_empty() {
        return Err("Missing license serial in token.".to_string());
    }
    let target_device = fields[1].trim().to_ascii_uppercase();
    if target_device != "ANY" && target_device != "*" && target_device != current_dev_upper {
        return Err("License is locked to a different Device ID.".to_string());
    }
    let raw_deadline = fields[2].trim().parse::<i64>().unwrap_or(0);
    let deadline_ms = if raw_deadline > 0 && raw_deadline < 10_000_000_000 {
        raw_deadline * 1000
    } else {
        raw_deadline
    };
    if deadline_ms > 0 && effective_now_ms > 0 && effective_now_ms > deadline_ms {
        return Err("Activation deadline for this license has expired.".to_string());
    }
    let days = fields
        .get(3)
        .and_then(|v| v.trim().parse::<i64>().ok())
        .unwrap_or(30)
        .clamp(1, 365);
    let gb = fields
        .get(4)
        .and_then(|v| v.trim().parse::<u64>().ok())
        .unwrap_or(50)
        .clamp(1, 1000);

    Ok(VerifiedLicense {
        serial,
        device_id: target_device,
        duration_ms: days * 24 * 3600 * 1000,
        max_bytes: gb * 1024 * 1024 * 1024,
    })
}

pub fn verify_signed_server_list(raw_content: &str, pub_key_b64: &str) -> Result<String, String> {
    let normalized = raw_content.replace("\r\n", "\n");
    let lines: Vec<&str> = normalized.trim().lines().collect();
    let first_line = lines
        .first()
        .map(|s| s.trim())
        .ok_or_else(|| "Empty server list".to_string())?;
    let sig_b64 = first_line
        .strip_prefix("#PENNY-SIG:")
        .ok_or_else(|| "Missing required #PENNY-SIG: header".to_string())?
        .trim();

    let canonical_lines: Vec<&str> = lines
        .iter()
        .skip(1)
        .map(|s| s.trim())
        .filter(|s| !s.is_empty() && !s.starts_with("#PENNY-SIG:"))
        .collect();
    let canonical_body = canonical_lines.join("\n");
    if canonical_body.is_empty() {
        return Err("Empty signed server list body".to_string());
    }

    let sig_bytes = decode_base64_flexible(sig_b64)?;
    if verify_ecdsa_signature(canonical_body.as_bytes(), &sig_bytes, pub_key_b64) {
        Ok(canonical_body)
    } else {
        Err("Invalid ECDSA P-256 signature on server list".to_string())
    }
}

pub fn decrypt_stealth_vault(raw_bytes: &[u8]) -> Result<String, String> {
    if raw_bytes.len() <= 8 || &raw_bytes[0..4] != b"OTTO" {
        return Err("Invalid stealth vault header".to_string());
    }
    let version_major = raw_bytes[5];
    let cipher_offset = if version_major >= 3 {
        if raw_bytes.len() <= 40 {
            return Err("Truncated OTTO v3 vault".to_string());
        }
        let provided_mac = &raw_bytes[8..40];
        let cipher_body = &raw_bytes[40..];
        let mut mac = HmacSha256::new_from_slice(STEALTH_VAULT_MAC_KEY.as_bytes())
            .map_err(|_| "HMAC init failed".to_string())?;
        mac.update(&raw_bytes[0..8]);
        mac.update(cipher_body);
        mac.verify_slice(provided_mac)
            .map_err(|_| "OTTO v3 HMAC verification failed".to_string())?;
        40
    } else {
        8
    };

    let payload_len = raw_bytes.len() - cipher_offset;
    let mut out = vec![0u8; payload_len];
    let mut block_idx: usize = 0;
    let mut offset: usize = 0;
    while offset < payload_len {
        let mut hasher = Sha256::new();
        hasher.update(format!("{STEALTH_VAULT_KEY_PREFIX}{block_idx}").as_bytes());
        let stream = hasher.finalize();
        for b in stream.iter() {
            if offset >= payload_len {
                break;
            }
            out[offset] = raw_bytes[cipher_offset + offset] ^ b;
            offset += 1;
        }
        block_idx += 1;
    }

    String::from_utf8(out)
        .map(|s| s.trim().to_string())
        .map_err(|_| "Decrypted stealth vault is not valid UTF-8".to_string())
}

#[cfg(test)]
mod tests {
    use super::*;
    use p256::ecdsa::{signature::Signer, SigningKey};
    use p256::pkcs8::EncodePublicKey;

    fn test_keypair(seed_byte: u8) -> (SigningKey, String) {
        let mut secret = [seed_byte; 32];
        secret[0] = seed_byte.max(1);
        let sk = SigningKey::from_bytes((&secret).into()).expect("valid test key");
        let vk = VerifyingKey::from(&sk);
        let der = vk.to_public_key_der().expect("spki der");
        (sk, STANDARD.encode(der.as_bytes()))
    }

    fn sign_token(sk: &SigningKey, payload: &str) -> String {
        let sig: Signature = sk.sign(payload.as_bytes());
        let sig_der = sig.to_der();
        format!(
            "PL1.{}.{}",
            encode_base64_url_no_pad(payload.as_bytes()),
            encode_base64_url_no_pad(sig_der.as_bytes())
        )
    }

    fn encrypt_vault(plaintext: &str, major: u8) -> Vec<u8> {
        let plain = plaintext.as_bytes();
        let mut cipher = vec![0u8; plain.len()];
        let mut block = 0usize;
        let mut offset = 0usize;
        while offset < plain.len() {
            let mut h = Sha256::new();
            h.update(format!("{STEALTH_VAULT_KEY_PREFIX}{block}").as_bytes());
            let stream = h.finalize();
            for b in stream.iter() {
                if offset >= plain.len() {
                    break;
                }
                cipher[offset] = plain[offset] ^ b;
                offset += 1;
            }
            block += 1;
        }
        let header = [b'O', b'T', b'T', b'O', 0, major, 0, 0];
        let mut out = Vec::new();
        out.extend_from_slice(&header);
        if major >= 3 {
            let mut mac = HmacSha256::new_from_slice(STEALTH_VAULT_MAC_KEY.as_bytes()).unwrap();
            mac.update(&header);
            mac.update(&cipher);
            out.extend_from_slice(&mac.finalize().into_bytes());
        }
        out.extend_from_slice(&cipher);
        out
    }

    #[test]
    fn verifies_pipe_and_json_licenses() {
        let (sk, pub_b64) = test_keypair(7);
        let now = 1_800_000_000_000i64;
        let token = sign_token(&sk, &format!("WIN001|A1B2-C3D4-E5F6-0708|{}|60|100", now + 60_000));
        let lic = verify_license_offline_with_key(&token, "A1B2-C3D4-E5F6-0708", now, &pub_b64)
            .expect("pipe license valid");
        assert_eq!(lic.serial, "WIN001");
        assert_eq!(lic.duration_ms, 60 * 24 * 3600 * 1000);
        assert_eq!(lic.max_bytes, 100 * 1024 * 1024 * 1024);

        let exp_sec = (now + 7 * 86_400_000) / 1000;
        let json_token = sign_token(
            &sk,
            &format!(r#"{{"s":"JSON01","d":"ANY","exp":{},"max":{}}}"#, exp_sec, 75u64 * 1024 * 1024 * 1024),
        );
        let jlic = verify_license_offline_with_key(&json_token, "1111-2222-3333-4444", now, &pub_b64)
            .expect("json license valid");
        assert_eq!(jlic.serial, "JSON01");
        assert_eq!(jlic.max_bytes, 75 * 1024 * 1024 * 1024);

        assert!(verify_license_offline_with_key(&token, "9999-8888-7777-6666", now, &pub_b64).is_err());
    }

    #[test]
    fn verifies_signed_server_list_and_rejects_tamper() {
        let (sk, pub_b64) = test_keypair(11);
        let body = "vless://11111111-1111-4111-8111-111111111111@example.com:443#DE-1";
        let sig: Signature = sk.sign(body.as_bytes());
        let signed = format!("#PENNY-SIG:{}\n{}", STANDARD.encode(sig.to_der().as_bytes()), body);
        assert_eq!(verify_signed_server_list(&signed, &pub_b64).unwrap(), body);

        let tampered = format!("{signed}\nvless://evil@attacker.test:443#Evil");
        assert!(verify_signed_server_list(&tampered, &pub_b64).is_err());
        assert!(verify_signed_server_list(body, &pub_b64).is_err());
    }

    #[test]
    fn decrypts_vault_v2_and_hmac_v3() {
        let sample = "#PENNY-SIG:ABC\nvless://node@1.1.1.1:443#Test";
        let v2 = encrypt_vault(sample, 2);
        assert_eq!(decrypt_stealth_vault(&v2).unwrap(), sample);

        let mut v3 = encrypt_vault(sample, 3);
        assert_eq!(decrypt_stealth_vault(&v3).unwrap(), sample);

        let last = v3.len() - 1;
        v3[last] ^= 0xFF;
        assert!(decrypt_stealth_vault(&v3).is_err());
    }

    #[test]
    fn clock_ratchet_and_identifiers() {
        let real = 1_800_000_000_000i64;
        assert_eq!(resolve_effective_now_ms(real, real + 365 * 86_400_000, 0, 0, 0), real);
        assert_eq!(
            resolve_effective_now_ms(real - 10 * 86_400_000, real + 365 * 86_400_000, real, 1000, 6000),
            real + 5000
        );
        let digest = Sha256::digest(b"windows-test-seed");
        let dev = compute_device_id_hex(&digest, 8);
        assert_eq!(dev.len(), 19);
        let uuid = compute_user_uuid_v4(&digest);
        assert_eq!(uuid.len(), 36);
        assert_eq!(&uuid[14..15], "4");
    }
}
