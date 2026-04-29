package com.olympus.system.hawkdeskpos.frontend;

import com.olympus.system.hawkdeskpos.dto.InvoiceDto;
import com.olympus.system.hawkdeskpos.dto.StockLevelDto;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.frontend.components.StatusPill;
import com.olympus.system.hawkdeskpos.service.ItemService;
import com.olympus.system.hawkdeskpos.service.ReportService;
import com.olympus.system.hawkdeskpos.service.SaleService;
import com.olympus.system.hawkdeskpos.service.SettingsService;
import com.olympus.system.hawkdeskpos.session.Permission;
import com.olympus.system.hawkdeskpos.session.SessionContext;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * Home dashboard.
 * Left/Centre: period selector + summary tiles + quick-action grid.
 * Right sidebar: credit reminders + low stock + quick notes + links.
 */
public class DashboardPanel extends JPanel implements com.olympus.system.hawkdeskpos.frontend.components.Refreshable {

    private static final Color NAVY    = new Color(0x1E, 0x3A, 0x5F);
    private static final Color BG      = new Color(0xF0, 0xF2, 0xF5);
    private static final Color TEXT1   = new Color(0x1A, 0x1D, 0x23);
    private static final Color TEXT2   = new Color(0x5A, 0x60, 0x70);
    private static final Color RED     = new Color(0xC6, 0x28, 0x28);
    private static final Color AMBER   = new Color(0xE6, 0x51, 0x00);

    private static final String[] SUMMARY_PERIODS =
            { "Today", "This Week", "This Month", "This Year", "Custom" };

    private static final String NOTES_FILE =
            System.getProperty("user.home") + File.separator + "HawkDeskPOS" + File.separator + "notes.txt";

    private final ItemService     itemService;
    private final SaleService     saleService;
    private final SettingsService settings;
    private final ReportService   reportService;

    // Summary tiles
    private JLabel salesRevVal, discountVal, netRevVal, stockPurchVal, cashRefundVal, netProfitVal;
    private JPanel summaryTilesRow;

    // Sidebar
    private JPanel lowStockList;
    private JTextArea notesArea;
    private JPanel debtRemindersPanel;

    // Period selector state (persisted across rebuilds)
    private String   dashPeriod = "Today";
    private JSpinner dashFromSpinner, dashToSpinner;
    private JPanel   dashCustomPanel;
    private JLabel   summaryPeriodLabel;

    // Rebuilt on each refresh so permission checks run after login
    private JPanel    mainColumn;
    private CardPanel linksCard;

    public DashboardPanel(ItemService itemService, SaleService saleService,
                          SettingsService settings, ReportService reportService) {
        this.itemService   = itemService;
        this.saleService   = saleService;
        this.settings      = settings;
        this.reportService = reportService;
        setBackground(BG);
        setLayout(new BorderLayout(0, 0));
        buildUI();
    }

    public void refresh() {
        rebuildMainColumn();
        rebuildLinksCard();
        loadSidebarAsync();
        loadSummaryAsync();
    }

    // ── Main UI ────────────────────────────────────────────────────────────────

    private void buildUI() {
        JPanel content = new JPanel(new BorderLayout(14, 0));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(16, 16, 16, 16));

        mainColumn = new JPanel(new BorderLayout());
        mainColumn.setOpaque(false);
        rebuildMainColumn();
        content.add(mainColumn, BorderLayout.CENTER);

