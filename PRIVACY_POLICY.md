# Privacy Policy — Penny VPN (`com.penny.vpn`)

**Effective Date:** November 4, 2026

## 1. Overview
Penny VPN (`com.penny.vpn`) provides encrypted VPN tunneling using Android's `VpnService` API. We are committed to protecting user privacy and minimizing data collection.

## 2. Data We Do NOT Collect
- **No Browsing Logs:** Penny VPN does not log, store, or inspect the websites you visit, DNS queries, or payload contents transmitted through the VPN tunnel.
- **No Camera Access:** The `android.permission.CAMERA` permission has been removed from the application.

## 3. Data Processed for Service Operation
- **Android VpnService (`android.permission.BIND_VPN_SERVICE`):** Used exclusively to establish a local encrypted tunnel between your device and the selected ECDSA-verified VPN server node.
- **License & Quota Verification (VIP Plan):** If you activate a 1-Month / 50 GB VIP license (`PL1.<payload>.<signature>`), the app sends a cryptographic hash of your Android Device ID (`deviceId`), license serial number, and aggregate byte counter (`usedBytes`) over HTTPS to our license verification server to enforce the 30-day / 50 GB plan limit across app reinstalls.
- **Advertising ID (`com.google.android.gms.permission.AD_ID`):** For free-tier users, Google AdMob uses the Android Advertising ID to serve frequency-capped interstitial and user-initiated rewarded video ads (`+ Add Time`). VIP subscribers operate in Ad-Free mode.

## 4. Security Measures
- All server subscription lists require a valid ECDSA P-256 digital signature (`#PENNY-SIG:`).
- Cleartext HTTP traffic is disabled (`usesCleartextTraffic="false"`), and cloud/device backup of local preferences (`penny_prefs.xml`) is disabled (`allowBackup="false"`).

## 5. Contact
For privacy inquiries or support, contact the developer via the official GitHub repository.
