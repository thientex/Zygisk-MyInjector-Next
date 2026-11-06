package com.jiqiu.configapp;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class AppOrderTest {
    private AppInfo app(String name, String pkg, boolean enabled) {
        AppInfo app = new AppInfo(name, pkg, null, false);
        app.setEnabled(enabled);
        return app;
    }
    @Test public void enabledAppsComeFirstAndBothGroupsAreAlphabetical() {
        AppInfo z = app("Zulu", "z", true), a = app("Alpha", "a", false);
        AppInfo b = app("Bravo", "b", true), c = app("Charlie", "c", false);
        List<AppInfo> apps = new ArrayList<>(Arrays.asList(a, z, c, b));
        apps.sort(new AppOrder(Locale.ENGLISH));
        assertEquals(Arrays.asList(b, z, a, c), apps);
        z.setEnabled(false);
        apps.sort(new AppOrder(Locale.ENGLISH));
        assertEquals(Arrays.asList(b, a, c, z), apps);
    }
    @Test public void equalLabelsHaveDeterministicPackageOrder() {
        AppInfo a = app("Alpha", "a", true), b = app("alpha", "b", true);
        assertTrue(new AppOrder(Locale.ENGLISH).compare(a, b) < 0);
    }
}
