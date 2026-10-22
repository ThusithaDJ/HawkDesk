package com.olympus.system.hawkdeskpos.frontend.admin;

import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.service.BackupService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Backup & Restore screen.
 * Can run standalone (with header/back button) or embedded inside SettingsPanel.
 */
public class BackupPanel extends JPanel {

    private static final Color BG    = new Color(0xF0, 0xF2, 0xF5);
    private static final Color TEXT2 = new Color(0x5A, 0x60, 0x70);
    private static final Color GREEN = new Color(0x2E, 0x7D, 0x32);
    private static final Color AMBER = new Color(0xE6, 0x51, 0x00);
    private static final Color RED   = new Color(0xC6, 0x28, 0x28);

    private static final String[] HIST_COLS = {"File Name", "Date/Time", "Size (bytes)", "Type"};
    // Steps reported by BackupService.backup()
    private static final String[] BACKUP_STEPS = {"Preparing", "Dumping database", "Verifying", "Done"};

    private final BackupService backupService;
    private final boolean       embedded;

    private JLabel            statusBanner;
    private JLabel            lastBackupLabel, totalBackupsLabel, storageUsedLabel;
    private DefaultTableModel historyModel;
    private JProgressBar      backupProgress;
    private JLabel            progressLabel;
    private JButton           backupBtn;
    private JTextField        backupFolderField;
    private JTextField        mysqldumpField;
    private JLabel            dbStatusLabel;
    private JLabel            toolStatusLabel;

    private JCheckBox         autoBackupEnabled;
    private JComboBox<String> autoBackupDay;
    private JTextField        autoBackupTime;
    private JPanel            autoBackupControlsPanel;
    private JButton           restoreBtn;
    private JLabel            restoreStatus;

    public BackupPanel(BackupService backupService) {
        this(backupService, false);
    }

    public BackupPanel(BackupService backupService, boolean embedded) {
        this.backupService = backupService;
        this.embedded      = embedded;
        setBackground(BG);
        setLayout(new BorderLayout());
        buildUI();
    }

    public void refresh() { loadDataAsync(); }

    // ── UI construction ───────────────────────────────────────────────────────

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        if (!embedded) {
            JPanel header = new JPanel(new BorderLayout());
            header.setOpaque(false);
            JLabel title = new JLabel("Backup & Restore");
            title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
            header.add(title, BorderLayout.WEST);
            JButton back = new JButton("← Back");
            back.addActionListener(e -> Home.navigate(Home.CARD_SETTINGS));
            JPanel hBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            hBtns.setOpaque(false);
            hBtns.add(back);
            header.add(hBtns, BorderLayout.EAST);
            root.add(header, BorderLayout.NORTH);
        }

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(buildStatusBanner());
        content.add(Box.createVerticalStrut(10));
        content.add(buildStatCells());
        content.add(Box.createVerticalStrut(10));
        content.add(buildConnectionToolsCard());
        content.add(Box.createVerticalStrut(10));

