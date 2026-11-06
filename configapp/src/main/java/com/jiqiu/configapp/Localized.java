package com.jiqiu.configapp;

import android.content.Context;
import android.content.res.Configuration;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

/** Resource access for dialogs, including adapter/static helper code. */
final class Localized {
    static Context application;
    static String text(int id) {
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        Context context = application;
        if (!locales.isEmpty()) {
            Configuration config = new Configuration(context.getResources().getConfiguration());
            config.setLocale(locales.get(0));
            context = context.createConfigurationContext(config);
        }
        return context.getString(id);
    }
}
