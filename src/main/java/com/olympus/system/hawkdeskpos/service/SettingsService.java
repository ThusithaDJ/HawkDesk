package com.olympus.system.hawkdeskpos.service;

import com.olympus.system.hawkdeskpos.util.Configs;
import org.hibernate.SessionFactory;

/**
 * Reads and writes application settings via Configs (config.cnf).
 */
public class SettingsService {

    private final Configs configs = new Configs();

    public SettingsService(SessionFactory sf) {
        // sf not used by this service but kept for constructor consistency
    }

    public String get(String key, String defaultValue) {
        String v = configs.getProp(key);
        return (v == null || v.isEmpty()) ? defaultValue : v;
    }

    public void set(String key, String value) {
        configs.SaveProp(key, value);
    }

    public int getInt(String key, int defaultValue) {
        try { return Integer.parseInt(get(key, String.valueOf(defaultValue))); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    public boolean getBool(String key, boolean defaultValue) {
        String v = get(key, null);
        if (v == null) return defaultValue;
        return "true".equalsIgnoreCase(v) || "1".equals(v);
    }

    // ── Well-known keys ────────────────────────────────────────────────────────
    public String shopName()    { return get("ShopName", "Colombo Hardware Supplies"); }
    public String shopAddress() { return get("ShopAddress", ""); }
    public String shopPhone()   { return get("ShopPhone", ""); }
    public String currency()    { return get("Currency", "Rs."); }
    public int    fontSizePx()  { return getInt("UIFontSize", 14); }
    public boolean allowNegativeStock() { return getBool("AllowNegativeStock", false); }
    public boolean autoPrintReceipt()   { return getBool("AutoPrintReceipt", false); }
}
