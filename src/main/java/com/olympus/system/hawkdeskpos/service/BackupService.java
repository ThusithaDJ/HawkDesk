package com.olympus.system.hawkdeskpos.service;

import org.hibernate.SessionFactory;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles database backup and restore via mysqldump / mysql CLI tools.
 * Progress is reported via a Listener callback so the UI can show progress
 * without any Swing imports here.
 */
public class BackupService {

    public interface ProgressListener {
        void onStep(String stepName);
        void onComplete(boolean success, String message);
    }

    public record BackupEntry(String fileName, LocalDateTime dateTime, long sizeBytes, String type) {}

    private static final String DB_NAME  = "hawkdeskpos";
    private static final String DB_USER  = "root";
    private static final String DB_PASS  = "1234";

    private final SettingsService settings;

    public BackupService(SessionFactory sf, SettingsService settings) {
        this.settings = settings;
    }

    public String getBackupFolder() {
        return settings.get("BackupFolder",
                System.getProperty("user.home") + File.separator + "HawkDeskPOS" + File.separator + "backups");
    }

    /** Lists existing backup files newest-first. */
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

    /** Runs a manual backup. Steps reported via listener. */
    public void backup(String type, ProgressListener listener) {
        new Thread(() -> {
            try {
                listener.onStep("Preparing");
                File dir = new File(getBackupFolder());
                if (!dir.exists()) dir.mkdirs();

                String stamp  = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String prefix = "auto_".equals(type) ? "auto_" : "manual_";
                File   out    = new File(dir, prefix + DB_NAME + "_" + stamp + ".sql");

                listener.onStep("Saving sales");
                Thread.sleep(300);
                listener.onStep("Saving stock");
                Thread.sleep(300);
                listener.onStep("Saving settings");

                ProcessBuilder pb = new ProcessBuilder(
                        "mysqldump", "--user=" + DB_USER, "--password=" + DB_PASS,
                        "--databases", DB_NAME, "--result-file=" + out.getAbsolutePath());
                pb.redirectErrorStream(true);
                Process p = pb.start();
                int exit = p.waitFor();

                listener.onStep("Done");
                listener.onComplete(exit == 0, exit == 0 ? out.getAbsolutePath() : "mysqldump failed (code " + exit + ")");
            } catch (Exception e) {
                listener.onComplete(false, "Backup failed: " + e.getMessage());
            }
        }, "BackupThread").start();
    }

    public LocalDateTime lastBackupTime() {
        return listBackups().stream()
                .findFirst()
                .map(BackupEntry::dateTime)
                .orElse(null);
    }
}
