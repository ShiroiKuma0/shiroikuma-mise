<div align="center">

<img src="app/src/main/ic_launcher-playstore.png" width="120" alt="白い熊 店 icon" />

# 白い熊 店

**A Google Play client that looks like the rest of the house — and updates the apps you froze.**

A fork of [Aurora Store](https://gitlab.com/AuroraOSS/AuroraStore) with **major additions**: a full black-yellow theming page with live previews, updates for frozen apps, a category-based backup whose data can be backed up and restored on a wiped phone, and an in-app account picker.

Installs **side-by-side** with Aurora Store (app id `shiroikuma.mise`).

**📥 Latest release: [`4.8.4+019`](https://github.com/ShiroiKuma0/shiroikuma-mise/releases/latest)** — [all releases & APK downloads »](https://github.com/ShiroiKuma0/shiroikuma-mise/releases)

</div>

---

## 🎨 The 白い熊 店 UI page

Every knob that shapes the app's look, on one page — colours, fonts, roundness, border and divider widths, indent step, row padding, group spacing. It is not a settings screen that takes effect on restart: each write lands in preferences *and* in Compose state, so dragging a slider repaints the whole app underneath you. The page is themed by the very values it edits, which makes the app its own preview.

The border, divider and roundness sliders all reach **0 meaning "draw nothing"** — not "draw the Material default". Colours come from an RGBA picker with a one-click row of colours you have used before, and fonts from a picker that renders every candidate in its own glyphs.

---

## 🧊 Updates for frozen apps

Stock hides updates for disabled apps unless you turn on "extended updates" — which also drags in apps whose signature does not match Play's, two unrelated things behind one switch. Here frozen apps get their **own** switch, on by default, in Settings › Updates and on the UI page.

A frozen app is still installed and still updatable, so it is listed like any other and set in **italics** to tell it apart. The italics are resolved from live system state, so freezing or thawing an app changes them straight away rather than at the next update check.

---

## 🖤 The house look, everywhere

Buttons, dividers, sheets and dialogs are drop-in replacements for their Material counterparts that carry the house border by default — a file opts in by changing one import, so call sites stay untouched and screens upstream adds later inherit the styling the moment their imports are switched.

Toasts are ours too: black ground, yellow text, yellow border, corner radius and border width from the same knobs, and the imported font honoured in the View world. And the launcher icon is upstream's basket redrawn as stroke-only line-art, pure `#FFFF00` on black, with the triangle drawn as three separate strokes so all six ends are rounded tips.

---

## 💾 Backup, restore, and automation

A category ZIP — UI, settings, favourites, ignored updates, fonts — written atomically, `.part` first and renamed only once the archive is complete. Import merges per key and skips categories the archive doesn't carry.

Two deliberate omissions: the **accounts** table is never backed up, because it holds live Google auth tokens and a settings backup that carries credentials into a shared folder is a different object; and the automation token lives in its own preferences file, outside every category.

The 保存復元 automation contract lets 白い熊 自由作業盤 drive an export headlessly — real per-category progress, and cancellation at category boundaries with the partial file deleted. Since contract v2 the app answers **out of the box**: the master switch defaults to on and 「Use authorization token?」 defaults to off, because a pasted secret cannot survive a wipe and the point of all this is a phone that has just been wiped. A token sent anyway is ignored rather than refused.

And 白い熊 応用管理 can now back this app up **with its data** and put it back on a clean phone. That runs through a separate door — a `ContentProvider` that identifies its caller by exact package name, by the uid the kernel reports, and by a pinned signing certificate, then moves the archive through a file descriptor the caller opened rather than a path it named. `import` lives only there, never on the exported receiver.

**Your Google account is not in the backup.** Session and auth tokens are never exported, so a restored install comes back fully configured but signed out. That is not only about secrets: restoring the signed-in flag without the accounts table manufactures a session the app cannot use, which this fork has already had to fix once.

---

## 🔐 Signing in without leaving the app

The Google sign-in flow used to hand off to the system's account chooser — a dialog owned by another process, which no amount of theming here can reach. Now the accounts already on the device are listed in our own sheet. Only *adding* a new account still hands off, because that is microG's own activity.

---

## 🧩 Our own Shizuku

Shizuku shows up under Installation method even when the installed build is `shiroikuma.shizuku` rather than the stock package name, because the check takes a list of manager packages with ours first.

---

## Built on Aurora Store

A fork of [Aurora Store](https://gitlab.com/AuroraOSS/AuroraStore) (app id `shiroikuma.mise`, so it coexists with the official build). Aurora Store is an unofficial, FOSS client to Google Play that lets you search and download apps without the proprietary Play Store — the hard part, and all the credit for it belongs upstream. Anonymous login still runs on Aurora's own token dispenser, which is working infrastructure rather than branding and is left exactly as it is.

Like upstream, this app does not own, license or distribute any apps; everything is accessed directly from Google Play, and there is no approval, sponsorship or affiliation from Google or any app developer. The code remains under **GPL-3.0-or-later**.

## Building

```bash
git clone https://github.com/ShiroiKuma0/shiroikuma-mise
cd shiroikuma-mise

# signed release into ~/tmp/ and bump the build counter
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildFork

# release APK only
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :app:assembleVanillaRelease
```

Needs JDK 21 and the Android SDK (`local.properties` → `sdk.dir`). Signing reads a gitignored `signing.properties` at the repo root; without it the build still succeeds but the APK is unsigned and will not install. The shipped flavor is `vanilla`, and the APK is universal — there is no native code, so no ABI splits.
