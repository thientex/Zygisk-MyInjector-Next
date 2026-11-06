package com.jiqiu.configapp;

import android.app.Activity;
import android.app.Application;
import android.os.Process;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowProcess;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 34)
public class AppExitTest {
    public static class Host extends androidx.appcompat.app.AppCompatActivity {
        @Override protected void onCreate(android.os.Bundle saved) {
            setTheme(R.style.Theme_ZygiskMyInjector);
            super.onCreate(saved);
        }
    }
    @Test public void deniedRootDialogHasOnlyCloseAndClickKillsProcess() {
        try (ActivityController<Host> controller = Robolectric.buildActivity(Host.class).setup()) {
            ShadowProcess.setPid(24682);
            androidx.appcompat.app.AlertDialog dialog = RootDeniedDialog.show(controller.get());
            assertEquals(controller.get().getString(R.string.root_message), ((android.widget.TextView) dialog.findViewById(android.R.id.message)).getText().toString());
            assertEquals(android.view.View.VISIBLE, dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).getVisibility());
            assertNotEquals(android.view.View.VISIBLE, dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL).getVisibility());
            assertNotEquals(android.view.View.VISIBLE, dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).getVisibility());
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertTrue(ShadowProcess.wasKilled(Process.myPid()));
            assertTrue(controller.get().isFinishing());
        }
    }

    @Test public void closeFinishesTaskAndKillsOnlyOwnProcessWithoutSu() {
        try (ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup()) {
            ShadowProcess.setPid(24680);
            AppExit.close(controller.get());
            assertTrue(controller.get().isFinishing());
            assertTrue(ShadowProcess.wasKilled(Process.myPid()));
            assertFalse(ShadowProcess.wasKilled(1));
        }
    }
    @Test public void processStillEndsIfTaskRemovalThrows() {
        Activity activity = new Activity() {
            @Override public void finishAndRemoveTask() { throw new IllegalStateException("task removal failed"); }
        };
        ShadowProcess.setPid(24681);
        try { AppExit.close(activity); fail("should retain the task-removal exception"); }
        catch (IllegalStateException expected) { assertTrue(ShadowProcess.wasKilled(24681)); }
    }
}
