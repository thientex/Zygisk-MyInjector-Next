# Private upgrade 1.5.2

This working tree is based on jiqiu2022/Zygisk-MyInjector. The original MIT license and attribution are retained. The maintained source is published at https://github.com/thientex/Zygisk-MyInjector-Next.

## Deployment, logging and UI in 1.5.2

- Save is on the left, Cancel on the right, with the short localized Save label. Save/toggle no longer produce toasts. Failures use a modal message; failed saves retain selections and failed toggles revert.
- Settings ends with a clickable © thientex credit linking to https://github.com/thientex. Original MIT, Fira Sans and Lucide licenses/credits remain intact.
- All module logging uses MyInjector, including the custom linker and service. Runtime messages are English. Selecting a target is explicitly logged as preparation, not a false report that dlopen already succeeded. Missing/unreadable libraries report the actual path, errno and process UID.
- The current native loader reads `<app_data_dir>/files/<name>` using the directory provided during Zygisk specialization. With this design, deployment into that app-private files directory is required; `/data/user/0` is only one possible location and `/data/data` is normally its user-0 alias.
- The config app now resolves ApplicationInfo.dataDir with PackageManager for its current Android user, also supporting secondary users and adopted-storage paths. SO, Gadget configuration, cleanup and reset use the same resolver. It does not guess paths for other users, invent a missing app data root, or fall back to loading arbitrary paths under /data/local/tmp.
- A single checked root script obtains the data directory's UID:GID and full SELinux label (including MLS categories), refuses a symlink files directory, creates missing files directories without widening existing permissions, and copies to a unique staging inode. It checks byte equality, sets owner/label/read-only mode (0500 SO, 0400 Gadget config), and atomically renames. Failures abort and clean staging. No permissive SELinux or generic `app_data_file:s0` category stripping is used.
- Re-saving an enabled target republishes its libraries and restarts that target even if settings are unchanged, repairing files lost after clearing target data. Disabled no-change saves remain a no-op. Multi-file updates are not a rollback transaction; a failed deployment can require retry.
- The external Gadget config path uses the same publication routine. Tests exercise the production shell script with real Linux copy/rename/permissions, while Android SELinux commands are controlled fixtures. These tests cannot establish device policy compatibility.
- Missing/locked user storage, denied root app profiles or namespaces, ROM SELinux policy, isolated processes, SO ABI/dependencies and root/Zygisk implementation support can still prevent loading. Work-profile management must run from that profile. Only the supplied ARM64 build is packaged; 32-bit targets require matching module/SO ABI.
- The module is linked with 16 KiB LOAD alignment and the custom linker queries the runtime page size instead of assuming 4096. Host alignment arithmetic is tested with controlled 4/16 KiB sysconf values; actual custom-linker execution on 16 KiB Android remains untested.
- Android 8+ compatibility uses long-standing PackageManager and Android shell facilities. No physical Android 8–latest device matrix or SukiSU device is connected, so universal compatibility is not claimed.

## Save dialog and wording fixes in 1.5.1

- The app configuration dialog has a bounded scrolling body and a separate, pinned Cancel / Apply and save action row. The notice is inside the scroll area instead of a separate alert message; a long SO list or large text cannot push the save action below the dialog. Saving still uses the existing deployment/config publication and target restart sequence. A failed apply leaves the dialog open for retry.
- The denied-root dialog only instructs users to close the app, grant root in their root manager, and reopen. Technical diagnostics are omitted. Its sole Close app button still removes the task and terminates this app's own process without root.
- The settings action is named Delete all data (Xóa toàn bộ dữ liệu in Vietnamese). Its scope and confirmation remain unchanged.
- Robolectric covers a long dialog at normal and 1.5x text sizes, action visibility, a save callback writing selected SO data to a local JSON fixture, cancellation, and retry after failure. These checks do not execute root config publication or injection on a physical device.
- Updating the APK is sufficient; native module code is unchanged.

## App refresh and data management in 1.5.0

