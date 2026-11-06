# Private upgrade 1.4.1

This working tree is based on jiqiu2022/Zygisk-MyInjector. The original MIT license and attribution are retained. Nothing has been pushed to GitHub.

## Root detection fix in 1.4.1

Version 1.4.0 incorrectly read stdout from `shell.newJob().add("id -u").exec()` without configuring output collection. libsu's shell-bound jobs discard stdout by default (unlike `Shell.cmd()`). As a result, even an authorized root shell was rejected. The production probe now explicitly uses `.to(stdout, stderr)` and verifies the exit code and captured UID, while keeping stderr separate.

`tests/host/run.py` runs the production RootProbe against the real libsu 6.0.0 runtime and a real host `sh`, using a small TextUtils platform shim only during shell initialization. It reproduces the old empty-stdout result and covers root/non-root outputs, failed commands, stderr warnings, missing output and ambiguous output. Controlled `id` output tests command I/O, not actual Android root privileges. Existing session-policy tests did not exercise this libsu contract and therefore missed the regression in 1.4.0.

APK signing is unchanged; updating 1.4.0 in place is sufficient and does not require wiping data. Native module code is unchanged from 1.4.0.

## Changes in 1.4.0

- Bundled Fira Sans Regular/Medium/SemiBold/Bold from Google Fonts with its OFL license, and Lucide 0.468.0 vector icons with their license. Both licenses are included in the APK assets and source tree.
- A periwinkle header, rounded content pane, floating navigation, icon tiles and spacious settings cards based on the supplied UI reference. Light/dark/system appearance is saved in Settings. Framework spinner rows also use Fira Sans.
- New launches open the SO library; activity recreation for theme/language preserves the current tab.
- Root checking closes stale/non-root libsu sessions, probes `id -u`, discards denied sessions immediately and permits retry without clearing app data.
- App dialog edits are staged, including nested Gadget changes. Cancel does not save them. Save publishes selected libraries and config once, then automatically force-stops only the affected target app, as explicitly requested, with a reopen notification. No-change saves do not restart it.
- Same-name library imports now ask to replace the file instead of silently ignoring it. Enabled consumers receive the new file and are closed for their next launch. Files remain atomically replaced instead of truncating mapped inodes.
- Native configuration is reloaded for each new process specialization. Android cannot replace an existing in-process mapping merely because its activity was closed; restarting the target process is necessary. The user no longer needs to manually Force stop it after a successful apply.
- Apply failures are reported. The target is not closed after a deployment or config publication failure. File/config commits are individually atomic; a failure midway through a multi-file deployment can require retry and is not a cross-file rollback transaction.

## Changes inherited from 1.3.0

- English default resources, Vietnamese and Chinese translations, with a persistent language picker in Settings. Runtime Java dialog strings also use resources.
- Retained tab fragments preserve search text and scroll position. Application labels are cached in memory and on disk, invalidated by package changes and locale. Icons load on two background workers and use a bounded cache; recycled rows reject stale icon results. Initial package discovery and root checks still take time, so a cold launch is not guaranteed to be instantaneous.
- Root authorization precedes the main interface. Denial shows instructions and a Close app action.
- Material 3 surfaces, rounded outlined cards, toolbar, consistent spacing, and light/dark colors.
- Enabled applications sort first, using locale-aware alphabetical order within each group and package names as deterministic ties. Toggle changes immediately reorder the list.
- Injection delay runs on the loading worker, avoiding blocking application startup. Native loading checks JNI inputs, owns worker arguments, attaches/detaches its own JNI thread, rejects truncated paths, and validates numeric settings without throwing in a build with exceptions disabled.
- SO deployment publishes a new inode by rename instead of truncating a potentially mapped SO. Existing target SELinux labels are retained; deployment fails if they cannot be determined. Config publication also uses rename.
- The service uses bounded startup waits, installs the APK only when needed, and logs installation failures. Module IDs are consistent between Gradle and shell packaging.

## Build

Use JDK 17, Android SDK platforms 32 and 34, build tools 30.0.3/34.0.0, NDK 25.2.9519653 and CMake 3.22.1. Configure `sdk.dir` in local.properties.

```sh
bash gradlew :configapp:assembleDebug :configapp:testDebugUnitTest :configapp:lintDebug :module:assembleRelease -PtargetAbis=arm64-v8a
```

The optional `targetAbis` property limits the module build. Without it, the upstream ABI defaults apply. The supplied private build targets ARM64 for Lenovo Y700 Gen 2.

Host native regression check:

```sh
g++ -std=c++17 -fno-exceptions -Itests/native tests/native/config_test.cpp -o /tmp/config-test
/tmp/config-test
```

## Device validation still required

No Lenovo Y700 / Android 15 / SukiSU Ultra 40798 device was connected during development. Kernel/SUSFS compatibility and the reported crash cannot be confirmed without running on the device. A loaded library can still crash from its own constructors, hooks, or ABI mismatch. The upstream custom linker has known limitations with Frida Gadget; those limitations are not resolved by this update.

Verify root denial/grant, English/Vietnamese switches, light/dark UI, tab return, app install/uninstall, enabled ordering, and standard SO loading. Do not change SELinux to Permissive for this build.

The APK is signed with a development key. An upstream APK signed with a different key cannot be updated in place. Back up configuration before uninstalling the previous APK if Android reports a signature mismatch. Keep the development keystore private for future updates.

For a reproducible crash, capture `adb logcat -b all -v threadtime` while launching the target, and retain any tombstone available on the test device. Note the SO, ABI, loading method and delay. These are diagnostic steps, not a guarantee of compatibility.
