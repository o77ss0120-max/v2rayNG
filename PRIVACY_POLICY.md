# Privacy Policy — Penny VPN (`com.penny.vpn`)

**Effective Date:** November 21, 2026  
**Version:** 2.9.8

## 1. Overview
Penny VPN (`com.penny.vpn`) provides encrypted VPN tunneling on Android and Android TV using Android's `VpnService` API. This policy discloses what data is never logged at the application layer, which optional device permissions exist, and what technical identifiers are processed for quota enforcement and ad attribution.

## 2. Application-Level Zero Browsing Logs & Infrastructure Transparency
- **No Browsing or Destination Logs:** Penny VPN and its official Edge Worker do not write browsing history, visited domains, destination IPs, DNS queries, or traffic payloads to persistent application storage or KV logs.
- **Cloud & Edge Transport Visibility:** Like all internet services, underlying hosting/CDN providers (such as Cloudflare Edge or cloud API gateways) transiently process your network IP address at the transport layer to route HTTPS requests and mitigate abuse/DDoS. Penny VPN does not persist your IP address in its quota database.

## 3. Device Permissions
- **Android VpnService (`android.permission.BIND_VPN_SERVICE`):** Establishes the encrypted tunnel between your device and the selected VPN node.
- **Optional Camera (`android.permission.CAMERA`):** Requested only at runtime when you explicitly open the QR Code Scanner (`ScannerActivity`) to import a server configuration. Camera frames are decoded strictly on-device in memory and are never saved or transmitted.
- **No Microphone, GPS Location, or Contacts:** The app never requests microphone, location, or contacts permissions.

## 4. Data Processed for Service & Quota Enforcement
- **Hardware-Derived Pseudonymous Quota Identifiers (`deviceId` & `userUuid`):**
  - Computed locally via one-way SHA-256 hash over Android ID, MediaDrm Widevine ID, and device hardware properties (64-bit `deviceId` and 128-bit RFC 4122 `userUuid`).
  - Quota sync requests transmit only `deviceId`, `userUuid`, license serial (VIP only), cumulative byte counters (`usedBytes` / `deltaBytes`), and active tier mode (`PUBLIC`, `DEDICATED`, or `GHOST`) over HTTPS.
- **Advertising (Free Tier Only, Consent-Gated):**
  - Free-tier users must provide explicit in-app consent before advertising SDKs (`Start.io SDK`, App ID `210814549`, disabled in F-Droid builds) or AADS sponsor banners are initialized.
  - **Direct-IP Ad Notice:** To comply with ad-network anti-fraud rules, viewing a sponsored ad temporarily pauses the VPN tunnel so the ad request uses your device's direct network IP, then reconnects automatically. Do not accept the ad consent dialog if you do not wish to expose your direct IP to ad partners.
  - Active VIP subscribers are 100% ad-free and advertising SDKs are never initialized for VIP users.

## 5. Network & Cryptographic Protections
- Global cleartext HTTP traffic is disabled (`usesCleartextTraffic="false"` and `<base-config cleartextTrafficPermitted="false">`); cleartext is strictly scoped to local loopback (`127.0.0.1` / `localhost`).
- System CA trust anchors only (`<certificates src="system" />`).
- Remote subscription lists and remote Stealth Vault updates require valid ECDSA P-256 (`#PENNY-SIG:`) cryptographic signatures.

---

## سیاست حفظ حریم خصوصی — Penny VPN (`com.penny.vpn`)

۱. **عدم ثبت تاریخچه مرور در لایه اپلیکیشن:** اپلیکیشن Penny VPN و ورکر رسمی آن هیچ‌گونه لاگی از دامنه‌ها، آی‌پی مقصد، کوئری‌های DNS یا محتوای ترافیک کاربران در دیتابیس یا KV ذخیره نمی‌کنند. با این حال، زیرساخت شبکه میزبان (مانند کلودفلر یا سرورهای ابری) به‌صورت طبیعی در لایه شبکه برای مسیریابی درخواست‌های HTTPS آی‌پی مبدا را مشاهده می‌کند.  
۲. **دسترسی اختیاری دوربین (`CAMERA`):** دسترسی دوربین تنها در زمان باز کردن اسکنر QR جهت وارد کردن کانفیگ درخواست می‌شود و پردازش تصویر کاملاً آفلاین روی دستگاه انجام شده و هیچ تصویری ذخیره یا ارسال نمی‌شود. اپلیکیشن دسترسی به میکروفون، موقعیت مکانی یا مخاطبین ندارد.  
۳. **مدیریت سهمیه (Quota):** جهت اعمال سهمیه ۱ گیگابایت روزانه (بازنشانی در 00:00 UTC) و اشتراک‌های VIP، شناسه هش‌شده یک‌طرفه (`deviceId` ۶۴ بیتی و `userUuid` ۱۲۸ بیتی) به همراه شمارنده حجم مصرفی از طریق HTTPS با سرور همگام‌سازی می‌شود.  
۴. **تبلیغات در نسخه رایگان (با رضایت صریح کاربر):** راه‌اندازی SDK تبلیغاتی **Start.io** (که در نسخه F-Droid غیرفعال است) و نمایش بنرهای حامی AADS تنها پس از تایید صریح کاربر انجام می‌شود. هنگام نمایش تبلیغ، تونل VPN به‌طور موقت متوقف می‌شود تا درخواست تبلیغ از آی‌پی مستقیم دستگاه ارسال شود و سپس مجدداً وصل می‌گردد؛ اگر مایل به نمایش آی‌پی مستقیم خود به شبکه تبلیغاتی نیستید، رضایت تبلیغ را تایید نکنید. در نسخه VIP هیچ تبلیغی نمایش داده نمی‌شود.
