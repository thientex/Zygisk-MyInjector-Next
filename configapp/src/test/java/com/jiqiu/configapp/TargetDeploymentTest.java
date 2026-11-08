package com.jiqiu.configapp;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 34)
public class TargetDeploymentTest {
    @Test public void resolvesSecondaryUserAndAdoptedStorageWithoutGuessingUserZero() {
        ApplicationInfo info = new ApplicationInfo(); info.packageName = "com.example.game";
        info.dataDir = "/data/user/10/com.example.game";
        Shadows.shadowOf(RuntimeEnvironment.getApplication().getPackageManager()).installPackage(packageInfo(info));
        assertEquals(info.dataDir + "/files", TargetDeployment.filesDir(RuntimeEnvironment.getApplication(), info.packageName));
        info.dataDir = "/mnt/expand/volume/user/10/com.example.game";
        Shadows.shadowOf(RuntimeEnvironment.getApplication().getPackageManager()).installPackage(packageInfo(info));
        assertEquals(info.dataDir + "/files", TargetDeployment.filesDir(RuntimeEnvironment.getApplication(), info.packageName));
    }
    private android.content.pm.PackageInfo packageInfo(ApplicationInfo app) {
        android.content.pm.PackageInfo result = new android.content.pm.PackageInfo(); result.packageName = app.packageName; result.applicationInfo = app; return result;
    }
    @Test public void rejectsUnknownPackageAndUnsafeNames() {
        assertThrows(IllegalArgumentException.class, () -> TargetDeployment.filesDir(RuntimeEnvironment.getApplication(), "missing.package"));
        assertThrows(IllegalArgumentException.class, () -> TargetDeployment.script("/source", "/target/files", "../bad.so", true));
        assertThrows(IllegalArgumentException.class, () -> TargetDeployment.validateDirectory("/data/user/0/other", "com.game"));
    }
    // These execute the PRODUCTION shell script with real cp/mv/stat/chown/chmod/cmp.
    // Only Android SELinux utilities are controlled fixtures; no real root/SELinux is claimed.
    @Test public void replacementPreservesOpenInodeAndUsesReadOnlyMode() throws Exception {
        try (Fixture f = new Fixture()) {
            assertEquals(0, f.publish("old"));
            Path dest = f.files.resolve("lib' test.so");
            Object oldInode = Files.getAttribute(dest, "unix:ino");
            try (java.io.InputStream opened = Files.newInputStream(dest)) {
                assertEquals(0, f.publish("new"));
                assertEquals("old", new String(opened.readAllBytes()));
            }
            assertNotEquals(oldInode, Files.getAttribute(dest, "unix:ino"));
            assertEquals("new", read(dest));
            assertEquals(0500, ((Number) Files.getAttribute(dest, "unix:mode")).intValue() & 0777);
            assertEquals(Files.getAttribute(f.data, "unix:uid"), Files.getAttribute(dest, "unix:uid"));
            assertTrue(read(f.calls).contains("u:object_r:app_data_file:s0:c123,c456"));
            assertTrue(Files.list(f.files).noneMatch(p -> p.getFileName().toString().startsWith(".myinjector.")));
        }
    }
    @Test public void selinuxFailureDoesNotReplaceWorkingLibraryAndCleansStage() throws Exception {
        try (Fixture f = new Fixture()) {
            assertEquals(0, f.publish("old"));
            write(f.fail, "1");
            assertNotEquals(0, f.publish("new"));
            assertEquals("old", read(f.files.resolve("lib' test.so")));
            assertTrue(Files.list(f.files).noneMatch(p -> p.getFileName().toString().startsWith(".myinjector.")));
        }
    }
    @Test public void missingAppDataIsNotInventedAndFilesSymlinksAreRejected() throws Exception {
        try (Fixture f = new Fixture()) {
            Files.delete(f.data);
            assertNotEquals(0, f.publish("new")); assertFalse(Files.exists(f.data));
            Files.createDirectory(f.data);
            Path outside = Files.createDirectory(f.root.resolve("outside"));
            Files.createSymbolicLink(f.files, outside);
            assertNotEquals(0, f.publish("new")); assertEquals(0, Files.list(outside).count());
        }
    }
    @Test public void failedCopyKeepsOldFileAndCleansStaging() throws Exception {
        try (Fixture f = new Fixture()) {
            assertEquals(0, f.publish("old"));
            write(f.bin.resolve("cp"), "#!/bin/sh\nexit 1\n"); f.bin.resolve("cp").toFile().setExecutable(true);
            assertNotEquals(0, f.publish("new"));
            assertEquals("old", read(f.files.resolve("lib' test.so")));
            assertEquals(1, Files.list(f.files).count());
        }
    }
    @Test public void contextCanBeFoundOutsideFirstLsColumn() throws Exception {
        try (Fixture f = new Fixture()) {
            f.executable("stat", "#!/bin/sh\n[ \"$2\" != '%C' ] || exit 1\nexec /usr/bin/stat \"$@\"\n");
            f.executable("ls", "#!/bin/sh\necho 'drwx------ 2 u0_a123 u0_a123 u:object_r:app_data_file:s0:c123,c456 4096 files'\n");
            assertEquals(0, f.publish("new"));
        }
    }
    @Test public void existingDirectoryDoesNotRequireRelabelPermission() throws Exception {
        try (Fixture f = new Fixture()) {
            Files.createDirectory(f.files);
            String original = read(f.bin.resolve("chcon"));
            f.executable("chcon", original.replace("#!/bin/sh\n", "#!/bin/sh\n[ \"$2\" != " + TargetDeployment.quote(f.files.toString()) + " ] || exit 1\n"));
            assertEquals(0, f.publish("new"));
        }
    }
    @Test public void inaccessibleAppDirectoryCanUseAnExplicitRootMountView() throws Exception {
        try (Fixture f = new Fixture()) {
            Files.delete(f.data);
            Path rootView = Files.createDirectories(f.root.resolve("global-view"));
            Path realData = Files.createDirectories(rootView.resolve(f.data.toString().substring(1)));
            f.rootView = rootView.toString();
            assertEquals(0, f.publish("new"));
            assertEquals("new", read(realData.resolve("files/lib' test.so")));
            assertFalse(Files.exists(f.data));
        }
    }
    @Test public void alreadyCorrectFileLabelDoesNotRequireRelabelPermission() throws Exception {
        try (Fixture f = new Fixture()) {
            Files.createDirectory(f.files);
            write(f.root.resolve("already-labeled"), "1");
            f.executable("chcon", "#!/bin/sh\nexit 1\n");
            assertEquals(0, f.publish("new"));
        }
    }
    private static String read(Path p) throws Exception { return new String(Files.readAllBytes(p), java.nio.charset.StandardCharsets.UTF_8); }
    private static void write(Path p, String text) throws Exception { Files.write(p, text.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    static final class Fixture implements AutoCloseable {
        Path root = Files.createTempDirectory("deployment ' test"), data = Files.createDirectory(root.resolve("com.game")),
            files = data.resolve("files"), bin = Files.createDirectory(root.resolve("bin")), source = root.resolve("source.so"),
            fail = root.resolve("fail-label"), calls = root.resolve("labels");
        String rootView = "/proc/1/root";
        Fixture() throws Exception {
            executable("ls", "#!/bin/sh\necho 'u:object_r:app_data_file:s0:c123,c456 directory'\n");
            executable("chcon", "#!/bin/sh\n[ ! -f " + TargetDeployment.quote(fail.toString()) + " ] || case \"$2\" in *.myinjector.*) exit 1 ;; esac\n"
                + "echo \"$*\" >> " + TargetDeployment.quote(calls.toString()) + "\nexit 0\n");
            executable("stat", "#!/bin/sh\nif [ \"$2\" = '%C' ]; then\nlabel='u:object_r:app_data_file:s0:c123,c456'\n"
                + "case \"$3\" in */.myinjector.*)\nif [ ! -f " + TargetDeployment.quote(root.resolve("already-labeled").toString())
                + " ] && ! grep -Fqx \"$label $3\" " + TargetDeployment.quote(calls.toString())
                + " 2>/dev/null; then label='u:object_r:magisk_file:s0'; fi ;; esac\necho \"$label\"; exit 0\nfi\nexec /usr/bin/stat \"$@\"\n");
        }
        void executable(String name, String script) throws Exception { Path p = bin.resolve(name); write(p, script); p.toFile().setExecutable(true); }
        int publish(String text) throws Exception {
            write(source, text);
            ProcessBuilder builder = new ProcessBuilder("/bin/sh", "-c", TargetDeployment.script(source.toString(), files.toString(), "lib' test.so", true, rootView));
            builder.environment().put("PATH", bin + ":" + System.getenv("PATH"));
            builder.redirectErrorStream(true); Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes()); int status = process.waitFor();
            if (status != 0) System.out.println(output);
            return status;
        }
        public void close() throws Exception {
            try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
                for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
            }
        }
    }
}
