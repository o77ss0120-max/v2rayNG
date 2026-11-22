use crate::crypto::{compute_device_id_hex, compute_user_uuid_v4};
use sha2::{Digest, Sha256};

#[derive(Debug, Clone)]
pub struct DeviceIdentity {
    pub device_id: String,
    pub user_uuid: String,
}

#[cfg(windows)]
fn read_windows_machine_seed() -> String {
    use winreg::enums::HKEY_LOCAL_MACHINE;
    use winreg::RegKey;

    let hklm = RegKey::predef(HKEY_LOCAL_MACHINE);
    let machine_guid = hklm
        .open_subkey("SOFTWARE\\Microsoft\\Cryptography")
        .and_then(|k| k.get_value::<String, _>("MachineGuid"))
        .unwrap_or_default();
    let hw_id = hklm
        .open_subkey("SYSTEM\\CurrentControlSet\\Control\\SystemInformation")
        .and_then(|k| k.get_value::<String, _>("ComputerHardwareId"))
        .unwrap_or_default();
    let computer_name = std::env::var("COMPUTERNAME").unwrap_or_default();
    format!("PENNY_WIN_HW_V1:{machine_guid}:{hw_id}:{computer_name}")
}

#[cfg(not(windows))]
fn read_windows_machine_seed() -> String {
    let host = std::env::var("HOSTNAME")
        .or_else(|_| std::env::var("USER"))
        .unwrap_or_else(|_| "penny-desktop".to_string());
    format!("PENNY_DESKTOP_FALLBACK_V1:{host}")
}

pub fn get_device_identity() -> DeviceIdentity {
    let seed = read_windows_machine_seed();
    let digest = Sha256::digest(seed.as_bytes());
    DeviceIdentity {
        device_id: compute_device_id_hex(&digest, 8),
        user_uuid: compute_user_uuid_v4(&digest),
    }
}
