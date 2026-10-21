# Verification record

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

See [WORKSPACE_TOOLS_BUILD_RESULT.json](WORKSPACE_TOOLS_BUILD_RESULT.json) for artifact details. This is local build/test evidence. No physical-device/emulator UI acceptance, interactive package installation or performance benchmark was performed. Remote CI for this feature update has not run yet. The foundation and dependency-update CI builds already passed before this feature work.

## Foundation build — 2026-10-16 (historical)

Base: `termux/termux-app@8629e632fcb95da272221be327db653fb24befe9`.

## Passed locally

- Universal debug APK assembly for ARM64, ARMv7, x86 and x86-64.
- App unit tests: 2 passed; terminal-emulator unit tests: 145 passed. No failures, errors or skipped tests.
- APK metadata confirms the `Pocket-Terminal (Dev)` launcher label and version `0.1.0-pocket-dev`; the compatibility application ID remains `com.termux`.
- APK signature verification using Android SDK `apksigner` succeeded, including APK Signature Scheme v2.

- 19 SDK-independent path/filesystem assertions: literal spaces/quotes/metacharacters, home expansion, canonical symlinks, invalid paths, regular-file rejection and control-character handling.
- Java 8 syntax parsing of seven changed/new Android Java files using the JDK parser. This parses syntax; it does not resolve Android types or compile an APK.
- Studio resource XML and resource-reference checks.
- Studio imports resolved to actual source modules.
- Whitespace checks for newly authored files; byte-for-byte upstream source/documentation copies retain their original whitespace.

Repeat with `python3 tools/check_source.py` using Python 3 and Java 17.

The successful build command was:

```bash
./gradlew --no-daemon --max-workers=2 :app:assembleDebug :app:testDebugUnitTest :terminal-emulator:testDebugUnitTest
```

The local environment additionally needed its existing network proxy/system CA settings and a full JDK 17 installation. The Android SDK, build tools and NDK were installed before the successful build. The JitPack dependency failure was resolved by building the exact upstream command-library version from local source; see `third_party/termux-am-library/UPSTREAM.md`.

The final artifact's filename, size and SHA-256 are recorded in `BUILD_RESULT.json`. Generated APKs and SDK files are excluded from Git.

## Not performed

- No physical-device/emulator UI acceptance test, interactive package installation or performance benchmark completed.
- The APK has not been installed on a user's device or published as a release.
- No GitHub Actions run had completed at the time of the original 2026-10-16 local build record; later foundation/dependency CI builds passed.

`.github/workflows/pocket-terminal-build.yml` runs on pushes/PRs to `main` and supports manual dispatch. It sets up Java/SDK components, checks source, assembles an APK and runs app/emulator unit tests. Remote workflow success still needs verification after publication. The physical-device acceptance checklist is in `FEATURE_MATRIX.md`.
