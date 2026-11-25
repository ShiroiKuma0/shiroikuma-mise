# CLAUDE.md — guide for Claude Code in this repo

**shiroikuma-mise** — 白い熊's fork of [Aurora Store](https://gitlab.com/AuroraOSS/AuroraStore), an
unofficial FOSS client to Google Play (Kotlin, Jetpack Compose, Hilt, Room; no native code).
Renamed to `shiroikuma.mise` / **白い熊 店** so it installs side-by-side with upstream.

This repo (`ShiroiKuma0/shiroikuma-mise`) is a fork. We track upstream on `master` and layer our
customizations on `custom`.

## Read this first

Before any work, read **`.claude/skills/build-apk/SKILL.md`** (canonical build + delivery) and
**`.claude/skills/upstream-new-version/SKILL.md`** (upstream sync + rebase, with the mandatory
proceed-gated upstream-changes table). Publishing a release uses the **global** `/publish-version`
skill — this repo has no local copy.

## Fork workflow — READ THIS FIRST

### Git remotes & branches

- `origin` → `git@github.com:ShiroiKuma0/shiroikuma-mise` (push here).
- `upstream` → `https://gitlab.com/AuroraOSS/AuroraStore` (fetch only; its push URL is `DISABLED`).
- `master` — mirrors `upstream/master`, **fast-forward only**, no fork work.
- `custom` — all our work, and the GitHub default branch so the repo page lands on the fork.
- `dev` — a one-time snapshot of upstream's `dev` branch, pushed when the fork was created. We do
  **not** keep it in sync; it exists only so the mirror is complete.

**Upstream is GitLab, not GitHub.** `github.com/whyorean/AuroraStore` is a mirror of the GitLab repo
(identical `master`/`dev`, but it also carries ~28 stale topic branches, GitHub issues are disabled,
and it has no releases). F-Droid builds from GitLab, merge requests and the issue tracker live
there, and CI is `.gitlab-ci.yml`. Always fetch from GitLab.

- **Upstream tracking: `git`** — `custom` is rebased onto upstream `master`'s tip, not release tags
  (Aurora tags a release only every few months while `master` moves continuously), so the fork
  versionName pins the upstream base:
  `<upstream>+<base date>.<HH-MM>.g<sha>+<BUILD_NUMBER, 3 digits>`.
  See the global **`git-versioning`** skill. (Adopted 2026-10-22, replacing the plain `+NNN` form
  chosen on 2026-08-03; `4.8.4+020` was the last plain-form release.)

### Our customizations (install identity + build)

| What | Value | Where |
| --- | --- | --- |
| applicationId | `shiroikuma.mise` | `app/build.gradle.kts` → `defaultConfig` |
| namespace (R/BuildConfig pkg) | `com.aurora.store` (**never rename**) | `app/build.gradle.kts` |
| App label | `白い熊 店` | `app_name` in `app/src/main/res/values/strings.xml` |
| App icon | black-yellow traced basket + triangle (yellow `#FFFF00` line-art on black) | `drawable/ic_launcher_foreground.xml`, `drawable-v24/…`, `drawable/ic_launcher_monochrome.xml`, `values/colors.xml` → `ic_launcher_background`, `mipmap-*/ic_launcher*.png`, `app/src/main/ic_launcher-playstore.png` |
| Version tail | `versionName = "<upstream>+NNN"`, `versionCode = <upstream code>*10000+N` | `app/build.gradle.kts` fork blocks |
| Signing | gitignored `signing.properties` → `~/.android-keystores/shiroikuma-mise.jks` (alias `mise`) | upstream's own `signingConfigs` block |
| De-branding | our name + our GitHub links everywhere user-visible | About / Welcome / Help screens, `values*/strings.xml`, `arrays.xml`, `Constants.kt` |
| Self-update feed | our `updates.json` on `raw.githubusercontent.com` | `Constants.kt`, `updates.json` |

### Versioning & APK naming

- The upstream base lives in `app/build.gradle.kts` `defaultConfig` as upstream's own
  `versionCode = 76` / `versionName = "4.8.4"` literals. Our fork lines sit **immediately after**
  them and multiply/append, so a rebase brings the new base in automatically.
  **Never hand-edit those two literals.**
- `BUILD_NUMBER` (in `gradle.properties`) is our per-build `N`:
  `versionName = "<upstream name>+<base date>.<HH-MM>.g<base sha>+<N zero-padded to 3>"`
  (e.g. `4.8.4+2026-08-02.08-09.g4b717c67+021`),
  `versionCode = <upstream code> * 10000 + N` (plain integer, e.g. `760021`).
  The pin is the UTC committer time and first 8 chars of `git merge-base HEAD master` — the
  upstream commit our patches sit on — so it moves only on a sync.
  The `buildFork` task bumps `BUILD_NUMBER` after every successful build. It is **not** reset on a
  sync — only when upstream's own `versionCode` literal moves. Resetting on a mere repin would send
  `versionCode` backwards, and the phone would refuse every sync build as a downgrade.
