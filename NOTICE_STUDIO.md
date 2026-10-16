# Source origin and modifications

Upstream: https://github.com/termux/termux-app

Source commit: `8629e632fcb95da272221be327db653fb24befe9`.

Prepared: 2026-10-16.

The distribution preserves the upstream terminal-emulator, terminal-view, termux-shared, shell service, tests, resources, Gradle wrapper and notices. Workspace UI and directory handling are added under `app/src/main/java/com/termux/app/studio/`. Small integration edits affect `TermuxActivity`, `TermuxTerminalSessionActivityClient`, and `TermuxSessionsListViewController`. The original README is `UPSTREAM_README.md`.

New Studio files use SPDX `GPL-3.0-only`. The upstream license declares GPLv3-only with exceptions; read original notices for component-specific terms. Full GPLv3 and Apache 2.0 texts are included in `LICENSES/`. Downloaded runtime packages retain their own licenses. The vendored command library retains its Apache-2.0 license and is pinned to `termux/termux-am-library@d979ee308d52c59c6265ed9d9e254c97702ee4fb` (`v2.0.0`); its provenance and build-file adaptations are documented beside its source.

The existing public development keystore is a test fixture, not a release credential. This source creates no production signing key. Pocket-Terminal is not affiliated with or endorsed by the Termux maintainers.
