# Native implementation

## Foundation

The first build uses the full pinned Termux source. Its native Android Views are retained to avoid mixing a UI-toolkit migration with runtime identity work. Compose can later own app chrome through a separate migration; terminal cells should remain in a dedicated terminal surface.

The byte stream follows the existing PTY → TerminalSession → TerminalEmulator → TerminalView pipeline. There is no command interception, output rewriting, AI dependency or WebView terminal layer.

The command library formerly resolved from JitPack is a local source module pinned to the same upstream `v2.0.0` release. Its Java files are unchanged. This removes a dependency-resolution failure without changing runtime behavior. The module's manifest namespace and Gradle file are adapted to the parent build.

## Integration

`StudioChrome` attaches a header, directory row, horizontal session strip and action bar to the existing root. The weighted terminal layout, drawer, extra keys and bottom-space sentinel stay connected to the inherited IME-overlap handling.

Session tabs call the existing `setCurrentSession`. The added `addNewSessionInDirectory` overload passes an explicit directory into the same session-creation path. The original method delegates with no override, preserving its previous directory-selection behavior.

Chrome refreshes on activity start, session selection, session-list/title events and debounced output from the active shell. Updates are coalesced and bounded so high-volume terminal text-change callbacks do not rebuild tabs for every byte. The directory breadcrumb may lag briefly during sustained output.

`StudioProjectPicker` resolves/lists directories on one worker. Cancellation generations discard late results. The worker and dialogs are cleaned up when the activity is destroyed. Saved paths remain local app metadata.

Paths are canonicalized and checked for readability. Spaces, quotes, dollar signs and semicolons remain literal path data. Selecting a project starts another shell without typing into a running editor, server or password prompt. Missing saved directories can still have their bookmarks removed.

## Visual behavior

Charcoal surfaces, lime selection, readable text and 48 dp action targets define the initial chrome. Terminal font/color preferences remain owned by upstream settings. Labels strip control/format characters and truncate at Unicode code-point boundaries.

Focus mode hides secondary chrome while leaving the header/logo as a way back. Compact-window thresholds use hysteresis to reduce layout churn. Session state comes from real sessions; there are no simulated process meters.

## Workspace tools

`StudioOutputSearch` owns a full-height native dialog. `OutputSnapshot` reads the active emulator only on the UI thread and bounds the copy to the newest 2,000 rows / 200,000 UTF-16 characters. It honors soft wraps and preserves hard newlines. Search runs on detached strings on a single worker after a 180 ms debounce. `OutputSearch` quotes literal queries, retains original UTF-16 offsets for Unicode highlighting, and caps results at 2,000. Request generations and cancellation discard stale results and scroll callbacks; dismiss/destroy cancels pending work. There is no text-change hook, continuous snapshotting or PTY mutation.

`StudioSnippets` separates library, preview and editor flows. `CommandSnippet` validates names and preserves command text, rejecting newlines, tabs, control characters and malformed surrogate sequences. `SnippetStore` serializes up to 50 entries with stable IDs in private app preferences; deleting the starter entries does not seed them again. Insertion revalidates both text and the exact previewed live session and uses its existing emulator paste path, including bracketed paste when enabled, without adding Enter. It does not assume a shell is waiting for a command.

`StudioUi` resolves one of four saved accents with a fallback to Lime. An explicit Apply recreates only the Activity so the header, tabs, buttons and new dialogs use the same accent; the bound shell service and terminal color files remain owned by their existing paths. Ctrl+Alt+Space/F/S/A use the upstream hardware-shortcut setting and dispatch to null-safe Activity entry points.

## Development sequence

1. Compile the compatibility build and run upstream unit tests in CI.
2. Validate bootstrap, session behavior, terminal compatibility, IME and accessibility on fresh emulators and physical devices.
3. Profile this fork against the same upstream commit on the same device. Record measured memory, idle CPU, frame pacing and output throughput.
4. Establish independent identity and package infrastructure using `RUNTIME_IDENTITY.md`.
5. Validate native split panes, file editing, host profiles, package catalog, theme editing and exportable layouts on supported devices.
6. Validate signing, updates, backup/restore and repository trust before release.

