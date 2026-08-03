---
name: upstream-new-version
description: Sync the shiroikuma-mise fork onto new upstream work from AuroraOSS/AuroraStore on GitLab — fast-forward master, rebase custom, reset BUILD_NUMBER, build the new +001. Use when 白い熊 says a new upstream version is out, asks to check/update/sync to upstream, or to rebase custom onto the latest Aurora Store. ALWAYS present the proceed-gated upstream-changes table BEFORE rebasing.
---

# Sync shiroikuma-mise onto new upstream Aurora Store work

This fork tracks [AuroraOSS/AuroraStore](https://gitlab.com/AuroraOSS/AuroraStore) — an unofficial
FOSS client to Google Play. `master` mirrors `upstream/master` (fast-forward only); `custom` carries
our patches and is rebased onto it.

**Upstream is GitLab.** `github.com/whyorean/AuroraStore` is only a mirror — same `master`/`dev`, but
issues are disabled there, it has no releases, and it carries ~28 stale topic branches. Fetch from
GitLab, read the MRs and issues there.

**We follow `master`'s tip, not release tags.** Aurora tags a release every few months while `master`
moves continuously (Weblate merges, MR merges). So a sync is normal even when upstream's
`versionName` has not changed — expect `4.8.4+001` to be followed by another `4.8.4+001` on the next
base. The global `/git-versioning` skill does **not** apply here (白い熊's call, 2026-08-03).

## Branch / remote model

| Branch | Role | Update mode |
| --- | --- | --- |
| `master` | Mirrors `upstream/master`. No fork work here. | fast-forward only |
| `custom` | Our patches; the working branch and the GitHub default branch. | rebased onto `master` each sync |
| `dev` | One-time snapshot of upstream's `dev`. Not maintained. | never |

`origin` = `git@github.com:ShiroiKuma0/shiroikuma-mise` (push). `upstream` =
`https://gitlab.com/AuroraOSS/AuroraStore` (fetch only; push URL is `DISABLED`).

## Steps

1. **Fetch upstream and see what is new:**
   ```bash
   git fetch upstream --tags
   git log --oneline master..upstream/master | wc -l        # how much landed
   git show upstream/master:app/build.gradle.kts | grep -E '^\s+version(Code|Name) = '
   ```
   If `master..upstream/master` is empty, stop and report "already current".

2. **PROCEED GATE — present the upstream changes as a table, then STOP.** 白い熊's standing
   requirement: **before** anything is rebased, show what the new upstream work actually brings.

   Gather the material from all of these — they complement each other:
   ```bash
   git log --oneline --no-merges master..upstream/master        # what really landed
   git log --merges --format='%s' master..upstream/master       # which MRs were merged
   git diff --stat master..upstream/master                      # where the weight is
   git show upstream/master:CHANGELOG | head -40                # upstream's own release notes
   ls fastlane/metadata/android/en-US/changelogs/               # per-versionCode store notes
   ```
   Weblate translation merges are the bulk of Aurora's commit traffic — fold them into one row, do
   not list them individually.

   Render **one markdown table**, ordered most-significant first, in this exact shape:

   | # | Change | Kind | What it means in the app | Touches our patches? |
   | --- | --- | --- | --- | --- |
   | 1 | … | Feature / Fix / UI / Perf / Refactor / Dependency | one clear sentence, in plain terms | No — or: yes, `<file>` (our icon / label / version block …) |

   Rules for the table:
   - **Every** notable upstream change gets a row — do not summarise into "various fixes". Group only
     genuinely trivial churn (translation drops, dependency bumps, typo fixes) into a single final
     row, and say how many were folded in.
   - The **last column is the point**: flag every change landing in a file we patch — the launcher
     icon resources, `values/strings.xml` (`app_name` + every de-branded string), `arrays.xml`,
     `Constants.kt` (links + self-update feed), `app/build.gradle.kts`, `.gitignore`, the Welcome /
     About / Help screens under `compose/ui/`. Those are the rebase conflicts, predicted in advance.
   - Below the table, add the base line: old base sha → new base sha, old `versionCode`/`versionName`
     → new, and the resulting fork version (`<newVersionName>+001`, code `<newVersionCode>*10000+1`).

   **Then stop and wait for 白い熊's explicit go-ahead.** Do not fast-forward `master`, do not
   rebase, do not build until they say proceed. If they decline, nothing has been touched.

3. **Advance `master`** (mirror; no fork work lives here):
   ```bash
   git checkout master
   git merge --ff-only upstream/master
   git push origin master
   ```

4. **Rebase `custom`:**
   ```bash
   git checkout custom
   git rebase master
   ```
   Resolve conflicts so **all** our customizations survive (table in step 6). Upstream's
   `versionCode` / `versionName` literals in `app/build.gradle.kts` flow in automatically — keep
   **upstream's** values for those two lines; our fork lines sit right after them and derive from
   them, so they are never edited by hand.

   If upstream restructured a screen we de-branded, port our change to the new structure rather than
   forcing the old diff. Re-check for **new** upstream branding that the rebase introduced: new
   strings mentioning "Aurora", new links to `auroraoss.com` / `gitlab.com/AuroraOSS`, a new About
   entry. Those need the same treatment as the original de-branding sweep.

5. **Reset the build tail:** in `gradle.properties`, set **`BUILD_NUMBER=1`** — a new upstream base
   starts its `+N` at 1, whether or not upstream bumped its own version.

   Consequence to expect: when upstream ships commits *without* bumping `versionCode` (the usual
   case between releases), the reset lowers our `versionCode` below the installed build. Deliver such
   a build with **`adb install -r -d`** (`-d` allows a version-code downgrade); plain `-r` fails with
   `INSTALL_FAILED_VERSION_DOWNGRADE`. Never `adb uninstall` to work around it — that wipes the
   account session and the downloads/updates database.

6. **Verify our customizations are intact after the rebase:**

   | What | Expected | Where |
   | --- | --- | --- |
   | Installed app id | `shiroikuma.mise` | `app/build.gradle.kts` → `defaultConfig.applicationId` |
   | Code namespace | `com.aurora.store` (**unchanged** from upstream) | `app/build.gradle.kts` → `namespace` |
   | App label | `白い熊 店` | `app_name` in `app/src/main/res/values/strings.xml` |
   | Fork version block | upstream literals + `forkVersionName` / `forkVersionCode` lines after them | `app/build.gradle.kts` |
   | `buildFork` task + `archivesName` | present at the end of the script | `app/build.gradle.kts` |
   | Signing path patch | `rootProject.file("signing.properties")`, not `File("signing.properties")` | `app/build.gradle.kts` |
   | Build tail | `BUILD_NUMBER=1` | `gradle.properties` |
   | Black-yellow icon | yellow `#FFFF00` traced line-art, black `ic_launcher_background` | `drawable/ic_launcher_foreground.xml`, `drawable-v24/…`, `ic_launcher_monochrome.xml`, `values/colors.xml`, `mipmap-*/`, `ic_launcher-playstore.png` |
   | De-branding | no upstream app name, wiki/FAQ/donate/XDA/Telegram links left in user-visible text | `values*/strings.xml`, `arrays.xml`, `Constants.kt`, `compose/ui/onboarding/`, About screen |
   | Self-update feed | points at our `updates.json` on `raw.githubusercontent.com` | `Constants.kt`, `updates.json` |
   | Kept as functional | dispenser `auroraoss.com/api/auth`, cert pins, Exodus/Plexus endpoints, `com.aurora.services` installer | `Constants.kt`, `OkHttpClientModule.kt` |
   | Committed agent files | `CLAUDE.md`, `.claude/` un-ignored; signing material ignored | `.gitignore` |

   Sanity check that the build script still evaluates:
   ```bash
   JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :app:tasks --console=plain | head
   ```

7. **Build the new `+001`** via the **build-apk** skill
   (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildFork < /dev/null`), then deliver it
   via the global **/after-build** skill (no transfer prompt). This is the first build of the new
   upstream base.

8. **Stop.** Let 白い熊 test. Commit/push only on their explicit **"Push"**. `custom` was rebased, so
   it needs `git push --force-with-lease origin custom`; `master` is a plain fast-forward.

## Hard rules

- **Never rebase before the step-2 table has been shown and approved.**
- Never `adb uninstall` — it destroys the logged-in session and the local database.
- Never commit/push unprompted; wait for "Push".
- `signing.properties` and `*.jks` are gitignored — never commit them.
- Never rename the `com.aurora.store` namespace.

---

**Commit convention — no Claude attribution.** Never add a `Co-Authored-By: Claude …` / "Generated with Claude" trailer to commit messages or PR bodies; end the message at the last line of the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
