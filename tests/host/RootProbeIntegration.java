package com.jiqiu.configapp;

import com.topjohnwu.superuser.Shell;
import java.util.concurrent.ExecutorService;

/** Real libsu 6.0.0 + a real host sh. UID command output is controlled, not host privileges. */
public final class RootProbeIntegration {
    private static int checks;
    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        checks++;
        System.out.println("PASS: " + name);
    }
    public static void main(String[] args) throws Exception {
        Shell.enableVerboseLogging = false;
        Shell.enableLegacyStderrRedirection = true;
        try (Shell shell = Shell.Builder.create().setTimeout(5).build(new ProcessBuilder("sh").start())) {
            Shell.Result old = shell.newJob().add("printf '0\\n'").exec();
            check(old.isSuccess() && old.getOut().isEmpty(), "reproduce 1.4.0: successful shell-bound job has no stdout by default");
            shell.newJob().add("id() { printf '0\\n'; }").exec();
            check(RootProbe.uidIsRoot(shell), "production probe collects UID 0 through real libsu");
            shell.newJob().add("id() { printf '1000\\n'; }").exec();
            check(!RootProbe.uidIsRoot(shell), "non-root UID is rejected");
            shell.newJob().add("id() { printf '0\\n'; return 1; }").exec();
            check(!RootProbe.uidIsRoot(shell), "failed command with misleading root output is rejected");
            shell.newJob().add("id() { printf '0\\n'; printf 'su warning\\n' >&2; }").exec();
            check(RootProbe.uidIsRoot(shell), "stderr is captured separately and does not corrupt stdout");
            shell.newJob().add("id() { :; }").exec();
            check(!RootProbe.uidIsRoot(shell), "missing UID output is rejected");
            shell.newJob().add("id() { printf '0\\n1000\\n'; }").exec();
            check(!RootProbe.uidIsRoot(shell), "ambiguous stdout is rejected");
        } finally {
            ((ExecutorService) Shell.EXECUTOR).shutdownNow();
        }
        System.out.println(checks + " real-libsu checks passed");
    }
}
