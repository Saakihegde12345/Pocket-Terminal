# Verification record

## Complete development workspace — 2026-10-22

Base: `main` at `9520c30e`, with the preserved local stash and the six planned workspace features integrated in the working tree. This record covers the development build, not an independent public release.

- Universal debug APK: `0.4.0-pocket-dev`, versionCode **120**, application ID `com.termux`.
- `:app:assembleDebug`, `:app:testDebugUnitTest` and `:terminal-emulator:testDebugUnitTest` passed using JDK 17, Gradle 9.2.1, Android SDK 36 and NDK 29.0.14206865 under Ubuntu/WSL.
- **167 unit tests** passed: 22 app tests and 145 terminal-emulator tests, with zero failures, errors or skips. New tests cover saved-host persistence/capacity/validation, workspace schema and import boundaries, and pane/extra-key input routing through themed contexts.
- **149 source regression assertions** passed: 19 filesystem-path, 40 search/snippet, 45 file-browser/editor and 45 host/package/theme checks. The optional path symlink assertion ran successfully in this environment. Java 8 syntax parsing passed for 29 integration files; resource XML/references and repository imports passed.
- The APK contains ARM64, ARMv7, x86 and x86-64 native libraries. `apksigner` verifies its v1 and v2 signatures with the existing development key. Metadata reports min SDK 21 and target SDK 28. The exact artifact size and SHA-256 are in `COMPLETION_BUILD_RESULT.json`.
- `git diff HEAD --check` passed.

The build used `TERMUX_PACKAGE_VARIANT=apt-android-7`, `TERMUX_SPLIT_APKS_FOR_DEBUG_BUILDS=0`, `TERMUX_APP_VERSION_NAME=0.4.0-pocket-dev` and `TERMUX_APK_VERSION_TAG=pocket-terminal-dev`, with `JITPACK_NDK_VERSION` unset. This Windows checkout was built through Gradle's Java wrapper entry point under WSL. The initial Java bootstrap download timed out; fetching the identical four pinned official archives with curl and verifying all existing SHA-256 values resolved the environment issue. Build downloads now have explicit connect/read timeouts. The source checker reads UTF-8 explicitly for Windows compatibility.

### Fresh emulator smoke checks

Test environment: isolated Android 9/API 28 AOSP x86-64 emulator, Pixel 2 profile, booted with KVM. The final APK installed and launched successfully; the embedded Termux bootstrap completed and produced a real shell.

- Both top/bottom and side-by-side native panes displayed distinct sessions. Selecting the second pane routed a marker command to its actual shell. Applying a workspace accent recreated the Activity while preserving the two-session count and selected second pane.
- Created and persisted a localhost SSH/SFTP host through the UI. Live remote connections were not attempted.
- The package catalog read 79 local APT entries and filtered `libc++` to exactly one result. After the keyboard-height fix, its actions remained above the visible keyboard and Cancel worked directly. Index updates, package installation/removal and full upgrades were not executed.
- Previewed and edited a fixture containing Japanese, an emoji, UTF-8 BOM and CRLF. Saving preserved the exact expected raw bytes and mode **750**. The Edit action is positioned before the copy/shell actions so it is visible without horizontal scrolling on this screen.
- An unsaved editor marker survived an Activity recreation triggered by changing the emulator font scale. The draft was then explicitly discarded, and font scale was restored. Process-death draft recovery is not claimed.
- Applied the Paper terminal preset through the UI and verified all 19 persisted properties. Workspace accent remained separate.
- Exported workspace JSON through Android's document picker into Downloads, verified its schema and saved host/snippet/session data, and imported it through the picker. Import showed its replacement confirmation and retained the one-session count. The separate confirmed restore action opened the saved directory in one fresh shell, bringing the count to two.
- AndroidRuntime error logging contained no fatal application exception during these smoke checks.

### Remaining acceptance

Physical devices, additional Android versions/ABIs at runtime, vendor keyboards/background restrictions, TalkBack, full large-font/landscape matrices, live SSH/SFTP authentication and changed-host-key rejection, real package changes, storage revocation/races, and comparative performance remain to be exercised. `FEATURE_MATRIX.md` retains the complete acceptance checklist. Public distribution still requires the runtime identity, package infrastructure and release-signing work in `RUNTIME_IDENTITY.md`.

## Project file browser — 2026-10-22

Base: merged `main` at `1f9bdb08bfa64b04e812637d2bd67aa8f000e2cb`.

- Local source validation passed **98 SDK-independent checks**: 19 path checks, 40 search/snippet checks and 39 new file-browser checks.
- New coverage includes deterministic folder-first ordering and the 200-entry cap, filtering beyond that initial cap, hidden files, Unicode/CRLF/tabs, literal shell metacharacters in filenames, UTF-8 BOM, exact/over-limit previews, malformed UTF-8, binary/control bytes, empty/missing files and directories, project path boundaries, internal/external symlinks and cancellation.
- Java 8 syntax parsing passed for 16 integration files, along with resource XML, resource-reference and repository-import checks. `git diff --check` passed.
- Source checks do not compile Android code. Android assembly and app/emulator unit tests run in the feature PR's existing build workflow. Physical-device UI, TalkBack, large-font, storage permission and performance acceptance remain required.
- The development version is `0.3.0-pocket-dev`, versionCode 119; `com.termux` and the existing development signing identity remain unchanged.

