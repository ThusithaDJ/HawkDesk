package com.olympus.system.hawkdeskpos.frontend;

import com.olympus.system.hawkdeskpos.db.dao.AuditLog;
import com.olympus.system.hawkdeskpos.dto.EmployeeDto;
import com.olympus.system.hawkdeskpos.service.AuditService;
import com.olympus.system.hawkdeskpos.service.AuthService;
import com.olympus.system.hawkdeskpos.session.SessionContext;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Full-screen navy login panel.
 * Left: staff card list. Right: selected user + 4-dot PIN display + numpad.
 */
public class LoginPanel extends JPanel {

    private static final Color NAVY    = new Color(0x1E, 0x3A, 0x5F);
    private static final Color WHITE   = Color.WHITE;
    private static final Color WHITE15 = new Color(255, 255, 255, 38);
    private static final Color WHITE40 = new Color(255, 255, 255, 102);
    private static final Color SUCCESS = new Color(0x4C, 0xAF, 0x50);
    private static final Color DANGER  = new Color(0xEF, 0x9A, 0x9A);

    private final AuthService   authService;
    private final AuditService  auditService;
    private final Runnable      onSuccess;

    private EmployeeDto selectedEmployee;
    private final StringBuilder pin = new StringBuilder();

    // PIN dot references
    private final JPanel[] dots = new JPanel[4];

    // Error / lockout label
    private JLabel errorLabel;
    private JLabel nameLabel;
    private JLabel hintLabel;
    private JPanel avatarCircle;
    private JLabel avatarInitials;

    // Staff card references for highlight
    private JPanel[] staffCards;
    private EmployeeDto[] employees;

    // Clock label
    private JLabel clockLabel;

    private Timer lockoutTimer;

    public LoginPanel(AuthService authService, AuditService auditService, Runnable onSuccess) {
        this.authService  = authService;
        this.auditService = auditService;
        this.onSuccess    = onSuccess;
        setLayout(new BorderLayout());
        setBackground(NAVY);
        buildUI();
        refreshClock();
        new Timer(30_000, e -> refreshClock()).start();
    }

    private void buildUI() {
        // ── Top bar ───────────────────────────────────────────────────────────
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.setBorder(new EmptyBorder(20, 28, 12, 28));

        JPanel logoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        logoRow.setOpaque(false);
        JPanel logoBox = createLogoBox();
        logoRow.add(logoBox);
        JPanel logoText = new JPanel(new GridLayout(2, 1, 0, 2));
        logoText.setOpaque(false);
        JLabel appName = label("HawkPOS", 17, Font.BOLD, WHITE);
        JLabel subName = label("Hardware Shop · Colombo", 12, Font.PLAIN, WHITE40);
        logoText.add(appName);
        logoText.add(subName);
        logoRow.add(logoText);
        top.add(logoRow, BorderLayout.WEST);

        JPanel clockPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        clockPanel.setOpaque(false);
        clockLabel = new JLabel("10:42 AM");
        clockLabel.setFont(clockLabel.getFont().deriveFont(Font.PLAIN, 28f));
        clockLabel.setForeground(WHITE);
        clockLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        JLabel dateLabel = label(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy")),
                13, Font.PLAIN, WHITE40);
        dateLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        clockPanel.add(clockLabel);
        clockPanel.add(dateLabel);
        top.add(clockPanel, BorderLayout.EAST);

        add(top, BorderLayout.NORTH);

        // ── Centre: staff list | divider | PIN pad ────────────────────────────
        JPanel centre = new JPanel(new GridBagLayout());
        centre.setOpaque(false);
        centre.setBorder(new EmptyBorder(20, 0, 20, 0));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 24, 0, 24);
        gbc.fill   = GridBagConstraints.VERTICAL;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.gridx  = 0; gbc.gridy = 0;
        centre.add(buildStaffList(), gbc);

        // Divider
        gbc.gridx = 1;
        JPanel divider = new JPanel();
        divider.setBackground(new Color(255, 255, 255, 30));
        divider.setPreferredSize(new Dimension(1, 320));
        centre.add(divider, gbc);

        // PIN section
        gbc.gridx = 2;
        centre.add(buildPinSection(), gbc);

        add(centre, BorderLayout.CENTER);

