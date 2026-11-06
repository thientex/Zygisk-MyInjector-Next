# Private upgrade 1.3.0

This working tree is based on jiqiu2022/Zygisk-MyInjector. The original MIT license and attribution are retained. Nothing has been pushed to GitHub.

## Changes

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