Use tmux for panes in this foundation. CLI packages remain responsible for SSH, development tools and distribution management; native management screens are future work.

## Acceptance requirements

- No lost/reordered bytes under sustained output, and bounded scrollback memory.
- No extra shells from rotation/activity recreation.
- Correct terminal/IME resizing, selection and hardware keyboard behavior.
- Worker cancellation prevents old dialogs from reopening.
- Restoring saved UI metadata does not pretend to restore an OS-killed process.
- Performance claims must come from measurements, not the choice of toolkit.


## Project files

`StudioFileBrowser` is owned and closed by `StudioProjectPicker`. A selected project or browsed directory becomes the browser root. `WorkspaceFiles` resolves canonical paths, enforces that root boundary, lists folders before files and keeps at most 200 sorted entries. Filename filtering occurs before the visible-entry cap. Hidden files are opt-in for each panel. Directory enumeration still uses Android-compatible `File.list()`, which enumerates the directory before selecting visible entries.

File and directory I/O runs on a single worker; cancellation and generation checks discard results after navigation, query changes, dialog dismissal or Activity destruction. Preview reads enforce a 256 KiB cap both before and during reading, reject malformed UTF-8 and binary/control data, and publish only a detached string to the UI. Preview text is selectable and read-only. The explicit copy action places only the selected path on Android's clipboard. Opening a shell reuses the validated directory/session creation path; it never types a path into an active program.

The panel uses the saved workspace accent and standard Studio controls. Its toolbar scrolls horizontally on narrow windows. Device rendering, accessibility and performance acceptance remain separate from source checks and Android compilation.

## Completed workspace features

`StudioFileEditor` edits an existing preview. `WorkspaceFiles.prepareSave` validates encoded UTF-8 length and compares a SHA-256 snapshot of the original raw bytes, including its BOM. The worker writes and syncs a same-directory temporary file, preserves its POSIX mode with `Os.chmod`, rechecks the original and replaces it using `Os.rename`. Errors retain the original file and the edit buffer. A final external write between the last comparison and rename cannot be excluded; concurrent writers must coordinate separately. Draft text and its original snapshot survive Activity recreation via the custom non-configuration instance, without placing large file contents into a saved-state Bundle. Process-death draft recovery is not supplied.

`StudioPanes` owns two real terminal views, each attached to a distinct session. `StudioTerminalView` selects the active view before touch/focus input, and the Activity and shared extra keys follow it. Choosing a visible session selects its pane. Output notifications update whichever view owns that session. A collapsed second view is detached, preventing it from imposing an obsolete PTY size. UI recreation reuses service sessions; it never starts a shell solely to restore a split. Font/color updates reach both views and session colors. Pane orientation and the last secondary handle are local preferences.

`HostProfile` validates endpoint fields and constructs argument arrays. `HostStore` keeps up to 50 endpoints in private preferences. `StudioHosts` requires an explicit connection action, then `StudioCommands` starts the existing OpenSSH executable in a new terminal session. Authentication and known_hosts checks remain owned by OpenSSH; no password, key or remote command is stored by the host UI.

`StudioPackages` runs only local `apt-cache search` on a cancellable worker with a literal POSIX-regex query and up to 200 results. Catalog identifiers are validated before becoming arguments. Index updates, install/update, upgrade and removal are explicit interactive `pkg` sessions, with no automatic yes flag and no mutation of an existing shell's input. The configured APT repositories and signature validation remain intact.

`TerminalTheme` validates all nineteen `#RRGGBB` fields and defines three presets. `StudioThemes` loads/writes colors.properties on a worker using Android AtomicFile, then reloads colors across the live sessions and both views. Applying a terminal scheme is distinct from choosing the workspace accent.

`WorkspaceState` whitelists versioned JSON metadata and validates collection sizes, identifiers, absolute paths, command snippets, host profiles and scalar types before any preferences are changed. `StudioWorkspace` uses Android document pickers and bounded UTF-8 reads on a worker, confirms replacement, and retains a metadata snapshot for undo. Import never starts commands or sessions. A separate confirmed layout-restore action resolves every directory before opening fresh shells within the existing eight-session cap. Export includes session names/directories and pane orientation, not terminal contents, keys, passwords or live process state.
