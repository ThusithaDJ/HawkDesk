package com.olympus.system.hawkdeskpos.util;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Base64;

/**
 * Stores and retrieves database connection settings from config.cnf.
 * Password is base64-obfuscated (not encrypted — suitable for local POS use).
 */
public class DbConfig {

    private static final String KEY_HOST = "db.host";
    private static final String KEY_PORT = "db.port";
    private static final String KEY_NAME = "db.name";
    private static final String KEY_USER = "db.user";
    private static final String KEY_PASS = "db.password";

    private final Configs configs = new Configs();

    /** True if a db.host entry has been saved to config.cnf. */
    public boolean isConfigured() {
        String host = configs.getProp(KEY_HOST);
        return host != null && !host.isEmpty();
    }

    public String getHost()     { return nvl(configs.getProp(KEY_HOST), "localhost"); }
    public String getPort()     { return nvl(configs.getProp(KEY_PORT), "3306"); }
    public String getName()     { return nvl(configs.getProp(KEY_NAME), "hawkdeskpos"); }
    public String getUser()     { return nvl(configs.getProp(KEY_USER), "root"); }
    public String getPassword() { return decodePass(configs.getProp(KEY_PASS)); }

    public void save(String host, String port, String name, String user, String password) {
        configs.SaveProp(KEY_HOST, host);
        configs.SaveProp(KEY_PORT, port);
        configs.SaveProp(KEY_NAME, name);
        configs.SaveProp(KEY_USER, user);
        configs.SaveProp(KEY_PASS, encodePass(password));
    }

    public String buildJdbcUrl() {
        return "jdbc:mysql://" + getHost() + ":" + getPort() + "/" + getName()
                + "?createDatabaseIfNotExist=true&zeroDateTimeBehavior=convertToNull&serverTimezone=UTC";
    }

    /** Returns null on success, error message on failure. */
    public String testConnection() {
        return testConnection(getHost(), getPort(), getName(), getUser(), getPassword());
    }

    /** Static test with explicit params (before saving). */
    public static String testConnection(String host, String port, String name,
                                         String user, String password) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            String url = "jdbc:mysql://" + host + ":" + port + "/" + name
                    + "?createDatabaseIfNotExist=true&zeroDateTimeBehavior=convertToNull"
                    + "&serverTimezone=UTC&connectTimeout=5000";
            try (Connection c = DriverManager.getConnection(url, user, password)) {
                return null; // success
            }
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String encodePass(String plain) {
        if (plain == null || plain.isEmpty()) return "";
        return Base64.getEncoder().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodePass(String encoded) {
        if (encoded == null || encoded.isEmpty()) return "";
        try {
            return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return encoded; // backwards compat: value stored as plain text
        }
    }

    private static String nvl(String val, String def) {
        return (val == null || val.isEmpty()) ? def : val;
    }
}
