# Penny VPN (v2.9.7)

**Connect When Others Can't** — Anti-Censorship VPN Client for Android & Android TV (`com.penny.vpn`)

## Key Capabilities
- **On-Device Cloudflare Clean IP Scanner**: Tests Cloudflare Anycast subnets across TLS ports (`443`, `8443`, `2083`) using fragmented `tlshello` handshakes and strict TLS hostname verification.
- **ECDSA P-256 & Stealth Vault Subscriptions**:
  - Lists signed with `#PENNY-SIG:` undergo mandatory ECDSA P-256 verification and are rejected on mismatch.
  - Built-in Dedicated Vault uses `OTTO` SHA-256-CTR obfuscation; community Public Mode lists are segregated under `PUBLIC` mode.
  - Third-party proxy relays (`ghproxy.net`) are permanently disabled.
- **Three Connection Modes**:
  - `VIP Mode` (1 GB/day free quota or 50 GB VIP License)
  - `Ghost Mode` (Emergency Messaging-Only lock: shared 42 KB/s down / 28 KB/s up limiter + heavy media domain & app isolation, safely restoring Per-App Proxy settings on exit)
  - `Public Mode` (Unlimited community fallback nodes)
- **Iran ISP Auto-Tuner & Network Doctor**: Carrier-specific TLS fragment presets (Irancell, Hamrah Aval/MCI, Rightel) and one-tap 4-stage diagnostics (`DNS -> Fragment -> Route -> Speed`).
- **Iran Direct Routing & Split Tunneling**: Domestic `.ir` domains and local LAN subnets bypass the tunnel cleanly (`PREF_VPN_BYPASS_LAN = "1"`).
- **Direct-IP Transparent Ads**: Free tier uses Start.io SDK (`+100 MB` per rewarded ad, up to `3/day`), pausing VPN briefly so requests originate from the user's real ISP IP.

## Security
See [`SECURITY.md`](./SECURITY.md) and [`PRIVACY_POLICY.md`](./PRIVACY_POLICY.md).
