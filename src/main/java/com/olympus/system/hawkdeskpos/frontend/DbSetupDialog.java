package com.olympus.system.hawkdeskpos.frontend;

import com.olympus.system.hawkdeskpos.util.DbConfig;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Modal dialog shown when no database connection is configured or the
 * existing connection fails. Lets the user discover available databases,
 * test the connection, and save the config.
 */
public class DbSetupDialog extends JDialog {

    private static final Color BG    = new Color(0xF7, 0xF8, 0xFA);
    private static final Color NAVY  = new Color(0x1E, 0x3A, 0x5F);
    private static final Color GREEN = new Color(0x2E, 0x7D, 0x32);
    private static final Color RED   = new Color(0xC6, 0x28, 0x28);
    private static final Color AMBER = new Color(0xE6, 0x51, 0x00);
    private static final Color TEXT2 = new Color(0x5A, 0x60, 0x70);

    private JTextField    hostField, portField, userField;
    private JPasswordField passField;
    private JComboBox<String> dbCombo;
    private JLabel        statusLabel;
    private JButton       saveBtn;
    private boolean       saved = false;

    public DbSetupDialog(Window parent) {
        super(parent, "Database Setup", ModalityType.APPLICATION_MODAL);
        setSize(500, 430);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setResizable(false);
        buildUI();
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(24, 28, 20, 28));

        root.add(buildHeader(),  BorderLayout.NORTH);
        root.add(buildForm(),    BorderLayout.CENTER);
        root.add(buildBottom(),  BorderLayout.SOUTH);

