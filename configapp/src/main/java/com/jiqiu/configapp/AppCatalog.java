package com.jiqiu.configapp;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import java.util.ArrayList;
import java.util.List;

/** Labels survive process restarts. Package changes invalidate the snapshot. */
final class AppCatalog {
    static volatile List<AppInfo> memory;
    static void clear() { memory = null; }
    static List<AppInfo> load(Context context) {
        PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> installed = pm.getInstalledApplications(0);
        installed.sort((a,b) -> a.packageName.compareTo(b.packageName));
        StringBuilder signature = new StringBuilder(context.getResources().getConfiguration().getLocales().toLanguageTags());
        for (ApplicationInfo info : installed) {
            signature.append('|').append(info.packageName).append(':').append(info.sourceDir)
                .append(':').append(new java.io.File(info.sourceDir).lastModified());
        }
        android.content.SharedPreferences prefs = context.getSharedPreferences("app_catalog", 0);
        String key = signature.toString();
        boolean fresh = key.equals(prefs.getString("signature", ""));
        List<AppInfo> apps = new ArrayList<>();
        android.content.SharedPreferences.Editor editor = prefs.edit().clear().putString("signature", key);
        for (ApplicationInfo info : installed) {
            String label = fresh ? prefs.getString(info.packageName, null) : null;
            if (label == null) label = pm.getApplicationLabel(info).toString();
            editor.putString(info.packageName, label);
            apps.add(new AppInfo(label, info.packageName, null, (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0));
        }
        editor.apply();
        memory = apps;
        return apps;
    }
}