        // ── Bottom bar ────────────────────────────────────────────────────────
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.setBorder(new EmptyBorder(10, 28, 14, 28));
        bottom.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(255,255,255,20)),
                new EmptyBorder(12, 28, 14, 28)));
        JButton exitBtn = new JButton("Exit");
        exitBtn.setForeground(WHITE);
        exitBtn.setBackground(new Color(255, 255, 255, 0));
        exitBtn.setOpaque(true);
        exitBtn.setBorderPainted(false);
        exitBtn.setFocusPainted(false);
        exitBtn.setFont(exitBtn.getFont().deriveFont(12f));
        exitBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        exitBtn.addActionListener(e -> System.exit(0));
        exitBtn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) { exitBtn.setForeground(new Color(0xFC, 0x8C, 0x8C)); }
            @Override public void mouseExited(java.awt.event.MouseEvent e)  { exitBtn.setForeground(WHITE); }
        });
        bottom.add(exitBtn, BorderLayout.WEST);
        JLabel copy = label("© 2026 Olympus Systems", 12, Font.PLAIN, new Color(255,255,255,89));
        bottom.add(copy, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);
    }

    // ── Staff list ────────────────────────────────────────────────────────────

    private JPanel buildStaffList() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel who = label("WHO ARE YOU?", 11, Font.BOLD, WHITE40);
        who.setBorder(new EmptyBorder(0, 0, 8, 0));
        panel.add(who);

        List<EmployeeDto> list = authService.listActiveEmployees();
        employees  = list.toArray(new EmployeeDto[0]);
        staffCards = new JPanel[employees.length];

        for (int i = 0; i < employees.length; i++) {
            final int idx = i;
            JPanel card = buildStaffCard(employees[i], false);
            card.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) { selectEmployee(idx); }
                @Override public void mouseEntered(java.awt.event.MouseEvent e) { card.setBackground(new Color(255,255,255,56)); }
                @Override public void mouseExited(java.awt.event.MouseEvent e)  {
                    card.setBackground(idx == getSelectedIndex()
                            ? new Color(255,255,255,46) : new Color(255,255,255,20));
                }
            });
            staffCards[i] = card;
            panel.add(card);
            panel.add(Box.createVerticalStrut(8));
        }

        // Select first by default
        if (employees.length > 0) SwingUtilities.invokeLater(() -> selectEmployee(0));

        JScrollPane scroll = new JScrollPane(panel);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        scroll.setPreferredSize(new Dimension(210, 340));

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(scroll);
        return wrapper;
    }

    private JPanel buildStaffCard(EmployeeDto emp, boolean selected) {
        JPanel card = new JPanel(new BorderLayout(11, 0));
        card.setBorder(new EmptyBorder(12, 14, 12, 14));
        card.setBackground(selected ? new Color(255,255,255,46) : new Color(255,255,255,20));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Avatar
        JPanel avatar = makeAvatarCircle(initials(emp.name()), 36);
        card.add(avatar, BorderLayout.WEST);

        // Name + role
        JPanel info = new JPanel(new GridLayout(2, 1, 0, 2));
        info.setOpaque(false);
        info.add(label(emp.name(), 13, Font.BOLD, WHITE));
        info.add(label(roleLabel(emp.role()), 11, Font.PLAIN, WHITE40));
        card.add(info, BorderLayout.CENTER);

        // Online dot
        JPanel dot = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(emp.active() ? new Color(0x4C, 0xAF, 0x50) : new Color(0x90, 0xA4, 0xAE));
                g2.fillOval(0, 0, 8, 8);
                g2.dispose();
            }
        };
        dot.setOpaque(false);
        dot.setPreferredSize(new Dimension(8, 8));
        JPanel dotWrapper = new JPanel(new GridBagLayout());
        dotWrapper.setOpaque(false);
        dotWrapper.add(dot);
        card.add(dotWrapper, BorderLayout.EAST);

        // Rounded border
        card.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(12, selected ? new Color(255,255,255,102) : new Color(255,255,255,30)),
                new EmptyBorder(12, 14, 12, 14)));
        return card;
    }

    // ── PIN section ───────────────────────────────────────────────────────────

    private JPanel buildPinSection() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Avatar + name
        avatarCircle   = makeAvatarCircle("?", 60);
        avatarCircle.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(avatarCircle);
        panel.add(Box.createVerticalStrut(8));

        nameLabel = label("Select user", 16, Font.BOLD, WHITE);
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(nameLabel);
        panel.add(Box.createVerticalStrut(4));

        hintLabel = label("Enter your 4-digit PIN", 13, Font.PLAIN, WHITE40);
        hintLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(hintLabel);
        panel.add(Box.createVerticalStrut(18));

        // Dots
        JPanel dotsRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
        dotsRow.setOpaque(false);
        for (int i = 0; i < 4; i++) {
            dots[i] = new JPanel() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(getBackground());
                    g2.fillOval(0, 0, 14, 14);
                    g2.dispose();
                }
            };
            dots[i].setPreferredSize(new Dimension(14, 14));
            dots[i].setBackground(new Color(255, 255, 255, 89));
            dots[i].setOpaque(false);
            dotsRow.add(dots[i]);
        }
        dotsRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(dotsRow);
        panel.add(Box.createVerticalStrut(6));

        errorLabel = new JLabel(" ");
        errorLabel.setFont(errorLabel.getFont().deriveFont(13f));
        errorLabel.setForeground(DANGER);
        errorLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        errorLabel.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(errorLabel);
        panel.add(Box.createVerticalStrut(12));

        // Numpad
        JPanel numpad = buildNumpad();
        numpad.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(numpad);

        return panel;
    }

    private JPanel buildNumpad() {
        JPanel grid = new JPanel(new GridLayout(4, 3, 10, 10));
        grid.setOpaque(false);

        String[] labels = {"1","2","3","4","5","6","7","8","9","C","0","⌫"};
        for (String lbl : labels) {
            if (lbl.equals("C")) {
                JButton btn = numBtn("C", false);
                btn.setOpaque(false);
                btn.setBorderPainted(false);
                btn.addActionListener(e -> clearPin());
                grid.add(btn);
            } else if (lbl.equals("⌫")) {
                JButton btn = numBtn("⌫", true);
                btn.addActionListener(e -> backspacePin());
                grid.add(btn);
            } else {
                JButton btn = numBtn(lbl, true);
                btn.addActionListener(e -> pressPin(lbl));
                grid.add(btn);
            }
        }
        return grid;
    }

    private JButton numBtn(String text, boolean bordered) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                if (bordered) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(getModel().isPressed()
                            ? new Color(255,255,255,60)
                            : getModel().isRollover()
                                ? new Color(255,255,255,46)
                                : new Color(255,255,255,26));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                    g2.setColor(new Color(255,255,255,51));
                    g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 14, 14);
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        btn.setPreferredSize(new Dimension(72, 72));
        btn.setFont(btn.getFont().deriveFont(Font.PLAIN, 22f));
        btn.setForeground(WHITE);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // ── Logic ─────────────────────────────────────────────────────────────────

    private void selectEmployee(int idx) {
        selectedEmployee = employees[idx];
        pin.setLength(0);
        updateDots();
        errorLabel.setText(" ");

        // Update avatar + name
        avatarInitials = new JLabel(initials(selectedEmployee.name()));
        avatarInitials.setFont(avatarInitials.getFont().deriveFont(Font.BOLD, 20f));
        avatarInitials.setForeground(WHITE);
        avatarCircle.removeAll();
        avatarCircle.add(new JLabel() {{
            setText(initials(selectedEmployee.name()));
            setFont(getFont().deriveFont(Font.BOLD, 20f));
            setForeground(WHITE);
        }});
        nameLabel.setText(selectedEmployee.name());

        // Highlight selected card
        for (int i = 0; i < staffCards.length; i++) {
            staffCards[i].setBackground(i == idx
                    ? new Color(255, 255, 255, 46)
                    : new Color(255, 255, 255, 20));
        }
    }

    private int getSelectedIndex() {
        if (selectedEmployee == null) return -1;
        for (int i = 0; i < employees.length; i++)
            if (employees[i].id().equals(selectedEmployee.id())) return i;
        return -1;
    }

    private void pressPin(String digit) {
        if (pin.length() >= 4) return;
        pin.append(digit);
        updateDots();
        if (pin.length() == 4) SwingUtilities.invokeLater(this::submitPin);
    }

    private void backspacePin() {
        if (pin.length() > 0) { pin.deleteCharAt(pin.length() - 1); updateDots(); }
    }

    private void clearPin() {
        pin.setLength(0);
        updateDots();
        errorLabel.setText(" ");
    }

    private void submitPin() {
        if (selectedEmployee == null) return;
        AuthService.LoginOutcome outcome = authService.login(selectedEmployee.id(), pin.toString());
        switch (outcome.result()) {
            case SUCCESS -> {
                flashDots(true);
                SessionContext.login(outcome.employee(), outcome.permissions());
                auditService.log(AuditLog.Action.LOGIN, "employees",
                        selectedEmployee.id(), null, null, selectedEmployee.id(), null);
                new Timer(400, e -> { ((Timer)e.getSource()).stop(); onSuccess.run(); }).start();
            }
            case WRONG_PIN -> {
                flashDots(false);
                long remaining = authService.lockoutSecondsRemaining(selectedEmployee.id());
                if (remaining > 0) {
                    startLockoutCountdown(remaining);
                } else {
                    errorLabel.setText("Incorrect PIN. Try again.");
                }
                new Timer(600, e -> { ((Timer)e.getSource()).stop(); clearPin(); }).start();
            }
            case LOCKED -> {
                long remaining = authService.lockoutSecondsRemaining(selectedEmployee.id());
                startLockoutCountdown(remaining);
            }
            case INACTIVE -> errorLabel.setText("Account is inactive. Contact your manager.");
            default       -> errorLabel.setText("User not found.");
        }
    }

    private void flashDots(boolean success) {
        Color c = success ? SUCCESS : DANGER;
        for (JPanel d : dots) d.setBackground(c);
    }

    private void updateDots() {
        for (int i = 0; i < 4; i++) {
            dots[i].setBackground(i < pin.length()
                    ? WHITE
                    : new Color(255, 255, 255, 89));
        }
    }

    private void startLockoutCountdown(long seconds) {
        if (lockoutTimer != null && lockoutTimer.isRunning()) lockoutTimer.stop();
        final long[] remaining = {seconds};
        errorLabel.setText("Locked. Try again in " + remaining[0] + " s.");
        lockoutTimer = new Timer(1000, e -> {
            remaining[0]--;
            if (remaining[0] <= 0) {
                ((Timer)e.getSource()).stop();
                errorLabel.setText(" ");
                clearPin();
            } else {
                errorLabel.setText("Locked. Try again in " + remaining[0] + " s.");
            }
        });
        lockoutTimer.start();
    }

    private void refreshClock() {
        if (clockLabel != null)
            clockLabel.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a")));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static JLabel label(String text, float size, int style, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(style, size));
        l.setForeground(color);
        return l;
    }

    private static JPanel makeAvatarCircle(String initials, int diameter) {
        JPanel p = new JPanel(new GridBagLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0xE8, 0xEA, 0xF6));
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(diameter, diameter));
        JLabel lbl = new JLabel(initials);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, (float)(diameter / 3)));
        lbl.setForeground(new Color(0x28, 0x35, 0x93));
        p.add(lbl);
        return p;
    }

    private static JPanel createLogoBox() {
        JPanel p = new JPanel(new GridBagLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(255, 255, 255, 38));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 9, 9);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(36, 36));
        return p;
    }

    private static String initials(String name) {
        if (name == null || name.isEmpty()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
    }

    private static String roleLabel(String role) {
        return switch (role) {
            case "OWNER"        -> "Owner";
            case "MANAGER"      -> "Manager";
            case "STOCK_KEEPER" -> "Stock Keeper";
            default             -> "Cashier";
        };
    }

    // ── Rounded border helper ─────────────────────────────────────────────────

    private static class RoundedBorder extends javax.swing.border.AbstractBorder {
        private final int radius; private final Color color;
        RoundedBorder(int r, Color c) { radius = r; color = c; }
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, w-1, h-1, radius, radius);
            g2.dispose();
        }
        @Override public Insets getBorderInsets(Component c) { return new Insets(radius/2, radius/2, radius/2, radius/2); }
    }
}
