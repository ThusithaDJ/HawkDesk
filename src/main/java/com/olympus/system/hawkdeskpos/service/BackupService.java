package com.olympus.system.hawkdeskpos.service;

import com.olympus.system.hawkdeskpos.util.DbConfig;
import org.hibernate.SessionFactory;

import java.io.*;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Handles database backup via mysqldump.
 *
 * Key design choices:
 *  - Uses --single-transaction so InnoDB tables are snapshotted consistently
 *    without acquiring table locks — the application keeps running.
 *  - Includes --routines, --triggers, --hex-blob, --complete-insert for a
 *    complete logical backup that can be restored on any compatible MySQL.
 *  - Reads DB credentials from DbConfig (config.cnf) rather than hardcoding.
 *  - stderr is captured separately so SQL files are never contaminated.
 */
public class BackupService {

    public interface ProgressListener {
        void onStep(String stepName);
        void onComplete(boolean success, String message);
    }

    public record BackupEntry(String fileName, LocalDateTime dateTime, long sizeBytes, String type) {}

    private final SettingsService    settings;
    private ScheduledExecutorService autoBackupScheduler;
    private LocalDateTime            lastAutoBackupRun;

    public BackupService(SessionFactory sf, SettingsService settings) {
        this.settings = settings;
    }

    // ── Folder & tool settings ────────────────────────────────────────────────

    public String getBackupFolder() {
        return settings.get("BackupFolder",
                System.getProperty("user.home") + File.separator + "HawkDeskPOS"
                        + File.separator + "backups");
    }

    public void setBackupFolder(String path) {
        settings.set("BackupFolder", path);
    }

    public String getMysqldumpPath() {
        return findMysqldump();
    }

    public void setMysqldumpPath(String path) {
        settings.set("MysqldumpPath", path);
    }

    // ── Backup list ───────────────────────────────────────────────────────────

