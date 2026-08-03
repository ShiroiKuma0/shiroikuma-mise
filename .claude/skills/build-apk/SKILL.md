---
name: build-apk
description: Build the signed release APK of 白い熊 店 (shiroikuma-mise, our Aurora Store fork) with the buildFork Gradle task, then deliver it automatically via the global /after-build skill. Build PROACTIVELY as soon as a coherent code change compiles — do NOT wait for 白い熊 to say "build it". Also use whenever 白い熊 asks to build the app, build the APK, make a release build, or build and send to the phone.
---

# Build the release APK and deliver it

This is **shiroikuma-mise** — 白い熊's fork of [Aurora Store](https://gitlab.com/AuroraOSS/AuroraStore),
renamed to `shiroikuma.mise` ("白い熊 店") so it installs side-by-side with upstream. Kotlin +
Compose + Hilt, no native code — so the APK is universal, no ABI suffix.

## When to build

Build **proactively** — do NOT wait for "build it" and do NOT ask "want me to build?" first. As soon
as a coherent set of code changes compiles, run the steps below. Don't rebuild after every tiny
intermediate edit — build once the change is in a testable state. Skip the build for non-functional
edits (docs, comments).

This removes only the *ask-before-build* wait. The repo's commit/push rules are unchanged: a
commit/push still waits for 白い熊's explicit **"Push"**.

## Steps

1. **Note the output filename.** The version base comes from upstream's own literals in the build
   script, the tail from `gradle.properties`:
   ```bash
   grep -E '^\s+versionCode = |^\s+versionName = ' app/build.gradle.kts   # upstream's two literals
   grep -E '^BUILD_NUMBER' gradle.properties    # the N used for THIS build, before the task bumps it
   ```
   - APK will be `shiroikuma-mise_<upstream versionName>+<NNN>.apk`, the counter **zero-padded to
     three digits** — `BUILD_NUMBER=7` → `4.8.4+007`. The padding is applied in
     `app/build.gradle.kts`; `gradle.properties` stores the plain integer.
   - `versionCode` for this build = `<upstream versionCode> * 10000 + BUILD_NUMBER`
     (e.g. `76 * 10000 + 7 = 760007`).

2. **Build** (the toolchain needs JDK 21):
   ```bash
   JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildFork < /dev/null
   ```
   (`< /dev/null` guarantees it never blocks on stdin.)
   - `buildFork` runs `assembleVanillaRelease` (signed from `signing.properties`), copies the signed
     APK to `~/tmp/<apk name>`, and auto-increments `BUILD_NUMBER` in `gradle.properties`.
   - It prints `>>> <path>` and `>>> versionCode <n>`. Confirm `BUILD SUCCESSFUL` and take the exact
     filename/code from those lines.
   - If it fails with **`SDK location not found`**, create the gitignored `local.properties` at the
     repo root with `sdk.dir=/home/shiroikuma/android-sdk`.
   - If the APK comes out unsigned, `signing.properties` is missing from the repo root — see below.
   - **`vanilla` is the flavor we ship.** `huawei` (Huawei HMS, no anonymous login) and `preload`
     (system-image builds) are upstream's; the `nightly` build type is upstream's CI. Don't build
     them unless 白い熊 asks.

3. **Deliver automatically via the global /after-build skill** — every build, no asking. After the
   signed APK is in `~/tmp/`, invoke **/after-build**: it runs `/adb-check` UNSANDBOXED (a sandboxed
   check falsely reports no device), then `/adb-push` to `/sdcard/tmp/` if a phone is connected,
   otherwise `/scp` to `skhw:~/tmp/`, and announces the filename. Deliver to exactly ONE target.

## Signing

Release signing is non-interactive and uses **upstream's own** `signingConfigs` block, which reads a
`signing.properties` at the repo root (gitignored):

```
KEY_ALIAS=mise
KEY_PASSWORD=…
STORE_FILE=/home/shiroikuma/.android-keystores/shiroikuma-mise.jks
```

Upstream uses `KEY_PASSWORD` for both the store and the key (our keystores are created that way).
The keystore is PKCS12/RSA-4096, alias `mise`, created 2026-08-03, 10000-day validity. Its password
is recorded in `~/〇/[666] 私資料/[666][27] 暗号/android-keystores.org`, and the `.jks` is backed up
to `~/〇/[666] 私資料/[666][27] 暗号/android-keystores/`. If `signing.properties` is absent the build
still succeeds but the APK is **unsigned** and will not install.

One fork patch here: upstream wrote `File("signing.properties")`, which resolves against the JVM
working directory; we pin it to `rootProject.file("signing.properties")` so signing never silently
degrades to unsigned. Keep that on every rebase.

## Notes / invariants

- **Toolchain:** JDK 21 at `/usr/lib/jvm/java-21-openjdk-amd64`; Android SDK at `~/android-sdk`;
  `compileSdk 37`, `targetSdk 37`, `minSdk 23`; Gradle wrapper 9.5.0, AGP 9.x, configuration cache on.
- **Minified release** — upstream ships `isMinifyEnabled = true` + `isShrinkResources = true`. If a
  Compose/Hilt/Room class disappears at runtime, the fix is a `proguard-rules.pro` keep rule, not
  turning minification off.
- **Universal APK** — no ABI splits, no native libs, so no `_arm64-v8a` tail in the filename.
- **Config-cache discipline:** anything the `buildFork` task needs must be captured at configuration
  time (see the task's `val`s). Don't touch `layout` / `rootProject` inside `doLast`.
- **Never commit/push on your own.** Wait for 白い熊's explicit "Push". Build artifacts (`*.apk`),
  `signing.properties`, `*.jks` and `local.properties` are gitignored.

---

**Commit convention — no Claude attribution.** Never add a `Co-Authored-By: Claude …` / "Generated with Claude" trailer to commit messages or PR bodies; end the message at the last line of the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