- APK: `shiroikuma-mise_<versionName>.apk`, copied to `~/tmp/`. **No ABI suffix** — the app has no
  native code, so the APK is universal. The versionName contains no `_`, so the
  `shiroikuma-mise_*.apk` globs in `/adb-push`, `/scp` and `/publish-version` still resolve.

### Build commands

```bash
# Our build: signed release → ~/tmp + bump BUILD_NUMBER (use this)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildFork < /dev/null
# Release APK only (no copy / no bump)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :app:assembleVanillaRelease
```

We ship the **`vanilla`** flavor (upstream's default). The `huawei` and `preload` flavors and the
`nightly` build type are upstream's — we don't build or ship them.

### Releasing

Releases go through the global `/publish-version` skill (tag = the full versionName, pin included,
e.g. `4.8.4+2026-08-02.08-09.g4b717c67+021`; APK attached).
**One extra step this repo needs:** refresh `updates.json` at the repo root and commit it to `custom`
before/with the release. That file *is* our self-update feed — the app fetches it from
`raw.githubusercontent.com/ShiroiKuma0/shiroikuma-mise/custom/updates.json` and offers the build in
the Updates tab when its `version_code` exceeds the installed one. Its schema is
`app/src/main/java/com/aurora/store/data/model/SelfUpdate.kt` (all values are JSON **strings**):
`version_name`, `version_code`, `download_url` (the release asset URL), `size`, `sha256`,
`changelog`, `updated_on`, `timestamp`. A stale `updates.json` means the app never sees the release.

### Toolchain

- JDK **21** at `/usr/lib/jvm/java-21-openjdk-amd64` (the host default `java` is older; always set
  `JAVA_HOME`). The build also declares a Java 21 toolchain.
- Android SDK at `~/android-sdk` via the gitignored `local.properties`; `compileSdk 37`,
  `targetSdk 37`, `minSdk 23`. Gradle wrapper 9.5.0, AGP 9.x, configuration cache **on**.
- Release is minified + resource-shrunk (`isMinifyEnabled = true`, `isShrinkResources = true`) —
  that is upstream's setting; leave it unless asked.

## Our subsystems (fork-only code, all under `mise/` + `compose/ui/mise/`)

| Where | What |
| --- | --- |
| `mise/MiseUiConfig.kt` | SharedPreferences store for every UI knob, seeded to the house black-yellow |
| `mise/MiseUiState.kt` | observable mirror; writes land in prefs AND Compose state, so edits repaint live. Derives the `ColorScheme` / `Shapes` / `Typography`, and provides `LocalMiseUi` |
| `mise/MiseFonts.kt` | `.ttf`/`.otf` import into app storage + family resolution |
| `mise/MiseBackup.kt` | the category ZIP: `writeZip(categories, OutputStream, onProgress, isCancelled)` is the ONE export implementation; atomic `.part`-then-rename on both paths |
| `mise/automation/` | the 保存復元 contract — `MiseAutomationAuth` (token, switch default OFF), `StateExportReceiver` (exported, 3 actions), `StateExportService` (foreground `dataSync`) |
| `compose/ui/mise/MiseUiScreen.kt` | the 白い熊 店 UI page, in kxkb's grammar |
| `compose/ui/mise/MiseDialogs.kt` | RGBA colour picker with recent-colour swatches, font picker (each font in its own glyphs), the Export/Import panel |
| `compose/composable/MiseButtons.kt` | drop-in `TextButton`/`Button`/`OutlinedButton`/`FilledTonalButton` with the house border by default |
| `compose/composable/MiseDividers.kt` | drop-in `HorizontalDivider`/`VerticalDivider` taking thickness + colour from the knobs (0 = draw nothing) |
| `compose/composable/MiseMenus.kt` | drop-in `DropdownMenu`/`DropdownMenuItem` in the house menu look: full-width outlined pill rows with an icon coloured by `MenuRole`, `opens` (›), `destructive`, `checked` (a toggle row with `MiseSwitch`), plus `MenuDivider` |
| `mise/MiseFlash.kt` | the black-yellow toast; `Context.toast()` and every direct call route through it |
| `design/` | the icon's source SVG |

### Things that will bite you if you don't know them

- **Never call `Toast.makeText` directly** — use `Context.toast()` or `MiseFlash`. Note the OS
  limit: since Android 11 a custom toast view is only honoured in the **foreground**, so a toast
  posted from a worker or receiver will still look like the platform's.
- **`MiseButtons.kt` / `MiseDividers.kt` are how buttons and dividers get the house look.** A file opts in by importing these instead of
  the `androidx.compose.material3` ones — 36 files (buttons) and 16 (dividers) already do. When
  upstream adds a screen, switch its imports over; don't patch borders or thicknesses at call sites.
- **Menus go through `MiseMenus.kt`.** A file opts in by importing `DropdownMenu` /
  `DropdownMenuItem` from `compose/composable` instead of Material's; every action row then needs
  an `icon` and, where it is not a plain "view", a `role`. A list of pure choices passes
  `iconColumn = false`. All seven menu files are switched over — switch upstream's new ones too.
- **A new colour role is one line in `MiseUiState.ColorSlot`** (key + default) and one `ColorRow`
  on the UI page; only the first nine roles have properties of their own in `MiseUiConfig`.
- **Held apps carry a "tested" mark** (`ignored_update.testedVersionCode/Name/At`, Room v14). It
  is set when a held app is installed (`UpdateHelper.markTested`, and it only ever rises, so
  restoring the old version from a backup keeps the newer version marked) or by the row's "Skip".
  The Updates screen derives the row state from it: offered version == tested → *skipped*;
  otherwise *untested*. Whether an older mark was a rejection is read from the installed version
  (tested > installed), never stored. "Unignore" deletes the rule and the mark with it.
- **The fork's own screens take their text from `mise_strings.xml`** (`values/` and `values-ja/`),
  never from literals — every string added needs its Japanese in the same change.
- **`MiseUiState` writers are `updateX()`, not `setX()`** — the properties' generated setters already
  own the `setX` JVM signature.
- **The export core is called from three places** (panel, service, and any future caller). Never
  duplicate export logic into a receiver, and never run it there: a manifest receiver that overruns
  the broadcast window gets the app ANR'd and killed mid-write.
- **The automation token lives in its own prefs file** and is deliberately not a backup category.
  The `accounts` Room table is excluded from backups too — it holds live Google auth tokens.
- **Blacklist and spoof are NOT Room-backed**; they are in the default SharedPreferences, so the
  `settings` category already carries them.
- **`/publish-version` must also refresh `updates.json`** — see "Releasing" above.

## Architecture (upstream Aurora Store)

Single `:app` module, `com.aurora.store` namespace, Hilt DI throughout.

| Area | Where |
| --- | --- |
| Compose UI (screens, navigation3) | `app/src/main/java/com/aurora/store/compose/` |
| ViewModels | `app/src/main/java/com/aurora/store/viewmodel/` |
| Play API access, auth, dispenser | `app/src/main/java/com/aurora/store/data/` (`network/`, `providers/`, `helper/`) |
| Installers (session, root, Shizuku, AM, services) | `app/src/main/java/com/aurora/store/data/installer/` |
| Room DB (downloads, updates, favourites) | `app/src/main/java/com/aurora/store/data/room/` |
| Shared constants + every external URL | `app/src/main/java/com/aurora/Constants.kt` |
| GPlay API itself | external dependency `libs.auroraoss.gplayapi`, not vendored here |

Anonymous login goes through a **dispenser** (`URL_DISPENSER`, `auroraoss.com/api/auth`) — that is
**functional infrastructure, not branding**: it hands out the anonymous Google tokens the app runs
on. Leave it (and the `auroraoss.com`/`gitlab.com` certificate pins in `OkHttpClientModule`) alone.

## Hard rules

- **Build proactively** after any coherent code change — never ask "shall I build?" — and deliver via
  the global `/after-build` skill. Delivery goes to exactly ONE target.
- **Never commit/push unprompted.** Wait for 白い熊's explicit "Push". `custom` is rebased on every
  upstream sync, so it pushes with `git push --force-with-lease origin custom`.
- **`/upstream-new-version` must show the proceed-gated upstream-changes table before rebasing.**
  This is a standing requirement, not a nicety — see the skill.
- **Never rename the `com.aurora.store` namespace.** Only `applicationId` differs; renaming would
  make every rebase a mass-conflict.
- `signing.properties`, `*.jks` and `local.properties` are gitignored — never commit them.
  (`app/testkey.jks` is upstream's public AOSP test key and stays committed.)
- **Never run `adb` inside the sandbox** — always `dangerouslyDisableSandbox: true`, or `adb devices`
  reports no device. Disconnect wireless adb at the end of every delivery batch.
- Distinguish **branding** from **infrastructure** when de-branding: names, links, wiki/FAQ/donate
  URLs and the self-update feed are ours; the dispenser URL, certificate pins, Exodus/Plexus
  endpoints and the `com.aurora.services` installer integration are Aurora's working parts and stay.

## Commit convention — no Claude attribution

Do **not** add any `Co-Authored-By: Claude …` trailer, nor a "🤖 Generated with Claude Code" /
Anthropic-attribution line, to commit messages or PR bodies in this repo. End the message at the
last line of the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
