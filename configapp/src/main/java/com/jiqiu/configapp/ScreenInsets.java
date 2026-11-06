package com.jiqiu.configapp;

import android.view.View;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

final class ScreenInsets {
    static void install(View root, View navigation) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            // Preserve IME insets; system bars are already handled by this parent.
            return new WindowInsetsCompat.Builder(insets)
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.NONE)
                .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars(), Insets.NONE)
                .build();
        });
        float scale = root.getResources().getConfiguration().fontScale;
        navigation.setMinimumHeight((int) ((80 + Math.max(0, scale - 1) * 32)
            * root.getResources().getDisplayMetrics().density));
    }
}
