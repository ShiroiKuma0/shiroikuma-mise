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

**Upstream tracking: `master` tip**, not release tags — Aurora tags a release only every few months
while `master` moves continuously (Weblate merges, MR merges). 白い熊 chose the plain `+NNN`
versionName anyway (2026-08-03), so the global **`/git-versioning`** skill does **not** apply here.

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
  `versionName = "<upstream name>+<N zero-padded to 3>"` (e.g. `4.8.4+001`),
  `versionCode = <upstream code> * 10000 + N` (plain integer, e.g. `760001`).
  The `buildFork` task bumps `BUILD_NUMBER` after every successful build; `/upstream-new-version`
  resets it to `1` on every sync, so `+N` always reads as "our Nth build on this upstream base".
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

Releases go through the global `/publish-version` skill (tag = `VERSION_NAME+NNN`, APK attached).
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