- A refresh icon beside Apps invalidates the in-memory catalog and icon cache, scans PackageManager on the I/O queue and reapplies the current search/filter. Repeated clicks are blocked while loading; failures re-enable the button. A Robolectric regression installs a new package and verifies it appears after the same cache-clear/rescan sequence.
- Clean configuration reads the on-disk JSON, takes an installed-package snapshot, and removes only missing package entries. Library SO files and installed-app entries are preserved. Invalid/unreadable JSON fails instead of being silently overwritten.
- Factory reset is confirmed in a dialog. It publishes default configuration, closes enabled configured targets, removes only their configured SO/Gadget files, clears the module SO library and app cache, then resets local language/theme/filter preferences. Program files, module binaries and original SO files outside the app/library are kept.
- Both operations run on the existing serial I/O queue. Partial failures are reported, not treated as a successful all-or-nothing transaction. Package existence is evaluated for apps visible to PackageManager in the current Android user/profile.
- No device-side reset or root deletion was executed during development. Model/path/cache tests and the Android package-catalog regression run locally; a physical device is still needed to verify root commands and installed-package visibility.

## Compact spacing and full close in 1.4.3

- Header logo/title margin is 11dp instead of 16dp, approximately one third closer.
- Navigation uses a 68dp minimum height plus a smaller font-scale allowance. Native Skia/Fira Sans layout tests measure the icon-to-visible-text gap at 25px → 13px for normal fonts and 41px → 21px at 1.5x configuration in the mdpi test. Light/dark bounds, ellipsis and system-bar handling still pass.
- The denied-root dialog now has exactly one button: Close app. The retry button and automatic live-process retry on return from the root manager are removed, as requested.
- Close app calls `finishAndRemoveTask()` and then `Process.killProcess(Process.myPid())` in a finally block. This ends this application's own process without su, root-manager-specific APIs, clearing app data or killing other apps. Saved configuration remains on disk.
- Android has no standard API that forces every root manager/kernel to refresh authorization for an existing PID. This release implements the user's explicit process-termination fallback; it does not claim universal live-process reauthorization on SukiSU/KernelSU.
- Robolectric verifies the dialog has no neutral/negative button, clicking Close kills the app PID, task removal is requested, and the process-termination call still happens if task removal throws. Android process killing is shadowed in this test; it does not kill the test JVM or validate a physical SukiSU device.

## Root, layout and scrolling changes in 1.4.2

- The app now owns a standalone `su` shell built with libsu's public Builder API. Retrying closes the old shell and starts a new `su` process, bypassing libsu's process-global main-shell cache and failed-initialization guard. Every root command in the app uses the exact authorized session and explicitly captures stdout/stderr. No reflection is used in production.
- Returning from the root manager automatically retries while the app is awaiting authorization. The retry button starts the check directly, without recreating the activity. Restored fragments wait for an authorized shell.
- A real-libsu host integration test runs a child-process permission policy through denial, grant, new-session commands, explicit retry and a deliberately poisoned libsu global initialization guard. The policy/UID output is controlled; this is not a claim of physical SukiSU testing.
- The root layout consumes system bar insets once. Material navigation no longer adds the same bottom space a second time. The bar wraps content with a font-scale-aware minimum height. Logo and title use an explicit 16dp gap.
- Robolectric API 34 measures the actual Android layout with Vietnamese resources, a 48px bottom system bar, normal and 1.5x fonts, and night mode. It checks icon/label bounds, label ellipsis and logo spacing.
- Removed synchronous root directory setup from SO screen creation. Icon requests are deduplicated on two workers with a bounded shared cache and weak view targets. Repeated unchanged filters do not notify/rebind the list. Search is debounced, sorting occurs only when required, and list change animations/card shadows are reduced.
- No frame-time benchmark or physical-device UI test was performed. A file browser directory listing still uses the upstream synchronous implementation; it is not covered by the scrolling performance improvements above.
- Jetifier is disabled: runtime dependencies are already AndroidX. Robolectric is test-only. The four obsolete fake-session tests were replaced with real-libsu session integration tests.

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
