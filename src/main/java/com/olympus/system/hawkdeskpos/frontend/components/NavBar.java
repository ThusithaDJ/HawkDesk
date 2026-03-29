package com.olympus.system.hawkdeskpos.frontend.components;

import com.olympus.system.hawkdeskpos.session.Permission;
import com.olympus.system.hawkdeskpos.session.SessionContext;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 54 px navy top navigation bar.
 * Hides nav buttons the current user has no permission for.
 * Exposes a Runnable callback for logout.
 */
public class NavBar extends JPanel {

    private static final Color NAVY       = new Color(0x1E, 0x3A, 0x5F);
    private static final Color NAVY_HOVER = new Color(0x16, 0x30, 0x4F);
    private static final Color WHITE      = Color.WHITE;
    private static final Color WHITE_DIM  = new Color(255, 255, 255, 178);

    private final JLabel clockLabel;
    private JLabel shopLabel;
    private final List<NavButton> navButtons = new ArrayList<>();
    private Runnable logoutCallback;

    public record NavItem(String label, String card, Permission requiredPermission) {}

    public NavBar(String shopName, Runnable onLogout) {
        this.logoutCallback = onLogout;
        setBackground(NAVY);
        setPreferredSize(new Dimension(0, 54));
        setLayout(new BorderLayout());

        // ── Left: logo + shop name ────────────────────────────────────────────
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        left.setOpaque(false);

        JPanel logo = buildLogoBox();
        left.add(logo);

        shopLabel = new JLabel("HawkPOS — " + shopName);
        shopLabel.setFont(shopLabel.getFont().deriveFont(Font.BOLD, 16f));
        shopLabel.setForeground(WHITE);
        left.add(shopLabel);

        add(left, BorderLayout.WEST);

        // ── Centre: nav buttons ───────────────────────────────────────────────
        JPanel centre = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        centre.setOpaque(false);

        List<NavItem> items = List.of(
                new NavItem("New Sale",      "NEW_SALE",    Permission.MAKE_SALE),
                new NavItem("View Stock",    "VIEW_STOCK",  Permission.VIEW_STOCK),
                new NavItem("Receive Stock", "RECEIVE_STOCK", Permission.RECEIVE_STOCK),
                new NavItem("Sales History", "SALES_HISTORY", Permission.VIEW_SALES),
                new NavItem("Returns",       "ALL_RETURNS", Permission.PROCESS_RETURNS),
                new NavItem("Reports",       "REPORTS",     Permission.VIEW_REPORTS),
                new NavItem("Settings",      "SETTINGS",    Permission.ACCESS_SETTINGS)
        );

        for (NavItem ni : items) {
            if (SessionContext.hasPermission(ni.requiredPermission())) {
                NavButton btn = new NavButton(ni.label(), ni.card());
                navButtons.add(btn);
                centre.add(btn);
            }
        }

        add(centre, BorderLayout.CENTER);

        // ── Right: user info + clock + logout ─────────────────────────────────
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);

        if (SessionContext.current() != null) {
            String empName = SessionContext.current().getEmployee().name();
            String role    = SessionContext.current().getEmployee().role();
            JLabel userLbl = new JLabel(empName);
            userLbl.setForeground(WHITE);
            userLbl.setFont(userLbl.getFont().deriveFont(13f));

            StatusPill rolePill = new StatusPill(roleLabel(role), StatusPill.PillStyle.INFO);
            rolePill.setForeground(new Color(0x18, 0x5F, 0xA5));

            right.add(userLbl);
            right.add(rolePill);
        }

        clockLabel = new JLabel("00:00 AM");
        clockLabel.setForeground(WHITE_DIM);
        clockLabel.setFont(clockLabel.getFont().deriveFont(13f));
        right.add(clockLabel);

