package com.olympus.system.hawkdeskpos.service;

import com.olympus.system.hawkdeskpos.util.Configs;
import org.hibernate.SessionFactory;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

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
    public boolean allowNegativeStock()  { return getBool("AllowNegativeStock",  false); }
    public boolean autoPrintReceipt()    { return getBool("AutoPrintReceipt",    false); }
    public boolean returnsEnabled()      { return getBool("ReturnsEnabled",      true);  }
    public int     returnPeriodDays()    { return getInt("ReturnPeriodDays",     30);    }
    /** Pattern for auto-generated batch numbers. Placeholders: {YYYY} = year, {NNN} = sequence. */
    public String  batchNumberPattern()  { return get("BatchNumberPattern", "B{YYYY}-{NNN}"); }

    // ── Default account routing ───────────────────────────────────────────────
    /** Account ID to credit for sale income (null = use Cash Drawer). */
    public Long defaultSaleAccountId() {
        String v = get("DefaultSaleAccountId", null);
        if (v == null || v.isBlank()) return null;
        try { return Long.parseLong(v.trim()); } catch (NumberFormatException e) { return null; }
    }

    /** Account ID to debit for GRN payments (null = use Cash Drawer). */
    public Long defaultGrnAccountId() {
        String v = get("DefaultGrnAccountId", null);
        if (v == null || v.isBlank()) return null;
        try { return Long.parseLong(v.trim()); } catch (NumberFormatException e) { return null; }
    }

    public void setDefaultSaleAccountId(Long id) { set("DefaultSaleAccountId", id == null ? "" : id.toString()); }
    public void setDefaultGrnAccountId(Long id)  { set("DefaultGrnAccountId",  id == null ? "" : id.toString()); }

    // ── Trial period ──────────────────────────────────────────────────────────

    private static final int TRIAL_DAYS = 15;

    /**
     * Records today as the trial start date if it has never been set.
     * Called once on application startup before the login window appears.
     */
    public void ensureTrialStartDate() {
        String val = configs.getProp("TrialStartDate");
        if (val == null || val.isEmpty()) {
            configs.SaveProp("TrialStartDate", LocalDate.now().toString());
        }
    }

    public LocalDate trialStartDate() {
        String val = configs.getProp("TrialStartDate");
        if (val == null || val.isEmpty()) {
            ensureTrialStartDate();
            return LocalDate.now();
        }
        try { return LocalDate.parse(val); } catch (Exception e) { return LocalDate.now(); }
    }

    /** Total days in the trial period (not user-editable). */
    public int trialTotalDays() { return TRIAL_DAYS; }

    /** Days that have elapsed since the trial started (clamped to 0). */
    public long trialDaysElapsed() {
        return Math.max(0, ChronoUnit.DAYS.between(trialStartDate(), LocalDate.now()));
    }

    /** Days remaining in the trial (0 when expired). */
    public long trialDaysRemaining() {
        return Math.max(0, TRIAL_DAYS - trialDaysElapsed());
    }

    public boolean isTrialExpired() {
        return trialDaysElapsed() >= TRIAL_DAYS;
    }
}
