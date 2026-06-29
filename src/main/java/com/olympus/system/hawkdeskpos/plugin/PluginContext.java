package com.olympus.system.hawkdeskpos.plugin;

import org.hibernate.SessionFactory;

import javax.swing.JPanel;
import java.util.function.Supplier;

/**
 * API surface given to plugins during onLoad().
 * Plugins register panels and nav items through this interface;
 * they never touch Home or NavBar directly.
 */
public interface PluginContext {
    /** Register a full-screen panel reachable via the card name. */
    void registerPanel(String cardName, Supplier<JPanel> panelFactory);

    /** Add a button to the top nav bar that navigates to cardName. */
    void addNavItem(String label, String cardName);

    /** Hibernate SessionFactory for plugins that need DB access. */
    SessionFactory getSessionFactory();
}
