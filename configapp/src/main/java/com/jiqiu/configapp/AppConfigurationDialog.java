package com.jiqiu.configapp;

import android.app.Activity;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatDialog;
import androidx.core.widget.TextViewCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;

/** A bounded scrolling body with a separate, permanently visible action row. */
final class AppConfigurationDialog {
    interface Completion { void finish(boolean saved); }
    interface SaveAction { void save(Completion completion); }
    static AppCompatDialog show(Activity activity, View body, SaveAction action) {
        float density = activity.getResources().getDisplayMetrics().density;
        int padding = (int) (20 * density);
        AppCompatDialog dialog = new AppCompatDialog(activity);
        dialog.supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = new LinearLayout(dialog.getContext());
        shell.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(dialog.getContext());
        title.setText(R.string.text_app_configuration);
        TextViewCompat.setTextAppearance(title, R.style.InjectorText_TitleLarge);
        title.setPadding(padding, padding, padding, padding / 2);
        shell.addView(title, new LinearLayout.LayoutParams(-1, -2));
        shell.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout footer = new LinearLayout(dialog.getContext());
        footer.setGravity(android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL);
        footer.setPadding(padding, padding / 2, padding, padding / 2);
        MaterialButton cancel = new MaterialButton(dialog.getContext(), null,
            com.google.android.material.R.attr.borderlessButtonStyle);
        cancel.setId(R.id.app_config_cancel);
        cancel.setText(R.string.text_cancel);
        MaterialButton save = new MaterialButton(dialog.getContext());
        save.setId(R.id.app_config_save);
        save.setText(R.string.text_save);
        cancel.setMaxLines(2); save.setMaxLines(2);
        footer.addView(save, new LinearLayout.LayoutParams(0, -2, 1));
        footer.addView(cancel, new LinearLayout.LayoutParams(0, -2, 1));
        shell.addView(footer, new LinearLayout.LayoutParams(-1, -2));
        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            save.setEnabled(false); cancel.setEnabled(false); dialog.setCancelable(false);
            action.save(saved -> {
                if (saved) dialog.dismiss();
                else { save.setEnabled(true); cancel.setEnabled(true); dialog.setCancelable(true); }
            });
        });
        dialog.setContentView(shell);
        dialog.show();
        Rect available = new Rect();
        activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(available);
        if (available.height() <= 0) available.set(0, 0,
            activity.getResources().getDisplayMetrics().widthPixels,
            activity.getResources().getDisplayMetrics().heightPixels);
        Window window = dialog.getWindow();
        GradientDrawable background = new GradientDrawable();
        background.setColor(MaterialColors.getColor(shell, com.google.android.material.R.attr.colorSurface));
        background.setCornerRadius(24 * density);
        window.setBackgroundDrawable(background);
        window.setLayout(Math.min((int) (640 * density), available.width() - padding * 2),
            (int) (available.height() * 0.88f));
        return dialog;
    }
}
