# Security Policy — Penny VPN (`com.penny.vpn`)

## Signing & CI Hygiene
- Release APKs are signed exclusively using encrypted GitHub Actions Secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`).
- Official GitHub Releases are only published when production keystore secrets are present; ephemeral test builds are uploaded solely as workflow artifacts.

## Transport & Certificate Validation
- Global cleartext HTTP is disabled (`cleartextTrafficPermitted="false"`). Only `127.0.0.1` and `localhost` permit loopback HTTP traffic for the local tunnel proxy.
- Only Android system trust anchors are trusted (`<certificates src="system" />`).
- TLS probes and Clean IP scanners enforce explicit `HostnameVerifier` verification against the expected SNI/host certificate.

## Licensing & Server Subscriptions
- Legacy static/offline license codes and the pre-rotation public key have been permanently revoked.
- VIP licenses use `PL1.<payload>.<ecdsa_p256_sig>` verified online against the backend license server.
- Signed subscription payloads (`#PENNY-SIG:`) enforce ECDSA P-256 verification and reject invalid signatures.

## Reporting a Vulnerability
Please report security issues privately through GitHub Security Advisories for `o77ss0120-max/v2rayNG`.
