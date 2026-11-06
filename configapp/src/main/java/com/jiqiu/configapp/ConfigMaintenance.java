package com.jiqiu.configapp;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class ConfigMaintenance {
    static List<String> obsolete(ConfigManager.ModuleConfig config, Set<String> installed) {
        List<String> missing = new ArrayList<>();
        for (String pkg : config.perAppConfig.keySet()) if (!installed.contains(pkg)) missing.add(pkg);
        return missing;
    }
    static boolean validName(String name) {
        return name != null && !name.isEmpty() && !name.contains("/") && !name.contains("\\")
            && !name.equals(".") && !name.equals("..");
    }
    static boolean clearCache(java.io.File directory) {
        java.io.File[] children = directory.listFiles();
        if (children == null) return !directory.exists();
        boolean success = true;
        for (java.io.File child : children) {
            try {
                java.io.File normalized = new java.io.File(directory.getCanonicalFile(), child.getName());
                boolean link = !normalized.getCanonicalPath().equals(normalized.getAbsolutePath());
                if (!link && child.isDirectory() && !clearCache(child)) success = false;
                if (!child.delete()) success = false;
            } catch (java.io.IOException error) { success = false; }
        }
        return success;
    }
}