        JPanel split = new JPanel(new GridLayout(1, 2, 14, 0));
        split.setOpaque(false);
        split.setAlignmentX(Component.LEFT_ALIGNMENT);
        split.setMaximumSize(new Dimension(Integer.MAX_VALUE, 420));
        split.add(buildBackupActionsCard());
        split.add(buildHistoryCard());
        content.add(split);
        content.add(Box.createVerticalStrut(10));
        content.add(buildAutoBackupCard());
        content.add(Box.createVerticalStrut(10));
        content.add(buildRestoreCard());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        root.add(scroll, BorderLayout.CENTER);
        add(root);
        loadDataAsync();
    }

    private JLabel buildStatusBanner() {
        statusBanner = new JLabel("Checking backup status…", SwingConstants.CENTER);
        statusBanner.setOpaque(true);
        statusBanner.setBackground(AMBER);
        statusBanner.setForeground(Color.WHITE);
        statusBanner.setFont(statusBanner.getFont().deriveFont(Font.BOLD, 13f));
        statusBanner.setBorder(new EmptyBorder(10, 16, 10, 16));
        statusBanner.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusBanner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        return statusBanner;
    }

    private JPanel buildStatCells() {
        JPanel bar = new JPanel(new GridLayout(1, 3, 10, 0));
        bar.setOpaque(false);
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        bar.add(statCard("Last Backup",   "—")); lastBackupLabel   = valueLabel(bar);
        bar.add(statCard("Total Backups", "—")); totalBackupsLabel = valueLabel(bar);
        bar.add(statCard("Backup Folder", "—")); storageUsedLabel  = valueLabel(bar);
        return bar;
    }

    /** Card for DB connection test and mysqldump tool configuration. */
    private CardPanel buildConnectionToolsCard() {
        CardPanel c = new CardPanel(new BorderLayout(0, 10));
        ((JPanel)c).setBorder(new EmptyBorder(14, 14, 14, 14));
        ((JPanel)c).setAlignmentX(Component.LEFT_ALIGNMENT);
        ((JPanel)c).setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));

        JLabel t = new JLabel("DATABASE & TOOLS");
        t.setFont(t.getFont().deriveFont(Font.BOLD, 11f));
        t.setForeground(TEXT2);
        ((JPanel)c).add(t, BorderLayout.NORTH);

        JPanel inner = new JPanel();
        inner.setOpaque(false);
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));

        // ── DB Connection row ──
        JPanel dbRow = new JPanel(new BorderLayout(8, 0));
        dbRow.setOpaque(false);
        dbRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        dbRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        dbStatusLabel = new JLabel("Press 'Test Connection' to verify the database.");
        dbStatusLabel.setFont(dbStatusLabel.getFont().deriveFont(12f));
        dbStatusLabel.setForeground(TEXT2);

        JButton testDbBtn = new JButton("Test DB Connection");
        testDbBtn.setFont(testDbBtn.getFont().deriveFont(12f));
        testDbBtn.addActionListener(e -> testDbConnection());

        dbRow.add(dbStatusLabel, BorderLayout.CENTER);
        dbRow.add(testDbBtn,     BorderLayout.EAST);
        inner.add(dbRow);
        inner.add(Box.createVerticalStrut(10));

        // ── mysqldump path row ──
        JPanel dumpRow = new JPanel(new BorderLayout(6, 0));
        dumpRow.setOpaque(false);
        dumpRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        dumpRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        JLabel dumpLbl = new JLabel("mysqldump:");
        dumpLbl.setFont(dumpLbl.getFont().deriveFont(Font.BOLD, 12f));
        dumpLbl.setForeground(TEXT2);
        dumpLbl.setPreferredSize(new Dimension(90, 0));

        String autoDetected = backupService.getMysqldumpPath();
        mysqldumpField = new JTextField(autoDetected != null ? autoDetected : "Not found");
        mysqldumpField.setFont(mysqldumpField.getFont().deriveFont(12f));
        mysqldumpField.setEditable(false);

        JButton browseDumpBtn = new JButton("Browse…");
        browseDumpBtn.setFont(browseDumpBtn.getFont().deriveFont(12f));
        browseDumpBtn.addActionListener(e -> browseMysqldump());

        JButton testDumpBtn = new JButton("Test");
        testDumpBtn.setFont(testDumpBtn.getFont().deriveFont(12f));
        testDumpBtn.addActionListener(e -> testMysqldump());

        JPanel dumpBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        dumpBtns.setOpaque(false);
        dumpBtns.add(browseDumpBtn);
        dumpBtns.add(testDumpBtn);

        dumpRow.add(dumpLbl,     BorderLayout.WEST);
        dumpRow.add(mysqldumpField, BorderLayout.CENTER);
        dumpRow.add(dumpBtns,    BorderLayout.EAST);
        inner.add(dumpRow);
        inner.add(Box.createVerticalStrut(6));

        toolStatusLabel = new JLabel(" ");
        toolStatusLabel.setFont(toolStatusLabel.getFont().deriveFont(11f));
        toolStatusLabel.setForeground(TEXT2);
        toolStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        inner.add(toolStatusLabel);

        ((JPanel)c).add(inner, BorderLayout.CENTER);
        return c;
    }

    private CardPanel buildBackupActionsCard() {
        CardPanel c = new CardPanel(new BorderLayout(0, 12));
        ((JPanel)c).setBorder(new EmptyBorder(14, 14, 14, 14));

        JLabel t = new JLabel("MANUAL BACKUP");
        t.setFont(t.getFont().deriveFont(Font.BOLD, 11f));
        t.setForeground(TEXT2);
        ((JPanel)c).add(t, BorderLayout.NORTH);

        JPanel inner = new JPanel();
        inner.setOpaque(false);
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));

        backupBtn = new JButton("Start Backup Now");
        backupBtn.setBackground(GREEN);
        backupBtn.setForeground(Color.WHITE);
        backupBtn.setOpaque(true);
        backupBtn.setBorderPainted(false);
        backupBtn.setFont(backupBtn.getFont().deriveFont(Font.BOLD, 14f));
        backupBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        backupBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        backupBtn.addActionListener(e -> startBackup());

        backupProgress = new JProgressBar(0, BACKUP_STEPS.length);
        backupProgress.setValue(0);
        backupProgress.setStringPainted(true);
        backupProgress.setString("Ready");
        backupProgress.setAlignmentX(Component.LEFT_ALIGNMENT);
        backupProgress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

        progressLabel = new JLabel(" ");
        progressLabel.setFont(progressLabel.getFont().deriveFont(11f));
        progressLabel.setForeground(TEXT2);
        progressLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        inner.add(backupBtn);
        inner.add(Box.createVerticalStrut(12));
        inner.add(backupProgress);
        inner.add(Box.createVerticalStrut(4));
        inner.add(progressLabel);
        inner.add(Box.createVerticalStrut(12));

        // Backup folder row
        JPanel locationRow = new JPanel(new BorderLayout(6, 0));
        locationRow.setOpaque(false);
        locationRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        locationRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        JLabel locationLbl = new JLabel("Save to:");
        locationLbl.setFont(locationLbl.getFont().deriveFont(Font.BOLD, 12f));
        locationLbl.setForeground(TEXT2);
        locationLbl.setPreferredSize(new Dimension(60, 0));
        backupFolderField = new JTextField(backupService.getBackupFolder());
        backupFolderField.setFont(backupFolderField.getFont().deriveFont(12f));
        backupFolderField.setEditable(false);
        JButton browseBtn = new JButton("Browse…");
        browseBtn.setFont(browseBtn.getFont().deriveFont(12f));
        browseBtn.addActionListener(e -> browseBackupFolder());
        locationRow.add(locationLbl,      BorderLayout.WEST);
        locationRow.add(backupFolderField, BorderLayout.CENTER);
        locationRow.add(browseBtn,         BorderLayout.EAST);
        inner.add(locationRow);

        ((JPanel)c).add(inner, BorderLayout.CENTER);
        return c;
    }

    private CardPanel buildHistoryCard() {
        CardPanel c = new CardPanel(new BorderLayout(0, 0));
        ((JPanel)c).setBorder(new EmptyBorder(14, 14, 14, 14));

        JLabel t = new JLabel("BACKUP HISTORY");
        t.setFont(t.getFont().deriveFont(Font.BOLD, 11f));
        t.setForeground(TEXT2);
        t.setBorder(new EmptyBorder(0, 0, 10, 0));
        ((JPanel)c).add(t, BorderLayout.NORTH);

        historyModel = new DefaultTableModel(HIST_COLS, 0) {
            @Override public boolean isCellEditable(int r, int col) { return false; }
        };
        JTable table = new JTable(historyModel);
        table.setRowHeight(34);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.getTableHeader().setFont(table.getFont().deriveFont(Font.BOLD, 11f));
        table.getTableHeader().setBackground(new Color(0xF7, 0xF8, 0xFA));
        ((JPanel)c).add(new JScrollPane(table) {{ setBorder(null); }}, BorderLayout.CENTER);
        return c;
    }

    private CardPanel buildRestoreCard() {
        CardPanel c = new CardPanel(new BorderLayout(0, 10));
        ((JPanel)c).setBorder(new EmptyBorder(14, 14, 14, 14));
        ((JPanel)c).setAlignmentX(Component.LEFT_ALIGNMENT);
        ((JPanel)c).setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));

        JLabel t = new JLabel("RESTORE");
        t.setFont(t.getFont().deriveFont(Font.BOLD, 11f));
        t.setForeground(TEXT2);
        ((JPanel)c).add(t, BorderLayout.NORTH);

        JPanel inner = new JPanel(new BorderLayout(10, 0));
        inner.setOpaque(false);
        JLabel warning = new JLabel(
                "<html><b style='color:#C62828'>⚠ Warning:</b> "
                + "Restoring will overwrite ALL current data. This cannot be undone.</html>");
        warning.setFont(warning.getFont().deriveFont(12f));

        JPanel rightPanel = new JPanel();
        rightPanel.setOpaque(false);
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnRow.setOpaque(false);
        restoreBtn = new JButton("Choose Backup File…");
        restoreBtn.addActionListener(e -> restoreFromFile());
        btnRow.add(restoreBtn);
        restoreStatus = new JLabel(" ");
        restoreStatus.setFont(restoreStatus.getFont().deriveFont(11f));
        restoreStatus.setForeground(TEXT2);
        rightPanel.add(btnRow);
        rightPanel.add(restoreStatus);

        inner.add(warning, BorderLayout.CENTER);
        inner.add(rightPanel, BorderLayout.EAST);
        ((JPanel)c).add(inner, BorderLayout.CENTER);
        return c;
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private void loadDataAsync() {
        new SwingWorker<List<BackupService.BackupEntry>, Void>() {
            private LocalDateTime lastBackup;
            @Override protected List<BackupService.BackupEntry> doInBackground() {
                lastBackup = backupService.lastBackupTime();
                return backupService.listBackups();
            }
            @Override protected void done() {
                try {
                    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                    if (lastBackup != null) {
                        lastBackupLabel.setText(lastBackup.format(fmt));
                        long hoursAgo = java.time.Duration.between(lastBackup, LocalDateTime.now()).toHours();
                        boolean old = hoursAgo > 24;
                        statusBanner.setText(old
                                ? "⚠ Last backup was over 24 hours ago — backup recommended"
                                : "✓ Backup is up to date (last: " + lastBackup.format(fmt) + ")");
                        statusBanner.setBackground(old ? AMBER : GREEN);
                    } else {
                        lastBackupLabel.setText("Never");
                        statusBanner.setText("⚠ No backups found — backup recommended");
                        statusBanner.setBackground(RED);
                    }

                    List<BackupService.BackupEntry> entries = get();
                    totalBackupsLabel.setText(String.valueOf(entries.size()));
                    storageUsedLabel.setText(backupService.getBackupFolder());

                    historyModel.setRowCount(0);
                    for (BackupService.BackupEntry e : entries) {
                        historyModel.addRow(new Object[]{
                                e.fileName(),
                                e.dateTime() != null ? e.dateTime().format(fmt) : "—",
                                e.sizeBytes(),
                                e.type()
                        });
                    }
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void startBackup() {
        backupBtn.setEnabled(false);
        backupProgress.setValue(0);
        backupProgress.setString("Starting…");
        progressLabel.setText("Starting backup…");

        backupService.backup("manual", new BackupService.ProgressListener() {
            @Override public void onStep(String stepName) {
                SwingUtilities.invokeLater(() -> {
                    int idx = java.util.Arrays.asList(BACKUP_STEPS).indexOf(stepName);
                    backupProgress.setValue(idx >= 0 ? idx + 1 : backupProgress.getValue() + 1);
                    backupProgress.setString(stepName);
                    progressLabel.setText(stepName + "…");
                });
            }
            @Override public void onComplete(boolean success, String message) {
                SwingUtilities.invokeLater(() -> {
                    backupProgress.setValue(success ? BACKUP_STEPS.length : 0);
                    backupProgress.setString(success ? "Done ✓" : "Failed");
                    progressLabel.setText(success ? "Saved to: " + message : message);
                    backupBtn.setEnabled(true);
                    if (success) {
                        loadDataAsync();
                        JOptionPane.showMessageDialog(BackupPanel.this,
                                "Backup saved to:\n" + message,
                                "Backup Complete", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(BackupPanel.this,
                                "Backup failed:\n" + message,
                                "Backup Error", JOptionPane.ERROR_MESSAGE);
                    }
                });
            }
        });
    }

    private void testDbConnection() {
        dbStatusLabel.setText("Testing…");
        dbStatusLabel.setForeground(TEXT2);
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() { return backupService.testDbConnection(); }
            @Override protected void done() {
                try {
                    String err = get();
                    if (err == null) {
                        dbStatusLabel.setText("✓ Database connection is OK");
                        dbStatusLabel.setForeground(GREEN);
                    } else {
                        dbStatusLabel.setText("✗ " + shorten(err));
                        dbStatusLabel.setForeground(RED);
                    }
                } catch (Exception ex) {
                    dbStatusLabel.setText("✗ " + ex.getMessage());
                    dbStatusLabel.setForeground(RED);
                }
            }
        }.execute();
    }

    private void testMysqldump() {
        toolStatusLabel.setText("Testing mysqldump…");
        toolStatusLabel.setForeground(TEXT2);
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() { return backupService.testMysqldump(); }
            @Override protected void done() {
                try {
                    String ver = get();
                    if (ver != null) {
                        toolStatusLabel.setText("✓ " + ver);
                        toolStatusLabel.setForeground(GREEN);
                    } else {
                        toolStatusLabel.setText("✗ mysqldump not found or not executable at the configured path");
                        toolStatusLabel.setForeground(RED);
                    }
                } catch (Exception ex) {
                    toolStatusLabel.setText("✗ " + ex.getMessage());
                    toolStatusLabel.setForeground(RED);
                }
            }
        }.execute();
    }

    private void browseMysqldump() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Select mysqldump executable");
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            String path = fc.getSelectedFile().getAbsolutePath();
            backupService.setMysqldumpPath(path);
            mysqldumpField.setText(path);
            toolStatusLabel.setText("Path saved — click Test to verify.");
            toolStatusLabel.setForeground(TEXT2);
        }
    }

    private void browseBackupFolder() {
        JFileChooser fc = new JFileChooser(backupService.getBackupFolder());
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Select Backup Folder");
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            String chosen = fc.getSelectedFile().getAbsolutePath();
            backupService.setBackupFolder(chosen);
            backupFolderField.setText(chosen);
            storageUsedLabel.setText(chosen);
        }
    }

    private void restoreFromFile() {
        JFileChooser fc = new JFileChooser(backupService.getBackupFolder());
        fc.setDialogTitle("Select Backup SQL File");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("SQL files", "sql"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        String path = fc.getSelectedFile().getAbsolutePath();

        int confirm = JOptionPane.showConfirmDialog(this,
                "<html><b>⚠ This will overwrite ALL current database data and cannot be undone.</b><br><br>"
                + "File: " + path + "<br><br>Continue?</html>",
                "Confirm Restore", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        restoreBtn.setEnabled(false);
        restoreStatus.setText("Starting restore…");
        restoreStatus.setForeground(TEXT2);

        backupService.restore(path, new BackupService.ProgressListener() {
            @Override public void onStep(String step) {
                SwingUtilities.invokeLater(() -> restoreStatus.setText(step + "…"));
            }
            @Override public void onComplete(boolean success, String message) {
                SwingUtilities.invokeLater(() -> {
                    restoreBtn.setEnabled(true);
                    restoreStatus.setText(message);
                    restoreStatus.setForeground(success ? GREEN : RED);
                    if (success) {
                        JOptionPane.showMessageDialog(BackupPanel.this,
                                "Database restored successfully.\nPlease restart the application.",
                                "Restore Complete", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(BackupPanel.this,
                                "Restore failed:\n" + message,
                                "Restore Error", JOptionPane.ERROR_MESSAGE);
                    }
                });
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CardPanel statCard(String label, String value) {
        CardPanel c = new CardPanel(new BorderLayout(0, 4));
        c.setBorder(new EmptyBorder(12, 14, 12, 14));
        JLabel l = new JLabel(label);
        l.setFont(l.getFont().deriveFont(12f));
        l.setForeground(TEXT2);
        JLabel v = new JLabel(value);
        v.setFont(v.getFont().deriveFont(Font.BOLD, 13f));
        c.add(l, BorderLayout.NORTH);
        c.add(v, BorderLayout.CENTER);
        return c;
    }

    private JLabel valueLabel(JPanel parent) {
        CardPanel card = (CardPanel) parent.getComponent(parent.getComponentCount() - 1);
        return (JLabel) card.getComponent(1);
    }

    private String shorten(String msg) {
        if (msg == null) return "Unknown error";
        if (msg.contains("Communications link failure") || msg.contains("Connection refused"))
            return "Cannot reach MySQL server";
        if (msg.contains("Access denied")) return "Access denied — check credentials";
        return msg.length() > 80 ? msg.substring(0, 80) + "…" : msg;
    }

    // ── Auto backup card ───────────────────────────────────────────────────────

    private CardPanel buildAutoBackupCard() {
        CardPanel c = new CardPanel(new BorderLayout(0, 10));
        ((JPanel)c).setBorder(new EmptyBorder(14, 14, 14, 14));
        ((JPanel)c).setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel title = new JLabel("AUTO BACKUP");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 11f));
        title.setForeground(TEXT2);
        ((JPanel)c).add(title, BorderLayout.NORTH);

        autoBackupEnabled = new JCheckBox("Enable automatic backups");
        autoBackupEnabled.setSelected(backupService.isAutoBackupEnabled());
        autoBackupEnabled.setOpaque(false);
        autoBackupEnabled.setFont(autoBackupEnabled.getFont().deriveFont(13f));
        autoBackupEnabled.addActionListener(e -> {
            backupService.setAutoBackupEnabled(autoBackupEnabled.isSelected());
            updateAutoBackupControls();
        });

        JLabel dayLbl = new JLabel("Run on:");
        dayLbl.setFont(dayLbl.getFont().deriveFont(Font.BOLD, 12f));
        dayLbl.setForeground(TEXT2);
        String[] days = {"Daily", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        autoBackupDay = new JComboBox<>(days);
        String storedDay = backupService.getAutoBackupDay();
        autoBackupDay.setSelectedItem("DAILY".equals(storedDay) ? "Daily"
                : Character.toUpperCase(storedDay.charAt(0)) + storedDay.substring(1).toLowerCase());
        autoBackupDay.addActionListener(e -> {
            Object sel = autoBackupDay.getSelectedItem();
            if (sel != null)
                backupService.setAutoBackupDay("Daily".equals(sel.toString()) ? "DAILY" : sel.toString().toUpperCase());
        });

        JLabel timeLbl = new JLabel("At:");
        timeLbl.setFont(timeLbl.getFont().deriveFont(Font.BOLD, 12f));
        timeLbl.setForeground(TEXT2);
        autoBackupTime = new JTextField(backupService.getAutoBackupTime(), 7);
        autoBackupTime.setFont(autoBackupTime.getFont().deriveFont(13f));
        JLabel timeFmt = new JLabel("(HH:mm, 24-hr)");
        timeFmt.setFont(timeFmt.getFont().deriveFont(11f));
        timeFmt.setForeground(TEXT2);
        JButton saveBtn = new JButton("Save Schedule");
        saveBtn.addActionListener(e -> saveAutoBackupSchedule());

        autoBackupControlsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        autoBackupControlsPanel.setOpaque(false);
        autoBackupControlsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        autoBackupControlsPanel.add(dayLbl);
        autoBackupControlsPanel.add(autoBackupDay);
        autoBackupControlsPanel.add(timeLbl);
        autoBackupControlsPanel.add(autoBackupTime);
        autoBackupControlsPanel.add(timeFmt);
        autoBackupControlsPanel.add(saveBtn);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        autoBackupEnabled.setAlignmentX(Component.LEFT_ALIGNMENT);
        autoBackupControlsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(autoBackupEnabled);
        body.add(autoBackupControlsPanel);

        ((JPanel)c).add(body, BorderLayout.CENTER);
        updateAutoBackupControls();
        return c;
    }

    private void saveAutoBackupSchedule() {
        String time = autoBackupTime.getText().trim();
        if (!time.matches("\\d{1,2}:\\d{2}")) {
            JOptionPane.showMessageDialog(this, "Invalid time format. Please use HH:mm (e.g. 02:00).",
                    "Invalid Input", JOptionPane.ERROR_MESSAGE);
            return;
        }
        backupService.setAutoBackupTime(time);
        JOptionPane.showMessageDialog(this, "Auto backup schedule saved.", "Saved",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void updateAutoBackupControls() {
        boolean enabled = autoBackupEnabled != null && autoBackupEnabled.isSelected();
        if (autoBackupControlsPanel != null)
            for (Component comp : autoBackupControlsPanel.getComponents())
                comp.setEnabled(enabled);
        if (autoBackupDay  != null) autoBackupDay.setEnabled(enabled);
        if (autoBackupTime != null) autoBackupTime.setEnabled(enabled);
    }
}