    public List<BackupEntry> listBackups() {
        List<BackupEntry> result = new ArrayList<>();
        File dir = new File(getBackupFolder());
        if (!dir.exists()) return result;
        File[] files = dir.listFiles(f -> f.getName().endsWith(".sql"));
        if (files == null) return result;
        for (File f : files) {
            LocalDateTime dt = LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(f.lastModified()),
                    java.time.ZoneId.systemDefault());
            result.add(new BackupEntry(f.getName(), dt, f.length(),
                    f.getName().startsWith("auto_") ? "Auto" : "Manual"));
        }
        result.sort((a, b) -> b.dateTime().compareTo(a.dateTime()));
        return result;
    }

    public LocalDateTime lastBackupTime() {
        return listBackups().stream().findFirst().map(BackupEntry::dateTime).orElse(null);
    }

    // ── Backup execution ──────────────────────────────────────────────────────

    /**
     * Runs a full logical backup using mysqldump.
     * --single-transaction ensures a consistent InnoDB snapshot without locks.
     */
    public void backup(String type, ProgressListener listener) {
        new Thread(() -> {
            try {
                listener.onStep("Preparing");

                File dir = new File(getBackupFolder());
                if (!dir.exists()) dir.mkdirs();

                String stamp  = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String prefix = "auto_".equals(type) ? "auto_" : "manual_";
                DbConfig db   = new DbConfig();
                String dbName = db.isConfigured() ? db.getName() : "hawkdeskpos";
                File   out    = new File(dir, prefix + dbName + "_" + stamp + ".sql");

                String tool = findMysqldump();
                if (tool == null) {
                    listener.onComplete(false,
                            "mysqldump not found. Set the path in Backup & Restore settings.");
                    return;
                }

                listener.onStep("Dumping database");

                String host = db.isConfigured() ? db.getHost() : "localhost";
                String port = db.isConfigured() ? db.getPort() : "3306";
                String user = db.isConfigured() ? db.getUser() : "root";
                String pass = db.isConfigured() ? db.getPassword() : "1234";

                ProcessBuilder pb = new ProcessBuilder(
                        tool,
                        "--user=" + user,
                        "--password=" + pass,
                        "--host=" + host,
                        "--port=" + port,
                        "--databases", dbName,
                        "--single-transaction",   // consistent snapshot; no table locks
                        "--routines",             // stored procedures & functions
                        "--triggers",             // triggers
                        "--hex-blob",             // safe binary columns
                        "--complete-insert",      // portable INSERT statements
                        "--result-file=" + out.getAbsolutePath()
                );
                pb.redirectErrorStream(false); // capture stderr separately

                Process p = pb.start();

                // Drain stderr on a separate thread (prevent blocking)
                StringBuilder stderrBuf = new StringBuilder();
                Thread errDrainer = new Thread(() -> {
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(p.getErrorStream()))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            if (!line.contains("[Warning]") && !line.contains("Using a password")) {
                                stderrBuf.append(line).append("\n");
                            }
                        }
                    } catch (Exception ignored) {}
                }, "BackupStderr");
                errDrainer.setDaemon(true);
                errDrainer.start();

                int exit = p.waitFor();
                errDrainer.join(3000);

                listener.onStep("Verifying");

                if (exit != 0 || !out.exists() || out.length() == 0) {
                    String detail = stderrBuf.length() > 0 ? stderrBuf.toString().trim()
                            : "mysqldump exited with code " + exit;
                    out.delete();
                    listener.onComplete(false, detail);
                    return;
                }

                listener.onStep("Done");
                listener.onComplete(true, out.getAbsolutePath());

            } catch (Exception e) {
                listener.onComplete(false, "Backup failed: " + e.getMessage());
            }
        }, "BackupThread").start();
    }

    // ── Diagnostics ───────────────────────────────────────────────────────────

    /** Returns null on success, error message on failure. */
    public String testDbConnection() {
        DbConfig cfg = new DbConfig();
        if (cfg.isConfigured()) return cfg.testConnection();
        // Try hibernate defaults
        try {
            org.hibernate.cfg.Configuration hcfg = new org.hibernate.cfg.Configuration().configure();
            String url  = hcfg.getProperty("hibernate.connection.url");
            String user = hcfg.getProperty("hibernate.connection.username");
            String pass = hcfg.getProperty("hibernate.connection.password");
            java.sql.DriverManager.getConnection(url, user, pass).close();
            return null;
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    /**
     * Runs {@code mysqldump --version}.
     * Returns the version string on success, null if the tool is not usable.
     */
    public String testMysqldump() {
        String tool = findMysqldump();
        if (tool == null) return null;
        try {
            ProcessBuilder pb = new ProcessBuilder(tool, "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            int exit = p.waitFor();
            return exit == 0 ? out : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ── mysqldump discovery ───────────────────────────────────────────────────

    private String findMysqldump() {
        String configured = settings.get("MysqldumpPath", "");
        if (!configured.isEmpty()) {
            File f = new File(configured);
            if (f.exists()) return f.getAbsolutePath();
        }
        if (isInPath("mysqldump")) return "mysqldump";

        // Scan C:\Program Files\MySQL\MySQL Server X.X\bin\
        File mysqlRoot = new File("C:\\Program Files\\MySQL");
        if (mysqlRoot.isDirectory()) {
            File[] vers = mysqlRoot.listFiles();
            if (vers != null) {
                for (File ver : vers) {
                    File candidate = new File(ver, "bin\\mysqldump.exe");
                    if (candidate.exists()) return candidate.getAbsolutePath();
                }
            }
        }
        String[] known = {
            "C:\\xampp\\mysql\\bin\\mysqldump.exe",
            "C:\\wamp64\\bin\\mysql\\mysql8.0.27\\bin\\mysqldump.exe",
            "C:\\wamp\\bin\\mysql\\mysql8.0\\bin\\mysqldump.exe",
            "C:\\laragon\\bin\\mysql\\mysql-8.0.30-winx64\\bin\\mysqldump.exe"
        };
        for (String p : known) if (new File(p).exists()) return p;
        return null; // not found — caller should show an error
    }

    private boolean isInPath(String cmd) {
        try {
            new ProcessBuilder(cmd, "--version").redirectErrorStream(true).start().destroy();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String findMysql() {
        String configured = settings.get("MysqldumpPath", "");
        if (!configured.isEmpty()) {
            File f = new File(configured);
            if (f.exists()) {
                String name = System.getProperty("os.name").toLowerCase().contains("win") ? "mysql.exe" : "mysql";
                File mysql = new File(f.getParentFile(), name);
                if (mysql.exists()) return mysql.getAbsolutePath();
            }
        }
        if (isInPath("mysql")) return "mysql";
        File mysqlRoot = new File("C:\\Program Files\\MySQL");
        if (mysqlRoot.isDirectory()) {
            File[] vers = mysqlRoot.listFiles();
            if (vers != null) {
                for (File ver : vers) {
                    File candidate = new File(ver, "bin\\mysql.exe");
                    if (candidate.exists()) return candidate.getAbsolutePath();
                }
            }
        }
        String[] known = {
            "C:\\xampp\\mysql\\bin\\mysql.exe",
            "C:\\wamp64\\bin\\mysql\\mysql8.0.27\\bin\\mysql.exe",
            "C:\\wamp\\bin\\mysql\\mysql8.0\\bin\\mysql.exe",
            "C:\\laragon\\bin\\mysql\\mysql-8.0.30-winx64\\bin\\mysql.exe"
        };
        for (String p : known) if (new File(p).exists()) return p;
        return null;
    }

    // ── Restore ───────────────────────────────────────────────────────────────

    /** Restores the database from a mysqldump SQL file using the mysql client. */
    public void restore(String sqlFilePath, ProgressListener listener) {
        new Thread(() -> {
            try {
                listener.onStep("Preparing");
                String tool = findMysql();
                if (tool == null) {
                    listener.onComplete(false,
                            "mysql client not found. Set the mysqldump path so mysql can be located automatically.");
                    return;
                }
                DbConfig db = new DbConfig();
                String host = db.isConfigured() ? db.getHost()     : "localhost";
                String port = db.isConfigured() ? db.getPort()     : "3306";
                String user = db.isConfigured() ? db.getUser()     : "root";
                String pass = db.isConfigured() ? db.getPassword() : "1234";

                listener.onStep("Restoring");
                // Backups are created with --databases, so the SQL file includes CREATE DATABASE / USE.
                // Connect without specifying a database; the USE statement in the file selects it.
                ProcessBuilder pb = new ProcessBuilder(
                        tool,
                        "--user=" + user,
                        "--password=" + pass,
                        "--host=" + host,
                        "--port=" + port
                );
                pb.redirectInput(new java.io.File(sqlFilePath));
                pb.redirectErrorStream(false);
                Process p = pb.start();

                StringBuilder stderrBuf = new StringBuilder();
                Thread errDrainer = new Thread(() -> {
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(p.getErrorStream()))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            if (!line.contains("[Warning]") && !line.contains("Using a password")) {
                                stderrBuf.append(line).append("\n");
                            }
                        }
                    } catch (Exception ignored) {}
                }, "RestoreStderr");
                errDrainer.setDaemon(true);
                errDrainer.start();
                int exit = p.waitFor();
                errDrainer.join(3000);

                listener.onStep("Verifying");
                if (exit != 0) {
                    String detail = stderrBuf.length() > 0 ? stderrBuf.toString().trim()
                            : "mysql exited with code " + exit;
                    listener.onComplete(false, detail);
                    return;
                }
                listener.onComplete(true, "Database restored successfully.");
            } catch (Exception e) {
                listener.onComplete(false, "Restore failed: " + e.getMessage());
            }
        }, "RestoreThread").start();
    }

    // ── Auto backup schedule ──────────────────────────────────────────────────

    public boolean isAutoBackupEnabled()          { return settings.getBool("AutoBackupEnabled", false); }
    public void    setAutoBackupEnabled(boolean v) { settings.set("AutoBackupEnabled", String.valueOf(v)); }

    /** "DAILY" or a DayOfWeek name (e.g. "MONDAY"). */
    public String  getAutoBackupDay()             { return settings.get("AutoBackupDay", "DAILY"); }
    public void    setAutoBackupDay(String day)   { settings.set("AutoBackupDay", day); }

    /** Time in HH:mm 24-hour format (e.g. "02:00"). */
    public String  getAutoBackupTime()            { return settings.get("AutoBackupTime", "02:00"); }
    public void    setAutoBackupTime(String time) { settings.set("AutoBackupTime", time); }

    public void startAutoBackupScheduler() {
        if (autoBackupScheduler != null && !autoBackupScheduler.isShutdown()) return;
        autoBackupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AutoBackupScheduler");
            t.setDaemon(true);
            return t;
        });
        autoBackupScheduler.scheduleAtFixedRate(this::checkAndRunAutoBackup, 30, 60, TimeUnit.SECONDS);
    }

    private void checkAndRunAutoBackup() {
        if (!isAutoBackupEnabled()) return;
        LocalDateTime now = LocalDateTime.now();
        try {
            String[] parts = getAutoBackupTime().split(":");
            if (parts.length != 2) return;
            int h = Integer.parseInt(parts[0].trim());
            int m = Integer.parseInt(parts[1].trim());
            if (now.getHour() != h || now.getMinute() != m) return;
        } catch (NumberFormatException e) { return; }

        String dayConfig = getAutoBackupDay();
        if (!"DAILY".equals(dayConfig)) {
            try {
                DayOfWeek configured = DayOfWeek.valueOf(dayConfig);
                if (now.getDayOfWeek() != configured) return;
            } catch (IllegalArgumentException e) { return; }
        }

        // Prevent double-firing within the same minute
        if (lastAutoBackupRun != null
                && lastAutoBackupRun.toLocalDate().equals(now.toLocalDate())
                && lastAutoBackupRun.getHour()   == now.getHour()
                && lastAutoBackupRun.getMinute() == now.getMinute()) return;
        lastAutoBackupRun = now;

        backup("auto", new ProgressListener() {
            @Override public void onStep(String s) {}
            @Override public void onComplete(boolean ok, String msg) {}
        });
    }
}
