package com.jiqiu.configapp;

import android.os.Handler;
import android.os.Looper;
import com.topjohnwu.superuser.Shell;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class RootAccess {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    interface Callback { void onResult(boolean granted); }
    static void request(Callback callback) {
        WORKER.execute(() -> {
            boolean granted = RootSession.check(new RootSession.Backend() {
                public RootSession.Session cached() { return wrap(Shell.getCachedShell()); }
                public RootSession.Session open() { return wrap(Shell.getShell()); }
            });
            if (granted) {
                // Re-authorization can follow an earlier non-root configuration read.
                // Serialize refresh with applies already queued in this process.
                ConfigManager.IO.execute(() -> {
                    new ConfigManager(Localized.application).reloadConfig();
                    new Handler(Looper.getMainLooper()).post(() -> callback.onResult(true));
                });
            } else {
                new Handler(Looper.getMainLooper()).post(() -> callback.onResult(false));
            }
        });
    }
    private static RootSession.Session wrap(Shell shell) {
        if (shell == null) return null;
        return new RootSession.Session() {
            public boolean alive() { return shell.isAlive(); }
            public boolean root() { return shell.isRoot(); }
            public boolean uidIsRoot() {
                Shell.Result probe = shell.newJob().add("id -u").exec();
                return probe.isSuccess() && probe.getOut().size() == 1 && "0".equals(probe.getOut().get(0).trim());
            }
            public void close() { try { shell.close(); } catch (IOException ignored) {} }
        };
    }
}
