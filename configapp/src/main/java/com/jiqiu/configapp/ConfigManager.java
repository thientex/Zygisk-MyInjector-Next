package com.jiqiu.configapp;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.topjohnwu.superuser.Shell;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {
    static final java.util.concurrent.ExecutorService IO = java.util.concurrent.Executors.newSingleThreadExecutor();
    private static final String TAG = "ConfigManager";
    public static final String MODULE_PATH = "/data/adb/modules/zygisk-myinjector";
    public static final String CONFIG_FILE = MODULE_PATH + "/config.json";
    public static final String SO_STORAGE_DIR = MODULE_PATH + "/so_files";
    
    private final Context context;
    private final Gson gson;
    private static volatile ModuleConfig config;
    
    static { Shell.enableVerboseLogging = false; }

    public static void initializeShell() { /* initialize the I/O queue without acquiring su */ }

    public ConfigManager(Context context) {
        this.context = context.getApplicationContext();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        
        // Ensure we get root shell on creation
        RootShell.get();
        
        if (config == null) loadConfig();
    }
    
    public boolean isRootAvailable() {
        return RootShell.get().isRoot();
    }
    
    public void ensureModuleDirectories() {
        // Check root access first
        if (!isRootAvailable()) {
            Log.e(TAG, "Root access not available!");
            return;
        }
        
        // Create module directories
        Shell.Result result1 = RootShell.cmd("mkdir -p " + MODULE_PATH).exec();
        if (!result1.isSuccess()) {
            Log.e(TAG, "Failed to create module directory: " + MODULE_PATH);
        }
        
        Shell.Result result2 = RootShell.cmd("mkdir -p " + SO_STORAGE_DIR).exec();
        if (!result2.isSuccess()) {
            Log.e(TAG, "Failed to create SO storage directory: " + SO_STORAGE_DIR);
        }
        
        // Set permissions
        RootShell.cmd("chmod 755 " + MODULE_PATH).exec();
        RootShell.cmd("chmod 755 " + SO_STORAGE_DIR).exec();
        
        // Verify directories exist
        Shell.Result verify = RootShell.cmd("ls -la " + MODULE_PATH).exec();
        if (verify.isSuccess()) {
            Log.i(TAG, "Module directory ready: " + String.join("\n", verify.getOut()));
        }
    }
    
    private void loadConfig() {
        Shell.Result result = RootShell.cmd("cat " + CONFIG_FILE).exec();
        if (result.isSuccess() && !result.getOut().isEmpty()) {
            String json = String.join("\n", result.getOut());
            try {
                config = gson.fromJson(json, ModuleConfig.class);
                if (config == null) config = new ModuleConfig();
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse config", e);
                config = new ModuleConfig();
            }
        } else {
            config = new ModuleConfig();
        }
    }
    
    /**
     * Public method to reload configuration from file
     * 从文件重新加载配置（用于外部更新配置后）
     */
    public void reloadConfig() {
        loadConfig();
        Log.i(TAG, "Configuration reloaded");
    }
    
    private static String quote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    public boolean saveConfig() {
        String json = gson.toJson(config);
        // Write to temp file first
        String tempFile = context.getCacheDir() + "/config-" + java.util.UUID.randomUUID() + ".json";
        try {
            java.io.FileWriter writer = new java.io.FileWriter(tempFile);
            writer.write(json);
            writer.close();
            
            // Copy to module directory with root
            Shell.Result published = RootShell.cmd("cp " + quote(tempFile) + " " + quote(CONFIG_FILE + ".new")
                + " && chmod 644 " + quote(CONFIG_FILE + ".new")
                + " && mv -f " + quote(CONFIG_FILE + ".new") + " " + quote(CONFIG_FILE)).exec();
            if (!published.isSuccess()) Log.e(TAG, "Failed to publish configuration");
            
            // Clean up temp file
            new File(tempFile).delete();
            return published.isSuccess();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save config", e);
            return false;
        }
    }
    
    public boolean isAppEnabled(String packageName) {
        AppConfig appConfig = config.perAppConfig.get(packageName);
        return appConfig != null && appConfig.enabled;
    }
    
    public ApplyResult setAppEnabled(String packageName, boolean enabled) {
        AppConfig appConfig = config.perAppConfig.get(packageName);
        if (appConfig == null) {
            appConfig = new AppConfig();
            config.perAppConfig.put(packageName, appConfig);
        }
        boolean previous = appConfig.enabled;
        if (previous == enabled) return ApplyResult.SAVED;
        appConfig.enabled = enabled;
        if (enabled && !deploySoFilesToApp(packageName)) {
            appConfig.enabled = previous;
            return ApplyResult.FAILED;
        }
        if (!saveConfig()) {
            appConfig.enabled = previous;
            return ApplyResult.FAILED;
        }
        if (!enabled) cleanupAppSoFiles(packageName);
        return stopTarget(packageName);
    }
    
    public List<SoFile> getAppSoFiles(String packageName) {
        AppConfig appConfig = config.perAppConfig.get(packageName);
        if (appConfig == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(appConfig.soFiles);
    }
    
    public List<SoFile> getAllSoFiles() {
        if (config.globalSoFiles == null) {
            config.globalSoFiles = new ArrayList<>();
        }
        return new ArrayList<>(config.globalSoFiles);
    }
    
    public enum ApplyResult { SAVED, RESTARTED, FAILED, STOP_FAILED }

    public boolean hasSoFileName(String name) {
        return getAllSoFiles().stream().anyMatch(file -> file.name.equals(name));
    }

    public ApplyResult addGlobalSoFile(String originalPath, boolean deleteOriginal) {
        String name = new File(originalPath).getName();
        if (!safeFileName(name)) return ApplyResult.FAILED;
        String storedPath = SO_STORAGE_DIR + "/" + name;
        String stage = storedPath + "." + java.util.UUID.randomUUID() + ".tmp";
        Shell.Result copied = RootShell.cmd("mkdir -p " + quote(SO_STORAGE_DIR)
            + " && cp " + quote(originalPath) + " " + quote(stage)
            + " && chmod 755 " + quote(stage)
            + " && mv -f " + quote(stage) + " " + quote(storedPath)).exec();
        if (!copied.isSuccess()) {
            RootShell.cmd("rm -f " + quote(stage)).exec();
            return ApplyResult.FAILED;
        }
        SoFile file = getAllSoFiles().stream().filter(f -> f.name.equals(name)).findFirst().orElse(null);
        if (file == null) {
            file = new SoFile(); file.name = name; file.storedPath = storedPath;
            config.globalSoFiles.add(file);
        }
        file.originalPath = originalPath;
        if (!saveConfig()) return ApplyResult.FAILED;
        ApplyResult result = ApplyResult.SAVED;
        for (Map.Entry<String, AppConfig> app : config.perAppConfig.entrySet()) {
            if (app.getValue().enabled && app.getValue().soFiles.stream().anyMatch(f -> storedPath.equals(f.storedPath))) {
                if (!deploySoFilesToApp(app.getKey())) return ApplyResult.FAILED;
                ApplyResult stopped = stopTarget(app.getKey());
                if (stopped == ApplyResult.STOP_FAILED) result = stopped;
                else if (result != ApplyResult.STOP_FAILED) result = ApplyResult.RESTARTED;
            }
        }
        if (deleteOriginal && !originalPath.equals(storedPath)) RootShell.cmd("rm -f " + quote(originalPath)).exec();
        return result;
    }

    private static boolean safeFileName(String name) {
        return name != null && !name.isEmpty() && !name.contains("/") && !name.equals(".") && !name.equals("..");
    }

    private ApplyResult stopTarget(String packageName) {
        if (!packageName.matches("[a-zA-Z0-9_]+(?:\\.[a-zA-Z0-9_]+)+")
                || packageName.equals(context.getPackageName())) return ApplyResult.STOP_FAILED;
        // Explicitly authorized by the user: a fresh process is required for the new mapping.
        return RootShell.cmd("am force-stop --user " + (android.os.Process.myUid() / 100000)
            + " " + quote(packageName)).exec().isSuccess() ? ApplyResult.RESTARTED : ApplyResult.STOP_FAILED;
    }

    /** One commit per Save: cancellation never writes partial app configuration. */
    public ApplyResult applyAppConfiguration(String packageName, String method, List<SoFile> selected,
                                             boolean useGlobal, GadgetConfig gadget) {
        AppConfig previous = config.perAppConfig.get(packageName);
        AppConfig next = previous == null ? new AppConfig() : gson.fromJson(gson.toJson(previous), AppConfig.class);
        next.injectionMethod = method;
        next.useGlobalGadget = useGlobal;
        next.gadgetConfig = gadget;
        next.soFiles = new ArrayList<>(selected);
        GadgetConfig effective = useGlobal ? config.globalGadgetConfig : gadget;
        if (effective != null) {
            SoFile gadgetFile = getAllSoFiles().stream().filter(f -> f.name.equals(effective.gadgetName)).findFirst().orElse(null);
            if (gadgetFile == null) return ApplyResult.FAILED;
            if (!next.soFiles.contains(gadgetFile)) next.soFiles.add(gadgetFile);
        }
        if (previous != null && gson.toJson(previous).equals(gson.toJson(next))) return ApplyResult.SAVED;
        config.perAppConfig.put(packageName, next);
        ApplySequence.Outcome applied = ApplySequence.run(next.enabled,
            () -> deploySoFilesToApp(packageName), this::saveConfig,
            () -> stopTarget(packageName) == ApplyResult.RESTARTED);
        if (applied == ApplySequence.Outcome.FAILED) {
            if (previous == null) config.perAppConfig.remove(packageName);
            else config.perAppConfig.put(packageName, previous);
            return ApplyResult.FAILED;
        }
        // Remove only files that this configuration used to own, after publication.
        if (previous != null) for (SoFile old : previous.soFiles) {
            if (safeFileName(old.name) && next.soFiles.stream().noneMatch(f -> old.name.equals(f.name)))
                RootShell.cmd("rm -f " + quote("/data/data/" + packageName + "/files/" + old.name)).exec();
        }
        return ApplyResult.valueOf(applied.name());
    }

    public ApplyResult removeGlobalSoFile(SoFile soFile) {
        if (config.globalSoFiles == null) return ApplyResult.SAVED;
        List<String> affected = new ArrayList<>();
        for (Map.Entry<String, AppConfig> app : config.perAppConfig.entrySet()) {
            boolean removed = app.getValue().soFiles.removeIf(f -> f.storedPath.equals(soFile.storedPath));
            if (removed && app.getValue().enabled) affected.add(app.getKey());
        }
        config.globalSoFiles.remove(soFile);
        if (!saveConfig()) return ApplyResult.FAILED;
        ApplyResult result = ApplyResult.SAVED;
        for (String pkg : affected) {
            RootShell.cmd("rm -f " + quote("/data/data/" + pkg + "/files/" + soFile.name)).exec();
            ApplyResult stopped = stopTarget(pkg);
            if (stopped == ApplyResult.STOP_FAILED) result = stopped;
            else if (result != ApplyResult.STOP_FAILED) result = ApplyResult.RESTARTED;
        }
        if (!RootShell.cmd("rm -f " + quote(soFile.storedPath)).exec().isSuccess()) return ApplyResult.FAILED;
        return result;
    }

    public void addSoFileToApp(String packageName, SoFile globalSoFile) {
        AppConfig appConfig = config.perAppConfig.get(packageName);
        if (appConfig == null) {
            appConfig = new AppConfig();
            config.perAppConfig.put(packageName, appConfig);
        }
        
        // Check if already added
        for (SoFile existing : appConfig.soFiles) {
            if (existing.storedPath.equals(globalSoFile.storedPath)) {
                return; // Already added
            }
        }
        
        // Add reference to the global SO file
        appConfig.soFiles.add(globalSoFile);
        saveConfig();
        
        // If app is enabled, deploy the new SO file
        if (appConfig.enabled) {
            deploySoFilesToApp(packageName);
        }
    }
    
    public void removeSoFileFromApp(String packageName, SoFile soFile) {
        AppConfig appConfig = config.perAppConfig.get(packageName);
        if (appConfig == null) return;
        
        appConfig.soFiles.removeIf(s -> s.storedPath.equals(soFile.storedPath));
        saveConfig();
        
        // If app is enabled, re-deploy to update SO files
        if (appConfig.enabled) {
            deploySoFilesToApp(packageName);
        }
    }
    
    public boolean getHideInjection() {
        return config.hideInjection;
    }
    
    public void setHideInjection(boolean hide) {
        config.hideInjection = hide;
        saveConfig();
    }
    
    public String getAppInjectionMethod(String packageName) {
        AppConfig appConfig = config.perAppConfig.get(packageName);
        if (appConfig == null) {
            return "standard"; // Default to standard
        }
        return appConfig.injectionMethod != null ? appConfig.injectionMethod : "standard";
    }
    
    public void setAppInjectionMethod(String packageName, String method) {
        AppConfig appConfig = config.perAppConfig.get(packageName);
        if (appConfig == null) {
            appConfig = new AppConfig();
            config.perAppConfig.put(packageName, appConfig);
        }
        appConfig.injectionMethod = method;
        saveConfig();
    }
    
    public int getInjectionDelay() {
        return config.injectionDelay;
    }
    
    public void setInjectionDelay(int delay) {
        config.injectionDelay = delay;
        saveConfig();
    }
    
    public GadgetConfig getAppGadgetConfig(String packageName) {
        AppConfig appConfig = config.perAppConfig.get(packageName);
        if (appConfig == null) {
            // If no app config, return global gadget config
            return config.globalGadgetConfig;
        }
        
        // If app is set to use global gadget, return global config
        if (appConfig.useGlobalGadget) {
            return config.globalGadgetConfig;
        }
        
        // Otherwise return app-specific gadget config
        return appConfig.gadgetConfig;
    }
    
    public GadgetConfig getGlobalGadgetConfig() {
        return config.globalGadgetConfig;
    }
    
    public ApplyResult setGlobalGadgetConfig(GadgetConfig gadgetConfig) {
        GadgetConfig old = config.globalGadgetConfig;
        if (gson.toJson(old).equals(gson.toJson(gadgetConfig))) return ApplyResult.SAVED;
        SoFile gadgetFile = gadgetConfig == null ? null : getAllSoFiles().stream()
            .filter(f -> f.name.equals(gadgetConfig.gadgetName)).findFirst().orElse(null);
        if (gadgetConfig != null && gadgetFile == null) return ApplyResult.FAILED;
        config.globalGadgetConfig = gadgetConfig;
        for (AppConfig app : config.perAppConfig.values()) if (app.useGlobalGadget) {
            if (old != null) app.soFiles.removeIf(file -> file.name.equals(old.gadgetName));
            if (gadgetFile != null && !app.soFiles.contains(gadgetFile)) app.soFiles.add(gadgetFile);
        }
        if (!saveConfig()) return ApplyResult.FAILED;
        ApplyResult result = ApplyResult.SAVED;
        for (Map.Entry<String, AppConfig> entry : config.perAppConfig.entrySet()) {
            if (!entry.getValue().enabled || !entry.getValue().useGlobalGadget) continue;
            if (!deploySoFilesToApp(entry.getKey())) return ApplyResult.FAILED;
            ApplyResult stopped = stopTarget(entry.getKey());
            if (stopped == ApplyResult.STOP_FAILED) result = stopped;
            else if (result != ApplyResult.STOP_FAILED) result = ApplyResult.RESTARTED;
        }
        return result;
    }

    public boolean getAppUseGlobalGadget(String packageName) {
        AppConfig appConfig = config.perAppConfig.get(packageName);
        if (appConfig == null) {
            return true; // Default to use global
        }
        return appConfig.useGlobalGadget;
    }
    
    public void setAppUseGlobalGadget(String packageName, boolean useGlobal) {
        AppConfig appConfig = config.perAppConfig.get(packageName);
        if (appConfig == null) {
            appConfig = new AppConfig();
            config.perAppConfig.put(packageName, appConfig);
        }
        appConfig.useGlobalGadget = useGlobal;
        saveConfig();
    }
    
    public void setAppGadgetConfig(String packageName, GadgetConfig gadgetConfig) {
        AppConfig appConfig = config.perAppConfig.get(packageName);
        if (appConfig == null) {
            appConfig = new AppConfig();
            config.perAppConfig.put(packageName, appConfig);
        }
        
        // Remove old gadget from SO list if exists
        if (appConfig.gadgetConfig != null) {
            String oldGadgetName = appConfig.gadgetConfig.gadgetName;
            appConfig.soFiles.removeIf(soFile -> soFile.name.equals(oldGadgetName));
        }
        
        appConfig.gadgetConfig = gadgetConfig;
        
        // Add new gadget to SO list if configured
        if (gadgetConfig != null) {
            // Check if gadget SO file exists in global storage
            String gadgetPath = SO_STORAGE_DIR + "/" + gadgetConfig.gadgetName;
            Shell.Result checkResult = RootShell.cmd("test -f \"" + gadgetPath + "\" && echo 'exists'").exec();
            
            if (checkResult.isSuccess() && !checkResult.getOut().isEmpty()) {
                // Add gadget as a SO file
                SoFile gadgetSoFile = new SoFile();
                gadgetSoFile.name = gadgetConfig.gadgetName;
                gadgetSoFile.storedPath = gadgetPath;
                gadgetSoFile.originalPath = gadgetPath;
                
                // Check if already in list
                boolean alreadyExists = false;
                for (SoFile soFile : appConfig.soFiles) {
                    if (soFile.name.equals(gadgetSoFile.name)) {
                        alreadyExists = true;
                        break;
                    }
                }
                
                if (!alreadyExists) {
                    appConfig.soFiles.add(gadgetSoFile);
                    Log.i(TAG, "Added gadget SO to app's SO list: " + gadgetSoFile.name);
                }
            } else {
                Log.w(TAG, "Gadget SO file not found in storage: " + gadgetPath);
                Log.w(TAG, "Please ensure " + gadgetConfig.gadgetName + " is added to SO library");
            }
        }
        
        saveConfig();
        
        // If app is enabled, deploy both gadget SO and config file
        if (appConfig.enabled) {
            if (gadgetConfig != null) {
                deployGadgetConfigFile(packageName, gadgetConfig);
            }
            // Re-deploy all SO files including gadget
            deploySoFilesToApp(packageName);
        }
    }
    
    private boolean deployGadgetConfigFile(String packageName, GadgetConfig gadgetConfig) {
        try {
            // Create gadget config JSON
            String configJson;
            if ("script".equals(gadgetConfig.mode)) {
                configJson = String.format(java.util.Locale.ROOT,
                    "{\n" +
                    "  \"interaction\": {\n" +
                    "    \"type\": \"script\",\n" +
                    "    \"path\": \"%s\"\n" +
                    "  }\n" +
                    "}",
                    gadgetConfig.scriptPath
                );
            } else {
                configJson = String.format(java.util.Locale.ROOT,
                    "{\n" +
                    "  \"interaction\": {\n" +
                    "    \"type\": \"listen\",\n" +
                    "    \"address\": \"%s\",\n" +
                    "    \"port\": %d,\n" +
                    "    \"on_port_conflict\": \"%s\",\n" +
                    "    \"on_load\": \"%s\"\n" +
                    "  }\n" +
                    "}",
                    gadgetConfig.address,
                    gadgetConfig.port,
                    gadgetConfig.onPortConflict,
                    gadgetConfig.onLoad
                );
            }
            
            // Write to temp file
            String tempFile = context.getCacheDir() + "/" + gadgetConfig.gadgetName + ".config";
            java.io.FileWriter writer = new java.io.FileWriter(tempFile);
            writer.write(configJson);
            writer.close();
            
            // Copy to app's files directory
            String filesDir = "/data/data/" + packageName + "/files";
            String gadgetConfigName = gadgetConfig.gadgetName.replace(".so", ".config.so");
            String targetPath = filesDir + "/" + gadgetConfigName;
            
            String staged = targetPath + ".new";
            Shell.Result copyResult = RootShell.cmd("cp " + quote(tempFile) + " " + quote(staged)
                + " && chmod 644 " + quote(staged)
                + " && mv -f " + quote(staged) + " " + quote(targetPath)).exec();
            if (copyResult.isSuccess()) {
                // Set permissions
                RootShell.cmd("chmod 644 " + targetPath).exec();
                Log.i(TAG, "Deployed gadget config to: " + targetPath);
            } else {
                Log.e(TAG, "Failed to deploy gadget config: " + String.join("\n", copyResult.getErr()));
            }
            
            // Clean up temp file
            new java.io.File(tempFile).delete();
            return copyResult.isSuccess();
        } catch (Exception e) {
            Log.e(TAG, "Failed to create gadget config file", e);
            return false;
        }
    }
    
    // Copy SO files directly to app's data directory
    private boolean deploySoFilesToApp(String packageName) {
        AppConfig appConfig = config.perAppConfig.get(packageName);
        if (appConfig == null) return false;
        if (appConfig.soFiles.isEmpty()) return true;
        
        // First check if we have root access
        if (!RootShell.get().isRoot()) {
            Log.e(TAG, "No root access available!");
            return false;
        }
        
        // Create files directory in app's data dir
        String filesDir = "/data/data/" + packageName + "/files";
        
        Log.i(TAG, "Deploying SO files to: " + filesDir);
        
        // Create directory without su -c for better compatibility
        Shell.Result mkdirResult = RootShell.cmd("mkdir -p " + filesDir).exec();
        if (!mkdirResult.isSuccess()) {
            Log.e(TAG, "Failed to create directory: " + filesDir);
            Log.e(TAG, "Error: " + String.join("\n", mkdirResult.getErr()));
            return false;
        }
        
        // Set proper permissions and ownership for the files directory
        RootShell.cmd("chmod 771 " + filesDir).exec();
        
        // Get UID and GID for the package
        Shell.Result uidResult = RootShell.cmd("stat -c %u /data/data/" + packageName).exec();
        String uid = "";
        if (uidResult.isSuccess() && !uidResult.getOut().isEmpty()) {
            uid = uidResult.getOut().get(0).trim();
            Log.i(TAG, "Package UID: " + uid);
            
            // Set ownership of files directory to match app
            Shell.Result chownDirResult = RootShell.cmd("chown " + uid + ":" + uid + " \"" + filesDir + "\"").exec();
            if (!chownDirResult.isSuccess()) {
                Log.e(TAG, "Failed to set directory ownership: " + String.join("\n", chownDirResult.getErr()));
            }
            
            // Set SELinux context for the directory
            RootShell.cmd("restorecon -F " + quote(filesDir)).exec();
        } else {
            Log.e(TAG, "Failed to get package UID");
        }
        
        Shell.Result labelResult = RootShell.cmd("ls -Zd " + quote(filesDir)).exec();
        String label = labelResult.isSuccess() && !labelResult.getOut().isEmpty()
            ? labelResult.getOut().get(0).trim().split("\\s+")[0] : "";
        if (!label.matches("[a-zA-Z0-9_:,]+")) {
            Log.e(TAG, "Cannot determine target SELinux label; deployment cancelled");
            return false;
        }
        // Copy each SO file configured for this app
        for (SoFile soFile : appConfig.soFiles) {
            // Use original filename
            if (soFile.name == null || soFile.name.isEmpty() || soFile.name.contains("/")
                    || soFile.name.equals(".") || soFile.name.equals("..")) return false;
            String destPath = filesDir + "/" + soFile.name;
            
            // Check if source file exists
            Shell.Result checkResult = RootShell.cmd("test -f \"" + soFile.storedPath + "\" && echo 'exists'").exec();
            if (!checkResult.isSuccess() || checkResult.getOut().isEmpty()) {
                Log.e(TAG, "Source SO file not found: " + soFile.storedPath);
                // Log more details about the missing file
                Shell.Result lsResult = RootShell.cmd("ls -la \"" + SO_STORAGE_DIR + "\"").exec();
                Log.e(TAG, "Contents of SO storage dir: " + String.join("\n", lsResult.getOut()));
                return false;
            }
            
            Log.i(TAG, "Copying: " + soFile.storedPath + " to " + destPath);
            
            // First, ensure the destination directory exists and has proper permissions
            RootShell.cmd("mkdir -p \"" + filesDir + "\"").exec();
            RootShell.cmd("chmod 755 \"" + filesDir + "\"").exec();
            
            // Publish a new inode atomically. Truncating a mapped library can cause SIGBUS
            // in an already-running target process, even after dlopen succeeded.
            String stagedPath = destPath + "." + java.util.UUID.randomUUID() + ".tmp";
            String owner = uid.matches("[0-9]+") ? " && chown " + uid + ":" + uid + " " + quote(stagedPath) : "";
            Shell.Result result = RootShell.cmd("cp " + quote(soFile.storedPath) + " " + quote(stagedPath)
                + " && chmod 755 " + quote(stagedPath) + owner
                + " && chcon " + quote(label) + " " + quote(stagedPath)
                + " && mv -f " + quote(stagedPath) + " " + quote(destPath)).exec();
            if (!result.isSuccess()) {
                RootShell.cmd("rm -f " + quote(stagedPath)).exec();
                Log.e(TAG, "Atomic SO deployment failed: " + String.join("\n", result.getErr()));
                return false;
            }

            // Verify the file was copied with correct permissions
            Shell.Result verifyResult = RootShell.cmd("ls -laZ \"" + destPath + "\" 2>/dev/null").exec();
            if (verifyResult.isSuccess() && !verifyResult.getOut().isEmpty()) {
                Log.i(TAG, "Successfully deployed: " + String.join(" ", verifyResult.getOut()));
            } else {
                // Fallback verification without SELinux context
                verifyResult = RootShell.cmd("ls -la \"" + destPath + "\" 2>/dev/null").exec();
                if (verifyResult.isSuccess() && !verifyResult.getOut().isEmpty()) {
                    Log.i(TAG, "Successfully deployed: " + String.join(" ", verifyResult.getOut()));
                } else {
                    Log.e(TAG, "Failed to verify SO file copy: " + destPath);
                }
            }
        }
        
        Log.i(TAG, "Deployment complete for: " + packageName);
        
        // Deploy gadget config if configured
        ConfigManager.GadgetConfig gadgetToUse = getAppGadgetConfig(packageName);
        if (gadgetToUse != null) {
            if (!deployGadgetConfigFile(packageName, gadgetToUse)) return false;
        }
        return true;
    }

    // Clean up deployed SO files when app is disabled
    private void cleanupAppSoFiles(String packageName) {
        AppConfig appConfig = config.perAppConfig.get(packageName);
        if (appConfig == null || appConfig.soFiles.isEmpty()) {
            Log.w(TAG, "No SO files to clean up for: " + packageName);
            return;
        }
        
        // First check if we have root access
        if (!RootShell.get().isRoot()) {
            Log.e(TAG, "No root access available!");
            return;
        }
        
        String filesDir = "/data/data/" + packageName + "/files";
        
        // Only delete the SO files we deployed, not the entire directory
        for (SoFile soFile : appConfig.soFiles) {
            // Use original filename
            String filePath = filesDir + "/" + soFile.name;
            
            Log.i(TAG, "Cleaning up: " + filePath);
            
            // Check if file exists before trying to delete
            Shell.Result checkResult = RootShell.cmd("test -f \"" + filePath + "\" && echo 'exists'").exec();
            if (checkResult.isSuccess() && !checkResult.getOut().isEmpty()) {
                // Try to remove the file
                Shell.Result result = RootShell.cmd("rm -f \"" + filePath + "\"").exec();
                
                // Verify deletion
                Shell.Result verifyResult = RootShell.cmd("test -f \"" + filePath + "\" && echo 'still_exists'").exec();
                if (!verifyResult.isSuccess() || verifyResult.getOut().isEmpty()) {
                    Log.i(TAG, "Successfully deleted SO file: " + filePath);
                } else {
                    Log.e(TAG, "Failed to delete SO file: " + filePath);
                    // Try with su -c
                    RootShell.cmd("su -c 'rm -f \"" + filePath + "\"'").exec();
                }
            } else {
                Log.w(TAG, "SO file not found for cleanup: " + filePath);
            }
        }
        
        // Clean up gadget config file if exists
        ConfigManager.GadgetConfig gadgetToUse = getAppGadgetConfig(packageName);
        if (gadgetToUse != null) {
            String gadgetConfigName = gadgetToUse.gadgetName.replace(".so", ".config.so");
            String configPath = filesDir + "/" + gadgetConfigName;
            
            Shell.Result checkConfigResult = RootShell.cmd("test -f \"" + configPath + "\" && echo 'exists'").exec();
            if (checkConfigResult.isSuccess() && !checkConfigResult.getOut().isEmpty()) {
                Shell.Result deleteResult = RootShell.cmd("rm -f \"" + configPath + "\"").exec();
                if (deleteResult.isSuccess()) {
                    Log.i(TAG, "Deleted gadget config: " + configPath);
                } else {
                    // Try with su -c
                    RootShell.cmd("su -c 'rm -f \"" + configPath + "\"'").exec();
                }
            }
        }
        
        Log.i(TAG, "Cleanup complete for: " + packageName);
    }
    
    // Deploy SO files for all enabled apps
    public void deployAllSoFiles() {
        for (Map.Entry<String, AppConfig> entry : config.perAppConfig.entrySet()) {
            if (entry.getValue().enabled) {
                deploySoFilesToApp(entry.getKey());
            }
        }
    }
    
    /**
     * Public method to deploy SO files for a specific package
     * 为指定包名部署 SO 文件（外部调用）
     * @param packageName Target package name
     */
    public void deployForPackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            Log.e(TAG, "Package name cannot be null or empty");
            return;
        }
        deploySoFilesToApp(packageName);
    }
    
    // Data classes
    public static class ModuleConfig {
        public boolean enabled = true;
        public boolean hideInjection = false;
        public int injectionDelay = 2; // Default 2 seconds
        public List<SoFile> globalSoFiles = new ArrayList<>();
        public Map<String, AppConfig> perAppConfig = new HashMap<>();
        public GadgetConfig globalGadgetConfig = null; // Global gadget configuration
    }
    
    public static class AppConfig {
        public boolean enabled = false;
        public List<SoFile> soFiles = new ArrayList<>();
        public String injectionMethod = "standard"; // "standard", "riru" or "custom_linker"
        public GadgetConfig gadgetConfig = null;
        public boolean useGlobalGadget = true; // Whether to use global gadget settings
    }
    
    public static class SoFile {
        public String name;
        public String storedPath;
        public String originalPath;
        
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof SoFile) {
                return storedPath.equals(((SoFile) obj).storedPath);
            }
            return false;
        }
    }
    
    public static class GadgetConfig {
        public String mode = "server"; // "server" or "script"
        // Server mode config
        public String address = "0.0.0.0";
        public int port = 27042;
        public String onPortConflict = "fail";
        public String onLoad = "wait";
        // Script mode config
        public String scriptPath = "/data/local/tmp/script.js";
        // Common config
        public String gadgetName = "libgadget.so";
    }
}
