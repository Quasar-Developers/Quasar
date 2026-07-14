package dev.kate.erd.bukkit.addon;
import dev.kate.erd.core.addon.ERDAddon;
import dev.kate.erd.core.addon.AddonContext;
import dev.kate.erd.core.addon.AddonInfo;
import dev.kate.erd.core.util.ErdLogger;
import org.yaml.snakeyaml.Yaml;
import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
/**
 * Manages loading and lifecycle of ERD addons.
 * 
 * <p>Scans {@code plugins/ERD/addons/} and {@code plugins/ERD/dev-addons/} folders
 * for addon JARs, loads them with isolated classloaders, and manages their lifecycle.</p>
 */
public class AddonManager {
    private final ErdLogger logger;
    private final File addonsFolder;
    private final File devAddonsFolder;
    private final AddonContext context;
    private final List<LoadedAddon> loadedAddons = new ArrayList<>();
    public AddonManager(ErdLogger logger, File dataFolder, AddonContext context) {
        this.logger = logger;
        this.addonsFolder = new File(dataFolder, "addons");
        this.devAddonsFolder = new File(dataFolder, "dev-addons");
        this.context = context;
    }
    /**
     * Load all addons from folders.
     * Creates folders if they don't exist.
     */
    public void loadAddons() {
        // Create folders if they don't exist
        if (!addonsFolder.exists()) {
            if (!addonsFolder.mkdirs() && !addonsFolder.exists()) {
                logger.error("Failed to create addons folder: %s", addonsFolder.getAbsolutePath());
            }
        }
        if (!devAddonsFolder.exists()) {
            if (!devAddonsFolder.mkdirs() && !devAddonsFolder.exists()) {
                logger.error("Failed to create dev-addons folder: %s", devAddonsFolder.getAbsolutePath());
            }
        }
        logger.info("Loading addons...");
        // Load from addons folder
        int prodCount = loadFromFolder(addonsFolder, false);
        // Load from dev-addons folder
        int devCount = loadFromFolder(devAddonsFolder, true);
        logger.info("Loaded %d addon(s) (%d production, %d dev)", 
            loadedAddons.size(), prodCount, devCount);
    }
    /**
     * Enable all loaded addons.
     * Call this after core systems are initialized.
     */
    public void enableAddons() {
        for (LoadedAddon addon : loadedAddons) {
            try {
                addon.instance.onEnable();
                logger.info("Enabled addon: %s v%s%s", 
                    addon.info.name(), 
                    addon.info.version(),
                    addon.isDev ? " [DEV]" : "");
            } catch (Exception e) {
                logger.error("Failed to enable addon %s: %s", 
                    addon.info.name(), e.getMessage());
                logger.debug("Stack trace:", e);
            }
        }
    }
    /**
     * Disable all addons.
     * Call this during plugin shutdown.
     */
    public void disableAddons() {
        for (LoadedAddon addon : loadedAddons) {
            try {
                addon.instance.onDisable();
                logger.info("Disabled addon: %s", addon.info.name());
            } catch (Exception e) {
                logger.error("Error disabling addon %s: %s", 
                    addon.info.name(), e.getMessage());
            }
        }
        loadedAddons.clear();
    }
    /**
     * Get all loaded addons (for debugging).
     */
    public List<AddonInfo> getLoadedAddons() {
        return loadedAddons.stream()
            .map(a -> a.info)
            .toList();
    }
    /**
     * Reload all addons by disabling and re-enabling them.
     * Machine state is preserved in world markers during the reload.
     */
    public void reloadAddons() {
        logger.info("Reloading addons...");

        // Snapshot machine runtime state so we can restore it after reload
        // Note: Markers are also saved by the plugin's periodic update or on disable
        List<dev.kate.erd.core.machine.MachineSnapshot> snapshots = context.snapshotMachines();

        // Disable existing addons
        disableAddons();

        // Load new/updated addons
        loadAddons();

        // Enable addons
        enableAddons();

        // Restore machine runtime state (queues state for when instances re-register)
        context.restoreMachines(snapshots);

        // Trigger re-detection from markers to actually recreate the machines
        context.redetectMachinesFromMarkers();

        logger.info("Addons reloaded successfully.");
    }
    private int loadFromFolder(File folder, boolean isDev) {
        int count = 0;
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".jar"));
        if (files == null || files.length == 0) {
            logger.debug("No addon JARs found in %s", folder.getName());
            return 0;
        }
        for (File jarFile : files) {
            try {
                loadAddon(jarFile, isDev);
                count++;
            } catch (Exception e) {
                logger.error("Failed to load addon from %s: %s", 
                    jarFile.getName(), e.getMessage());
                logger.debug("Stack trace:", e);
            }
        }
        return count;
    }
    private void loadAddon(File jarFile, boolean isDev) throws Exception {
        logger.debug("Loading addon from: %s", jarFile.getName());
        // Load addon.yml from JAR
        AddonManifest manifest;
        try (JarFile jar = new JarFile(jarFile)) {
            JarEntry manifestEntry = jar.getJarEntry("addon.yml");
            if (manifestEntry == null) {
                throw new IllegalArgumentException(
                    "No addon.yml found in " + jarFile.getName());
            }
            try (InputStream is = jar.getInputStream(manifestEntry)) {
                Yaml yaml = new Yaml();
                Map<String, Object> data = yaml.load(is);
                manifest = parseManifest(data);
            }
        }
        // Load JAR with custom classloader
        URL[] urls = {jarFile.toURI().toURL()};
        URLClassLoader classLoader = new URLClassLoader(
            urls, 
            getClass().getClassLoader()
        );
        // Load main class
        Class<?> mainClass = classLoader.loadClass(manifest.mainClass);
        if (!ERDAddon.class.isAssignableFrom(mainClass)) {
            throw new IllegalArgumentException(
                "Main class must implement ERDAddon: " + manifest.mainClass);
        }
        // Create instance
        ERDAddon addon = (ERDAddon) mainClass.getDeclaredConstructor().newInstance();
        AddonInfo info = addon.getInfo();
        logger.info("Loading addon: %s v%s by %s%s", 
            info.name(), 
            info.version(), 
            info.author(), 
            isDev ? " [DEV]" : "");
        // Call onLoad - this registers machines/controllers
        addon.onLoad(context);
        // Store loaded addon
        loadedAddons.add(new LoadedAddon(addon, info, classLoader, isDev));
    }
    private AddonManifest parseManifest(Map<String, Object> data) {
        String mainClass = (String) data.get("main");
        if (mainClass == null || mainClass.isEmpty()) {
            throw new IllegalArgumentException(
                "addon.yml must specify 'main' class");
        }
        return new AddonManifest(mainClass);
    }
    private record AddonManifest(String mainClass) {}
    private record LoadedAddon(
        ERDAddon instance, 
        AddonInfo info, 
        URLClassLoader classLoader, 
        boolean isDev
    ) {}
}
