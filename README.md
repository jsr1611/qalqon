# Aqlli Qalqon

A PIN **app-locker** for Android — made for **shared family phones**. The parent
uses the phone normally; apps kids abuse (Roblox, YouTube, browsers, stores) open
only after the **parent password**. Wrong passwords have **no penalty** (no
timeout, no OS lockout), so kids hammering it never lock the parent out.

Package: `uz.jumanazar.qalqon` · Display name: **Aqlli Qalqon** ("Smart Shield")

## How it works

- An **Accessibility service** (`LockAccessibilityService`) watches which app comes
  to the foreground. If it's a protected app, a full-screen PIN gate
  (`LockActivity`) appears over it.
  - Correct password → the app opens for you.
  - Wrong / Cancel / Back → the app closes; retry as many times as you want, no
    penalty.
  - Leaving a protected app re-locks it, so re-entry asks again.
- **Parent dashboard** (`MainActivity`, PIN-gated): turn the service on, pick which
  apps are protected (full installed-app list with search), change the password,
  and enable uninstall protection. Default password **1234**.
- **Uninstall protection**: registers as a *device admin* (not device owner), so the
  app can't be removed from the launcher in one tap.

This needs **no device-owner, no account removal, no factory reset** — just install
and enable the Accessibility service.

### Optional: DNS filter
A parent-toggled switch (**off by default**) runs a local `VpnService`
(`DnsFilterVpnService`) that returns NXDOMAIN for YouTube/Roblox domains, blocking
them inside browsers and web-views too. Caveat: while on, it blocks those sites for
*everyone, including the parent*, until toggled off — so it's an occasional extra,
not the main gate. The app-locker (browser behind a password) is the primary
control.

### Stealth & anti-tamper
- **Anonymous lock screen** — the PIN gate over apps shows no app name/branding, so
  the child can't tell what's blocking them.
- **Disguised icon** — toggle in the dashboard makes the launcher icon show as a
  **"Calculator"** (fake name + icon). It still opens the dashboard for the parent.
  Exactly one of the two launcher aliases is ever enabled, so there is always a
  working icon — no lock-out risk (unlike hiding, which left dead icons on One UI).
- **Settings locked by default** — `com.android.settings` is protected, so the child
  can't reach Accessibility, Device admin, or Uninstall without the password.
- **Uninstall protection** — device-admin blocks one-tap uninstall.

> Trade-off: still not bulletproof against a determined, tech-savvy teen (Safe Mode
> or factory reset bypasses any non-device-owner app). For younger kids grabbing a
> phone, the combo above is very hard to beat.

## Build — no Android Studio needed

CI (`.github/workflows/build-apk.yml`) builds a signed APK on every push to `main`
and on manual dispatch, uploading `AqlliQalqon-v1.0.x.apk` as an artifact.
Keystore + 4 secrets are already configured (see repo secrets).

## Install & set up (on the phone)

1. Download the latest APK from **Actions → latest run → Artifacts**.
2. `adb install -r AqlliQalqon-v1.0.x.apk` (or copy to the phone and tap it).
3. Open **Aqlli Qalqon** (password `1234`) → **change the password**.
4. Tap **Protection service → Off** → it opens Accessibility settings → enable
   **Aqlli Qalqon**.
5. (Optional) Tap **Uninstall protection** to make it a device admin.
6. Pick the apps to protect (Roblox, YouTube, Chrome, Samsung Internet, Play Store,
   Galaxy Store are on by default).

Done — handing the phone to a kid now blocks those apps behind your password.

## Local build (optional)

The wrapper jar is fetched by CI; to build locally run `gradle wrapper` once, then
`./gradlew assembleRelease`.
