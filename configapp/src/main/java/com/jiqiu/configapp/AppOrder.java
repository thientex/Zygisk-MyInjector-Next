package com.jiqiu.configapp;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;

final class AppOrder implements Comparator<AppInfo> {
    private final Collator collator;
    AppOrder(Locale locale) {
        collator = Collator.getInstance(locale);
        collator.setStrength(Collator.PRIMARY);
    }
    @Override public int compare(AppInfo a, AppInfo b) {
        int enabled = Boolean.compare(b.isEnabled(), a.isEnabled());
        if (enabled != 0) return enabled;
        int name = collator.compare(a.getAppName(), b.getAppName());
        return name != 0 ? name : a.getPackageName().compareTo(b.getPackageName());
    }
}
