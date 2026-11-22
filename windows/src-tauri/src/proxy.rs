#[cfg(windows)]
mod win {
    use std::ffi::c_void;
    use winreg::enums::{HKEY_CURRENT_USER, KEY_READ, KEY_WRITE};
    use winreg::RegKey;

    const INTERNET_OPTION_SETTINGS_CHANGED: u32 = 39;
    const INTERNET_OPTION_REFRESH: u32 = 37;

    #[link(name = "wininet")]
    extern "system" {
        fn InternetSetOptionW(
            h_internet: *mut c_void,
            dw_option: u32,
            lp_buffer: *mut c_void,
            dw_buffer_length: u32,
        ) -> i32;
    }

    fn notify_wininet_proxy_changed() {
        unsafe {
            InternetSetOptionW(
                std::ptr::null_mut(),
                INTERNET_OPTION_SETTINGS_CHANGED,
                std::ptr::null_mut(),
                0,
            );
            InternetSetOptionW(
                std::ptr::null_mut(),
                INTERNET_OPTION_REFRESH,
                std::ptr::null_mut(),
                0,
            );
        }
    }

    pub fn set_system_proxy(host: &str, http_port: u16, bypass_domestic: bool) -> Result<(), String> {
        let hkcu = RegKey::predef(HKEY_CURRENT_USER);
        let key = hkcu
            .open_subkey_with_flags(
                "Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                KEY_WRITE,
            )
            .map_err(|e| format!("Failed to open Internet Settings registry key: {e}"))?;

        let proxy_server = format!("{host}:{http_port}");
        let bypass = if bypass_domestic {
            "localhost;127.*;10.*;172.16.*;172.17.*;172.18.*;172.19.*;172.2*.*;172.30.*;172.31.*;192.168.*;*.local;*.ir;<local>"
        } else {
            "localhost;127.*;<local>"
        };

        key.set_value("ProxyEnable", &1u32)
            .map_err(|e| format!("Failed to set ProxyEnable: {e}"))?;
        key.set_value("ProxyServer", &proxy_server)
            .map_err(|e| format!("Failed to set ProxyServer: {e}"))?;
        key.set_value("ProxyOverride", &bypass)
            .map_err(|e| format!("Failed to set ProxyOverride: {e}"))?;

        notify_wininet_proxy_changed();
        Ok(())
    }

    pub fn clear_system_proxy() -> Result<(), String> {
        let hkcu = RegKey::predef(HKEY_CURRENT_USER);
        let key = hkcu
            .open_subkey_with_flags(
                "Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                KEY_WRITE,
            )
            .map_err(|e| format!("Failed to open Internet Settings registry key: {e}"))?;

        key.set_value("ProxyEnable", &0u32)
            .map_err(|e| format!("Failed to disable ProxyEnable: {e}"))?;
        notify_wininet_proxy_changed();
        Ok(())
    }

    pub fn set_autostart(enabled: bool) -> Result<(), String> {
        let hkcu = RegKey::predef(HKEY_CURRENT_USER);
        let key = hkcu
            .open_subkey_with_flags(
                "Software\\Microsoft\\Windows\\CurrentVersion\\Run",
                KEY_WRITE,
            )
            .map_err(|e| format!("Failed to open Run registry key: {e}"))?;

        if enabled {
            let exe = std::env::current_exe()
                .map_err(|e| format!("Failed to resolve executable path: {e}"))?;
            let quoted = format!("\"{}\"", exe.display());
            key.set_value("PennyVPN", &quoted)
                .map_err(|e| format!("Failed to register autostart: {e}"))?;
        } else {
            let _ = key.delete_value("PennyVPN");
        }
        Ok(())
    }

    pub fn get_autostart() -> bool {
        let hkcu = RegKey::predef(HKEY_CURRENT_USER);
        hkcu.open_subkey_with_flags(
            "Software\\Microsoft\\Windows\\CurrentVersion\\Run",
            KEY_READ,
        )
        .and_then(|k| k.get_value::<String, _>("PennyVPN"))
        .map(|v| !v.trim().is_empty())
        .unwrap_or(false)
    }
}

#[cfg(not(windows))]
mod win {
    pub fn set_system_proxy(_host: &str, _http_port: u16, _bypass_domestic: bool) -> Result<(), String> {
        Ok(())
    }
    pub fn clear_system_proxy() -> Result<(), String> {
        Ok(())
    }
    pub fn set_autostart(_enabled: bool) -> Result<(), String> {
        Ok(())
    }
    pub fn get_autostart() -> bool {
        false
    }
}

pub use win::{clear_system_proxy, get_autostart, set_autostart, set_system_proxy};
