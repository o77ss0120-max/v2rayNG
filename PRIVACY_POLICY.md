# Privacy Policy — Penny VPN (`com.penny.vpn`)

**Effective Date:** November 10, 2026  
**Version:** 2.9.4

## 1. Overview
Penny VPN (`com.penny.vpn`) provides encrypted VPN tunneling on Android and Android TV using Android's `VpnService` API. This policy transparently discloses what data is never collected and what technical identifiers are processed strictly for quota enforcement and service operation.

## 2. Data We Do NOT Collect
- **No Browsing or Payload Logs:** Penny VPN does not log, store, or inspect the websites you visit, DNS queries, destination IPs, or payload contents transmitted through the VPN tunnel.
- **No Camera, Contacts, or Location Permissions:** The app does not request camera, microphone, GPS location, or contact list permissions.

## 3. Data Processed for Quota Enforcement & Service Operation
- **Android VpnService (`android.permission.BIND_VPN_SERVICE`):** Used exclusively to establish a local encrypted tunnel between your device and the selected VPN server node.
- **Hardware-Derived Identifiers for Quota Sync (`deviceId` & `userUuid`):**
  - To enforce the free daily VIP Mode quota (1 GB/day) and the 1-Month / 50 GB VIP License limit across app reinstalls, the app computes a one-way SHA-256 cryptographic hash (`deviceId` and deterministic `userUuid`) derived from your device's `Settings.Secure.ANDROID_ID` and MediaDrm Widevine hardware identifier.
  - During quota synchronization (`/api/v1/quota/daily-sync`) and VIP license activation/heartbeat (`/api/v1/license/activate`, `/api/v1/license/heartbeat`), the app transmits only: `deviceId`, `userUuid`, license serial (if VIP), cumulative byte counter (`usedBytes` / `localUsedBytes`), and current mode (`PUBLIC`, `DEDICATED`, or `GHOST`) over HTTPS.
  - Raw hardware identifiers are never transmitted, and identifiers are masked (`[PROTECTED-UUID]`, `[PROTECTED-HWID]`) in local diagnostic logs.
- **Advertising & Sponsor Links (Free Tier Only):**
  - Free-tier users may see Google AdMob ads (which use `com.google.android.gms.permission.AD_ID`) or open external sponsor Smartlinks in their system browser when voluntarily claiming the `+500 MB Free VIP Quota` reward or clicking the sponsor banner.
  - Paid VIP subscribers operate in 100% Ad-Free mode.

## 4. Security Measures
- Remote subscription lists require a valid ECDSA P-256 digital signature (`#PENNY-SIG:`). Unsigned or tampered lists are rejected.
- Third-party proxy relays (`ghproxy.net`) are disabled.
- Global cleartext HTTP traffic is disabled (`usesCleartextTraffic="false"`), allowing loopback (`127.0.0.1`) only for the local proxy tunnel.

---

## سیاست حفظ حریم خصوصی — Penny VPN (`com.penny.vpn`)

۱. **عدم ثبت ترافیک و تاریخچه مرور:** اپلیکیشن Penny VPN هیچ‌گونه لاگی از سایت‌های بازدیدشده، درخواست‌های DNS یا محتوای ترافیک عبوری شما ذخیره یا بررسی نمی‌کند.  
۲. **شناسه‌های فنی جهت مدیریت سهمیه (Quota):** برای اعمال سهمیه ۱ گیگابایت روزانه رایگان در حالت VIP Mode و سهمیه ۵۰ گیگابایتی لایسنس‌های VIP، یک هش یک‌طرفه رمزنگاری‌شده (`deviceId` و `userUuid`) مشتق از `ANDROID_ID` و شناسه سخت‌افزاری Widevine به همراه میزان حجم مصرفی (`usedBytes`) و حالت اتصال (`PUBLIC`/`DEDICATED`/`GHOST`) از طریق HTTPS با سرور سهمیه همگام‌سازی می‌شود. این شناسه‌ها در لاگ‌های داخلی برنامه سانسور (`[PROTECTED-UUID]`) می‌شوند.  
۳. **تبلیغات در نسخه رایگان:** کاربران نسخه رایگان ممکن است تبلیغات Google AdMob یا لینک‌های حامی (Adsterra Smartlink) را هنگام دریافت پاداش `+500 MB` مشاهده کنند. در نسخه VIP تمامی تبلیغات غیرفعال هستند.
