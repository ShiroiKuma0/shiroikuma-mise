# Changelog

This file carries **both** histories. 白い熊 店's own releases are listed below, newest first, each
naming the upstream release it is built on. Upstream Aurora Store's own history lives in
[`CHANGELOG`](CHANGELOG) beside this file, exactly as upstream maintains it — it is left untouched
so every upstream sync merges cleanly.

## 白い熊 店 4.8.4+2026-10-02.16-53.g3a15ff67+025 — 2026-11-25

Built on Aurora Store 4.8.4, upstream `master` at `3a15ff67` (2026-10-02) — the same base as
`+024`; this release is fork work only.

### Held apps: test a newer version, or skip it

- **A held app remembers the version that was tested.** Installing the offered update of a held
  app marks that version as tested. Going back to the old version — from a backup, say — keeps the
  mark, because it only ever rises.
- **A skipped version is shown as skipped.** Under *Ignored* on the Updates tab, an offered
  version that equals the mark is dimmed and struck through, with “Skipped” and the day, and it
  sorts below the rest. It stays that way until a newer version appears.
- **A newer version is shown as untested**, with a green dot, a bold **Test** button that installs
  it and a **Skip** button that marks it without installing. The line beneath names the version
  skipped last and when.
- **Tapping a held row opens a menu:** *Details*, *Test again* (clears the mark of a skipped
  version) and *Unignore*, which removes the rule and the mark with it.
- The database moves to version 14 with a migration; the *ignored updates* backup category
  carries the mark, and restores it.

### UI & theming

- **Every popup menu is in the house look.** An action row is an outlined pill across the whole
  menu, with its icon inside at the left; a › at the right marks a row that opens a screen, a
  dialog or the browser; a row that deletes takes the warning colour. All seven menus are switched:
  app details, blacklist, downloads, favourites, spoof, accounts and the search filters.
- **The icon's colour says what the row does** — view, connect, contact, share, block or delete —
  and each of these roles is a colour you can set on the UI page.
- **Toggle rows carry the outlined house switch** instead of a checkbox: “Hold at current version”
  in an app's menu is now such a row.
- **On the UI page, every row that opens something or runs an action is an outlined pill** hugging
  its label, with the explanation beneath it; “Regenerate” is a pill in the warning colour.
- New colour rows on the UI page: the two held-app marks, the five menu roles and the switch's
  off colour.

### Japanese

- **The app is fully translated.** 140 strings that had no Japanese — upstream's newer ones and
  the fork's — are translated, so the Japanese key set matches the default one.
- **The fork's own screens are translated too:** the UI page, its colour and font pickers, the
  export and import panel, the font import and the automation notifications took their text from
  literals; they now come from resources, each with its Japanese (109 strings).
- The category names the automation contract reports now follow the phone's language.

### Fixes & behaviour

- Three English texts no longer say “folder” where a directory is meant.

## 白い熊 店 4.8.4+2026-10-02.16-53.g3a15ff67+024 — 2026-10-22

Built on Aurora Store 4.8.4, upstream `master` at `3a15ff67` (2026-10-02) — synced from
`4b717c67`, 113 upstream commits.

### New from upstream

- **Installs stalled on a missed system prompt are recovered.** A download whose install prompt
  never appeared used to sit on “Installing” forever; it now waits as “Waiting for confirmation”
  and can be resumed from the notification shade or the new in-app **notification centre**.
- **Free space is checked before a download starts** — a “Not enough space” dialog at the tap,
  instead of a failed download later.
- **Every Shizuku variant is supported**, found by whichever app serves Shizuku rather than by
  package name; a running Shizuku service counts on its own, and the installer row names the fork
  that serves it. Installs go through `pm`.
- **Changing the spoofed device now takes effect:** a session built under another device is
  rebuilt, so Play serves the new device's splits.
- **Downloads can be sorted** by date, name and size, and the files list is back under
  *More about app*.
