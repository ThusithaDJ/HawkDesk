package com.olympus.system.hawkdeskpos.frontend.reports;

import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.service.ReportService;
import com.olympus.system.hawkdeskpos.service.SettingsService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * Reports & Analytics screen.
 * Sections: Sales stats (period-scoped) · Top-selling items ·
 *           Stock overview (real-time) · Customer debt analytics (real-time).
 *
 * Period selector: Today / This Week / This Month / Custom (date pickers).
 * Refresh + Back are in the top-right, matching the other views.
 */
public class ReportsPanel extends JPanel implements com.olympus.system.hawkdeskpos.frontend.components.Refreshable {

    private static final Color BG      = new Color(0xF0, 0xF2, 0xF5);
    private static final Color TEXT2   = new Color(0x5A, 0x60, 0x70);
    private static final Color TEXT1   = new Color(0x1A, 0x1D, 0x23);
    private static final Color NAVY    = new Color(0x1E, 0x3A, 0x5F);
    private static final Color GREEN   = new Color(0x2E, 0x7D, 0x32);
    private static final Color PURPLE  = new Color(0x4A, 0x14, 0x8C);
    private static final Color RED     = new Color(0xC6, 0x28, 0x28);
    private static final Color AMBER   = new Color(0xE6, 0x51, 0x00);

    private static final String[] PERIODS = { "Today", "This Week", "This Month", "Custom" };

    private final ReportService reportService;

    // Period selector
    private JComboBox<String> periodCombo;
    private JPanel            customDatePanel;
    private JSpinner          fromSpinner, toSpinner;

    // Sales stat labels (period-scoped)
    private JLabel revenueVal, txVal, itemsSoldVal, avgSaleVal, profitVal, returnsVal;

    // Stock stat labels (real-time)
    private JLabel totalItemsVal, lowStockVal, outOfStockVal, stockValueVal;

    // Debt labels (real-time)
    private JLabel totalDebtVal, overdueVal;
    private JPanel debtorsList;

    // Top-items chart
    private JPanel topItemsBars;

    public ReportsPanel(ReportService reportService, SettingsService settingsService) {
        this.reportService = reportService;
        setBackground(BG);
        setLayout(new BorderLayout());
        buildUI();
    }

    @Override
    public void refresh() { loadDataAsync(); }

    // ── Build UI ──────────────────────────────────────────────────────────────

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        root.add(buildTopBar(),  BorderLayout.NORTH);
        root.add(buildContent(), BorderLayout.CENTER);

