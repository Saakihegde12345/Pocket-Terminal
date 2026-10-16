# Vendored Termux command library

Source: https://github.com/termux/termux-am-library

Tag: `v2.0.0`

Commit: `d979ee308d52c59c6265ed9d9e254c97702ee4fb`

License: Apache-2.0; original license and source-file notices are preserved.

The four Java source files and consumer ProGuard rules are unchanged from that revision. The Android manifest's package attribute moved to Gradle's namespace property. The module build file uses the parent project's SDK/Java settings and omits standalone Maven publication tasks.

This replaces the same upstream `com.termux:termux-am-library:v2.0.0` binary dependency with a local source module. JitPack returned HTTP 403 during dependency resolution in the preparation environment. The pinned source removes that JitPack requirement without changing the library version or Java implementation.