        setContentPane(root);
    }

    private JPanel buildHeader() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Database Setup Required");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setForeground(NAVY);

        JLabel sub = new JLabel(
            "<html><font color='#5A6070'>Enter your MySQL server details to connect.<br>"
            + "Use <b>Discover Databases</b> to list databases on the server.</font></html>");
        sub.setFont(sub.getFont().deriveFont(13f));

        p.add(title);
        p.add(Box.createVerticalStrut(6));
        p.add(sub);
        return p;
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(new Color(0xD0, 0xD5, 0xDD)),
                        "Server Connection",
                        TitledBorder.LEFT, TitledBorder.TOP,
                        new Font("SansSerif", Font.BOLD, 12), TEXT2),
                new EmptyBorder(8, 12, 8, 12)));

        DbConfig saved = new DbConfig();
        hostField = tf(saved.isConfigured() ? saved.getHost() : "localhost", 14);
        portField = tf(saved.isConfigured() ? saved.getPort() : "3306",     6);
        userField = tf(saved.isConfigured() ? saved.getUser() : "root",     14);
        passField = new JPasswordField(saved.isConfigured() ? saved.getPassword() : "", 14);
        dbCombo   = new JComboBox<>();
        dbCombo.setEditable(true);
        String savedName = saved.isConfigured() ? saved.getName() : "hawkdeskpos";
        dbCombo.addItem(savedName);
        dbCombo.setSelectedItem(savedName);

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 4, 6, 8);
        c.anchor = GridBagConstraints.WEST;
        c.fill   = GridBagConstraints.HORIZONTAL;

        // Row 0: Host | Port
        addLbl(form, c, 0, 0, "Host:");
        c.gridx = 1; c.gridy = 0; c.weightx = 1.0; form.add(hostField, c); c.weightx = 0;
        addLbl(form, c, 2, 0, "Port:");
        c.gridx = 3; c.gridy = 0; form.add(portField, c);

        // Row 1: Username | Password
        addLbl(form, c, 0, 1, "Username:");
        c.gridx = 1; c.gridy = 1; c.weightx = 1.0; form.add(userField, c); c.weightx = 0;
        addLbl(form, c, 2, 1, "Password:");
        c.gridx = 3; c.gridy = 1; form.add(passField, c);

        // Row 2: Database with Discover button
        addLbl(form, c, 0, 2, "Database:");
        JButton discoverBtn = new JButton("Discover Databases");
        discoverBtn.setFont(discoverBtn.getFont().deriveFont(11f));
        discoverBtn.addActionListener(e -> discoverDatabases());
        JPanel dbRow = new JPanel(new BorderLayout(6, 0));
        dbRow.setOpaque(false);
        dbRow.add(dbCombo, BorderLayout.CENTER);
        dbRow.add(discoverBtn, BorderLayout.EAST);
        c.gridx = 1; c.gridy = 2; c.gridwidth = 3; c.weightx = 1.0;
        form.add(dbRow, c);
        c.gridwidth = 1; c.weightx = 0;

        return form;
    }

    private JPanel buildBottom() {
        statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(12f));
        statusLabel.setBorder(new EmptyBorder(0, 0, 6, 0));

        JButton testBtn = new JButton("Test Connection");
        testBtn.addActionListener(e -> testConnection());

        saveBtn = new JButton("Save & Continue");
        saveBtn.setBackground(NAVY);
        saveBtn.setForeground(Color.WHITE);
        saveBtn.setOpaque(true);
        saveBtn.setBorderPainted(false);
        saveBtn.setFont(saveBtn.getFont().deriveFont(Font.BOLD, 13f));
        saveBtn.setEnabled(false);
        saveBtn.addActionListener(e -> saveAndClose());

        JPanel btnRow = new JPanel(new BorderLayout(8, 0));
        btnRow.setOpaque(false);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(testBtn);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        right.add(saveBtn);
        btnRow.add(left, BorderLayout.WEST);
        btnRow.add(right, BorderLayout.EAST);

        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        p.add(statusLabel, BorderLayout.NORTH);
        p.add(btnRow,      BorderLayout.SOUTH);
        return p;
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void discoverDatabases() {
        String host = hostField.getText().trim();
        String port = portField.getText().trim();
        String user = userField.getText().trim();
        String pass = new String(passField.getPassword());

        setStatus("Connecting to MySQL server…", TEXT2, false);

        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                Class.forName("com.mysql.cj.jdbc.Driver");
                String url = "jdbc:mysql://" + host + ":" + port
                        + "/?connectTimeout=5000&serverTimezone=UTC";
                List<String> dbs = new ArrayList<>();
                try (Connection c = DriverManager.getConnection(url, user, pass);
                     Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("SHOW DATABASES")) {
                    while (rs.next()) {
                        String db = rs.getString(1);
                        if (!db.equals("information_schema") && !db.equals("performance_schema")
                                && !db.equals("sys") && !db.equals("mysql")) {
                            dbs.add(db);
                        }
                    }
                }
                return dbs;
            }

            @Override
            protected void done() {
                try {
                    List<String> dbs = get();
                    String current = getDbName();
                    dbCombo.removeAllItems();
                    dbs.forEach(dbCombo::addItem);
                    // restore selection or default
                    if (dbs.contains(current)) dbCombo.setSelectedItem(current);
                    else if (!dbs.isEmpty()) dbCombo.setSelectedIndex(0);
                    setStatus("✓ Found " + dbs.size() + " database(s) — select one or type a new name.",
                            GREEN, false);
                } catch (Exception ex) {
                    setStatus("✗ " + simplify(ex.getMessage()), RED, false);
                }
            }
        }.execute();
    }

    private void testConnection() {
        String host = hostField.getText().trim();
        String port = portField.getText().trim();
        String user = userField.getText().trim();
        String pass = new String(passField.getPassword());
        String name = getDbName();

        if (name.isEmpty()) {
            setStatus("✗ Enter a database name before testing.", AMBER, false);
            return;
        }

        setStatus("Testing connection…", TEXT2, false);
        saveBtn.setEnabled(false);

        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() {
                return DbConfig.testConnection(host, port, name, user, pass);
            }
            @Override protected void done() {
                try {
                    String err = get();
                    if (err == null) {
                        setStatus("✓ Connection successful — ready to save.", GREEN, true);
                    } else {
                        setStatus("✗ " + simplify(err), RED, false);
                    }
                } catch (Exception ex) {
                    setStatus("✗ " + ex.getMessage(), RED, false);
                }
            }
        }.execute();
    }

    private void saveAndClose() {
        new DbConfig().save(
                hostField.getText().trim(),
                portField.getText().trim(),
                getDbName(),
                userField.getText().trim(),
                new String(passField.getPassword()));
        this.saved = true;
        dispose();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setStatus(String msg, Color color, boolean enableSave) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(msg);
            statusLabel.setForeground(color);
            saveBtn.setEnabled(enableSave);
        });
    }

    private String getDbName() {
        Object sel = dbCombo.getSelectedItem();
        if (sel != null && !sel.toString().isBlank()) return sel.toString().trim();
        Component ed = dbCombo.getEditor().getEditorComponent();
        if (ed instanceof JTextField tf) return tf.getText().trim();
        return "";
    }

    private String simplify(String msg) {
        if (msg == null) return "Unknown error";
        if (msg.contains("Communications link failure") || msg.contains("Connection refused"))
            return "Cannot reach MySQL server — is it running on that host/port?";
        if (msg.contains("Access denied"))
            return "Access denied — check username and password";
        if (msg.length() > 90) return msg.substring(0, 90) + "…";
        return msg;
    }

    private void addLbl(JPanel p, GridBagConstraints c, int x, int y, String text) {
        c.gridx = x; c.gridy = y;
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 13f));
        p.add(l, c);
    }

    private JTextField tf(String value, int cols) {
        return new JTextField(value, cols);
    }

    /** Whether the user saved a valid config (vs cancelled). */
    public boolean isSaved() { return saved; }
}