The preceding workspace-tools implementation also passed its post-merge Android build and unit tests on `main`: [GitHub Actions run 39671784628](https://github.com/Saakihegde12345/Pocket-Terminal/actions/runs/39671784628). Its APK and unit-test artifacts were present and not expired when checked on 2026-10-22.

## Workspace tools — 2026-10-21

Base: Pocket-Terminal `4896ed88045201dbd10de79071747e7dc910dc85`, after merging dependency PRs #1–#3.

Passed locally with the real Android SDK/NDK and JDK 17:

- Universal debug APK assembly for ARM64, ARMv7, x86 and x86-64.
- All **155 unit tests**: 10 app tests and 145 terminal-emulator tests, with no failures, errors or skips.
- New Android tests cover snippet persistence, edits at capacity, deletion of all starter snippets, invalid/duplicate metadata, soft-wrapped and Unicode transcript capture, detached snapshots, and row/character limits.
- **59 SDK-independent checks**: the existing 19 filesystem-path checks and 40 search/snippet regression assertions. Search checks include literal metacharacters, Unicode case matching and UTF-16 offsets, navigation wraparound, cancellation and bounded results. Snippet checks cover exact text preservation and control/newline rejection.
- Java 8 syntax parsing of 14 integration files; resource XML/reference checks and repository-import resolution.
- APK signature verification passed with v1 and v2 schemes using the existing public development key. APK metadata: `com.termux`, version `0.2.0-pocket-dev`, versionCode 118. All four expected ABI directories are present; no partial native build files are packaged.
- `git diff --check` passed.

The build used `TERMUX_SPLIT_APKS_FOR_DEBUG_BUILDS=0`, `TERMUX_APP_VERSION_NAME=0.2.0-pocket-dev`, and `TERMUX_APK_VERSION_TAG=workspace-tools-dev`, then:

```bash
./gradlew --no-daemon --max-workers=2 :app:assembleDebug :app:testDebugUnitTest :terminal-emulator:testDebugUnitTest
```

The first test attempt could not download Robolectric's Android runtime because the test JVM lacked this environment's network proxy. Passing the existing proxy and system CA configuration to the test JVM resolved it; the final build and every test passed. The APK contents and signature were checked afterward.

See [WORKSPACE_TOOLS_BUILD_RESULT.json](WORKSPACE_TOOLS_BUILD_RESULT.json) for artifact details. This is local build/test evidence. No physical-device/emulator UI acceptance, interactive package installation or performance benchmark was performed. Remote CI for this feature update had not run at the time of the local build; its later PR and post-merge builds passed. The foundation and dependency-update CI builds already passed before this feature work.

## Foundation build — 2026-10-16 (historical)

Base: `termux/termux-app@8629e632fcb95da272221be327db653fb24befe9`.

This is the historical foundation-build record. Verification of the current working tree is recorded in the complete-development-workspace section above.

## Passed locally

- Universal debug APK assembly for ARM64, ARMv7, x86 and x86-64.
- App unit tests: 2 passed; terminal-emulator unit tests: 145 passed. No failures, errors or skipped tests.
- APK metadata confirms the `Pocket-Terminal (Dev)` launcher label and version `0.1.0-pocket-dev`; the compatibility application ID remains `com.termux`.
- APK signature verification using Android SDK `apksigner` succeeded, including APK Signature Scheme v2.

- Workspace path checks: `required=18`, `passed=18`, `skipped=1`. The required SDK-independent assertions cover literal spaces/quotes/metacharacters, home expansion, invalid paths, regular-file rejection and control-character handling. The optional symlink canonicalization assertion was skipped because symbolic-link creation was unavailable on the Windows build environment (Windows may require Developer Mode or link privilege for this operation).
- Java 8 syntax parsing of seven changed/new Android Java files using the JDK parser. This parses syntax; it does not resolve Android types or compile an APK.
- Studio resource XML and resource-reference checks.
- Studio imports resolved to actual source modules.
- Whitespace checks for newly authored files; byte-for-byte upstream source/documentation copies retain their original whitespace.

Repeat with `python3 tools/check_source.py` using Python 3 and Java 17.

The successful CI-equivalent local build used the same environment variables as the workflow:

```text
TERMUX_PACKAGE_VARIANT=apt-android-7
TERMUX_SPLIT_APKS_FOR_DEBUG_BUILDS=0
TERMUX_APP_VERSION_NAME=0.1.0-pocket-dev
TERMUX_APK_VERSION_TAG=pocket-terminal-dev
```

The build command was:

```bash
unset JITPACK_NDK_VERSION
TERMUX_PACKAGE_VARIANT=apt-android-7 \
TERMUX_SPLIT_APKS_FOR_DEBUG_BUILDS=0 \
TERMUX_APP_VERSION_NAME=0.1.0-pocket-dev \
TERMUX_APK_VERSION_TAG=pocket-terminal-dev \
./gradlew --no-daemon --max-workers=2 :app:assembleDebug :app:testDebugUnitTest :terminal-emulator:testDebugUnitTest
```

The local environment additionally needed its existing network proxy/system CA settings and a full JDK 17 installation. To match CI, leave `JITPACK_NDK_VERSION` unset so Gradle uses NDK `29.0.14206865` from `gradle.properties`. The Android SDK, build tools and NDK were installed before the successful build. The JitPack dependency failure was resolved by building the exact upstream command-library version from local source; see `third_party/termux-am-library/UPSTREAM.md`.

The final artifact's filename, size and SHA-256 are recorded in `BUILD_RESULT.json`. Generated APKs and SDK files are excluded from Git.

## Not performed

- No physical-device or emulator UI acceptance test, interactive package installation or performance benchmark completed.
- The APK has not been installed on a user's device or published as a release.
- No GitHub Actions run had completed at the time of the original 2026-10-16 local build record; later foundation/dependency CI builds passed.

`.github/workflows/pocket-terminal-build.yml` runs on pushes/PRs to `main` and supports manual dispatch. It sets up Java/SDK components, checks source, assembles an APK and runs app and terminal-emulator unit tests. Remote workflow success still needs verification after publication. The physical-device acceptance checklist is in `FEATURE_MATRIX.md`.
