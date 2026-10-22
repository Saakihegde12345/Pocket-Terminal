# Pocket-Terminal

An open-source Android terminal fork with a charcoal-and-lime workspace interface around Termux's existing terminal emulator, shell service and package runtime. **Project name: Pocket-Terminal.**

This is a development build of a native terminal workspace. The Android UI attaches to real sessions; the Android app contains no simulated command output. Public distribution still requires the independent runtime identity and device acceptance work described below.

## Implemented

- Native workspace header, live session tabs, new-shell control and long-press session renaming.
- Searchable quick actions for shells, keyboard, font size, extra keys, settings and projects.
- Persistent focus mode. Tap the logo to restore the full workspace UI.
- Search real terminal output in a full-height, read-only inspector, with highlighted matches, previous/next navigation, case matching and manual snapshot refresh.
- Searchable, editable command snippets with preview and explicit insertion into the selected session, without sending Enter.
- Four persistent workspace accents: Lime, Glacier, Orchid and Amber. Terminal color/font files remain under the existing terminal settings.
- Directory browsing and saved project paths. A selected directory starts a new shell with that working directory; paths are never inserted into shell command text.
- Native project file browser with current-folder filename filtering, hidden-file toggle, read-only UTF-8 source previews, path copying and opening a shell in the selected folder.
- UTF-8 file editing with explicit saves, external-change detection, atomic replacement, preserved permissions/BOM and unsaved drafts retained across Activity recreation.
- Two native terminal panes, stacked or side by side, with independent sessions and input routed to the selected pane.
- Saved SSH/SFTP hosts with explicit connection confirmation, using OpenSSH for authentication and host verification.
- Searchable local APT package catalog with installation, updates, upgrades and removal through interactive package-tool sessions.
- Terminal theme editor for background, foreground, cursor and all 16 ANSI colors, including three editable presets.
- Versioned workspace export/import through Android document pickers, with validated metadata, undo of the last import and explicit restoration of shell directories.
- Compact chrome when the app window has little vertical space.
- The existing terminal renderer, input handling, shell lifecycle, extra keys, bootstrap downloader and package environment are retained.

## Development identity

The application ID and native package prefix remain **`com.termux`**. This compatibility build cannot coexist with an existing Termux installation in the same Android user/profile. Test in a fresh emulator or separate Android user/profile. Do not uninstall your existing Termux environment to try it: uninstalling removes its private files.

An independent public release needs a separate ID, release key, rebuilt bootstrap/packages, repository and plugin strategy. See [RUNTIME_IDENTITY.md](docs/studio/RUNTIME_IDENTITY.md). Changing only `applicationId` is insufficient.

## Build

The source uses Java 17, Gradle 9.2.1, Android compile SDK 36 and NDK 29.0.14206865. Use the default `apt-android-7` package variant. The inherited target SDK remains 28; this is not a Google Play release configuration.

Open the project in Android Studio, install the SDK/NDK versions above, and run from the root with dependency-download access.

### Source-only checks

This command checks path handling, Java syntax, resources and repository imports; it does not compile Android code or assemble an APK:

```bash
python3 tools/check_source.py
```

Source checks require Python 3 and a JDK containing `jdk.compiler`.

### CI-equivalent debug build

Set the same four environment variables as `.github/workflows/pocket-terminal-build.yml` before running Gradle. They select the `apt-android-7` bootstrap, disable debug ABI splits so the output is one universal APK, set the development version, and set the CI artifact filename tag:

```text
TERMUX_PACKAGE_VARIANT=apt-android-7
TERMUX_SPLIT_APKS_FOR_DEBUG_BUILDS=0
TERMUX_APP_VERSION_NAME=0.4.0-pocket-dev
TERMUX_APK_VERSION_TAG=pocket-terminal-dev
```

On a POSIX shell:

```bash
export TERMUX_PACKAGE_VARIANT=apt-android-7
export TERMUX_SPLIT_APKS_FOR_DEBUG_BUILDS=0
export TERMUX_APP_VERSION_NAME=0.4.0-pocket-dev
export TERMUX_APK_VERSION_TAG=pocket-terminal-dev
unset JITPACK_NDK_VERSION
./gradlew --no-daemon --max-workers=2 :app:assembleDebug :app:testDebugUnitTest :terminal-emulator:testDebugUnitTest
```

On Windows PowerShell:

```powershell
$env:TERMUX_PACKAGE_VARIANT = "apt-android-7"
$env:TERMUX_SPLIT_APKS_FOR_DEBUG_BUILDS = "0"
$env:TERMUX_APP_VERSION_NAME = "0.4.0-pocket-dev"
$env:TERMUX_APK_VERSION_TAG = "pocket-terminal-dev"
Remove-Item Env:JITPACK_NDK_VERSION -ErrorAction SilentlyContinue
.\gradlew.bat --no-daemon --max-workers=2 :app:assembleDebug :app:testDebugUnitTest :terminal-emulator:testDebugUnitTest
```

`JITPACK_NDK_VERSION` is an optional upstream Gradle override; CI does not set it, so leave it unset to use `gradle.properties`. The matching artifact is `app/build/outputs/apk/debug/Pocket-Terminal_pocket-terminal-dev_universal.apk`. **No prebuilt APK is included.**

The **Pocket-Terminal development APK** workflow runs on pushes and pull requests to `main`. It can also be started from **Actions → Pocket-Terminal development APK → Run workflow**. Download its artifact after the job succeeds. The workflow uses the standard GitHub-hosted runner's Android SDK and downloads missing components.

Original Termux-specific CI/release workflows are archived under `docs/studio/upstream-workflows`; only the Pocket-Terminal build is active in this fork.

