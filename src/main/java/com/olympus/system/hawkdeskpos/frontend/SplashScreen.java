package com.olympus.system.hawkdeskpos.frontend;

import com.olympus.system.hawkdeskpos.frontend.components.FontManager;
import com.olympus.system.hawkdeskpos.service.SettingsService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Startup splash screen shown before the main window.
 *
 * Checks performed (with progress feedback):
 *   1. Configuration file presence
 *   2. Raw JDBC database connectivity
 *   3. Core and extended table existence
 *   4. Trial licence status
 *
 * Critical failures (DB unreachable, trial expired) block launch and show
 * an Exit button. Warnings (missing extended tables, trial near expiry) are
 * shown but do not block.
 */
public class SplashScreen extends JWindow {

    // ── App metadata ──────────────────────────────────────────────────────────
    private static final String APP_NAME     = "HawkDesk POS";
    private static final String APP_VERSION  = "v2.0";
    private static final String CREATOR      = "Olympus Systems";
    private static final String CREATOR_INFO = "support@olympussystems.lk";

    // ── Palette ───────────────────────────────────────────────────────────────
    private static final Color BG_TOP    = new Color(0x14, 0x1F, 0x33);
    private static final Color BG_BOTTOM = new Color(0x0B, 0x12, 0x20);
    private static final Color LOG_BG    = new Color(0x0F, 0x1A, 0x2D);
    private static final Color BORDER_C  = new Color(0x28, 0x3C, 0x58);
    private static final Color ACCENT    = new Color(0x3A, 0xA8, 0xFF);
    private static final Color TEXT_MAIN = Color.WHITE;
    private static final Color TEXT_DIM  = new Color(0x7A, 0x8E, 0xAA);
    static final Color COL_OK   = new Color(0x4C, 0xD9, 0x7E);
    static final Color COL_WARN = new Color(0xFF, 0xC5, 0x40);
    static final Color COL_ERR  = new Color(0xFF, 0x5C, 0x5C);
    static final Color COL_INFO = new Color(0x8A, 0x9E, 0xBA);

    // ── Tables to verify ─────────────────────────────────────────────────────
    private static final String[] CORE_TABLES = {
        "brands", "category", "item", "stock", "grninfo", "grn",
        "invoiceinfo", "invoice", "return", "employees", "user_permissions"
    };
    private static final String[] EXTENDED_TABLES = {
        "audit_log", "stock_adjustment", "item_batch",
        "customer", "uom_preset", "invoice_history",
        "cash_accounts", "income", "expense", "cash_transaction"
    };

    // ── UI components ─────────────────────────────────────────────────────────
    private JProgressBar progressBar;
    private JLabel       statusLabel;
    private DefaultListModel<Object[]> logModel;
    private JList<Object[]>            logList;
    private JButton                    exitButton;

    private volatile boolean hasErrors = false;

