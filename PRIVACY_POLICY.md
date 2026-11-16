# Privacy Policy — Penny VPN (`com.penny.vpn`)

**Effective Date:** November 15, 2026  
**Version:** 2.9.7

## 1. Overview
Penny VPN (`com.penny.vpn`) provides encrypted VPN tunneling on Android and Android TV using Android's `VpnService` API. This policy discloses what data is never collected and what technical identifiers are processed for quota enforcement and ad attribution.

## 2. Zero Browsing Logs
- **No Browsing or Destination Logs:** Penny VPN and its official Cloudflare Edge Worker do not log visited domains, destination IPs, ports, DNS queries, or payload traffic.
- **No Sensitive Device Permissions:** The app does not request camera (`CAMERA`), microphone, GPS location, or contacts permissions.

## 3. Data Processed for Service & Quota Enforcement
- **Android VpnService (`android.permission.BIND_VPN_SERVICE`):** Establishes the encrypted tunnel between your device and the selected VPN node.
- **Hardware-Derived Pseudonymous Quota Identifiers (`deviceId` & `userUuid`):**
  - Computed locally via one-way SHA-256 hash over Android ID and MediaDrm Widevine ID to enforce the daily 1 GB Dedicated quota (reset at 00:00 UTC) and the 1-Month / 50 GB VIP license quota.
  - Quota sync requests transmit only `deviceId`, `userUuid`, license serial (VIP only), cumulative byte counters (`usedBytes` / `deltaBytes`), and active tier mode (`PUBLIC`, `DEDICATED`, or `GHOST`) over HTTPS.
- **Advertising (Free Tier Only):**
  - Free-tier users may optionally watch rewarded video ads via **Start.io SDK** (`com.startapp:inapp-sdk`, App ID `210814549`) to unlock `+100 MB` per ad (max 3/day), which may use the Android Advertising ID (`com.google.android.gms.permission.AD_ID`).
  - Active VIP subscribers are 100% ad-free and Start.io SDK is not initialized for VIP users.

## 4. Network & Cryptographic Protections
- Global cleartext HTTP traffic is disabled (`usesCleartextTraffic="false"` and `<base-config cleartextTrafficPermitted="false">`); cleartext is strictly scoped to local loopback (`127.0.0.1` / `localhost`).
- System CA trust anchors only (`<certificates src="system" />`).

---

## سیاست حفظ حریم خصوصی — Penny VPN (`com.penny.vpn`)

۱. **عدم ثبت تاریخچه مرور:** اپلیکیشن Penny VPN و ورکر کلودفلر رسمی آن هیچ‌گونه لاگی از دامنه‌ها، آی‌پی مقصد، پورت یا محتوای ترافیک کاربران ثبت نمی‌کنند.  
۲. **بدون دسترسی‌های غیرضروری:** اپلیکیشن هیچ دسترسی به دوربین، میکروفون، موقعیت مکانی یا مخاطبین ندارد.  
۳. **مدیریت سهمیه (Quota):** جهت اعمال سهمیه ۱ گیگابایت روزانه (بازنشانی در 00:00 UTC) و بسته‌های ۵۰ گیگابایتی VIP، شناسه هش‌شده یک‌طرفه (`deviceId` و `userUuid`) به همراه شمارنده حجم مصرفی از طریق HTTPS با سرور همگام‌سازی می‌شود.  
۴. **تبلیغات در نسخه رایگان:** کاربران نسخه رایگان می‌توانند برای دریافت `+100 MB` حجم اضافه (حداکثر ۳ بار در روز) تبلیغات جایزه‌دار **Start.io SDK** (شناسه `210814549`) را با آی‌پی مستقیم خود مشاهده کنند. در نسخه VIP هیچ تبلیغی نمایش داده نمی‌شود و SDK تبلیغاتی نیز راه‌اندازی نمی‌شود.
