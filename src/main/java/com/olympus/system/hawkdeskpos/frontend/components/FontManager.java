package com.olympus.system.hawkdeskpos.frontend.components;

import com.olympus.system.hawkdeskpos.util.Configs;
import javax.swing.*;
import java.awt.*;
import java.util.Enumeration;

/**
 * Global font manager. Reads UIFontSize from config.cnf on startup and
 * overrides every UIManager font key so the change applies to all Swing
 * components immediately.
 */
public class FontManager {

    /** Preferred font family resolved once at startup. */
    public static final String FONT_FAMILY;

    static {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            FONT_FAMILY = "Segoe UI";
        } else if (os.contains("mac")) {
            FONT_FAMILY = "SF Pro Display";
        } else {
            FONT_FAMILY = "Ubuntu";
        }
    }

    /**
     * Applies the stored font size from config at app startup.
     * Call once before any Swing window is shown.
     */
    public static void applyFromConfig() {
        Configs cfg = new Configs();
        int size = 14;
        try {
            String v = cfg.getProp("UIFontSize");
            if (v != null && !v.isEmpty()) size = Integer.parseInt(v);
        } catch (NumberFormatException ignored) {}
        apply(size);
    }

    /**
     * Sets all UIManager font keys to the given size.
     * Safe to call from the EDT after the window is visible — takes effect immediately.
     */
    public static void apply(int sizePx) {
        Font base = new Font(FONT_FAMILY, Font.PLAIN, sizePx);
        Font bold = base.deriveFont(Font.BOLD);

        Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object val = UIManager.get(key);
            if (val instanceof Font) {
                Font f = (Font) val;
                UIManager.put(key, f.isBold() ? bold : base);
            }
        }

        // Persist the chosen size
        new Configs().SaveProp("UIFontSize", String.valueOf(sizePx));

        // Refresh all open windows
        for (Window w : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(w);
        }
    }

    /** Convenience: named size presets. */
    public static int sizeForName(String name) {
        return switch (name) {
            case "Small"       -> 12;
            case "Large"       -> 16;
            case "Extra Large" -> 18;
            default            -> 14;  // Medium
        };
    }
}
