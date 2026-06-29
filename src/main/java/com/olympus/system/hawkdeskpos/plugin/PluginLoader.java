package com.olympus.system.hawkdeskpos.plugin;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Discovers and loads plugin JARs from the plugins/ directory.
 *
 * Each plugin JAR must contain:
 *   META-INF/services/com.olympus.system.hawkdeskpos.plugin.Plugin
 * listing its Plugin implementation class (standard Java SPI).
 */
public final class PluginLoader {

    private static final Logger LOG = Logger.getLogger(PluginLoader.class.getName());

    private PluginLoader() {}

    /**
     * Returns the plugins directory, creating it if necessary.
     * Prefers <working-dir>/plugins, falls back to ~/.hawkdeskpos/plugins.
     */
    public static File resolvePluginsDir() {
        File appDir = new File(System.getProperty("user.dir"), "plugins");
        if (appDir.exists() || appDir.mkdirs()) return appDir;
        File homeDir = new File(System.getProperty("user.home"), ".hawkdeskpos/plugins");
        homeDir.mkdirs();
        return homeDir;
    }

    /** Load all plugin JARs found in pluginsDir and call onLoad on each one. */
    public static void loadAll(File pluginsDir, PluginContext context) {
        if (!pluginsDir.exists() || !pluginsDir.isDirectory()) return;
        File[] jars = pluginsDir.listFiles(f -> f.getName().endsWith(".jar"));
        if (jars == null) return;
        for (File jar : jars) loadPlugin(jar, context);
    }

    private static void loadPlugin(File jar, PluginContext context) {
        try {
            URLClassLoader loader = new URLClassLoader(
                    new URL[]{jar.toURI().toURL()},
                    PluginLoader.class.getClassLoader()
            );
            ServiceLoader<Plugin> sl = ServiceLoader.load(Plugin.class, loader);
            boolean found = false;
            for (Plugin plugin : sl) {
                found = true;
                try {
                    plugin.onLoad(context);
                    PluginRegistry.getInstance().addPlugin(new PluginEntry(plugin.getMeta(), plugin, jar));
                    LOG.info("Loaded plugin: " + plugin.getMeta().name() + " v" + plugin.getMeta().version());
                } catch (Exception ex) {
                    LOG.log(Level.WARNING, "Plugin onLoad() failed: " + jar.getName(), ex);
                }
            }
            if (!found) {
                LOG.warning("No Plugin implementation found in: " + jar.getName());
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load plugin JAR: " + jar.getName(), e);
        }
    }
}
