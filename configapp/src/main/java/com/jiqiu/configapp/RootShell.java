package com.jiqiu.configapp;

import com.topjohnwu.superuser.NoShellException;
import com.topjohnwu.superuser.Shell;
import java.io.IOException;
import java.util.ArrayList;

/** Own the exact su session used for authorization AND subsequent commands.
 * Standalone builders bypass libsu's process-global main-shell initialization state.
 */
final class RootShell {
    interface Factory { Shell open(); }
    static final class Store {
        private final Factory factory;
        private volatile Shell shell;
        private volatile String failure = "";
        Store(Factory factory) { this.factory = factory; }
        synchronized boolean authorize(boolean fresh) {
            if (fresh) close();
            try {
                if (valid(shell)) { failure = ""; return true; }
                close();
                shell = factory.open();
                if (valid(shell)) { failure = ""; return true; }
                failure = "su did not return UID 0";
            } catch (RuntimeException error) {
                failure = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            }
            close();
            return false;
        }
        Shell get() {
            Shell current = shell;
            if (current == null || !current.isAlive() || !current.isRoot())
                throw new NoShellException("No authorized su session; retry root authorization");
            return current;
        }
        boolean ready() { Shell current = shell; return current != null && current.isAlive() && current.isRoot(); }
        String failure() { return failure; }
        private boolean valid(Shell candidate) {
            return candidate != null && candidate.isAlive() && candidate.isRoot() && RootProbe.uidIsRoot(candidate);
        }
        synchronized void close() {
            Shell old = shell;
            shell = null;
            if (old != null) try { old.close(); } catch (IOException ignored) {}
        }
    }
    private static final Store SESSION = new Store(() -> Shell.Builder.create()
        .setTimeout(15).setCommands("su").build());
    static boolean authorize(boolean fresh) { return SESSION.authorize(fresh); }
    static boolean ready() { return SESSION.ready(); }
    static String failure() { return SESSION.failure(); }
    static Shell get() { return SESSION.get(); }
    static Shell.Job cmd(String... commands) {
        return get().newJob().add(commands).to(new ArrayList<>(), new ArrayList<>());
    }
}
