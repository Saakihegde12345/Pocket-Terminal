# Pocket-Terminal — native Android foundation

An open-source Android terminal fork with a charcoal-and-lime workspace interface around Termux's existing terminal emulator, shell service and package runtime. **Project name: Pocket-Terminal.**

This is a first native implementation, not a finished production release. The Android UI attaches to real sessions; the Android app contains no simulated command output.

## Implemented

- Native workspace header, live session tabs, new-shell control and long-press session renaming.
- Searchable quick actions for shells, keyboard, font size, extra keys, settings and projects.
- Persistent focus mode. Tap the logo to restore the full workspace UI.
- Search real terminal output in a full-height, read-only inspector, with highlighted matches, previous/next navigation, case matching and manual snapshot refresh.
- Searchable, editable command snippets with preview and explicit insertion into the selected session, without sending Enter.
- Four persistent workspace accents: Lime, Glacier, Orchid and Amber. Terminal color/font files remain under the existing terminal settings.
- Directory browsing and saved project paths. A selected directory starts a new shell with that working directory; paths are never inserted into shell command text.
- Native project file browser with current-folder filename filtering, hidden-file toggle, read-only UTF-8 source previews, path copying and opening a shell in the selected folder.
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

Choose **Project files** while browsing a directory or from a saved project to inspect its files. Folders sort before files. Filtering applies to filenames in the current folder; turn **Hidden** on to include dotfiles. Each view shows up to 200 matching entries, and filtering can find entries beyond that initial limit.

Tap a file for a read-only UTF-8 preview up to 256 KiB. Binary, invalid UTF-8 and oversized files show an explanation. Long-press preview text to select/copy it. **Copy path** copies the selected folder/file path; **Open shell here** creates a normal shell in that folder, or the previewed file's parent. No file contents or paths are executed. Links outside the selected project require opening that target directory as a separate project. File previews do not edit or save files.

## Workspace tools

The bottom bar opens **Search**, **Projects**, **Snippets** and **Keyboard**. The header's **Quick actions** button also exposes these tools, the session drawer, appearance and focus mode.

**Search** captures the current session's visible output and scrollback. Soft-wrapped rows join into one searchable line. Type literal text, use **↑ / ↓** to move through highlighted matches, or toggle **Aa** for case-sensitive search. Long-press output to select/copy text. **Refresh** captures later output; the terminal continues running while the inspector is open. Snapshots keep the most recent 2,000 terminal rows, up to 200,000 UTF-16 characters, and search navigation shows up to 2,000 non-overlapping matches. The UI reports when a snapshot or match limit is reached. Nothing is written to the PTY by search.

**Snippets** starts with three editable examples. Tap a snippet to preview its exact command and target session, then choose **Insert** to paste it into that session's active program. It sends no Enter; the active program handles the pasted input. Choose **Edit** to change or delete an entry, or **New snippet** to save your own. Up to 50 single-line commands are stored locally. Newlines, tabs and control characters are rejected rather than silently changing the command. Deleting every example leaves an empty library.

Choose **Quick actions → Workspace accent → Apply** to save a color choice. This recreates the interface around the existing shell service. Focus mode, projects and snippets remain saved.

Hardware keyboard shortcuts follow the existing Ctrl+Alt convention and honor the setting that disables hardware shortcuts:

| Shortcut | Action |
| --- | --- |
| Ctrl+Alt+Space | Quick actions |
| Ctrl+Alt+F | Search output |
| Ctrl+Alt+S | Snippets |
| Ctrl+Alt+A | Workspace accent |

## Validation and next work

Read [VERIFICATION.md](docs/studio/VERIFICATION.md) for actual check results, [FEATURE_MATRIX.md](docs/studio/FEATURE_MATRIX.md) for capability scope, and [IMPLEMENTATION.md](docs/studio/IMPLEMENTATION.md) for integration details.

The workspace-tools universal development APK builds successfully. All 155 app/terminal-emulator unit tests and 59 SDK-independent path/search/snippet checks passed. Its signature and four ABI directories were verified. See [VERIFICATION.md](docs/studio/VERIFICATION.md) and [WORKSPACE_TOOLS_BUILD_RESULT.json](docs/studio/WORKSPACE_TOOLS_BUILD_RESULT.json) for the results. Device rendering, interactive package installs and performance benchmarks remain untested. The original foundation artifact is recorded separately in [BUILD_RESULT.json](docs/studio/BUILD_RESULT.json).

The project-file browser adds 39 filesystem regression checks, bringing SDK-independent validation to 98 checks. Android build/test results for the feature are recorded by its pull request workflow. File editing remains planned.

## Origin and license

Based on `termux/termux-app` source commit **8629e632fcb95da272221be327db653fb24befe9**, retrieved 2026-10-16. Original documentation is preserved as [UPSTREAM_README.md](UPSTREAM_README.md).

The existing Termux command-library dependency is built from pinned `v2.0.0` source under [third_party/termux-am-library](third_party/termux-am-library/UPSTREAM.md). Its Java implementation is unchanged, and the build no longer requires JitPack.

New Studio code is GPL-3.0-only. Existing files retain upstream notices and exceptions. See [LICENSE.md](LICENSE.md), [NOTICE_STUDIO.md](NOTICE_STUDIO.md), and [LICENSES](LICENSES). This development fork is not an official Termux release.
