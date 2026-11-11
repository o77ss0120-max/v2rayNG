# Penny VPN (v2.9.5)

**Connect When Others Can't** — Smart Anti-Censorship VPN Client for Android & Android TV (`com.penny.vpn`)

## Key Capabilities
- **On-Device Cloudflare Clean IP Scanner**: Automatically tests Cloudflare Anycast subnets across ports `443`, `8443`, and `2083` using fragmented TLS handshakes.
- **Strict ECDSA P-256 Signed Subscriptions**: Remote server lists require a valid `#PENNY-SIG:` ECDSA P-256 signature; unsigned or tampered lists are strictly rejected.
- **Iran ISP Auto-Detector & TLS Fragment Tuner**: Detects MCI, Irancell, Rightel, and Broadband to apply optimal `tlshello` fragment chunk lengths and intervals.
- **Network Doctor**: One-tap 4-stage network diagnostic tool that detects ISP DNS hijacking (`10.10.34.36`), tunes TLS fragmentation, selects the lowest-latency Clean IP, and enables DoH (`1.1.1.1`).
- **Iran Direct Routing & Smart Split Tunneling**: Domestic `.ir` domains and banking apps bypass the VPN tunnel automatically.
- **ECDSA P-256 Server-Verified VIP Plan**: Cryptographically signed `PL1.<payload>.<signature>` tokens with server-side 50 GB quota enforcement.
