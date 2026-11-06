package com.jiqiu.configapp;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class ConfigMaintenanceTest {
    @Test public void cleanupRetainsInstalledAppsAndDoesNotAlterLibraryOrGadget() {
        ConfigManager.ModuleConfig config = new ConfigManager.ModuleConfig();
        config.perAppConfig.put("com.present", new ConfigManager.AppConfig());
        config.perAppConfig.put("com.removed", new ConfigManager.AppConfig());
        config.globalSoFiles.add(new ConfigManager.SoFile());
        config.globalGadgetConfig = new ConfigManager.GadgetConfig();
        assertEquals(Collections.singletonList("com.removed"), ConfigMaintenance.obsolete(config, Collections.singleton("com.present")));
        assertEquals(2, config.perAppConfig.size());
        assertEquals(1, config.globalSoFiles.size());
        assertNotNull(config.globalGadgetConfig);
    }
    @Test public void resetDefaultsHaveNoAppsLibrariesOrGadget() {
        ConfigManager.ModuleConfig defaults = new ConfigManager.ModuleConfig();
        assertTrue(defaults.perAppConfig.isEmpty());
        assertTrue(defaults.globalSoFiles.isEmpty());
        assertNull(defaults.globalGadgetConfig);
        assertEquals(2, defaults.injectionDelay);
    }
    @Test public void cleanupPathsCannotEscapeTargetFilesDirectory() {
        assertTrue(ConfigMaintenance.validName("libexample.so"));
        for (String name : Arrays.asList("../other", "/data/other", ".", "..", "", "a\\b"))
            assertFalse(ConfigMaintenance.validName(name));
        assertFalse(ConfigMaintenance.validName(null));
    }
    @Test public void resetCacheDoesNotFollowLinksOutsideAppCache() throws Exception {
        java.nio.file.Path cache = java.nio.file.Files.createTempDirectory("injector-cache");
        java.nio.file.Path outside = java.nio.file.Files.createTempFile("keep-original", ".so");
        java.nio.file.Files.write(outside, "original".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        java.nio.file.Files.write(cache.resolve("imported.so"), "cached".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        java.nio.file.Files.createSymbolicLink(cache.resolve("link.so"), outside);
        try {
            assertTrue(ConfigMaintenance.clearCache(cache.toFile()));
            assertEquals(0, cache.toFile().list().length);
            assertEquals("original", new String(java.nio.file.Files.readAllBytes(outside), java.nio.charset.StandardCharsets.UTF_8));
        } finally {
            java.nio.file.Files.deleteIfExists(cache.resolve("link.so"));
            java.nio.file.Files.deleteIfExists(cache.resolve("imported.so"));
            java.nio.file.Files.deleteIfExists(cache);
            java.nio.file.Files.deleteIfExists(outside);
        }
    }
}
