package com.jiqiu.configapp;

import com.topjohnwu.superuser.Shell;
import java.util.ArrayList;
import java.util.List;

/** A shell-bound libsu job discards stdout unless an output destination is set. */
final class RootProbe {
    static boolean uidIsRoot(Shell shell) {
        List<String> stdout = new ArrayList<>();
        List<String> stderr = new ArrayList<>();
        Shell.Result probe = shell.newJob().add("id -u").to(stdout, stderr).exec();
        return probe.isSuccess() && "0".equals(String.join("\n", stdout).trim());
    }
}
