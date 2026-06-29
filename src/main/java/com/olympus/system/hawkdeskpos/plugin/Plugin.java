package com.olympus.system.hawkdeskpos.plugin;

/**
 * Entry point for every HawkDeskPOS plugin.
 * Plugin JARs declare their implementation via
 * META-INF/services/com.olympus.system.hawkdeskpos.plugin.Plugin
 * so the loader can discover them with ServiceLoader.
 */
public interface Plugin {
    PluginMeta getMeta();
    void onLoad(PluginContext ctx);
    void onUnload();
}
