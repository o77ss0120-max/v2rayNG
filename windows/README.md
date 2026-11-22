# Penny VPN for Windows (Tauri v2 + Xray-core)

Native Windows 10/11 desktop client for Penny VPN built with **Tauri v2 (Rust)**, **React 19 + TypeScript + Tailwind CSS v4**, and bundled **Xray-core (`xray.exe`)**.

## Shared Security & Protocol Compatibility with Android
- **Pinned ECDSA P-256 verification** (`ROTATED_PUBLIC_KEY_B64`) for both `PL1.` VIP licenses and `#PENNY-SIG:` server lists.
- **Stealth Vault decoder** supporting `OTTO v2` and authenticated `OTTO v3` (HMAC-SHA256).
- **64-bit Hardware Device ID & 128-bit User UUID** derived from Windows Registry `MachineGuid` + `ComputerHardwareId`.
- **Two routing modes**:
  - **System Proxy** (default, no UAC prompt): sets `HKCU\...\Internet Settings` to `127.0.0.1:10809` with instant `InternetSetOptionW` refresh and guaranteed restore on disconnect/exit.
  - **TUN Mode** (optional, requires Administrator elevation): creates a virtual `PennyTun` interface inside Xray-core to tunnel all TCP/UDP desktop applications.
- **Anti-DPI & Clean DNS**: TLS ClientHello fragmentation (`NORMAL` / `SMART` / `GHOST`), Cloudflare DoH (`1.1.1.2`), and zero-external-file domestic `.ir` + RFC1918 LAN bypass.

## Local Development on Windows
1. Install **Node.js 22+**, **Rust stable (`x86_64-pc-windows-msvc`)**, and **Microsoft Edge WebView2**.
2. Download official `Xray-windows-64.zip` from `https://github.com/XTLS/Xray-core/releases` and place `xray.exe` at:
   ```text
   windows/src-tauri/binaries/xray-x86_64-pc-windows-msvc.exe
   ```
3. Install dependencies and run tests:
   ```powershell
   cd windows
   npm install
   cargo test --manifest-path src-tauri/Cargo.toml
   npm run tauri dev
   ```
4. Build the Windows NSIS `.exe` installer:
   ```powershell
   npm run tauri build -- --bundles nsis
   ```

## Automated GitHub Actions Release
The workflow `.github/workflows/build_penny_vpn_windows.yml` runs on `windows-latest`:
- Downloads pinned `Xray-core` `v25.3.6` and verifies its official SHA-256 digest.
- Generates crisp multi-resolution Windows `.ico` / `.png` icons from `V2rayNG/app/src/main/ic_launcher-web.png`.
- Executes `cargo test` for ECDSA, Stealth Vault, and license codec tests.
- Produces both the NSIS installer (`Penny-VPN_2.9.8_x64-setup.exe`) and a portable archive (`Penny-VPN-Windows-Portable-v2.9.8.zip`) with `SHA256SUMS-windows.txt`.
