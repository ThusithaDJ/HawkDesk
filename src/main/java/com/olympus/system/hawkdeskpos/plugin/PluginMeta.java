package com.olympus.system.hawkdeskpos.plugin;

/**
 * Metadata declared by a plugin via its getMeta() implementation.
 */
public record PluginMeta(
        String id,
        String name,
        String version,
        String description,
        String author,
        String minAppVersion
) {
    public static PluginMeta unknown(String jarName) {
        return new PluginMeta(jarName, jarName, "?", "", "", "");
    }
}
