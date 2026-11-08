package com.jiqiu.configapp;

import android.content.Context;
import android.content.pm.ApplicationInfo;

/** Resolve the current user's actual CE directory, including adopted storage. */
final class TargetDeployment {
    static String filesDir(Context context, String packageName) {
        if (packageName == null || !packageName.matches("[a-zA-Z0-9_]+(?:\\.[a-zA-Z0-9_]+)+"))
            throw new IllegalArgumentException("Invalid package name");
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(packageName, 0);
            return validateDirectory(info.dataDir, packageName) + "/files";
        } catch (android.content.pm.PackageManager.NameNotFoundException error) {
            throw new IllegalArgumentException("Package is not installed for this Android user: " + packageName, error);
        }
    }

    static String validateDirectory(String directory, String packageName) {
        if (directory == null || !directory.startsWith("/") || directory.contains("/../")
                || !new java.io.File(directory).getName().equals(packageName))
            throw new IllegalArgumentException("Invalid application data directory");
        return directory;
    }

    static String quote(String value) { return "'" + value.replace("'", "'\\''") + "'"; }

    /** A single job: verify parent, preserve MLS categories, copy into a NEW inode, then rename. */
    static String script(String source, String filesDir, String name, boolean library, String rootView) {
        if (!ConfigMaintenance.validName(name)) throw new IllegalArgumentException("Invalid filename");
        if (!filesDir.endsWith("/files")) throw new IllegalArgumentException("Invalid files directory");
        String data = filesDir.substring(0, filesDir.length() - 6);
        return "if [ -x /system/bin/sh ]; then injector_sh=/system/bin/sh; else injector_sh=sh; fi; \"$injector_sh\" -c " + quote("""
            set -eu
            # Do not inherit BusyBox applets/aliases from a root manager ahead of Android tools.
            if [ -d /system/bin ]; then PATH=/system/bin:/system/xbin:$PATH; export PATH; fi
            fail() { echo "MyInjector: $*" >&2; exit 1; }
            data=$1; files=$2; source=$3; name=$4; mode=$5; root_view=$6
            # A su child can retain app-data isolation mounts. Use init's view only for these exact paths.
            if [ ! -d "$data" ] && [ -d "$root_view$data" ]; then
                data="$root_view$data"; files="$data/files"
            fi
            if [ ! -r "$source" ] && [ -r "$root_view$source" ]; then source="$root_view$source"; fi
            [ -d "$data" ] || fail "App data unavailable (uninstalled, locked user, or storage not mounted): $data"
            [ ! -L "$files" ] || fail "Refusing symlink files directory: $files"
            [ -f "$source" ] && [ -r "$source" ] || fail "Source is missing or unreadable: $source"
            owner=$(stat -Lc '%u:%g' "$data") || fail "Cannot read app ownership: $data"
            case "$owner" in *[!0-9:]*|:*|*:) fail "Invalid app ownership: $owner" ;; esac
            get_context() {
                context=$(stat -Lc '%C' "$1" 2>/dev/null) || context=
                case "$context" in u:object_r:*:s0*) printf '%s' "$context"; return 0 ;; esac
                # Tool implementations differ: the context is not necessarily the first ls column.
                listing=$(ls -Zd "$1" 2>/dev/null) || return 1
                for field in $listing; do
                    case "$field" in u:object_r:*:s0*) printf '%s' "$field"; return 0 ;; esac
                done
                return 1
            }
            if [ ! -d "$files" ]; then
                label=$(get_context "$data") || fail "Cannot read SELinux label: $data"
                mkdir "$files" && chmod 700 "$files" && chown "$owner" "$files" && chcon "$label" "$files" || fail "Cannot prepare files directory: $files"
            fi
            # Existing app directories already have the correct label; do not require an unnecessary relabel.
            label=$(get_context "$files") || fail "Cannot read SELinux label: $files"
            files_owner=$(stat -Lc '%u:%g' "$files") || fail "Cannot read files directory ownership: $files"
            if [ "$files_owner" != "$owner" ]; then
                chown "$owner" "$files" || fail "Cannot fix files directory ownership: $files"
            fi
            stage=$(mktemp "$files/.myinjector.XXXXXX") || fail "Cannot stage file: $files"
            trap 'rm -f "$stage"' EXIT
            trap 'exit 1' HUP INT TERM
            cp "$source" "$stage" && cmp -s "$source" "$stage" || fail "Copy verification failed: $source"
            chown "$owner" "$stage" && chmod "$mode" "$stage" || fail "Cannot set file ownership/mode: $name"
            current_label=$(get_context "$stage") || current_label=
            if [ "$current_label" != "$label" ]; then
                chcon "$label" "$stage" || fail "Cannot set file SELinux label: $name"
            fi
            current_label=$(get_context "$stage") || fail "Cannot verify file SELinux label: $name"
            [ "$current_label" = "$label" ] || fail "File SELinux label mismatch: $name"
            mv -f "$stage" "$files/$name" || fail "Cannot publish file: $files/$name"
            [ -f "$files/$name" ] || fail "Published file is missing: $files/$name"
            """) + " myinjector-deploy " + quote(data) + " " + quote(filesDir) + " " + quote(source)
            + " " + quote(name) + " " + quote(library ? "500" : "400") + " " + quote(rootView);
    }

    static String script(String source, String filesDir, String name, boolean library) {
        return script(source, filesDir, name, library, "/proc/1/root");
    }
}
