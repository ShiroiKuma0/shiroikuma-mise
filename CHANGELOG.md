# Changelog

This file carries **both** histories. 白い熊 店's own releases are listed below, newest first, each
naming the upstream release it is built on. Upstream Aurora Store's own history lives in
[`CHANGELOG`](CHANGELOG) beside this file, exactly as upstream maintains it — it is left untouched
so every upstream sync merges cleanly.

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
