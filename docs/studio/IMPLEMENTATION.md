# Native implementation

## Foundation

The first build uses the full pinned Termux source. Its native Android Views are retained to avoid mixing a UI-toolkit migration with runtime identity work. Compose can later own app chrome through a separate migration; terminal cells should remain in a dedicated terminal surface.

The byte stream follows the existing PTY → TerminalSession → TerminalEmulator → TerminalView pipeline. There is no command interception, output rewriting, AI dependency or WebView terminal layer.

The command library formerly resolved from JitPack is a local source module pinned to the same upstream `v2.0.0` release. Its Java files are unchanged. This removes a dependency-resolution failure without changing runtime behavior. The module's manifest namespace and Gradle file are adapted to the parent build.

## Integration

`StudioChrome` attaches a header, directory row, horizontal session strip and action bar to the existing root. The weighted terminal layout, drawer, extra keys and bottom-space sentinel stay connected to the inherited IME-overlap handling.

Session tabs call the existing `setCurrentSession`. The added `addNewSessionInDirectory` overload passes an explicit directory into the same session-creation path. The original method delegates with no override, preserving its previous directory-selection behavior.

Chrome refreshes on activity start, session selection and session-list/title events. Updates are coalesced into one pending frame callback. High-volume terminal text-change callbacks do not rebuild tabs. The directory breadcrumb therefore can lag when a shell changes directory without changing its title.

`StudioProjectPicker` resolves/lists directories on one worker. Cancellation generations discard late results. The worker and dialogs are cleaned up when the activity is destroyed. Saved paths remain local app metadata.

Paths are canonicalized and checked for readability. Spaces, quotes, dollar signs and semicolons remain literal path data. Selecting a project starts another shell without typing into a running editor, server or password prompt. Missing saved directories can still have their bookmarks removed.

## Visual behavior

Charcoal surfaces, lime selection, readable text and 48 dp action targets define the initial chrome. Terminal font/color preferences remain owned by upstream settings. Labels strip control/format characters and truncate at Unicode code-point boundaries.

Focus mode hides secondary chrome while leaving the header/logo as a way back. Compact-window thresholds use hysteresis to reduce layout churn. Session state comes from real sessions; there are no simulated process meters.

## Development sequence

1. Compile the compatibility build and run upstream unit tests in CI.
2. Validate bootstrap, session behavior, terminal compatibility, IME and accessibility on fresh emulators and physical devices.
3. Profile this fork against the same upstream commit on the same device. Record measured memory, idle CPU, frame pacing and output throughput.
4. Establish independent identity and package infrastructure using `RUNTIME_IDENTITY.md`.
5. Implement native split panes, full file browsing/editing, host profiles, package catalog, theme editing and exportable layouts as separate changes.
6. Validate signing, updates, backup/restore and repository trust before release.

Use tmux for panes in this foundation. CLI packages remain responsible for SSH, development tools and distribution management; native management screens are future work.

## Acceptance requirements

- No lost/reordered bytes under sustained output, and bounded scrollback memory.
- No extra shells from rotation/activity recreation.
- Correct terminal/IME resizing, selection and hardware keyboard behavior.
- Worker cancellation prevents old dialogs from reopening.
- Restoring saved UI metadata does not pretend to restore an OS-killed process.
- Performance claims must come from measurements, not the choice of toolkit.
