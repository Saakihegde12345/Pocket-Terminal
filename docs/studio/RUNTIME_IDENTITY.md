# Independent public application identity

This compatibility source retains `com.termux`. It must not be presented as an independent app that coexists with official Termux in the same Android user/profile.

## Why an ID edit is insufficient

Android private paths contain the application ID. Runtime binaries, scripts, interpreter paths and package metadata can encode the Termux prefix. A new identity needs compatible bootstrap/packages and consistent intent, permission, authority and companion-app constants.

Official references:

- https://github.com/termux/termux-app#forking
- https://github.com/termux/termux-packages/wiki/Termux-execution-environment
- https://github.com/termux/termux-packages/wiki/Building-packages
- https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission

## Release work

1. Choose the final reverse-domain ID and prefix. Java namespaces and Android application identity are distinct; rename only what is necessary.
2. Follow `TermuxConstants` porting guidance and inspect authorities, permissions, actions, shortcuts, paths and manifest placeholders. Audit companions for hardcoded values.
3. Pin a `termux-packages` revision, configure its actual package identity/prefix settings, and rebuild bootstrap packages for supported ABIs. Verify ELF loader/interpreter requirements and executable paths on device.
4. Build and sign a compatible package repository. Keep repository-signing and app-signing keys separate, preserve APT signature checks, and plan key rotation/recovery. Do not point an incompatible prefix at stock packages.
5. Update bootstrap source/version/checksum definitions to the new artifacts. Test hash verification, interrupted extraction, retry, storage exhaustion and first-launch recovery.
6. Rebuild/re-sign required plugins with compatible identity constants. Document which integrations are bundled, optional or unsupported.
7. Validate the runtime/target-SDK strategy for the chosen distribution route. Writable-directory execution restrictions and store rules must be handled; simply raising the target SDK is insufficient.
8. Protect a new release key. Never distribute a production app signed with the checked-in public test key.
9. Test clean install, upgrades, backup/restore, removal, package updates and coexistence with official Termux.

The release gate is a fresh installation that bootstraps, updates packages from its signed repository, runs selected plugins, and coexists with official Termux. Test every claimed ABI/Android version. A successful APK compilation alone establishes none of those runtime properties.

This is a porting specification. No independent package prefix, package repository or production signing infrastructure is included.
