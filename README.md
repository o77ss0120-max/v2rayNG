# Penny VPN (v2.6.2)
**Connects When Others Can't** — Smart Anti-Censorship VPN Client for Android (`com.pennyvpn.app`)

## Key Capabilities
- **On-Device Cloudflare Clean IP Scanner**: Automatically tests 14 Cloudflare Anycast subnets across ports `443`, `8443`, and `2083` using fragmented TLS handshakes.
- **Iran ISP Auto-Detector & TLS Fragment Tuner**: Detects MCI (Hamrah Aval), Irancell (MTN), Rightel, and Broadband to apply optimal `tlshello` fragment chunk lengths and intervals.
- **Network Doctor**: One-tap 4-stage network diagnostic tool that detects ISP DNS hijacking (`10.10.34.36`), tunes TLS fragmentation, selects the lowest-latency Clean IP, and enables DoH (`1.1.1.1`).
- **Iran Direct Routing & Smart Split Tunneling**: Domestic `.ir` domains and banking apps bypass the VPN tunnel automatically.
- **User Config Preservation**: Subscription scans only manage tagged subscription nodes (`PENNY_MANAGED_SUB`) and never delete or rename personal configs imported by the user.
- **ECDSA P-256 Server-Verified VIP Plan**: Cryptographically signed `PL1.<payload>.<signature>` tokens with server-side 50 GB quota enforcement.
