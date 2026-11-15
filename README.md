<div align="center">

# 🛡 Aqlli Qalqon

**A free, open-source PIN/fingerprint app-locker for shared family Android phones.**

Kids grab your phone and dive into Roblox, YouTube or browser games — Aqlli Qalqon
locks *only the apps you choose* behind your password or fingerprint, while the rest
of the phone stays fully usable. Wrong guesses have **no lockout penalty**, so a kid
mashing the screen never locks *you* out.

</div>

---

## ✨ Features

- 🔒 **Per-app lock** — pick any apps (Roblox, YouTube, browsers, stores, banking…);
  they open only with your password.
- 👆 **Fingerprint or PIN** — unlock in a second (PIN always works as fallback).
- ♾️ **No lockout penalty** — unlimited wrong attempts, no timeout, no OS lock-out.
- 🥷 **Stealth** — hide the launcher icon or disguise it as a **"Calculator."**
- ⚙️ **Settings locked** — kids can't reach Accessibility / uninstall paths.
- 🚫 **Uninstall protection** — registered as device admin; can't be removed without
  your password.
- 🌐 **Optional DNS filter** — block YouTube/Roblox inside browsers too.
- 📵 **No root, no device-owner, no account removal** — installs on any Samsung /
  Android phone (Android 10+).

## 📥 Install

1. Download the latest **`AqlliQalqon-vX.Y.Z.apk`** (from Releases, or the Actions
   artifacts).
2. Copy it to the phone and tap to install.
   - Samsung: turn off **Settings → Security and privacy → Auto Blocker** first.
   - If **Google Play Protect** blocks it, tap *More details → Install anyway*, or
     install via `adb install -r`.
3. Open **Aqlli Qalqon** (default password **1234**) and **change the password**.
4. **Settings → Apps → Aqlli Qalqon → ⋮ → Allow restricted settings**, then
   **Settings → Accessibility → Aqlli Qalqon → On** (this powers the locking).
5. (Optional) Enable **Uninstall protection** and pick your protected apps.

> **First test, then trust:** open a locked app to confirm the password screen
> appears, and — if using Hidden mode — confirm **Volume Up ×5** reopens the app
> *before* hiding the icon.

## 🧱 How it works

An Android **Accessibility service** watches which app comes to the foreground; if
it's protected, a full-screen (anonymous) PIN/biometric gate appears over it. It uses
only standard Android APIs — no root, no device-owner — so it's a good fit for a
*shared* phone the parent keeps using normally.

**Honest limits:** a determined, tech-savvy teen could use Safe Mode or a factory
reset (only true device-owner mode can stop those). For younger kids grabbing a
phone, the combo of locked apps + locked Settings + uninstall protection is very hard
to beat.

## 🛠 Build

CI (`.github/workflows/build-apk.yml`) builds a **signed** APK on every push and on
manual dispatch, with an auto-incrementing version (`1.0.<run#>`). To build locally:
`gradle wrapper` once, then `./gradlew assembleRelease`.

Package: `uz.jumanazar.qalqon` · minSdk 29 · targetSdk 35 · Kotlin + Jetpack Compose.

## 📄 License

MIT — use it, fork it, improve it. Made for parents, by a parent.