    public SplashScreen() {
        setSize(540, 460);
        setLocationRelativeTo(null);
        buildUI();
        try {
            setShape(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 20, 20));
        } catch (UnsupportedOperationException ignored) {}
    }

    // ── UI construction ───────────────────────────────────────────────────────

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0, BG_TOP, 0, getHeight(), BG_BOTTOM));
                g2.fillRect(0, 0, getWidth(), getHeight());
                // Subtle bottom accent stripe
                g2.setColor(ACCENT);
                g2.fillRect(0, getHeight() - 3, getWidth(), 3);
                g2.dispose();
            }
        };
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(28, 32, 18, 32));

        root.add(buildHeader(),   BorderLayout.NORTH);
        root.add(buildCenter(),   BorderLayout.CENTER);
        root.add(buildFooter(),   BorderLayout.SOUTH);

        setContentPane(root);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        header.setBorder(new EmptyBorder(0, 0, 16, 0));

        try {
            ImageIcon raw = new ImageIcon(getClass().getResource("/images/icons/hawkpos-icon-64x64.png"));
            Image img = raw.getImage().getScaledInstance(64, 64, Image.SCALE_SMOOTH);
            JLabel logo = new JLabel(new ImageIcon(img));
            logo.setBorder(new EmptyBorder(0, 0, 0, 20));
            header.add(logo);
        } catch (Exception ignored) {}

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));

        JLabel nameLabel = label(APP_NAME, TEXT_MAIN, Font.BOLD, 28);
        JLabel verLabel  = label(APP_VERSION, ACCENT, Font.PLAIN, 13);

        // horizontal divider
        JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
        sep.setForeground(BORDER_C);
        sep.setMaximumSize(new Dimension(Short.MAX_VALUE, 1));

        JLabel creatorLabel = label("Developed by " + CREATOR, TEXT_DIM, Font.PLAIN, 12);
        JLabel contactLabel = label(CREATOR_INFO, TEXT_DIM, Font.PLAIN, 11);

        info.add(nameLabel);
        info.add(Box.createVerticalStrut(3));
        info.add(verLabel);
        info.add(Box.createVerticalStrut(8));
        info.add(sep);
        info.add(Box.createVerticalStrut(6));
        info.add(creatorLabel);
        info.add(contactLabel);

        header.add(info);
        header.add(Box.createHorizontalGlue());
        return header;
    }

    private JPanel buildCenter() {
        // ── Log scroll ────────────────────────────────────────────────────────
        logModel = new DefaultListModel<>();
        logList  = new JList<>(logModel);
        logList.setBackground(LOG_BG);
        logList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            Object[] row = (Object[]) value;
            JLabel lbl = new JLabel((String) row[0]);
            lbl.setForeground((Color) row[1]);
            lbl.setBackground(LOG_BG);
            lbl.setOpaque(true);
            lbl.setFont(new Font(FontManager.FONT_FAMILY, Font.PLAIN, 12));
            lbl.setBorder(new EmptyBorder(3, 8, 3, 8));
            return lbl;
        });

        JScrollPane scroll = new JScrollPane(logList,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER_C));
        scroll.setBackground(LOG_BG);
        scroll.getViewport().setBackground(LOG_BG);

        // ── Progress bar ──────────────────────────────────────────────────────
        progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        progressBar.setStringPainted(false);
        progressBar.setForeground(ACCENT);
        progressBar.setBackground(new Color(0x1C, 0x2C, 0x44));
        progressBar.setBorderPainted(false);
        progressBar.setPreferredSize(new Dimension(0, 5));
        progressBar.setMaximumSize(new Dimension(Short.MAX_VALUE, 5));

        statusLabel = label("Starting up...", TEXT_DIM, Font.PLAIN, 11);

        exitButton = new JButton("Exit Application");
        exitButton.setVisible(false);
        exitButton.setBackground(COL_ERR);
        exitButton.setForeground(Color.WHITE);
        exitButton.setFocusPainted(false);
        exitButton.setBorderPainted(false);
        exitButton.setFont(new Font(FontManager.FONT_FAMILY, Font.BOLD, 12));
        exitButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        exitButton.addActionListener(e -> System.exit(1));

        JPanel exitWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        exitWrap.setOpaque(false);
        exitWrap.add(exitButton);

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.add(Box.createVerticalStrut(12));
        bottom.add(progressBar);
        bottom.add(Box.createVerticalStrut(6));
        bottom.add(statusLabel);
        bottom.add(Box.createVerticalStrut(8));
        bottom.add(exitWrap);

        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(scroll, BorderLayout.CENTER);
        center.add(bottom, BorderLayout.SOUTH);
        return center;
    }

    private JPanel buildFooter() {
        JLabel footer = label("© 2025 Olympus Systems · All rights reserved.", new Color(0x3E, 0x52, 0x6A), Font.PLAIN, 10);
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 6));
        panel.setOpaque(false);
        panel.add(footer);
        return panel;
    }

    private JLabel label(String text, Color fg, int style, int size) {
        JLabel l = new JLabel(text);
        l.setForeground(fg);
        l.setFont(new Font(FontManager.FONT_FAMILY, style, size));
        return l;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Runs all startup checks in a background thread. Calls {@code onSuccess}
     * on the EDT after a short delay if all critical checks pass; otherwise
     * reveals the Exit button.
     */
    public void startChecks(Runnable onSuccess) {
        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() {
                runAllChecks();
                return !hasErrors;
            }

            @Override
            protected void done() {
                try {
                    boolean ok = get();
                    if (ok) {
                        updateProgress(100, "Launching " + APP_NAME + "...");
                        Timer t = new Timer(700, e -> { dispose(); onSuccess.run(); });
                        t.setRepeats(false);
                        t.start();
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            statusLabel.setText("Startup failed — resolve the errors above and restart.");
                            statusLabel.setForeground(COL_ERR);
                            exitButton.setVisible(true);
                        });
                    }
                } catch (Exception ignored) {}
            }
        };
        worker.execute();
    }

    // ── Check orchestration ───────────────────────────────────────────────────

    private void runAllChecks() {
        updateProgress(5,  "Checking configuration file...");
        checkConfig();
        pause(250);

        updateProgress(20, "Connecting to database...");
        pause(150);
        Connection conn = checkDbConnection();

        if (conn != null) {
            updateProgress(50, "Verifying database tables...");
            pause(150);
            checkTables(conn);
            closeSilently(conn);
        }

        updateProgress(80, "Verifying trial licence...");
        pause(300);
        checkTrial();

        updateProgress(95, "Preparing application...");
        pause(350);
    }

    // ── Individual checks ─────────────────────────────────────────────────────

    private void checkConfig() {
        File configFile = new File(
            System.getProperty("user.home") + File.separator +
            "HawkDeskPOS" + File.separator + "config.cnf"
        );
        if (configFile.exists()) {
            log("✓  Configuration file loaded from " + configFile.getParent(), COL_OK);
        } else {
            log("ℹ  Config file not found — default settings will be used", COL_INFO);
        }
    }

    private Connection checkDbConnection() {
        try {
            org.hibernate.cfg.Configuration cfg =
                    new org.hibernate.cfg.Configuration().configure();
            String url  = cfg.getProperty("hibernate.connection.url");
            String user = cfg.getProperty("hibernate.connection.username");
            String pass = cfg.getProperty("hibernate.connection.password");

            if (url == null || url.isBlank()) {
                log("✗  Database URL not set in hibernate.cfg.xml", COL_ERR);
                hasErrors = true;
                return null;
            }

            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(url, user, pass);
            // Report which DB we connected to
            String dbName = conn.getCatalog();
            log("✓  Connected to database '" + dbName + "' on " + conn.getMetaData().getURL().split("\\?")[0].replaceFirst("jdbc:mysql://", ""), COL_OK);
            return conn;

        } catch (ClassNotFoundException e) {
            log("✗  MySQL JDBC driver missing from classpath", COL_ERR);
            hasErrors = true;
        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("Access denied")) {
                log("✗  Database access denied — check username / password in hibernate.cfg.xml", COL_ERR);
            } else if (msg != null && (msg.contains("Connection refused") || msg.contains("Communications link failure"))) {
                log("✗  Cannot reach MySQL — is the server running on localhost:3306?", COL_ERR);
            } else {
                log("✗  Database connection failed: " + e.getMessage(), COL_ERR);
            }
            hasErrors = true;
        } catch (Exception e) {
            log("✗  Unexpected error: " + e.getMessage(), COL_ERR);
            hasErrors = true;
        }
        return null;
    }

    private void checkTables(Connection conn) {
        try {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();

            List<String> missingCore = missingFrom(meta, catalog, CORE_TABLES);
            List<String> missingExt  = missingFrom(meta, catalog, EXTENDED_TABLES);

            if (missingCore.isEmpty() && missingExt.isEmpty()) {
                int total = CORE_TABLES.length + EXTENDED_TABLES.length;
                log("✓  All " + total + " database tables verified", COL_OK);
                return;
            }

            if (missingCore.isEmpty()) {
                log("✓  Core tables verified (" + CORE_TABLES.length + " tables)", COL_OK);
            } else {
                // Brand-new database — Flyway will create everything
                log("ℹ  Fresh database detected — Flyway will run migrations on launch", COL_INFO);
            }

            if (!missingExt.isEmpty()) {
                log("ℹ  " + missingExt.size() + " pending migration(s) will apply on launch", COL_INFO);
            }

        } catch (Exception e) {
            log("⚠  Table check error: " + e.getMessage(), COL_WARN);
        }
    }

    private void checkTrial() {
        try {
            SettingsService s = new SettingsService(null);
            s.ensureTrialStartDate();

            if (s.isTrialExpired()) {
                log("✗  Trial has expired — contact Olympus Systems to activate a licence", COL_ERR);
                log("   " + CREATOR_INFO, COL_ERR);
                hasErrors = true;
            } else {
                long remaining = s.trialDaysRemaining();
                long total     = s.trialTotalDays();
                if (remaining <= 3) {
                    log("⚠  Trial expires in " + remaining + " day(s) — contact " + CREATOR_INFO, COL_WARN);
                } else {
                    log("✓  Trial licence active — " + remaining + " of " + total + " day(s) remaining", COL_OK);
                }
            }
        } catch (Exception e) {
            log("⚠  Could not verify trial status: " + e.getMessage(), COL_WARN);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<String> missingFrom(DatabaseMetaData meta, String catalog, String[] tables) throws SQLException {
        List<String> missing = new ArrayList<>();
        for (String tbl : tables) {
            try (ResultSet rs = meta.getTables(catalog, null, tbl, new String[]{"TABLE"})) {
                if (!rs.next()) missing.add(tbl);
            }
        }
        return missing;
    }

    private void log(String message, Color color) {
        SwingUtilities.invokeLater(() -> {
            logModel.addElement(new Object[]{message, color});
            logList.ensureIndexIsVisible(logModel.size() - 1);
        });
    }

    private void updateProgress(int value, String message) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(value);
            statusLabel.setText(message);
        });
    }

    private void pause(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    private void closeSilently(Connection conn) {
        try { if (conn != null && !conn.isClosed()) conn.close(); }
        catch (Exception ignored) {}
    }
}