        JButton minimizeBtn = new JButton("—");
        minimizeBtn.setForeground(WHITE);
        minimizeBtn.setBackground(new Color(255, 255, 255, 30));
        minimizeBtn.setOpaque(true);
        minimizeBtn.setBorderPainted(false);
        minimizeBtn.setFont(minimizeBtn.getFont().deriveFont(13f));
        minimizeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        minimizeBtn.setToolTipText("Minimize");
        minimizeBtn.addActionListener(e -> {
            java.awt.Window w = SwingUtilities.getWindowAncestor(NavBar.this);
            if (w instanceof java.awt.Frame f) f.setState(java.awt.Frame.ICONIFIED);
        });
        minimizeBtn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { minimizeBtn.setBackground(new Color(255,255,255,60)); }
            @Override public void mouseExited(MouseEvent e)  { minimizeBtn.setBackground(new Color(255,255,255,30)); }
        });
        right.add(minimizeBtn);

        JButton logoutBtn = new JButton("Log out");
        logoutBtn.setForeground(WHITE);
        logoutBtn.setBackground(new Color(255, 255, 255, 30));
        logoutBtn.setOpaque(true);
        logoutBtn.setBorderPainted(false);
        logoutBtn.setFont(logoutBtn.getFont().deriveFont(13f));
        logoutBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        logoutBtn.addActionListener(e -> { if (logoutCallback != null) logoutCallback.run(); });
        logoutBtn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { logoutBtn.setBackground(new Color(255,255,255,60)); }
            @Override public void mouseExited(MouseEvent e)  { logoutBtn.setBackground(new Color(255,255,255,30)); }
        });
        right.add(logoutBtn);

        JButton exitBtn = new JButton("Exit");
        exitBtn.setForeground(WHITE);
        exitBtn.setBackground(new Color(255, 255, 255, 30));
        exitBtn.setOpaque(true);
        exitBtn.setBorderPainted(false);
        exitBtn.setFont(exitBtn.getFont().deriveFont(13f));
        exitBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        exitBtn.addActionListener(e -> {
            int res = JOptionPane.showConfirmDialog(null,
                    "Exit HawkPOS? Any unsaved changes will be lost.",
                    "Exit", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (res == JOptionPane.YES_OPTION) System.exit(0);
        });
        exitBtn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { exitBtn.setBackground(new Color(0xC6, 0x28, 0x28)); }
            @Override public void mouseExited(MouseEvent e)  { exitBtn.setBackground(new Color(255,255,255,30)); }
        });
        right.add(exitBtn);

        add(right, BorderLayout.EAST);

        // Live clock — updates every 30 seconds
        updateClock();
        new Timer(30_000, e -> updateClock()).start();
    }

    private void updateClock() {
        clockLabel.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a")));
    }

    private static JPanel buildLogoBox() {
    Image logoImg;
    try {
        logoImg = new ImageIcon(
            NavBar.class.getResource("/images/icons/hawkpos-icon.png")
        ).getImage().getScaledInstance(20, 20, Image.SCALE_SMOOTH);
    } catch (Exception e) {
        logoImg = null;
    }

    final Image img = logoImg;

    JPanel box = new JPanel() {
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(255, 255, 255, 38));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 7, 7);
            if (img != null) {
                int pad = 4;
                g2.drawImage(img, pad, pad, getWidth() - pad * 2, getHeight() - pad * 2, this);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    };
    box.setOpaque(false);
    box.setPreferredSize(new Dimension(28, 28));
    return box;
}

    private static String roleLabel(String role) {
        return switch (role) {
            case "OWNER"        -> "Owner";
            case "MANAGER"      -> "Manager";
            case "STOCK_KEEPER" -> "Stock Keeper";
            default             -> "Cashier";
        };
    }

    public void updateShopName(String name) {
        shopLabel.setText("HawkPOS — " + name);
    }

    // ── Nav button component ──────────────────────────────────────────────────

    public static class NavButton extends JButton {
        private final String card;

        public NavButton(String label, String card) {
            super(label);
            this.card = card;
            setForeground(WHITE);
            setBackground(new Color(255, 255, 255, 0));
            setOpaque(true);
            setBorderPainted(false);
            setFocusPainted(false);
            setFont(getFont().deriveFont(13f));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { setBackground(NAVY_HOVER); }
                @Override public void mouseExited(MouseEvent e)  { setBackground(new Color(255,255,255,0)); }
            });
        }

        public String getCard() { return card; }
    }
}
