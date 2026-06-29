package com.olympus.system.hawkdeskpos.plugin;

import javax.swing.JPanel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Central store for everything plugins register at startup.
 * Populated during plugin loading; consumed by Home and NavBar.
 */
public final class PluginRegistry {

    public record NavEntry(String label, String cardName) {}
    public record PanelEntry(String cardName, Supplier<JPanel> factory) {}

    private static final PluginRegistry INSTANCE = new PluginRegistry();

    private final List<PluginEntry> loadedPlugins = new ArrayList<>();
    private final List<NavEntry>    navItems      = new ArrayList<>();
    private final List<PanelEntry>  panels        = new ArrayList<>();

    private PluginRegistry() {}

    public static PluginRegistry getInstance() { return INSTANCE; }

    public void addPlugin(PluginEntry entry)    { loadedPlugins.add(entry); }
    public void registerNav(NavEntry entry)      { navItems.add(entry); }
    public void registerPanel(PanelEntry entry)  { panels.add(entry); }

    public List<PluginEntry> getLoadedPlugins()  { return Collections.unmodifiableList(loadedPlugins); }
    public List<NavEntry>    getNavItems()        { return Collections.unmodifiableList(navItems); }
    public List<PanelEntry>  getPanels()          { return Collections.unmodifiableList(panels); }
}