- Fixes: self-update no longer reinstalls itself in a loop; a session waiting for confirmation is
  no longer re-committed (the stray “Can't install app” dialog); a stream that returns the same app
  twice no longer crashes.
- 89 Weblate translation updates, a new Filipino locale, and REUSE licence headers across the tree.

### Fork changes in this sync

- **Our mark in notifications.** Upstream switched every notification icon to Aurora's own mark;
  ours shows the 白い熊 店 basket instead.
- **The update check's ignore-rule fix is now upstream's own.** Upstream fixed the same hole the
  hold had closed in `+020`, so ours was dropped in favour of theirs; the hold toggle is unchanged.
- **Shizuku detection is upstream's**, which finds our renamed Shizuku build by itself, so the
  fork's own name list is gone.
- **The new notification centre and storage dialog use the house buttons.**
- **New translations de-branded again:** app-name strings in Catalan, Filipino, Italian, Japanese,
  Lao, Latvian, Serbian and Swedish say 白い熊 店; the Filipino locale drops the About links and
  donation strings, like every other locale.
- **Fixed:** the English About text called 白い熊 店 “a fork of 白い熊 店”; it names Aurora Store
  again.

## 白い熊 店 4.8.4+2026-08-02.08-09.g4b717c67+023 — 2026-10-22

Built on Aurora Store 4.8.4, upstream `master` at `4b717c67` (2026-08-02).

(`+021` and `+022` were intermediate test builds and were never released; everything in them is
included here.)

### Apps frozen by hiding are checked for updates at last

- **A hidden app did not exist for 白い熊 店.** An app frozen by hiding it — what device-owner
  freezers do — is still installed and still updatable, but Android reports it only to a lookup
  that also asks for uninstalled packages, and every lookup here used the plain one. So a hidden
  app was never checked for updates, was missing from the Installed list, and a held hidden app
  vanished from the *Ignored* section. On the phone this was found on, that was 63 apps.
- **Every package lookup now sees hidden apps**, while still rejecting apps uninstalled with
  their data kept, which the same lookup also returns.
- **A hidden app counts as frozen:** its row is in italics, and the “Updates for frozen apps”
  switch covers it. With that switch off, suspended apps are now excluded too, as its description
  always said; the description now names hidden apps.

### The version now names the upstream base

- **New version format:** `<upstream>+<base date>.<HH-MM>.g<base sha>+<build>`, e.g.
  `4.8.4+2026-08-02.08-09.g4b717c67+023`. Aurora tags a release only every few months, so `4.8.4` alone said
  nothing about which upstream commit a build sits on; the date, time (UTC) and short sha of that
  commit now do. This is the scheme the other 白い熊 forks use.
- **The build counter keeps counting across upstream syncs** and resets only when upstream's own
  version code moves, so a sync build always installs over the previous one and is offered by the
  self-update feed — no more version-code downgrades.

## 白い熊 店 4.8.4+020 — 2026-10-22

Built on Aurora Store 4.8.4.

### Hold an app at its installed version

- **A “Hold at current version” toggle in the app page's ⋯ menu.** It can be set on any installed
  app, whether or not an update is pending, and cleared from the same place at any time.
- **A held app's update is still listed, for information only.** It appears on the Updates tab
  under *Ignored*, with its new version and size, but is not counted in “N updates available” or
  the tab badge, and is not part of **Update all**. **Unignore** on that row releases the hold.
- **The hold is upstream's “Ignore all updates” rule**, not a second mechanism: the toggle and the
  update sheet's “Ignore all updates” set the same entry, and the existing backup category for
  ignored updates already carries it.
- **A manual Update from the held app's own page still works** — that is a deliberate per-app
  choice, not a batch.

### The background update check now honours ignore rules

- **Ignored updates were only hidden from the screen.** The periodic check still notified them
  and, in check-and-install mode, downloaded and installed them — so neither “Ignore all updates”
  nor “Skip version” held against the background run. Both now do; the updates are still recorded,
  so the *Ignored* section keeps showing what is available.

## 白い熊 店 4.8.4+019 — 2026-09-05

Built on Aurora Store 4.8.4.

Answers a refused foreground start instead of dying on it. (`+018` was an intermediate delivery
during the same fix round and was never released; everything in it is included here.)

### The automation path no longer crashes when it is started cold

- **A broadcast and a provider `call()` are both background starts on Android 12 and up**, so every
  foreground start in the automation path can be refused with
  `ForegroundServiceStartNotAllowedException`. That is an `IllegalStateException`, so in the
  receiver it escaped `onReceive` and the system killed the process rather than failing the export.
- **This never appeared by hand, and could not have.** The allowance that makes it work comes from
  recent interaction — open the app, run a backup, it succeeds. It fails in the cold unattended
  batch and on a restore onto a clean phone, which is the case the whole contract exists for. The
  failure is inversely correlated with how closely anyone is watching.
- **Four sites could be refused, not one:** the receiver's `startForegroundService`; the export
  service's own `startForeground`, which sat *above* the lines reading where to send the reply, so a
  refusal there had nothing to answer with and died silently; the data door's start of its service;
  and that service's own `startForeground`. Extras are now read first, then the guard, then the
  reply.
- **Catching without answering would only have traded a crash for silence.** The caller would wait
  out its full timeout and report "no response" — indistinguishable from an app that never
  implemented the contract. Every site now replies.

### How the refusal is reported

- **All four sites route through one decision.** Four copies of a two-branch rule is how the
  branches drift apart, which is the same reason the automation gate lives in a single `refuse()`.
- **The reply is keyed**, so the caller can offer a 「電池最適化を除外」 button on the failed row
  instead of printing an exception.
- **That key is reserved for the case the button can actually fix, and the test is deliberately
  positive** — it is sent only when the app is positively determined not to be exempt from battery
  optimisation. If the check throws, or there is no `PowerManager`, we do not know the exemption is
  the fault and must not promise a repair, so a descriptive line is sent instead. On EMUI a refused
  start can equally be アプリ起動管理 sitting on 自動管理, which no app can change for itself, and a
  button that cannot repair the fault turns one dead end into two.
- **The message is collapsed to a single line before it goes on the wire.** A reply carries one line
  and category listings are newline-delimited, so an embedded newline would corrupt the format
  rather than merely read badly.

**Scope, stated plainly: this makes a cold-phone failure diagnosable, not fixed.** The start is
still refused; the app now reports why instead of disappearing. Granting the app an exemption from
battery optimisation is what actually lets a cold export run.

## 白い熊 店 4.8.4+017 — 2026-09-04

Built on Aurora Store 4.8.4.

The 保存復元 automation contract moves to **v2**: the token becomes optional, and a second door is
added through which 白い熊 応用管理 can back this app up *with its data* and put that data back on a
phone that has just been wiped.

### The gate — a switch that is on, and a token that is off

- **`automation_enabled` now defaults to on, and a new 「Use authorization token?」 defaults to off.**
  v1 shipped the app closed and required a 48-character secret pasted from these settings into the
  caller. That is the wrong shape for a clean phone: a pasted secret cannot survive a wipe, and the
  case this now serves is a restore onto a device where nothing has been configured yet.
- **A token sent to the app while it is not asking for one is ignored, never refused.** Tokens live
  in task arguments that outlive the setting they were pasted for, so refusing them would turn one
  switch being off into half a batch mysteriously failing.
- **Both checks now live in a single `refuse()`** that every entry point asks, rather than being
  written out separately at each one — which is how "automation disabled" and "bad token" drift
  apart into reporting the wrong reason. They stay distinct errors, because they debug differently.
- The token row is **hidden unless a token is actually being asked for**, and is no longer even
  generated in that case. A secret sitting under an off switch invites being pasted somewhere it
  will do nothing.

### The data door

- **A `ContentProvider` answering `describe`, `export`, `import` and `cancel`.** A broadcast cannot
  say who sent it, and the caller supplies the destination the export is written into — so the
  receiver could never have been given this job.
- **The caller is identified three ways, and each exists because the one before it is not enough:**
  an exact package name, never a prefix, since any sideloaded app may call itself `shiroikuma.evil`;
  the uid the kernel reports, which cannot be borrowed the way a declared attribution can; and a
  pinned signing certificate, which is what covers a caller package being *absent* from the phone —
  precisely the clean-phone case this feature exists for. Both pins were re-derived from the
  callers' own signed APKs rather than taken on trust.
- **The archive moves through a file descriptor the caller opened**, duplicated before it leaves the
  binder call and closed in a `finally`. Not a path: 応用管理 renames its backup into place on
  commit and encrypts and checksums per file it knows about, so a file dropped into that directory
  would be renamed out from under it and would sit in plaintext inside an otherwise encrypted
  backup.
- **`import` exists only on this door.** It never gets a broadcast action — the exported receiver
  has no permission on it, so an import there would let any app on the phone overwrite this one's
  settings.
- A refusal is **returned, never thrown**: an exception across a binder reaches the caller as a
  stack trace, which tells 白い熊 nothing and tells a misbehaving caller rather more than it should.
  Identity is checked before the method is dispatched, so an unrecognised caller cannot even learn
  which methods exist.

### What a backup does and does not carry

- **The Google account is still excluded — and `describe` now says so verbatim**, so 応用管理 can
  render it on the backup row instead of leaving it to be assumed. A restored install comes back
  fully configured but signed out.
- That exclusion is not only about secrets. Restoring the signed-in flag *without* the accounts
  table manufactures a session the app cannot use — the failure this fork already had to fix in
  4.8.4+013 — so the filter is load-bearing on import, not merely tidy on export.

### Fixes and hardening in the new code

- **`startForeground()` now runs before every early return in the data service.** Once
  `startForegroundService()` has been called the platform requires it whatever the service then
  decides, so a request carrying a job whose descriptor is already gone would have killed the
  process instead of being ignored.
- **An import flushes both preference stores synchronously before reporting success.** The caller
  force-stops the app the instant it is told the import is done, and that force-stop is a `SIGKILL`
  while both restore paths write with `edit {}` — whose default is an asynchronous `apply()`. A
  restore would otherwise have reported success over settings that never reached disk. Flushing one
  store is not enough: the restore spans two files.
- **A failed service start no longer leaks the caller's descriptor.** A provider call is a
  background start, which the platform can refuse outright; the descriptor is now closed and the
  job dropped before the refusal is answered, rather than holding the caller's file open under a
  job that will never reply.
- **An import is spooled through the cache** instead of being read straight into a growing buffer,
  and an implausibly large archive is refused with a readable error rather than an
  `OutOfMemoryError`.
- **A `<queries>` entry naming both callers** — the app had none. Without it a reply broadcast's
  `setPackage()` fails silently on Android 11+, and package visibility also filters the very lookups
  the caller check depends on.

## 白い熊 店 4.8.4+014 — 2026-08-27

Built on Aurora Store 4.8.4.

### Fixes

- **An unbreakable microG consent dialog when the device account is gone.** A Google session minted
  through microG's `AccountManager` was replayed at every launch with the interactive
  `getAuthToken` overload. With the `com.google` account removed from the device that request can
  never finish: `AskPackageOverrideActivity` persists its grant with `setUserData()` on an account
  that does not exist — a silent no-op in AccountManagerService — so the check that follows fails
  and the consent is offered again, indefinitely. It is one `AccountManager` session looping inside
  the system, so the app never received a callback it could recover from. A saved device-account
  session is now refreshed non-interactively, and one that cannot be refreshed is dropped so the
  login buttons come back.
- **Denying or cancelling the consent no longer crashes the app.** `AccountManagerFuture.result`
  raises `OperationCanceledException` on the main looper, and the splash callback read it unguarded.
- **"Use microG to sign in to accounts" now applies to saved sessions.** The preference gated only
  the login button, so turning it off changed nothing for an already-signed-in user.
- **A blank-token account no longer reports as signed in.** `getLoginToken()` used `&&` where `||`
  was meant.

## 白い熊 店 4.8.4+013 — 2026-08-26

Built on Aurora Store 4.8.4.

### Fixes

- **Every app page failing with "Attempt to invoke virtual method … getClass() on a null object
  reference".** The null is `deviceInfoProvider` on the `AuthData("BOGUS")` placeholder session, and
  every Play-facing call shares the header builder that reads it. `isSavedAuthDataValid()` could not
  report the fault either, because gplayapi implements validity as a live Play call that threw the
  same NPE — so a bad session persisted instead of being rebuilt. It now short-circuits on a
  placeholder session and treats any exception as invalid, so the session repairs itself on the next
  start.
- **Backups no longer carry the signed-in account.** The `settings` category dumped whole
  preferences, including `ACCOUNT_SIGNED_IN`, `ACCOUNT_TYPE`, the three `ACCOUNT_*_PLAIN` token keys,
  `PREFERENCE_AUTH_DATA` and `PREFERENCE_AUTH_VIA_MICROG` — the same live Google credentials the
  `accounts` table is deliberately excluded to keep out. They are filtered on import as well as
  export, so an archive written before this build cannot wedge a working install.

## 白い熊 店 4.8.4+012 — 2026-08-25

Built on Aurora Store 4.8.4. First release with the fork README and the self-update feed pointing at
this repository.

### The fork, in full

Everything 白い熊 店 adds on top of stock Aurora Store — the 白い熊 店 UI page and its live-repaint
theming, updates for frozen apps, the category backup ZIP and the 保存復元 automation contract,
house-styled buttons, dividers, sheets, dialogs and toasts, our own account picker, Shizuku under our
own package name, the black-yellow icon and full de-branding across 53 locales, the self-update feed,
and the fork's packaging and build — is described in the
[release notes](https://github.com/ShiroiKuma0/shiroikuma-mise/releases/tag/4.8.4+012).
