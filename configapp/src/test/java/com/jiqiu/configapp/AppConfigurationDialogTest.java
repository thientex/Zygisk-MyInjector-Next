package com.jiqiu.configapp;

import android.app.Application;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDialog;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application.class, sdk = 34, qualifiers = "vi-rVN-w400dp-h640dp-mdpi")
public class AppConfigurationDialogTest {
    public static class Host extends AppCompatActivity {
        @Override protected void onCreate(Bundle saved) { setTheme(R.style.Theme_ZygiskMyInjector); super.onCreate(saved); }
    }
    @Test public void longBodyHasVisibleSaveAndSelectionCanBeWrittenToJson() throws Exception { checkDialog(1f); }
    @Test public void largeFontKeepsSaveVisibleAndUsable() throws Exception { checkDialog(1.5f); }
    private void checkDialog(float scale) throws Exception {
        Configuration config = new Configuration(RuntimeEnvironment.getApplication().getResources().getConfiguration());
        config.fontScale = scale;
        RuntimeEnvironment.getApplication().getResources().updateConfiguration(config, RuntimeEnvironment.getApplication().getResources().getDisplayMetrics());
        try (ActivityController<Host> controller = Robolectric.buildActivity(Host.class).setup().visible()) {
            Host host = controller.get();
            View body = host.getLayoutInflater().inflate(R.layout.dialog_app_config, null);
            LinearLayout content = (LinearLayout) ((ScrollView) body).getChildAt(0);
            CheckBox chosen = new CheckBox(host); chosen.setText("libnew.so"); content.addView(chosen, 1);
            for (int i = 0; i < 30; i++) { CheckBox item = new CheckBox(host); item.setText("lib" + i + ".so"); content.addView(item); }
            File destination = new File(host.getCacheDir(), "selected-config.json");
            AtomicBoolean called = new AtomicBoolean();
            AppCompatDialog dialog = AppConfigurationDialog.show(host, body, completion -> {
                called.set(true);
                ConfigManager.ModuleConfig document = new ConfigManager.ModuleConfig();
                ConfigManager.AppConfig app = new ConfigManager.AppConfig();
                if (chosen.isChecked()) { ConfigManager.SoFile file = new ConfigManager.SoFile(); file.name = "libnew.so"; file.storedPath = "/so_files/libnew.so"; app.soFiles.add(file); }
                document.perAppConfig.put("com.example.game", app);
                try (java.io.FileWriter writer = new java.io.FileWriter(destination)) { new com.google.gson.Gson().toJson(document, writer); }
                catch (java.io.IOException e) { throw new AssertionError(e); }
                completion.finish(true);
            });
            View decor = dialog.getWindow().getDecorView();
            decor.measure(View.MeasureSpec.makeMeasureSpec(dialog.getWindow().getAttributes().width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dialog.getWindow().getAttributes().height, View.MeasureSpec.EXACTLY));
            decor.layout(0, 0, decor.getMeasuredWidth(), decor.getMeasuredHeight());
            View save = dialog.findViewById(R.id.app_config_save);
            assertTrue(save.isShown());
            int[] buttonPos = new int[2], rootPos = new int[2];
            save.getLocationOnScreen(buttonPos); decor.getLocationOnScreen(rootPos);
            assertTrue(save.getHeight() > 0);
            assertTrue(buttonPos[1] >= rootPos[1]);
            assertTrue(buttonPos[1] + save.getHeight() <= rootPos[1] + decor.getHeight());
            ((ScrollView) body).fullScroll(View.FOCUS_DOWN);
            chosen.setChecked(true);
            save.performClick();
            assertTrue(called.get()); assertFalse(dialog.isShowing());
            try (java.io.FileReader reader = new java.io.FileReader(destination)) {
                ConfigManager.ModuleConfig stored = new com.google.gson.Gson().fromJson(reader, ConfigManager.ModuleConfig.class);
                assertEquals("libnew.so", stored.perAppConfig.get("com.example.game").soFiles.get(0).name);
            }
        }
    }
    @Test public void cancelDoesNotSaveAndFailureAllowsRetry() {
        try (ActivityController<Host> controller = Robolectric.buildActivity(Host.class).setup().visible()) {
            AtomicBoolean called = new AtomicBoolean();
            View body = controller.get().getLayoutInflater().inflate(R.layout.dialog_app_config, null);
            AppCompatDialog dialog = AppConfigurationDialog.show(controller.get(), body, done -> { called.set(true); done.finish(false); });
            dialog.findViewById(R.id.app_config_cancel).performClick();
            assertFalse(called.get());
            View retryBody = controller.get().getLayoutInflater().inflate(R.layout.dialog_app_config, null);
            AppCompatDialog retry = AppConfigurationDialog.show(controller.get(), retryBody, done -> done.finish(false));
            retry.findViewById(R.id.app_config_save).performClick();
            assertTrue(retry.isShowing()); assertTrue(retry.findViewById(R.id.app_config_save).isEnabled());
            retry.dismiss();
        }
    }
}
