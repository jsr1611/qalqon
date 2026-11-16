# Google Play — submission pack for Aqlli Qalqon

Everything needed to create the Play listing. Build artifacts come from CI
(`AqlliQalqon-v1.0.<run>-play-aab` → the `.aab` you upload).

---

## 1. Known policy risk — read first

Aqlli Qalqon uses an **AccessibilityService** to detect the foreground app. Google Play
restricts accessibility APIs to disability use cases unless an alternative use is approved.
Parental-control/app-lock apps using accessibility are sometimes rejected.

- **Plan A:** submit with the Accessibility declaration + justification below and see if review accepts it.
- **Plan B (fallback):** replace foreground detection with `UsageStatsManager` (Usage Access) + an
  overlay screen. More Play-friendly; a moderate code change. Keep this in reserve if Plan A is rejected.

Also expect a **Permissions declaration** prompt for the broad install-list access and the VPN service.

---

## 2. Store listing copy

**App name (≤30 chars):** `Aqlli Qalqon — App Lock`

**Short description (≤80 chars):**
`Lock kid-grabbed apps on a shared family phone with a password, fingerprint or face.`

**Full description (≤4000 chars):**
```
Kids don’t always have their own phone — they grab yours and open Roblox, YouTube, the
store, or the browser. Aqlli Qalqon ("Smart Shield") turns your phone into a shared family
phone you actually control.

Pick the apps you want to protect. The phone stays completely usable for you, but each
protected app opens only after your password, fingerprint, or face — and wrong attempts have
NO lockout penalty, so a child mashing the screen never locks you out of your own phone.

FEATURES
• App lock for a shared phone — protect Roblox, YouTube, Chrome, app stores, Settings, or any app
• Unlock with password, fingerprint, or face
• No lockout penalty for wrong tries
• One-tap "Lock recommended apps", plus a master pause switch
• Clean dashboard that shows your protection status at a glance
• Forgot-password recovery so you’re never locked out
• Optional uninstall protection
• Optional on-device website filter
• 5 languages: Uzbek, English, Russian, Korean, Arabic

PRIVACY
Aqlli Qalqon collects NO data. Nothing is sent off your device — no analytics, no ads, no
accounts. Your password is stored only on your phone, as a salted hash.

ACCESSIBILITY SERVICE
The app uses Android’s accessibility service only to detect which app is opening, so it can
show the password screen before a protected app appears. It does not read screen content and
transmits nothing.

Open source. Feedback welcome: Telegram @jsr1611
```

**Category:** Parenting (or Tools)
**Tags:** parental control, app lock, screen time
**Contact email:** _(Play requires a public support email — decide which address to use; it is shown on the listing)_
**Privacy policy URL:** `https://jsr1611.github.io/qalqon/privacy-policy.html` _(after enabling GitHub Pages — see §5)_

---

## 3. Permission / API declarations (what to write in the console)

- **Accessibility API usage:** "Aqlli Qalqon is a parental app-lock for shared family phones. It
  uses the accessibility service solely to detect which app has entered the foreground, so it can
  present a password prompt before a parent-protected app opens. It does not read screen content,
  collect data, or transmit anything. Core functionality of the app depends on it."
- **Query all packages (app list):** "Shown so the parent can choose which installed apps to
  lock. The list never leaves the device."
- **VpnService:** "An optional, user-enabled, fully on-device DNS filter that blocks selected
  websites. No traffic is routed to any server; nothing is logged."
- **Device admin:** "Optional uninstall protection so a child can’t remove the app without the
  parent password."
- **FOREGROUND_SERVICE_SPECIAL_USE:** "Keeps the protection service running so protected apps are
  gated reliably."

## 4. Data safety form
- Data collected: **None.** Data shared: **None.**
- No data types; the app works offline; account not required.

## 5. Checklist
- [ ] Enable **GitHub Pages** (repo Settings → Pages → Branch `main`, folder `/docs`) so the
      privacy policy is live at the URL above. (Or host it on jumanazar.uz.)
- [ ] Create app in Play Console → **Designed for Families: NO**, target audience **adults/parents**.
- [ ] Upload the **.aab** from CI to the **Internal testing** track first (fastest, avoids most friction).
- [ ] Use your existing `qalqon-release.jks` as the **upload key**; let Play App Signing manage the app key.
- [ ] Fill Data safety (None), content rating questionnaire, privacy policy URL, store listing copy.
- [ ] Complete the Permissions + Accessibility declarations (§3).
- [ ] Prepare graphics (§6), then roll out Internal testing.

## 6. Graphics to prepare (you capture on the phone)
- **App icon:** 512×512 PNG (exists as the launcher icon — export at 512).
- **Feature graphic:** 1024×500 PNG.
- **Phone screenshots:** 2–8, min 1080px on the long side. Good set: dashboard (protected), the
  password screen, the language picker, the setup checklist.

_Internal testing needs no screenshots to start, but production does._
