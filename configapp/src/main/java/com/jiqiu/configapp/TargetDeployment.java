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
    static String script(String source, String filesDir, String name, boolean library) {
        if (!ConfigMaintenance.validName(name)) throw new IllegalArgumentException("Invalid filename");
        if (!filesDir.endsWith("/files")) throw new IllegalArgumentException("Invalid files directory");
        String data = filesDir.substring(0, filesDir.length() - 6);
        return "sh -c " + quote("""
            set -eu
            fail() { echo "MyInjector: $*" >&2; exit 1; }
            data=$1; files=$2; source=$3; name=$4; mode=$5
            [ -d "$data" ] || fail "App data unavailable (uninstalled, locked user, or storage not mounted): $data"
            [ ! -L "$files" ] || fail "Refusing symlink files directory: $files"
            [ -f "$source" ] && [ -r "$source" ] || fail "Source is missing or unreadable: $source"
            owner=$(stat -Lc '%u:%g' "$data") || fail "Cannot read app ownership: $data"
            case "$owner" in *[!0-9:]*|:*|*:) fail "Invalid app ownership: $owner" ;; esac
            set -- $(ls -Zd "$data")
            label=$1
            case "$label" in u:object_r:*:s0*) ;; *) fail "Cannot determine app SELinux label: $data" ;; esac
            if [ ! -d "$files" ]; then
                mkdir "$files" || fail "Cannot create files directory: $files"
                chmod 700 "$files" || fail "Cannot set directory mode: $files"
            fi
            chown "$owner" "$files" && chcon "$label" "$files" || fail "Cannot set app directory ownership/SELinux label: $files"
            stage=$(mktemp "$files/.myinjector.XXXXXX") || fail "Cannot stage file: $files"
            trap 'rm -f "$stage"' EXIT
            trap 'exit 1' HUP INT TERM
            cp "$source" "$stage" && cmp -s "$source" "$stage" || fail "Copy verification failed: $source"
            chown "$owner" "$stage" && chmod "$mode" "$stage" && chcon "$label" "$stage" || fail "Cannot set file ownership/mode/SELinux label: $name"
            mv -f "$stage" "$files/$name" || fail "Cannot publish file: $files/$name"
            [ -f "$files/$name" ] || fail "Published file is missing: $files/$name"
            """) + " myinjector-deploy " + quote(data) + " " + quote(filesDir) + " " + quote(source)
            + " " + quote(name) + " " + quote(library ? "500" : "400");
    }
}
