package com.olympus.system.hawkdeskpos.plugin;

import java.io.File;

/** A successfully loaded plugin together with its source JAR. */
public record PluginEntry(PluginMeta meta, Plugin plugin, File jarFile) {}
