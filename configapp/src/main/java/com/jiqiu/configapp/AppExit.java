package com.jiqiu.configapp;

import android.app.Activity;
import android.os.Process;

/** End only our own process, including denied su requests and worker threads. */
final class AppExit {
    static void close(Activity activity) {
        try {
            activity.finishAndRemoveTask();
        } finally {
            // Killing our own UID's process requires no root or root-manager API.
            Process.killProcess(Process.myPid());
        }
    }
}
