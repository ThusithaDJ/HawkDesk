package com.olympus.system.hawkdeskpos.frontend.admin;

import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.frontend.components.ConfirmDialog;
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
 */
public class BackupPanel extends JPanel {

    private static final Color BG    = new Color(0xF0, 0xF2, 0xF5);
    private static final Color TEXT2 = new Color(0x5A, 0x60, 0x70);
    private static final Color NAVY  = new Color(0x1E, 0x3A, 0x5F);
    private static final Color GREEN = new Color(0x2E, 0x7D, 0x32);
    private static final Color AMBER = new Color(0xE6, 0x51, 0x00);
    private static final Color RED   = new Color(0xC6, 0x28, 0x28);

    private final BackupService backupService;

    private JLabel statusBanner;
    private JLabel lastBackupLabel, totalBackupsLabel, storageUsedLabel;
    private DefaultTableModel historyModel;
    private JProgressBar backupProgress;
    private JLabel progressLabel;
    private JButton backupBtn;

    private static final String[] HIST_COLS = {"File Name", "Date/Time", "Size (bytes)", "Type"};

    public BackupPanel(BackupService backupService) {
        this.backupService = backupService;
        setBackground(BG);
        setLayout(new BorderLayout());
        buildUI();
    }

    public void refresh() { loadDataAsync(); }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

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

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(buildStatusBanner());
        content.add(Box.createVerticalStrut(10));
        content.add(buildStatCells());
        content.add(Box.createVerticalStrut(10));

        JPanel split = new JPanel(new GridLayout(1, 2, 14, 0));
        split.setOpaque(false);
        split.setAlignmentX(Component.LEFT_ALIGNMENT);
        split.setMaximumSize(new Dimension(Integer.MAX_VALUE, 400));
        split.add(buildBackupActionsCard());
        split.add(buildHistoryCard());
        content.add(split);
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

        bar.add(statCard("Last Backup",   "—"));
        lastBackupLabel   = valueLabel(bar);
        bar.add(statCard("Total Backups", "—"));
        totalBackupsLabel = valueLabel(bar);
        bar.add(statCard("Backup Folder", "—"));
        storageUsedLabel  = valueLabel(bar);
        return bar;
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

        backupProgress = new JProgressBar(0, 5);
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

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(null);
        ((JPanel)c).add(scroll, BorderLayout.CENTER);
        return c;
    }

    private CardPanel buildRestoreCard() {
        CardPanel c = new CardPanel(new BorderLayout(0, 10));
        ((JPanel)c).setBorder(new EmptyBorder(14, 14, 14, 14));
        ((JPanel)c).setAlignmentX(Component.LEFT_ALIGNMENT);
        ((JPanel)c).setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));

        JLabel t = new JLabel("RESTORE");
        t.setFont(t.getFont().deriveFont(Font.BOLD, 11f));
        t.setForeground(TEXT2);
        ((JPanel)c).add(t, BorderLayout.NORTH);

        JPanel inner = new JPanel(new BorderLayout(10, 0));
        inner.setOpaque(false);
        JLabel warning = new JLabel("<html><b style='color:#C62828'>⚠ Warning:</b> "
                + "Restoring will overwrite ALL current data. This cannot be undone.</html>");
        warning.setFont(warning.getFont().deriveFont(12f));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnRow.setOpaque(false);
        JButton chooseFile = new JButton("Choose Backup File…");
        chooseFile.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "Restore via: mysql -u root -p hawkdeskpos < backup.sql", "Restore Instructions",
                JOptionPane.INFORMATION_MESSAGE));
        btnRow.add(chooseFile);

        inner.add(warning, BorderLayout.CENTER);
        inner.add(btnRow,  BorderLayout.EAST);
        ((JPanel)c).add(inner, BorderLayout.CENTER);
        return c;
    }

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

    private void startBackup() {
        backupBtn.setEnabled(false);
        backupProgress.setValue(0);
        backupProgress.setString("Starting…");
        final int[] step = {0};
        String[] steps = {"Preparing", "Saving sales", "Saving stock", "Saving settings", "Done"};

        backupService.backup("manual", new BackupService.ProgressListener() {
            @Override public void onStep(String stepName) {
                SwingUtilities.invokeLater(() -> {
                    int idx = java.util.Arrays.asList(steps).indexOf(stepName);
                    backupProgress.setValue(idx >= 0 ? idx + 1 : step[0]++);
                    backupProgress.setString(stepName);
                    progressLabel.setText(stepName + "…");
                });
            }
            @Override public void onComplete(boolean success, String message) {
                SwingUtilities.invokeLater(() -> {
                    backupProgress.setValue(5);
                    backupProgress.setString(success ? "Done ✓" : "Failed");
                    progressLabel.setText(message);
                    backupBtn.setEnabled(true);
                    if (success) {
                        loadDataAsync();
                        JOptionPane.showMessageDialog(BackupPanel.this, "Backup saved to:\n" + message,
                                "Backup Complete", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(BackupPanel.this, "Backup failed:\n" + message,
                                "Backup Error", JOptionPane.ERROR_MESSAGE);
                    }
                });
            }
        });
    }

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
}
