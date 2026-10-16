# Pocket-Terminal — native Android foundation

An open-source Android terminal fork with a charcoal-and-lime workspace interface around Termux's existing terminal emulator, shell service and package runtime. **Project name: Pocket-Terminal.**

This is a first native implementation, not a finished production release. The Android UI attaches to real sessions; the Android app contains no simulated command output.

## Implemented

- Native workspace header, live session tabs, new-shell control and long-press session renaming.
- Searchable quick actions for shells, keyboard, font size, extra keys, settings and projects.
- Persistent focus mode. Tap the logo to restore the full workspace UI.
- Directory browsing and saved project paths. A selected directory starts a new shell with that working directory; paths are never inserted into shell command text.
- Compact chrome when the app window has little vertical space.
- The existing terminal renderer, input handling, shell lifecycle, extra keys, bootstrap downloader and package environment are retained.

## Development identity

The application ID and native package prefix remain **`com.termux`**. This compatibility build cannot coexist with an existing Termux installation in the same Android user/profile. Test in a fresh emulator or separate Android user/profile. Do not uninstall your existing Termux environment to try it: uninstalling removes its private files.

An independent public release needs a separate ID, release key, rebuilt bootstrap/packages, repository and plugin strategy. See [RUNTIME_IDENTITY.md](docs/studio/RUNTIME_IDENTITY.md). Changing only `applicationId` is insufficient.

## Build

The source uses Java 17, Gradle 9.2.1, Android compile SDK 36 and NDK 29.0.14206865. Use the default `apt-android-7` package variant. The inherited target SDK remains 28; this is not a Google Play release configuration.

Open the project in Android Studio, install the SDK/NDK versions above, and run from the root with dependency-download access:

```bash
python3 tools/check_source.py
./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest :terminal-emulator:testDebugUnitTest
```

On Windows use `gradlew.bat`. Source checks require Python 3 and a JDK containing `jdk.compiler`.

Development APKs are written under `app/build/outputs/apk/debug/`. **No prebuilt APK is included.**

The **Pocket-Terminal development APK** workflow runs on pushes and pull requests to `main`. It can also be started from **Actions → Pocket-Terminal development APK → Run workflow**. Download its artifact after the job succeeds. The workflow uses the standard GitHub-hosted runner's Android SDK and downloads missing components.

Original Termux-specific CI/release workflows are archived under `docs/studio/upstream-workflows`; only the Pocket-Terminal build is active in this fork.

The upstream debug keystore is intentionally public and untrusted. Use it only for development; a public release needs a separate protected key.

## Projects

Open **Projects → Browse home directory** or **Enter directory path**, choose a directory and select **Open shell here**. Successful opens are bookmarked locally, up to 30 paths. Saved entries offer open, browse and remove-bookmark actions. Removing a bookmark never deletes files.

For phone storage first run `termux-setup-storage` and grant its requested permission. Enter your actual folder path, for example `/storage/emulated/0/Projects`; names are case-sensitive. The picker accepts filesystem paths, not `content://` URIs. Keep executables needing Unix permissions in app-private storage.

The directory breadcrumb refreshes on session/title/lifecycle events. It can lag after `cd` if the shell does not emit a title change.

## Validation and next work

Read [VERIFICATION.md](docs/studio/VERIFICATION.md) for actual check results, [FEATURE_MATRIX.md](docs/studio/FEATURE_MATRIX.md) for capability scope, and [IMPLEMENTATION.md](docs/studio/IMPLEMENTATION.md) for integration details.

The universal development APK builds successfully. All 147 app/terminal-emulator unit tests and 19 filesystem-path checks passed; APK branding and its signature were verified. Device rendering, interactive package installs and performance benchmarks remain untested. The latest local build record is [BUILD_RESULT.json](docs/studio/BUILD_RESULT.json).

## Origin and license

Based on `termux/termux-app` source commit **8629e632fcb95da272221be327db653fb24befe9**, retrieved 2026-10-16. Original documentation is preserved as [UPSTREAM_README.md](UPSTREAM_README.md).

The existing Termux command-library dependency is built from pinned `v2.0.0` source under [third_party/termux-am-library](third_party/termux-am-library/UPSTREAM.md). Its Java implementation is unchanged, and the build no longer requires JitPack.

New Studio code is GPL-3.0-only. Existing files retain upstream notices and exceptions. See [LICENSE.md](LICENSE.md), [NOTICE_STUDIO.md](NOTICE_STUDIO.md), and [LICENSES](LICENSES). This development fork is not an official Termux release.
