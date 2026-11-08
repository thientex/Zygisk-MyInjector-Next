package com.jiqiu.configapp;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import java.nio.file.*;
import java.util.*;
import com.topjohnwu.superuser.Shell;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Real ConfigManager -> real deployment shell -> real files and JSON.
 * Only Android SELinux tools, root transport and am force-stop are host fixtures.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 34)
public class ConfigManagerApplyIntegrationTest {
    @Test public void selectThenEnablePublishesSelectedSoAndEnabledJson() throws Exception { apply(false); }
    @Test public void enableThenSelectPublishesSelectedSoAndEnabledJson() throws Exception { apply(true); }
    private void apply(boolean enableFirst) throws Exception {
        try (Harness h = new Harness()) {
            if (enableFirst) assertEquals(ConfigManager.ApplyResult.RESTARTED, h.manager.setAppEnabled(h.pkg, true));
            assertEquals(enableFirst ? ConfigManager.ApplyResult.RESTARTED : ConfigManager.ApplyResult.SAVED,
                h.manager.applyAppConfiguration(h.pkg, "standard", Collections.singletonList(h.so), false, null));
            if (!enableFirst) assertEquals(ConfigManager.ApplyResult.RESTARTED, h.manager.setAppEnabled(h.pkg, true));
            assertTrue(h.stored().perAppConfig.get(h.pkg).enabled);
            assertEquals(h.so.name, h.stored().perAppConfig.get(h.pkg).soFiles.get(0).name);
            assertEquals("library bytes", new String(Files.readAllBytes(h.fixture.files.resolve(h.so.name))));
            assertTrue(h.stops > 0);
            assertEquals("", h.manager.getLastApplyError());
            assertEquals(ConfigManager.ApplyResult.RESTARTED, h.manager.setAppEnabled(h.pkg, false));
            assertFalse(h.stored().perAppConfig.get(h.pkg).enabled);
            assertFalse(Files.exists(h.fixture.files.resolve(h.so.name)));
        }
    }
    @Test public void failedDeploymentReportsExactStepRevertsEnableAndCanBeRetried() throws Exception {
        try (Harness h = new Harness()) {
            assertEquals(ConfigManager.ApplyResult.SAVED,
                h.manager.applyAppConfiguration(h.pkg, "standard", Collections.singletonList(h.so), false, null));
            Files.delete(h.fixture.source);
            assertEquals(ConfigManager.ApplyResult.FAILED, h.manager.setAppEnabled(h.pkg, true));
            assertFalse(h.manager.isAppEnabled(h.pkg));
            assertFalse(h.stored().perAppConfig.get(h.pkg).enabled);
            assertEquals(0, h.stops);
            assertTrue(h.manager.getLastApplyError(), h.manager.getLastApplyError().contains("Source is missing or unreadable"));
            Files.write(h.fixture.source, "repaired".getBytes());
            assertEquals(ConfigManager.ApplyResult.RESTARTED, h.manager.setAppEnabled(h.pkg, true));
            assertTrue(h.stored().perAppConfig.get(h.pkg).enabled);
            assertEquals("", h.manager.getLastApplyError());
        }
    }
    private static final class Harness implements AutoCloseable {
        final TargetDeploymentTest.Fixture fixture = new TargetDeploymentTest.Fixture();
        final Path state = Files.createTempDirectory("myinjector-apply-json"), json = state.resolve("config.json");
        final String pkg = "com.game";
        final ConfigManager.SoFile so = new ConfigManager.SoFile();
        final ConfigManager manager;
        int stops;
        Harness() throws Exception {
            Files.write(fixture.source, "library bytes".getBytes());
            PackageInfo info = new PackageInfo(); info.packageName = pkg;
            info.applicationInfo = new ApplicationInfo(); info.applicationInfo.packageName = pkg;
            info.applicationInfo.dataDir = fixture.data.toString();
            Shadows.shadowOf(RuntimeEnvironment.getApplication().getPackageManager()).installPackage(info);
            so.name = "lib' test.so"; so.storedPath = fixture.source.toString();
            ConfigManager.ModuleConfig initial = new ConfigManager.ModuleConfig(); initial.globalSoFiles.add(so);
            manager = new ConfigManager(RuntimeEnvironment.getApplication(), this::run, initial);
        }
        ConfigManager.ModuleConfig stored() throws Exception {
            return new com.google.gson.Gson().fromJson(new String(Files.readAllBytes(json)), ConfigManager.ModuleConfig.class);
        }
        Shell.Result run(String... commands) {
            try {
                String command = String.join("\n", commands).replace(ConfigManager.CONFIG_FILE, json.toString());
                if (command.startsWith("am force-stop")) {
                    // Config must be on disk BEFORE the target is closed.
                    assertTrue(stored().perAppConfig.containsKey(pkg));
                    stops++; return result(0, "");
                }
                ProcessBuilder b = new ProcessBuilder("/bin/sh", "-c", command);
                b.environment().put("PATH", fixture.bin + ":" + System.getenv("PATH"));
                Process child = b.redirectErrorStream(true).start();
                String output = new String(child.getInputStream().readAllBytes());
                return result(child.waitFor(), output);
            } catch (Exception error) { throw new AssertionError(error); }
        }
        private Shell.Result result(int status, String text) {
            List<String> lines = text.isEmpty() ? Collections.emptyList() : Arrays.asList(text.split("\\R"));
            return new Shell.Result() {
                public int getCode() { return status; }
                public List<String> getOut() { return status == 0 ? lines : Collections.emptyList(); }
                public List<String> getErr() { return status == 0 ? Collections.emptyList() : lines; }
            };
        }
        public void close() throws Exception {
            fixture.close();
            try (java.util.stream.Stream<Path> paths = Files.walk(state)) {
                for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
            }
        }
    }
}
