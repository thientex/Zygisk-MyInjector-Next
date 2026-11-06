package com.jiqiu.configapp;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.NoShellException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Real child processes: deny, grant, retry and poisoned libsu global state. */
public final class RootShellIntegration {
    private static int checks;
    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        checks++; System.out.println("PASS: " + name);
    }
    public static void main(String[] args) throws Exception {
        Path policy = Files.createTempFile("su-policy-", ".txt");
        AtomicInteger opens = new AtomicInteger();
        AtomicReference<Shell> opened = new AtomicReference<>();
        RootShell.Store store = new RootShell.Store(() -> {
            opens.incrementAndGet();
            try {
                Process process = new ProcessBuilder("sh", "-c",
                    "[ \"$(cat \"$1\")\" = allow ] || exit 1; "
                    + "id() { if [ \"$1\" = -u ]; then echo 0; else echo 'uid=0(root) gid=0(root)'; fi; }; . /dev/stdin",
                    "su-test", policy.toString()).start();
                Shell shell = Shell.Builder.create().setTimeout(3).build(process);
                opened.set(shell); return shell;
            } catch (java.io.IOException e) { throw new NoShellException(e.getMessage()); }
        });
        try {
            Files.writeString(policy, "deny");
            check(!store.authorize(true) && !store.ready(), "denied child process does not authorize the app");
            Files.writeString(policy, "allow");
            check(store.authorize(true) && opens.get() == 2, "grant after denial opens a new su process in the SAME JVM");
            check(store.get() == opened.get(), "commands use the exact session that passed authorization");
            List<String> output = new ArrayList<>();
            check(store.get().newJob().add("printf 'session-ok\\n'").to(output).exec().isSuccess()
                && output.contains("session-ok"), "authorized session executes subsequent application commands");
            Shell previous = store.get();
            check(store.authorize(true) && !previous.isAlive() && opens.get() == 3,
                "explicit retry discards even a live old session");
            store.close();
            // Reproduce libsu's sticky initialization guard without using reflection in production.
            Class<?> mainShell = Class.forName("com.topjohnwu.superuser.internal.MainShell");
            Field guard = mainShell.getDeclaredField("isInitMain"); guard.setAccessible(true); guard.setBoolean(null, true);
            boolean failed = false;
            try { Shell.getShell(); } catch (NoShellException expected) { failed = true; }
            check(failed && store.authorize(true), "standalone su recovery works even when libsu global initialization is poisoned");
            guard.setBoolean(null, false);
        } finally {
            store.close(); Files.deleteIfExists(policy);
            ((ExecutorService) Shell.EXECUTOR).shutdownNow();
        }
        System.out.println(checks + " root session integration checks passed");
    }
}
