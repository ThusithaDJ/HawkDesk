package com.olympus.system.hawkdeskpos.frontend;

import com.olympus.system.hawkdeskpos.dto.StockLevelDto;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.frontend.components.StatusPill;
import com.olympus.system.hawkdeskpos.service.ItemService;
import com.olympus.system.hawkdeskpos.service.SaleService;
import com.olympus.system.hawkdeskpos.service.SettingsService;
import com.olympus.system.hawkdeskpos.session.Permission;
import com.olympus.system.hawkdeskpos.session.SessionContext;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.List;

/**
 * Home dashboard.
 * Left/Centre: hero button + 2x2 stock grid + 1x3 sales grid.
 * Right sidebar: today's summary + low stock + quick notes + links.
 */
public class DashboardPanel extends JPanel {

    private static final Color NAVY    = new Color(0x1E, 0x3A, 0x5F);
    private static final Color BG      = new Color(0xF0, 0xF2, 0xF5);
    private static final Color TEXT1   = new Color(0x1A, 0x1D, 0x23);
    private static final Color TEXT2   = new Color(0x5A, 0x60, 0x70);
    private static final Color RED     = new Color(0xC6, 0x28, 0x28);
    private static final Color AMBER   = new Color(0xE6, 0x51, 0x00);

    private static final String NOTES_FILE =
            System.getProperty("user.home") + File.separator + "HawkDeskPOS" + File.separator + "notes.txt";

    private final ItemService    itemService;
    private final SaleService    saleService;
    private final SettingsService settings;

    private JLabel revenueVal, txVal, itemsVal;
    private JPanel lowStockList;
    private JTextArea notesArea;
    private JLabel lowBadge;

    public DashboardPanel(ItemService itemService, SaleService saleService, SettingsService settings) {
        this.itemService = itemService;
        this.saleService = saleService;
        this.settings    = settings;
        setBackground(BG);
        setLayout(new BorderLayout(0, 0));
        buildUI();
    }

    public void refresh() {
        loadDataAsync();
    }

    private void buildUI() {
        JPanel content = new JPanel(new BorderLayout(14, 0));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(16, 16, 16, 16));

