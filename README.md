# Aqlli Qalqon

A lightweight, tamper-resistant Android **parental-control** app that runs in
**Device Owner** mode (`DevicePolicyManager`). Built for a parent to provision
onto a child's device they own (tested target: Samsung Galaxy A35 / M35).

Package: `uz.jumanazar.qalqon` · Display name: **Aqlli Qalqon** ("Smart Shield")

## What it does — 3-layer lockdown

**Layer 1 — App & store suspension** (`PolicyManager`)
Suspends Roblox, YouTube, Play Store, Galaxy Store and Secure Folder, and sets
`DISALLOW_INSTALL_APPS` / `DISALLOW_UNINSTALL_APPS`.

**Layer 2 — Local DNS filter** (`DnsFilterVpnService`)
A server-less `VpnService` that captures UDP/53, answers blocked domains
(YouTube, Roblox, browser-game portals + subdomains) with NXDOMAIN, and forwards
everything else to `1.1.1.1`. Pinned as an always-on, lockdown VPN so it cannot
be turned off in Settings.

**Layer 3 — Anti-tamper + parent PIN**
`DISALLOW_SAFE_BOOT`, `DISALLOW_ADD_USER`, `DISALLOW_FACTORY_RESET`,
`DISALLOW_DEBUGGING_FEATURES`, uninstall blocked. All toggles — including
re-enabling ADB and full release — sit behind a PIN-gated dashboard.

## Build — no Android Studio needed

CI (`.github/workflows/build-apk.yml`) builds a signed APK on every push to
`main` and on manual dispatch, then uploads `app-release.apk` as an artifact.

### One-time local setup (≈3 min)

1. **Generate a signing keystore** (keep it forever — losing it blocks updates):
   ```bash
   keytool -genkey -v -keystore qalqon-release.jks \
     -keyalg RSA -keysize 2048 -validity 10000 -alias qalqon
   ```
2. **Add 4 GitHub secrets** (Settings → Secrets and variables → Actions):
   - `RELEASE_KEYSTORE_BASE64` — `base64 -w 0 qalqon-release.jks`
   - `KEYSTORE_PASSWORD`
   - `KEY_ALIAS` — `qalqon`
   - `KEY_PASSWORD`
3. Push to `main` (or run the workflow manually) and download the APK artifact.

## Provision the device (one-time, via USB)

1. On the phone: temporarily remove Google & Samsung accounts, turn off
   **Auto Blocker**, enable **USB Debugging**.
2. Install and set device owner:
   ```bash
   adb install -r app-release.apk
   adb shell dpm set-device-owner uz.jumanazar.qalqon/.admin.KidGuardAdminReceiver
   ```
3. Open the app once to activate policies (default PIN **1234** — change it).
4. Sign accounts back in and re-enable **Auto Blocker**.

> `set-device-owner` only works on a device with **no added accounts** and that
> has not completed setup with an owner already. If it refuses, factory reset,
> skip account setup, then run step 2 before adding accounts.

## Local build (optional)

The wrapper jar is fetched by CI; to build locally, run `gradle wrapper` once
(or drop a `gradle/wrapper/gradle-wrapper.jar` in place), then:
```bash
./gradlew assembleRelease
```
