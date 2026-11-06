package com.jiqiu.configapp;

import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

final class RootDeniedDialog {
    static AlertDialog show(Activity activity, String diagnostic) {
        String message = activity.getString(R.string.root_message);
        if (diagnostic != null && !diagnostic.isEmpty()) message += "\n\n" + diagnostic;
        return new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.root_required)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton(R.string.close, (dialog, which) -> AppExit.close(activity))
            .show();
    }
}