        // ── Right sidebar ──────────────────────────────────────────────────────
        JPanel sidebar = new JPanel();
        sidebar.setOpaque(false);
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(268, 0));
        sidebar.add(buildDebtRemindersCard());
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
        loadSidebarAsync();
        loadSummaryAsync();
    }

    // ── Main column ────────────────────────────────────────────────────────────

    private void rebuildMainColumn() {
        mainColumn.removeAll();

        JPanel main = new JPanel();
        main.setOpaque(false);
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));

        // ── Summary section header (label + period selector) ───────────────────
        JPanel summaryHeader = new JPanel(new BorderLayout());
        summaryHeader.setOpaque(false);
        summaryHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        summaryHeader.setAlignmentX(Component.LEFT_ALIGNMENT);

        summaryPeriodLabel = sectionLabel(periodLabelText());
        summaryHeader.add(summaryPeriodLabel, BorderLayout.WEST);

        // Period combo
        JPanel selRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        selRow.setOpaque(false);

        JComboBox<String> combo = new JComboBox<>(SUMMARY_PERIODS);
        combo.setSelectedItem(dashPeriod);
        combo.setFont(combo.getFont().deriveFont(11f));
        combo.setPreferredSize(new Dimension(110, 24));

        // Custom date pickers — initialize once, re-use across rebuilds
        if (dashFromSpinner == null) {
            dashFromSpinner = makeDateSpinner(toStartOfDay(LocalDate.now()));
            dashToSpinner   = makeDateSpinner(toEndOfDay(LocalDate.now()));
        }
        JButton applyBtn = new JButton("Apply");
        applyBtn.setFont(applyBtn.getFont().deriveFont(11f));
        applyBtn.addActionListener(e -> loadSummaryAsync());

        dashCustomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        dashCustomPanel.setOpaque(false);
        dashCustomPanel.setVisible("Custom".equals(dashPeriod));
        dashCustomPanel.add(new JLabel("From:"));
        dashCustomPanel.add(dashFromSpinner);
        dashCustomPanel.add(new JLabel("To:"));
        dashCustomPanel.add(dashToSpinner);
        dashCustomPanel.add(applyBtn);

        combo.addActionListener(e -> {
            dashPeriod = (String) combo.getSelectedItem();
            dashCustomPanel.setVisible("Custom".equals(dashPeriod));
            summaryPeriodLabel.setText(periodLabelText());
            if (!"Custom".equals(dashPeriod)) loadSummaryAsync();
        });

        selRow.add(combo);
        selRow.add(dashCustomPanel);
        summaryHeader.add(selRow, BorderLayout.EAST);

        main.add(summaryHeader);
        main.add(Box.createVerticalStrut(6));

        // ── Summary tiles (2 rows × 3) ────────────────────────────────────────
        summaryTilesRow = new JPanel(new GridLayout(2, 3, 10, 10));
        summaryTilesRow.setOpaque(false);
        summaryTilesRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 170));
        summaryTilesRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        salesRevVal  = addStatTile(summaryTilesRow, "Sales Revenue",    "Rs. 0.00", "#185FA5");
        discountVal  = addStatTile(summaryTilesRow, "Discounts Given",  "Rs. 0.00", "#E65100");
        netRevVal    = addStatTile(summaryTilesRow, "Net Revenue",       "Rs. 0.00", "#2E7D32");
        stockPurchVal= addStatTile(summaryTilesRow, "Stock Purchases",  "Rs. 0.00", "#C62828");
        cashRefundVal= addStatTile(summaryTilesRow, "Cash Refunds",     "Rs. 0.00", "#B71C1C");
        netProfitVal = addStatTile(summaryTilesRow, "Net Profit",        "Rs. 0.00", "#6A1B9A");

        main.add(summaryTilesRow);
        main.add(Box.createVerticalStrut(18));

        // ── Quick Actions ──────────────────────────────────────────────────────
        main.add(sectionLabel("QUICK ACTIONS"));
        main.add(Box.createVerticalStrut(6));

        JButton heroBtn = makeHeroButton("NEW SALE", "Start a new sales transaction", Home.CARD_SALE);
        heroBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        heroBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        main.add(heroBtn);
        main.add(Box.createVerticalStrut(14));

        // ── Sales & Transactions ───────────────────────────────────────────────
        main.add(sectionLabel("SALES & TRANSACTIONS"));
        main.add(Box.createVerticalStrut(6));

        JPanel salesGrid = new JPanel(new GridLayout(1, 4, 10, 0));
        salesGrid.setOpaque(false);
        salesGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 78));
        salesGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        salesGrid.add(gridTile("Sales History",  "Past transactions",      Home.CARD_HIST,         "#283593", Permission.VIEW_SALES));
        salesGrid.add(gridTile("Find Invoice",   "Search by invoice #",    Home.CARD_FIND_INV,     "#006064", Permission.FIND_INVOICE));
        salesGrid.add(gridTile("Goods Returns",  "Process item returns",   Home.CARD_RETURNS,      "#B71C1C", Permission.PROCESS_RETURNS));
        salesGrid.add(gridTile("All Returns",    "View all return records", Home.CARD_RETURNS_LIST, "#6A1B9A", Permission.PROCESS_RETURNS));
        main.add(salesGrid);
        main.add(Box.createVerticalStrut(14));

        // ── Stock Management ───────────────────────────────────────────────────
        main.add(sectionLabel("STOCK MANAGEMENT"));
        main.add(Box.createVerticalStrut(6));

        JPanel stockGrid = new JPanel(new GridLayout(2, 3, 10, 10));
        stockGrid.setOpaque(false);
        stockGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
        stockGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        stockGrid.add(gridTile("View Stock",          "All inventory items",       Home.CARD_STOCK,     "#1E3A5F", Permission.VIEW_STOCK));
        stockGrid.add(gridTile("Receive Stock (GRN)", "Record new stock delivery", Home.CARD_RECEIVE,   "#2E7D32", Permission.RECEIVE_STOCK));
        stockGrid.add(gridTile("Low Stock Alerts",    "Items needing attention",   Home.CARD_LOW_STOCK, "#E65100", Permission.VIEW_STOCK));
        stockGrid.add(gridTile("Add New Item",        "Register a new product",    Home.CARD_ADD_ITEM,  "#185FA5", Permission.ADD_ITEM));
        stockGrid.add(gridTile("GRN History",         "View stock deliveries",     Home.CARD_GRN_HIST,  "#1B5E20", Permission.VIEW_GRN));
        stockGrid.add(gridTile("Stock Adjustment",    "Correct stock levels",      Home.CARD_ADJUST,    "#4E342E", Permission.ADJUST_STOCK));
        main.add(stockGrid);
        main.add(Box.createVerticalStrut(14));

        // ── Finance & Customers ────────────────────────────────────────────────
        main.add(sectionLabel("FINANCE & CUSTOMERS"));
        main.add(Box.createVerticalStrut(6));

        JPanel custGrid = new JPanel(new GridLayout(1, 5, 10, 0));
        custGrid.setOpaque(false);
        custGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 78));
        custGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        custGrid.add(gridTile("Customers",        "Manage customer accounts",  Home.CARD_CUSTOMERS,       "#1565C0", Permission.VIEW_SALES));
        custGrid.add(gridTile("Credit Invoices",  "Outstanding credit sales",  Home.CARD_CREDIT_INVOICES, "#E65100", Permission.VIEW_SALES));
        custGrid.add(gridTile("Cash & Accounts",  "Account balances & ledger", Home.CARD_CASH_ACCOUNTS,   "#2E7D32", Permission.VIEW_REPORTS));
        custGrid.add(gridTile("Cash Flow",        "Financials & accounts",     Home.CARD_CASHFLOW,        "#00695C", Permission.VIEW_REPORTS));
        custGrid.add(gridTile("Reports",          "Insights & analytics",      Home.CARD_REPORTS,         "#4A148C", Permission.VIEW_REPORTS));
        main.add(custGrid);

        mainColumn.add(main, BorderLayout.CENTER);
        mainColumn.revalidate();
        mainColumn.repaint();
    }

    // ── Period helpers ─────────────────────────────────────────────────────────

    private String periodLabelText() {
        return switch (dashPeriod != null ? dashPeriod : "Today") {
            case "This Week"  -> "THIS WEEK'S SUMMARY";
            case "This Month" -> "THIS MONTH'S SUMMARY";
            case "This Year"  -> "THIS YEAR'S SUMMARY";
            case "Custom"     -> "CUSTOM PERIOD SUMMARY";
            default           -> "TODAY'S SUMMARY";
        };
    }

    private Date[] getSummaryDateRange() {
        LocalDate today = LocalDate.now();
        return switch (dashPeriod != null ? dashPeriod : "Today") {
            case "This Week"  -> new Date[]{ toStartOfDay(today.with(DayOfWeek.MONDAY)), toEndOfDay(today) };
            case "This Month" -> new Date[]{ toStartOfDay(today.withDayOfMonth(1)),       toEndOfDay(today) };
            case "This Year"  -> new Date[]{ toStartOfDay(today.withDayOfYear(1)),        toEndOfDay(today) };
            case "Custom"     -> dashFromSpinner != null
                    ? new Date[]{ (Date) dashFromSpinner.getValue(), (Date) dashToSpinner.getValue() }
                    : new Date[]{ toStartOfDay(today), toEndOfDay(today) };
            default           -> new Date[]{ toStartOfDay(today), toEndOfDay(today) };
        };
    }

    private static Date toStartOfDay(LocalDate d) {
        return Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static Date toEndOfDay(LocalDate d) {
        return Date.from(d.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant());
    }

    private JSpinner makeDateSpinner(Date initial) {
        JSpinner s = new JSpinner(new SpinnerDateModel());
        s.setEditor(new JSpinner.DateEditor(s, "yyyy-MM-dd"));
        s.setPreferredSize(new Dimension(110, 24));
        s.setValue(initial);
        return s;
    }

    // ── Summary loading ────────────────────────────────────────────────────────

    private void loadSummaryAsync() {
        Date[] range = getSummaryDateRange();
        new SwingWorker<Void, Void>() {
            ReportService.PeriodStats    stats;
            ReportService.FinanceSummary fin;

            @Override protected Void doInBackground() {
                stats = reportService.getStats(range[0], range[1]);
                fin   = reportService.getFinanceSummary(range[0], range[1]);
                return null;
            }

            @Override protected void done() {
                if (stats == null || fin == null) return;
                double salesRev  = fin.salesRevenue();
                double discount  = fin.discountGiven();
                double netRev    = salesRev - discount;
                double stockPurch= fin.grnCost();
                double refunds   = fin.refundsGiven();
                double netProfit = netRev - stockPurch - fin.expenses() - refunds;

                salesRevVal  .setText(String.format("Rs. %.2f", salesRev));
                discountVal  .setText(String.format("Rs. %.2f", discount));
                netRevVal    .setText(String.format("Rs. %.2f", netRev));
                stockPurchVal.setText(String.format("Rs. %.2f", stockPurch));
                cashRefundVal.setText(String.format("Rs. %.2f", refunds));
                netProfitVal .setText(String.format("Rs. %.2f", netProfit));
                netProfitVal .setForeground(netProfit >= 0
                        ? new Color(0x2E, 0x7D, 0x32) : new Color(0xC6, 0x28, 0x28));
            }
        }.execute();
    }

    // ── Sidebar loading ────────────────────────────────────────────────────────

    private void loadSidebarAsync() {
        new SwingWorker<Void, Void>() {
            List<StockLevelDto> low;
            List<InvoiceDto>    overdueCredits;

            @Override protected Void doInBackground() {
                low            = itemService.listLowStock();
                overdueCredits = saleService.listOverdueCreditInvoices();
                return null;
            }

            @Override protected void done() {
                if (low != null) {
                    lowStockList.removeAll();
                    String[] names = low.stream().map(StockLevelDto::itemName).toArray(String[]::new);
                    Home.lstNotifi.setListData(names);
                    low.stream().limit(10).forEach(dto -> {
                        JPanel row = new JPanel(new BorderLayout(6, 0));
                        row.setOpaque(false);
                        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
                        row.setMinimumSize(new Dimension(0, 32));
                        row.setPreferredSize(new Dimension(0, 32));
                        row.setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xE2, 0xE5, 0xEA)),
                                new EmptyBorder(0, 0, 0, 0)));
                        JLabel name = new JLabel(dto.itemName());
                        name.setFont(name.getFont().deriveFont(12f));
                        name.setForeground(TEXT1);
                        row.add(name, BorderLayout.CENTER);
                        row.add(StatusPill.forStatus(dto.stockStatus()), BorderLayout.EAST);
                        lowStockList.add(row);
                    });
                    if (low.isEmpty()) lowStockList.add(new JLabel("All stock levels OK \u2713"));
                    lowStockList.revalidate();
                    lowStockList.repaint();
                }
                if (debtRemindersPanel != null && overdueCredits != null) {
                    debtRemindersPanel.removeAll();
                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM");
                    if (overdueCredits.isEmpty()) {
                        JLabel ok = new JLabel("No overdue credit invoices");
                        ok.setForeground(new Color(0x2E, 0x7D, 0x32));
                        ok.setFont(ok.getFont().deriveFont(12f));
                        debtRemindersPanel.add(ok);
                    } else {
                        overdueCredits.stream().limit(5).forEach(inv -> {
                            JPanel row = new JPanel(new BorderLayout(6, 0));
                            row.setOpaque(false);
                            row.setBorder(new EmptyBorder(3, 0, 3, 0));
                            String customer = inv.customerName() != null ? inv.customerName() : "Unknown";
                            double balance  = inv.netTotal() - inv.paid();
                            JLabel info = new JLabel("<html><b>" + customer + "</b>  Rs. " +
                                    String.format("%.0f", balance) + "</html>");
                            info.setFont(info.getFont().deriveFont(12f));
                            info.setForeground(RED);
                            String dateStr = inv.creditResolveDate() != null
                                    ? sdf.format(inv.creditResolveDate()) : "";
                            JLabel dateLbl = new JLabel(dateStr);
                            dateLbl.setFont(dateLbl.getFont().deriveFont(10f));
                            dateLbl.setForeground(AMBER);
                            row.add(info,    BorderLayout.CENTER);
                            row.add(dateLbl, BorderLayout.EAST);
                            debtRemindersPanel.add(row);
                        });
                    }
                    debtRemindersPanel.revalidate();
                    debtRemindersPanel.repaint();
                }
            }
        }.execute();
    }

    // ── Hero button ────────────────────────────────────────────────────────────

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

    // ── Grid tiles ─────────────────────────────────────────────────────────────

    private JPanel gridTile(String title, String sub, String card, String colorHex, Permission perm) {
        if (!SessionContext.hasPermission(perm)) {
            JPanel empty = new JPanel(); empty.setOpaque(false); return empty;
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
        info.add(t); info.add(s);
        tile.add(info, BorderLayout.CENTER);
        java.awt.event.MouseAdapter click = new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { Home.navigate(card); }
        };
        tile.addMouseListener(click); info.addMouseListener(click);
        t.addMouseListener(click);    s.addMouseListener(click);
        return tile;
    }

    // ── Stat tile builder ──────────────────────────────────────────────────────

    /** Adds a stat tile to parent and returns a direct reference to its value label. */
    private JLabel addStatTile(JPanel parent, String label, String initial, String colorHex) {
        CardPanel tile = new CardPanel(new BorderLayout(0, 4));
        tile.setBorder(new EmptyBorder(12, 14, 12, 14));
        JLabel lbl = new JLabel(label);
        lbl.setFont(lbl.getFont().deriveFont(11f));
        lbl.setForeground(TEXT2);
        JLabel val = new JLabel(initial);
        val.setFont(val.getFont().deriveFont(Font.BOLD, 16f));
        val.setForeground(Color.decode(colorHex));
        tile.add(lbl, BorderLayout.NORTH);
        tile.add(val, BorderLayout.CENTER);
        parent.add(tile);
        return val;
    }

    // ── Section label ──────────────────────────────────────────────────────────

    private JLabel sectionLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 11f));
        lbl.setForeground(TEXT2);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        return lbl;
    }

    // ── Sidebar cards ──────────────────────────────────────────────────────────

    private CardPanel buildDebtRemindersCard() {
        CardPanel c = new CardPanel(new BorderLayout(0, 8));
        c.setBorder(new EmptyBorder(14, 14, 14, 14));
        JLabel title = new JLabel("CREDIT REMINDERS");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 11f));
        title.setForeground(TEXT2);
        c.add(title, BorderLayout.NORTH);
        debtRemindersPanel = new JPanel();
        debtRemindersPanel.setOpaque(false);
        debtRemindersPanel.setLayout(new BoxLayout(debtRemindersPanel, BoxLayout.Y_AXIS));
        JLabel loading = new JLabel("Loading\u2026"); loading.setForeground(TEXT2);
        debtRemindersPanel.add(loading);
        c.add(debtRemindersPanel, BorderLayout.CENTER);
        JButton viewAll = new JButton("View All Credit Invoices \u2192");
        viewAll.setForeground(new Color(0x18, 0x5F, 0xA5));
        viewAll.setBorderPainted(false); viewAll.setContentAreaFilled(false); viewAll.setFocusPainted(false);
        viewAll.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        viewAll.addActionListener(e -> Home.navigate(Home.CARD_CREDIT_INVOICES));
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        btnRow.setOpaque(false); btnRow.add(viewAll);
        c.add(btnRow, BorderLayout.SOUTH);
        return c;
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
        lowStockList.add(new JLabel("Loading\u2026"));
        JScrollPane scroll = new JScrollPane(lowStockList);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setPreferredSize(new Dimension(0, 220));
        c.add(scroll, BorderLayout.CENTER);
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
        btnRow.setOpaque(false); btnRow.add(save);
        c.add(btnRow, BorderLayout.SOUTH);
        return c;
    }

    private CardPanel buildLinksCard() {
        linksCard = new CardPanel(new GridLayout(3, 1, 0, 0));
        linksCard.setBorder(new EmptyBorder(0, 0, 0, 0));
        return linksCard;
    }

    private void rebuildLinksCard() {
        if (linksCard == null) return;
        linksCard.removeAll();
        linksCard.add(link("Settings",         Home.CARD_SETTINGS, Permission.ACCESS_SETTINGS));
        linksCard.add(link("User Management",  Home.CARD_USERS,    Permission.MANAGE_USERS));
        linksCard.add(link("Backup & Restore", Home.CARD_BACKUP,   Permission.ACCESS_BACKUP));
        linksCard.revalidate();
        linksCard.repaint();
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
            l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            java.awt.event.MouseAdapter click = new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) { Home.navigate(card); }
            };
            row.addMouseListener(click); l.addMouseListener(click);
        } else {
            l.setForeground(TEXT2);
        }
        return row;
    }

    // ── Notes persistence ──────────────────────────────────────────────────────

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
            JOptionPane.showMessageDialog(this, "Could not save notes: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