        // ── Main column ───────────────────────────────────────────────────────
        JPanel main = new JPanel();
        main.setOpaque(false);
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));

        // Hero: New Sale
        JButton heroBtn = makeHeroButton("NEW SALE",
                "Start a new sales transaction", Home.CARD_SALE);
        heroBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        heroBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        main.add(heroBtn);
        main.add(Box.createVerticalStrut(14));

        // Stock grid 2x2
        JPanel stockGrid = new JPanel(new GridLayout(2, 2, 10, 10));
        stockGrid.setOpaque(false);
        stockGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
        stockGrid.setAlignmentX(Component.LEFT_ALIGNMENT);

        stockGrid.add(gridTile("View Stock",         "All inventory items",       Home.CARD_STOCK,     "#1E3A5F", Permission.VIEW_STOCK));
        JPanel lowTile = gridTileWithBadge("Low Stock Alerts",  "Items needing attention",   Home.CARD_LOW_STOCK, "#E65100", Permission.VIEW_STOCK);
        stockGrid.add(lowTile);
        stockGrid.add(gridTile("Receive Stock (GRN)","Record new stock delivery", Home.CARD_RECEIVE,   "#2E7D32", Permission.RECEIVE_STOCK));
        stockGrid.add(gridTile("Add New Item",       "Register a new product",    Home.CARD_ADD_ITEM,  "#185FA5", Permission.ADD_ITEM));
        main.add(stockGrid);
        main.add(Box.createVerticalStrut(14));

        // Sales grid 1x3
        JPanel salesGrid = new JPanel(new GridLayout(1, 3, 10, 0));
        salesGrid.setOpaque(false);
        salesGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        salesGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        salesGrid.add(gridTile("Sales History", "Past transactions",    Home.CARD_HIST,    "#283593", Permission.VIEW_SALES));
        salesGrid.add(gridTile("Reports",       "Insights & analytics", Home.CARD_REPORTS, "#4A148C", Permission.VIEW_REPORTS));
        salesGrid.add(gridTile("Find Invoice",  "Search by invoice #",  Home.CARD_FIND_INV, "#006064", Permission.FIND_INVOICE));
        main.add(salesGrid);

        content.add(main, BorderLayout.CENTER);

        // ── Right sidebar ─────────────────────────────────────────────────────
        JPanel sidebar = new JPanel();
        sidebar.setOpaque(false);
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(268, 0));

        sidebar.add(buildSummaryCard());
        sidebar.add(Box.createVerticalStrut(10));
        sidebar.add(buildLowStockCard());
        sidebar.add(Box.createVerticalStrut(10));
        sidebar.add(buildNotesCard());
        sidebar.add(Box.createVerticalStrut(10));
        sidebar.add(buildLinksCard());

        JScrollPane sideScroll = new JScrollPane(sidebar);
        sideScroll.setOpaque(false);
        sideScroll.getViewport().setOpaque(false);
        sideScroll.setBorder(null);
        sideScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        content.add(sideScroll, BorderLayout.EAST);

        add(content);
        loadDataAsync();
    }

    // ── Hero button ───────────────────────────────────────────────────────────

    private JButton makeHeroButton(String title, String sub, String card) {
        JButton btn = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? new Color(0x16, 0x30, 0x4F) : NAVY);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(Color.WHITE);
                g2.setFont(getFont().deriveFont(Font.BOLD, 20f));
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth() - fm.stringWidth(title)) / 2;
                g2.drawString(title, tx, getHeight() / 2);
                g2.setColor(new Color(255, 255, 255, 178));
                g2.setFont(getFont().deriveFont(13f));
                fm = g2.getFontMetrics();
                tx = (getWidth() - fm.stringWidth(sub)) / 2;
                g2.drawString(sub, tx, getHeight() / 2 + 20);
                g2.dispose();
            }
        };
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> Home.navigate(card));
        return btn;
    }

    // ── Grid tiles ────────────────────────────────────────────────────────────

    private JPanel gridTile(String title, String sub, String card, String colorHex, Permission perm) {
        if (!SessionContext.hasPermission(perm)) {
            JPanel empty = new JPanel();
            empty.setOpaque(false);
            return empty;
        }
        CardPanel tile = new CardPanel(new BorderLayout());
        tile.setBorder(new EmptyBorder(16, 16, 16, 16));
        tile.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel t = new JLabel(title);
        t.setFont(t.getFont().deriveFont(Font.BOLD, 14f));
        t.setForeground(Color.decode(colorHex));
        JLabel s = new JLabel("<html>" + sub + "</html>");
        s.setFont(s.getFont().deriveFont(12f));
        s.setForeground(TEXT2);

        JPanel info = new JPanel(new GridLayout(2, 1, 0, 4));
        info.setOpaque(false);
        info.add(t);
        info.add(s);
        tile.add(info, BorderLayout.CENTER);
        tile.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { Home.navigate(card); }
        });
        return tile;
    }

    private JPanel gridTileWithBadge(String title, String sub, String card, String colorHex, Permission perm) {
        JPanel tile = gridTile(title, sub, card, colorHex, perm);
        // badge added after data load
        return tile;
    }

    // ── Sidebar cards ─────────────────────────────────────────────────────────

    private CardPanel buildSummaryCard() {
        CardPanel c = new CardPanel(new BorderLayout(0, 10));
        c.setBorder(new EmptyBorder(14, 14, 14, 14));

        JLabel title = new JLabel("TODAY'S SUMMARY");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 11f));
        title.setForeground(TEXT2);
        c.add(title, BorderLayout.NORTH);

        JPanel stats = new JPanel(new GridLayout(3, 2, 4, 8));
        stats.setOpaque(false);
        stats.add(stat("Revenue", "Rs. 0.00"));
        revenueVal = (JLabel) ((JPanel) stats.getComponent(0)).getComponent(1);
        stats.add(stat("Transactions", "0"));
        txVal = (JLabel) ((JPanel) stats.getComponent(1)).getComponent(1);
        stats.add(stat("Items Sold", "0"));
        itemsVal = (JLabel) ((JPanel) stats.getComponent(2)).getComponent(1);
        c.add(stats, BorderLayout.CENTER);
        return c;
    }

    private JPanel stat(String label, String value) {
        JPanel p = new JPanel(new GridLayout(2, 1, 0, 2));
        p.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setFont(l.getFont().deriveFont(11f));
        l.setForeground(TEXT2);
        JLabel v = new JLabel(value);
        v.setFont(v.getFont().deriveFont(Font.BOLD, 16f));
        v.setForeground(TEXT1);
        p.add(l);
        p.add(v);
        return p;
    }

    private CardPanel buildLowStockCard() {
        CardPanel c = new CardPanel(new BorderLayout(0, 8));
        c.setBorder(new EmptyBorder(14, 14, 14, 14));

        JLabel title = new JLabel("LOW STOCK");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 11f));
        title.setForeground(TEXT2);
        c.add(title, BorderLayout.NORTH);

        lowStockList = new JPanel();
        lowStockList.setOpaque(false);
        lowStockList.setLayout(new BoxLayout(lowStockList, BoxLayout.Y_AXIS));
        lowStockList.add(new JLabel("Loading…"));
        c.add(lowStockList, BorderLayout.CENTER);
        return c;
    }

    private CardPanel buildNotesCard() {
        CardPanel c = new CardPanel(new BorderLayout(0, 8));
        c.setBorder(new EmptyBorder(14, 14, 14, 14));

        JLabel title = new JLabel("QUICK NOTES");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 11f));
        title.setForeground(TEXT2);
        c.add(title, BorderLayout.NORTH);

        notesArea = new JTextArea(4, 20);
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        notesArea.setBorder(new EmptyBorder(6, 6, 6, 6));
        notesArea.setText(readNotes());
        JScrollPane sp = new JScrollPane(notesArea);
        sp.setBorder(BorderFactory.createLineBorder(new Color(0xC8, 0xCD, 0xD6)));
        c.add(sp, BorderLayout.CENTER);

        JButton save = new JButton("Save");
        save.addActionListener(e -> saveNotes(notesArea.getText()));
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
        btnRow.setOpaque(false);
        btnRow.add(save);
        c.add(btnRow, BorderLayout.SOUTH);
        return c;
    }

    private CardPanel buildLinksCard() {
        CardPanel c = new CardPanel(new GridLayout(3, 1, 0, 0));
        c.setBorder(new EmptyBorder(0, 0, 0, 0));
        c.add(link("Settings",        Home.CARD_SETTINGS, Permission.ACCESS_SETTINGS));
        c.add(link("User Management", Home.CARD_USERS,    Permission.MANAGE_USERS));
        c.add(link("Backup & Restore",Home.CARD_BACKUP,   Permission.ACCESS_BACKUP));
        return c;
    }

    private JPanel link(String text, String card, Permission perm) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xE2, 0xE5, 0xEA)),
                new EmptyBorder(10, 14, 10, 14)));
        JLabel l = new JLabel(text);
        l.setForeground(new Color(0x18, 0x5F, 0xA5));
        l.setFont(l.getFont().deriveFont(13f));
        row.add(l, BorderLayout.WEST);
        if (SessionContext.hasPermission(perm)) {
            row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            row.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) { Home.navigate(card); }
            });
        } else {
            l.setForeground(TEXT2);
        }
        return row;
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private void loadDataAsync() {
        new SwingWorker<Void, Void>() {
            private SaleService.TodaySummary summary;
            private List<StockLevelDto> low;

            @Override protected Void doInBackground() {
                summary = saleService.getTodaySummary();
                low     = itemService.listLowStock();
                return null;
            }

            @Override protected void done() {
                if (summary != null) {
                    revenueVal.setText(String.format("Rs. %.2f", summary.revenue()));
                    txVal.setText(String.valueOf(summary.transactions()));
                    itemsVal.setText(String.valueOf(summary.itemsSold()));
                }
                if (low != null) {
                    lowStockList.removeAll();
                    // Update global notification list
                    String[] names = low.stream().map(StockLevelDto::itemName).toArray(String[]::new);
                    Home.lstNotifi.setListData(names);

                    low.stream().limit(5).forEach(dto -> {
                        JPanel row = new JPanel(new BorderLayout(6, 0));
                        row.setOpaque(false);
                        row.setBorder(new EmptyBorder(3, 0, 3, 0));
                        JLabel name = new JLabel(dto.itemName());
                        name.setFont(name.getFont().deriveFont(12f));
                        name.setForeground(TEXT1);
                        row.add(name, BorderLayout.CENTER);
                        row.add(StatusPill.forStatus(dto.stockStatus()), BorderLayout.EAST);
                        lowStockList.add(row);
                    });
                    if (low.isEmpty()) lowStockList.add(new JLabel("All stock levels OK ✓"));
                    lowStockList.revalidate();
                    lowStockList.repaint();
                }
            }
        }.execute();
    }

    // ── Notes persistence ─────────────────────────────────────────────────────

    private String readNotes() {
        try {
            Path p = Path.of(NOTES_FILE);
            if (Files.exists(p)) return Files.readString(p);
        } catch (IOException ignored) {}
        return "";
    }

    private void saveNotes(String text) {
        try {
            Path p = Path.of(NOTES_FILE);
            Files.createDirectories(p.getParent());
            Files.writeString(p, text);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not save notes: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