The upstream debug keystore is intentionally public and untrusted. Use it only for development; a public release needs a separate protected key.

## Projects

Open **Projects → Browse home directory** or **Enter directory path**, choose a directory and select **Open shell here**. Successful opens are bookmarked locally, up to 30 paths. Saved entries offer open, browse and remove-bookmark actions. Removing a bookmark never deletes files.

For phone storage first run `termux-setup-storage` and grant its requested permission. Enter your actual folder path, for example `/storage/emulated/0/Projects`; names are case-sensitive. The picker accepts filesystem paths, not `content://` URIs. Keep executables needing Unix permissions in app-private storage.

The directory breadcrumb refreshes on session/title/lifecycle events and on debounced output from the active shell. It may lag briefly during sustained output, but tab views are not rebuilt for every byte.

Choose **Project files** while browsing a directory or from a saved project to inspect its files. Folders sort before files. Filtering applies to filenames in the current folder; turn **Hidden** on to include dotfiles. Each view shows up to 200 matching entries, and filtering can find entries beyond that initial limit.

Tap a file for a read-only UTF-8 preview up to 256 KiB. Binary, invalid UTF-8 and oversized files show an explanation. Long-press preview text to select/copy it. Choose **Edit** to change an existing text file, then **Save** to write it. Saving rechecks the original bytes and refuses to overwrite external edits; it atomically replaces the file while retaining permissions and its UTF-8 BOM. Unsaved drafts survive Activity recreation, including rotation; process-death recovery is not provided. **Copy path** copies the selected folder/file path; **Open shell here** creates a normal shell in that folder, or the previewed file's parent. Links outside the selected project require opening that target directory as a separate project.

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

## Panes, hosts and packages

Open **Quick actions → Terminal panes** for top/bottom or side-by-side panes. Select a pane by touching its terminal or its labeled header; tabs, extra keys, clipboard insertion and hardware input follow that selection. Choosing an already visible session selects its pane. The initial split reuses another session or creates one new shell; restoring the UI reuses existing sessions. Closing a pane keeps its process running in the session list.

**Quick actions → Saved hosts** creates, edits and deletes up to 50 SSH/SFTP endpoints. Save a name, hostname/IP address, username and port. Connect opens an explicitly confirmed session using `ssh` or `sftp`; install `openssh` first. Passwords, private keys and host verification remain in OpenSSH. Host strings are passed as arguments rather than shell text.

**Quick actions → Package catalog** searches the on-device APT index. If it is empty, choose **Update index**. Selecting a package offers installation/update or confirmed removal. **Upgrade all** opens a normal interactive package session. Review the proposed changes there; the app does not send automatic confirmation. Package operations use the configured signed repositories.

## Themes and workspace files

**Quick actions → Terminal theme editor** edits all 19 default/ANSI color fields using `#RRGGBB`, with Pocket dark, Midnight and Paper presets. Apply writes `~/.termux/colors.properties` and refreshes the live terminal sessions. Workspace accents remain independently selectable.

**Quick actions → Workspace export / import** saves JSON through Android’s document picker. Exports contain saved projects, command snippets, host endpoints, font size, focus mode, pane orientation and shell directory/name metadata. They do not contain passwords, keys, running processes, terminal output or project files. Treat exported command snippets and host names as private information. Import validates the schema and limits before asking to replace preferences, and **Undo last import** restores the previous metadata. **Restore saved shell layout** explicitly opens fresh shells in the saved directories, subject to the eight-session limit; it does not rerun commands or reconnect hosts. Terminal theme files stay separate.

## Validation and next work

Read [VERIFICATION.md](docs/studio/VERIFICATION.md) for actual check results, [FEATURE_MATRIX.md](docs/studio/FEATURE_MATRIX.md) for capability scope, and [IMPLEMENTATION.md](docs/studio/IMPLEMENTATION.md) for integration details.

The `0.4.0-pocket-dev` universal APK builds successfully for all four ABIs. All **167 unit tests** and **149 SDK-independent assertions** passed. Its v1/v2 signature, package metadata and ABI contents were verified. A fresh Android 9 x86-64 emulator passed installation/bootstrap and smoke checks for panes, selected input, file editing, host persistence, package search, terminal themes and workspace export/import. Pane selection and unsaved file drafts survived Activity recreation.

Physical-device, multi-vendor, TalkBack and performance acceptance remain unperformed. Live SSH/SFTP connections and interactive package changes were not part of these smoke checks. See [VERIFICATION.md](docs/studio/VERIFICATION.md) and [COMPLETION_BUILD_RESULT.json](docs/studio/COMPLETION_BUILD_RESULT.json) for exact evidence and limits. Earlier records remain in [WORKSPACE_TOOLS_BUILD_RESULT.json](docs/studio/WORKSPACE_TOOLS_BUILD_RESULT.json) and [BUILD_RESULT.json](docs/studio/BUILD_RESULT.json).

## Origin and license

Based on `termux/termux-app` source commit **8629e632fcb95da272221be327db653fb24befe9**, retrieved 2026-10-16. Original documentation is preserved as [UPSTREAM_README.md](UPSTREAM_README.md).

The existing Termux command-library dependency is built from pinned `v2.0.0` source under [third_party/termux-am-library](third_party/termux-am-library/UPSTREAM.md). Its Java implementation is unchanged, and the build no longer requires JitPack.

New Studio code is GPL-3.0-only. Existing files retain upstream notices and exceptions. See [LICENSE.md](LICENSE.md), [NOTICE_STUDIO.md](NOTICE_STUDIO.md), and [LICENSES](LICENSES). This development fork is not an official Termux release.
