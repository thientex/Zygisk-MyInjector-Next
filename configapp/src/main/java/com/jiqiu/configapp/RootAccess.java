package com.jiqiu.configapp;

import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class RootAccess {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    interface Callback { void onResult(boolean granted); }
    static void request(boolean fresh, Callback callback) {
        WORKER.execute(() -> {
            boolean granted = RootShell.authorize(fresh);
            if (granted) {
                ConfigManager.IO.execute(() -> {
                    ConfigManager manager = new ConfigManager(Localized.application);
                    manager.reloadConfig();
                    manager.ensureModuleDirectories();
                    new Handler(Looper.getMainLooper()).post(() -> callback.onResult(true));
                });
            } else {
                new Handler(Looper.getMainLooper()).post(() -> callback.onResult(false));
            }
        });
    }
}
