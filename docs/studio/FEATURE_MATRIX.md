# Capability matrix

This is source scope, not a claim that device acceptance testing passed.

| Capability | Source status | Required work/verification |
| --- | --- | --- |
| PTY, escape sequences, truecolor, Unicode, scrollback, IME | Inherited | Emulator tests and device interaction |
| Bootstrap, apt/pkg and development packages | Inherited with `com.termux` prefix | Clean install and package updates |
| Terminal styling and extra keys | Inherited | Check with new chrome and different keyboards |
| Service lifecycle, shortcuts and intents | Inherited | Background, rotation and signing tests |
| Live tabs, rename and quick actions | New native implementation | Device/a11y/dialog tests |
| Directory picker and saved projects | New native implementation | Shared-storage and filesystem races |
| Project files, filename filtering and read-only UTF-8 preview | New native implementation | Device/a11y/large-font tests; 200 visible entries and 256 KiB preview cap; no editing |
| Focus mode and compact chrome | New native implementation | Portrait, landscape, split-screen, large fonts |
| Output search, match navigation and case matching | New native snapshot inspector | Device selection/IME/accessibility; bounded recent scrollback, manual refresh |
| Saved command snippets | New native implementation | Device insertion into different active programs; no automatic Enter |
| Four saved workspace accents | New native implementation | Activity recreation with running sessions; terminal color files remain separate |
| SSH/SFTP, servers, Neovim and tmux | Runtime packages | Install/exercise tools; no new native host manager |
| PRoot distributions and optional X11 | Separate ecosystem integrations | Device and identity-port testing |
| Companion plugins | Integration points retained | Compatible identity/signing; existing APKs not assumed compatible |
| Native split panes, file editor, host manager, package catalog | Planned | Separate implementation; tmux/CLI tools available meanwhile |
| Full terminal theme editor and workspace-layout export | Planned | Workspace accents/project/focus/snippet preferences are local metadata |
| Independent ID beside official Termux | Not implemented | Rebuild bootstrap/packages and integration constants |
| Root-only operations, Docker daemon, kernel modules | Not supplied by the app | OS/kernel privileges; PRoot is not real root |
| Survival after Android kills a process | Not guaranteed | Android lifecycle constraints remain |

## Device acceptance

Use a fresh emulator/profile and physical devices from more than one vendor:

1. Bootstrap, install/update packages, open up to eight sessions and exit them.
2. Run Bash plus another shell, Neovim/tmux, long output, Unicode/emoji and truecolor.
3. Rotate with running processes and resize split-screen; check for duplicate shells and stale selection.
4. Test Gboard suggestions/clipboard rows, hardware keyboard, extra keys and copy/paste.
5. Open paths with spaces, quotes, dollar signs and Unicode; confirm the actual working directory.
6. Grant/deny/revoke storage permission, remove saved directories, and cancel slow folder loading.
7. Exercise local servers and background restrictions. Distinguish retained metadata from live processes.
8. Profile cold start, idle CPU, memory and sustained output against the same upstream source build.
9. Search Unicode, wrapped lines, empty output and busy sessions. Refresh, change queries quickly, rotate and close while searches are pending; verify no stale highlights or reopened dialogs.
10. Create/edit/delete snippets, restart the app, fill the 50-item limit and insert into Bash/Neovim. Reject multiline/control input; close a target session while its preview is open.
11. Apply every accent with multiple shells and running jobs; confirm no duplicate shells or lost commands. Check TalkBack labels, 48 dp controls, landscape keyboard and large fonts.
12. Open Project files from a saved project and from folder browsing. Filter names, toggle hidden files, preview Unicode/CRLF/tabbed text, copy paths and open shells in the actual folder. Check empty, binary, oversized, deleted and unreadable files plus links outside the project. Close or rotate while loading and verify no dialog reopens; check narrow landscape, large fonts, TalkBack and selection handles.