        add(root);
        loadDataAsync();
    }

    // ── Top bar ───────────────────────────────────────────────────────────────

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);

        // Title
        JLabel title = new JLabel("Reports & Analytics");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        bar.add(title, BorderLayout.WEST);

        // Refresh + Back — top right, matching all other views
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        JButton refreshBtn = new JButton("↺ Refresh");
        refreshBtn.addActionListener(e -> loadDataAsync());
        JButton back = new JButton("← Back");
        back.addActionListener(e -> Home.navigate(Home.CARD_DASH));
        btns.add(refreshBtn);
        btns.add(back);
        bar.add(btns, BorderLayout.EAST);

        // Period selector row (below title)
        bar.add(buildPeriodRow(), BorderLayout.SOUTH);
        return bar;
    }

    private JPanel buildPeriodRow() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setBorder(new EmptyBorder(8, 0, 0, 0));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);

        JLabel pLbl = new JLabel("Period:");
        pLbl.setForeground(TEXT2);
        left.add(pLbl);

        periodCombo = new JComboBox<>(PERIODS);
        periodCombo.setSelectedItem("Today");
        periodCombo.addActionListener(e -> {
            boolean custom = "Custom".equals(periodCombo.getSelectedItem());
            customDatePanel.setVisible(custom);
            if (!custom) loadDataAsync();
        });
        left.add(periodCombo);

        // Custom date pickers — hidden unless "Custom" is selected
        customDatePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        customDatePanel.setOpaque(false);
        customDatePanel.setVisible(false);

        fromSpinner = new JSpinner(new SpinnerDateModel());
        fromSpinner.setEditor(new JSpinner.DateEditor(fromSpinner, "yyyy-MM-dd"));
        fromSpinner.setPreferredSize(new Dimension(130, 28));
        fromSpinner.setValue(toStartOfDay(LocalDate.now()));

        toSpinner = new JSpinner(new SpinnerDateModel());
        toSpinner.setEditor(new JSpinner.DateEditor(toSpinner, "yyyy-MM-dd"));
        toSpinner.setPreferredSize(new Dimension(130, 28));
        toSpinner.setValue(toEndOfDay(LocalDate.now()));

        JButton applyBtn = new JButton("Apply");
        applyBtn.addActionListener(e -> loadDataAsync());

        customDatePanel.add(new JLabel("From:"));
        customDatePanel.add(fromSpinner);
        customDatePanel.add(new JLabel("To:"));
        customDatePanel.add(toSpinner);
        customDatePanel.add(applyBtn);

        left.add(customDatePanel);
        wrapper.add(left, BorderLayout.WEST);
        return wrapper;
    }

    // ── Scrollable content ────────────────────────────────────────────────────

    private JScrollPane buildContent() {
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        // Sales section
        content.add(sectionLabel("SALES ANALYTICS"));
        content.add(Box.createVerticalStrut(6));
        content.add(buildSalesStatRow());
        content.add(Box.createVerticalStrut(16));

        // Charts + Stock/Debt side panel
        JPanel mid = new JPanel(new GridLayout(1, 2, 14, 0));
        mid.setOpaque(false);
        mid.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        mid.setAlignmentX(Component.LEFT_ALIGNMENT);
        mid.add(buildTopItemsCard());
        mid.add(buildRightColumn());
        content.add(mid);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }

    // ── Sales stat row (6 cards) ──────────────────────────────────────────────

    private JPanel buildSalesStatRow() {
        JPanel row = new JPanel(new GridLayout(1, 6, 10, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        revenueVal   = addStatCard(row, "Revenue",      "Rs. 0.00", GREEN);
        txVal        = addStatCard(row, "Transactions", "0",        NAVY);
        itemsSoldVal = addStatCard(row, "Items Sold",   "0",        new Color(0x18, 0x5F, 0xA5));
        avgSaleVal   = addStatCard(row, "Avg. Sale",    "Rs. 0.00", PURPLE);
        profitVal    = addStatCard(row, "Profit",       "Rs. 0.00", new Color(0x00, 0x60, 0x64));
        returnsVal   = addStatCard(row, "Returns",      "0",        RED);
        return row;
    }

    // ── Top-selling items chart ───────────────────────────────────────────────

    private CardPanel buildTopItemsCard() {
        CardPanel c = new CardPanel(new BorderLayout(0, 10));
        c.setBorder(new EmptyBorder(14, 14, 14, 14));

        JLabel hdr = new JLabel("TOP SELLING ITEMS");
        hdr.setFont(hdr.getFont().deriveFont(Font.BOLD, 11f));
        hdr.setForeground(TEXT2);
        c.add(hdr, BorderLayout.NORTH);

        topItemsBars = new JPanel();
        topItemsBars.setOpaque(false);
        topItemsBars.setLayout(new BoxLayout(topItemsBars, BoxLayout.Y_AXIS));
        topItemsBars.add(new JLabel("  Loading…"));

        JScrollPane sp = new JScrollPane(topItemsBars);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        c.add(sp, BorderLayout.CENTER);

        JButton viewSales = linkButton("View Full Sales History →");
        viewSales.addActionListener(e -> Home.navigate(Home.CARD_HIST));
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        btnRow.setOpaque(false);
        btnRow.add(viewSales);
        c.add(btnRow, BorderLayout.SOUTH);
        return c;
    }

    // ── Right column: Stock + Debt ────────────────────────────────────────────

    private JPanel buildRightColumn() {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.add(buildStockCard());
        col.add(Box.createVerticalStrut(12));
        col.add(buildDebtCard());
        return col;
    }

    private CardPanel buildStockCard() {
        CardPanel c = new CardPanel(new BorderLayout(0, 10));
        c.setBorder(new EmptyBorder(14, 14, 14, 14));

        JLabel hdr = new JLabel("STOCK OVERVIEW");
        hdr.setFont(hdr.getFont().deriveFont(Font.BOLD, 11f));
        hdr.setForeground(TEXT2);
        c.add(hdr, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(2, 2, 10, 8));
        grid.setOpaque(false);
        totalItemsVal = addMiniCard(grid, "Total Items",  "0",      NAVY);
        lowStockVal   = addMiniCard(grid, "Low Stock",    "0",      AMBER);
        outOfStockVal = addMiniCard(grid, "Out of Stock", "0",      RED);
        stockValueVal = addMiniCard(grid, "Stock Value",  "Rs. 0",  GREEN);
        c.add(grid, BorderLayout.CENTER);

        JButton viewStock = linkButton("View Stock →");
        viewStock.addActionListener(e -> Home.navigate(Home.CARD_STOCK));
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        btnRow.setOpaque(false);
        btnRow.add(viewStock);
        c.add(btnRow, BorderLayout.SOUTH);
        return c;
    }

    private CardPanel buildDebtCard() {
        CardPanel c = new CardPanel(new BorderLayout(0, 10));
        c.setBorder(new EmptyBorder(14, 14, 14, 14));

        JLabel hdr = new JLabel("CUSTOMER DEBTS");
        hdr.setFont(hdr.getFont().deriveFont(Font.BOLD, 11f));
        hdr.setForeground(TEXT2);
        c.add(hdr, BorderLayout.NORTH);

        JPanel summaryRow = new JPanel(new GridLayout(1, 2, 10, 0));
        summaryRow.setOpaque(false);
        totalDebtVal = addMiniCard(summaryRow, "Total Outstanding", "Rs. 0", RED);
        overdueVal   = addMiniCard(summaryRow, "Overdue Invoices",  "0",     AMBER);
        c.add(summaryRow, BorderLayout.CENTER);

        debtorsList = new JPanel();
        debtorsList.setOpaque(false);
        debtorsList.setLayout(new BoxLayout(debtorsList, BoxLayout.Y_AXIS));

        JScrollPane sp = new JScrollPane(debtorsList);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.setPreferredSize(new Dimension(0, 110));

        JButton viewCredit = linkButton("View Credit Invoices →");
        viewCredit.addActionListener(e -> Home.navigate(Home.CARD_CREDIT_INVOICES));
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        btnRow.setOpaque(false);
        btnRow.add(viewCredit);

        JPanel south = new JPanel();
        south.setOpaque(false);
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.add(Box.createVerticalStrut(6));
        south.add(sp);
        south.add(btnRow);
        c.add(south, BorderLayout.SOUTH);
        return c;
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private void loadDataAsync() {
        Date[] range = getDateRange();
        new SwingWorker<Void, Void>() {
            ReportService.PeriodStats   stats;
            List<ReportService.TopItem> topItems;
            ReportService.StockSummary  stockSummary;
            ReportService.DebtSummary   debtSummary;

            @Override
            protected Void doInBackground() {
                try {
                    stats        = reportService.getStats(range[0], range[1]);
                    topItems     = reportService.getTopItems(range[0], range[1], 8);
                    stockSummary = reportService.getStockSummary();
                    debtSummary  = reportService.getDebtSummary();
                } catch (Exception e) {
                    System.err.println("ReportsPanel.loadDataAsync: " + e.getMessage());
                }
                return null;
            }

            @Override
            protected void done() {
                // Sales stats
                if (stats != null) {
                    revenueVal  .setText(String.format("Rs. %.2f", stats.revenue()));
                    txVal       .setText(String.valueOf(stats.transactions()));
                    itemsSoldVal.setText(String.valueOf(stats.itemsSold()));
                    avgSaleVal  .setText(String.format("Rs. %.2f", stats.avgSale()));
                    profitVal   .setText(String.format("Rs. %.2f", stats.profit()));
                    returnsVal  .setText(String.valueOf(stats.returns()));
                }

                // Top-selling items
                topItemsBars.removeAll();
                if (topItems != null && !topItems.isEmpty()) {
                    int max = topItems.stream().mapToInt(ReportService.TopItem::qtySold).max().orElse(1);
                    for (ReportService.TopItem item : topItems) {
                        topItemsBars.add(buildBarRow(item.name(), item.qtySold(), max,
                                String.format("Rs. %.0f", item.revenue())));
                        topItemsBars.add(Box.createVerticalStrut(6));
                    }
                } else {
                    JLabel empty = new JLabel("  No sales data for selected period.");
                    empty.setForeground(TEXT2);
                    topItemsBars.add(empty);
                }
                topItemsBars.revalidate();
                topItemsBars.repaint();

                // Stock overview
                if (stockSummary != null) {
                    totalItemsVal.setText(String.valueOf(stockSummary.totalItems()));
                    lowStockVal  .setText(String.valueOf(stockSummary.lowStock()));
                    outOfStockVal.setText(String.valueOf(stockSummary.outOfStock()));
                    stockValueVal.setText(String.format("Rs. %.0f", stockSummary.totalStockValue()));
                }

                // Customer debts
                if (debtSummary != null) {
                    totalDebtVal.setText(String.format("Rs. %.2f", debtSummary.totalOutstanding()));
                    overdueVal  .setText(String.valueOf(debtSummary.overdueCount()));

                    debtorsList.removeAll();
                    if (debtSummary.topDebtors().isEmpty()) {
                        JLabel ok = new JLabel("No outstanding credit balances");
                        ok.setForeground(GREEN);
                        ok.setFont(ok.getFont().deriveFont(12f));
                        debtorsList.add(ok);
                    } else {
                        for (ReportService.CustomerDebt d : debtSummary.topDebtors()) {
                            debtorsList.add(debtorRow(d.name(), d.outstanding()));
                            debtorsList.add(Box.createVerticalStrut(4));
                        }
                    }
                    debtorsList.revalidate();
                    debtorsList.repaint();
                }
            }
        }.execute();
    }

    // ── Date range helpers ────────────────────────────────────────────────────

    /**
     * Returns [from, to] dates for the selected period.
     * Uses toEndOfDay (23:59:59) so BETWEEN covers the full last day.
     */
    private Date[] getDateRange() {
        String period = (String) periodCombo.getSelectedItem();
        LocalDate today = LocalDate.now();
        return switch (period) {
            case "Today"      -> new Date[]{ toStartOfDay(today),                       toEndOfDay(today) };
            case "This Week"  -> new Date[]{ toStartOfDay(today.with(DayOfWeek.MONDAY)), toEndOfDay(today) };
            case "This Month" -> new Date[]{ toStartOfDay(today.withDayOfMonth(1)),      toEndOfDay(today) };
            case "Custom"     -> new Date[]{ (Date) fromSpinner.getValue(), (Date) toSpinner.getValue() };
            default           -> new Date[]{ toStartOfDay(today),                       toEndOfDay(today) };
        };
    }

    private static Date toStartOfDay(LocalDate d) {
        return Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static Date toEndOfDay(LocalDate d) {
        return Date.from(d.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant());
    }

    // ── Component factory helpers ─────────────────────────────────────────────

    private JLabel sectionLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 11f));
        lbl.setForeground(TEXT2);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        return lbl;
    }

    /**
     * Adds a stat card to parent and returns a direct reference to the value label.
     * Avoids fragile component-tree traversal by keeping the reference at creation time.
     */
    private JLabel addStatCard(JPanel parent, String label, String initial, Color valueColor) {
        CardPanel c = new CardPanel(new BorderLayout(0, 4));
        c.setBorder(new EmptyBorder(12, 14, 12, 14));
        JLabel l = new JLabel(label);
        l.setFont(l.getFont().deriveFont(11f));
        l.setForeground(TEXT2);
        JLabel v = new JLabel(initial);
        v.setFont(v.getFont().deriveFont(Font.BOLD, 16f));
        v.setForeground(valueColor);
        c.add(l, BorderLayout.NORTH);
        c.add(v, BorderLayout.CENTER);
        parent.add(c);
        return v;
    }

    /** Same as addStatCard but smaller text, for the 2×2 grids. */
    private JLabel addMiniCard(JPanel parent, String label, String initial, Color valueColor) {
        CardPanel c = new CardPanel(new BorderLayout(0, 2));
        c.setBorder(new EmptyBorder(8, 10, 8, 10));
        JLabel l = new JLabel(label);
        l.setFont(l.getFont().deriveFont(10f));
        l.setForeground(TEXT2);
        JLabel v = new JLabel(initial);
        v.setFont(v.getFont().deriveFont(Font.BOLD, 13f));
        v.setForeground(valueColor);
        c.add(l, BorderLayout.NORTH);
        c.add(v, BorderLayout.CENTER);
        parent.add(c);
        return v;
    }

    private JButton linkButton(String text) {
        JButton btn = new JButton(text);
        btn.setForeground(NAVY);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JPanel buildBarRow(String name, int value, int max, String revLabel) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel nameLbl = new JLabel(name);
        nameLbl.setPreferredSize(new Dimension(130, 0));
        nameLbl.setFont(nameLbl.getFont().deriveFont(12f));
        nameLbl.setForeground(TEXT1);

        JProgressBar bar = new JProgressBar(0, max);
        bar.setValue(value);
        bar.setStringPainted(true);
        bar.setString(value + " sold  " + revLabel);
        bar.setForeground(NAVY);
        bar.setPreferredSize(new Dimension(0, 22));

        row.add(nameLbl, BorderLayout.WEST);
        row.add(bar,     BorderLayout.CENTER);
        return row;
    }

    private JPanel debtorRow(String name, double outstanding) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(new EmptyBorder(0, 2, 0, 2));

        JLabel nameLbl = new JLabel(name);
        nameLbl.setFont(nameLbl.getFont().deriveFont(12f));
        nameLbl.setForeground(TEXT1);

        JLabel amtLbl = new JLabel(String.format("Rs. %.0f", outstanding));
        amtLbl.setFont(amtLbl.getFont().deriveFont(Font.BOLD, 12f));
        amtLbl.setForeground(RED);

        row.add(nameLbl, BorderLayout.CENTER);
        row.add(amtLbl,  BorderLayout.EAST);
        return row;
    }
}
