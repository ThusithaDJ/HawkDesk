package com.olympus.system.hawkdeskpos.frontend.reports;

import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.frontend.components.Refreshable;
import com.olympus.system.hawkdeskpos.service.ReportService;
import com.olympus.system.hawkdeskpos.service.SettingsService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * Reports & Analytics — tabbed view organised by business context.
 *
 * Tabs:
 *   1. Sales       — revenue, profit, top items, category breakdown, payment methods
 *   2. Inventory   — stock health, category distribution, expiring batches
 *   3. Customers   — top debtors, top buyers, credit analytics
 *   4. Finance     — cash-flow, income/expense, GRN costs, net position
 *   5. Returns     — return counts, reasons, write-offs
 *
 * Period selector (top bar) drives the period-scoped tabs; Inventory debt totals are real-time.
 */
public class ReportsPanel extends JPanel implements Refreshable {

    private static final Color BG     = new Color(0xF0, 0xF2, 0xF5);
    private static final Color TEXT2  = new Color(0x5A, 0x60, 0x70);
    private static final Color TEXT1  = new Color(0x1A, 0x1D, 0x23);
    private static final Color NAVY   = new Color(0x1E, 0x3A, 0x5F);
    private static final Color GREEN  = new Color(0x2E, 0x7D, 0x32);
    private static final Color PURPLE = new Color(0x4A, 0x14, 0x8C);
    private static final Color RED    = new Color(0xC6, 0x28, 0x28);
    private static final Color AMBER  = new Color(0xE6, 0x51, 0x00);
    private static final Color TEAL   = new Color(0x00, 0x60, 0x64);
    private static final Color BLUE   = new Color(0x18, 0x5F, 0xA5);

    private static final String[]        PERIODS = {"Today", "This Week", "This Month", "Custom"};
    private static final SimpleDateFormat SDF     = new SimpleDateFormat("yyyy-MM-dd");

    private final ReportService reportService;

    // Period selector
    private JComboBox<String> periodCombo;
    private JPanel            customDatePanel;
    private JSpinner          fromSpinner, toSpinner;

    // ── Tab 1: Sales ──────────────────────────────────────────────────────────
    private JLabel revenueVal, txVal, itemsSoldVal, avgSaleVal, profitVal, returnsVal;
    private JPanel topItemsBars;
    private DefaultTableModel categoryRevModel;
    private DefaultTableModel payMethodModel;

    // ── Tab 2: Inventory ──────────────────────────────────────────────────────
    private JLabel totalItemsVal, lowStockVal, outOfStockVal, stockValueVal;
    private DefaultTableModel categoryStockModel;
    private DefaultTableModel expiringModel;
    private JLabel            expiringCountLbl;

    // ── Tab 3: Customers & Credit ─────────────────────────────────────────────
    private JLabel customerCountVal, totalDebtVal, overdueVal, avgTxVal;
    private JPanel debtorsList;
    private DefaultTableModel topCustomersModel;

    // ── Tab 4: Finance ────────────────────────────────────────────────────────
    private JLabel finRevenueVal, finIncomeVal, finGrnVal, finExpenseVal, finNetVal;
    private JLabel finDiscountVal, finRefundVal, finGrnCountVal, finCreditOutstandingVal;
    private DefaultTableModel finPayMethodModel;

    // ── Tab 5: Returns & Losses ───────────────────────────────────────────────
    private JLabel retCountVal, retQtyVal, retRefundVal, writeOffVal;
    private DefaultTableModel returnReasonsModel;

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
        root.add(buildTopBar(), BorderLayout.NORTH);
        root.add(buildTabs(),   BorderLayout.CENTER);
        add(root);
        loadDataAsync();
    }

    // ── Top bar ───────────────────────────────────────────────────────────────

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);

        JLabel title = new JLabel("Reports & Analytics");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        bar.add(title, BorderLayout.WEST);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        JButton refreshBtn = new JButton("↺ Refresh");
        refreshBtn.addActionListener(e -> loadDataAsync());
        JButton back = new JButton("← Back");
        back.addActionListener(e -> Home.navigate(Home.CARD_DASH));
        btns.add(refreshBtn);
        btns.add(back);
        bar.add(btns, BorderLayout.EAST);

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
        customDatePanel.add(new JLabel("From:")); customDatePanel.add(fromSpinner);
        customDatePanel.add(new JLabel("To:"));   customDatePanel.add(toSpinner);
        customDatePanel.add(applyBtn);
        left.add(customDatePanel);

        JLabel note = new JLabel("  Applies to: Sales, Finance, Returns & Top Customers  |  Inventory debt is real-time");
        note.setFont(note.getFont().deriveFont(10f));
        note.setForeground(TEXT2);

        wrapper.add(left, BorderLayout.WEST);
        wrapper.add(note, BorderLayout.EAST);
        return wrapper;
    }

    // ── Tabs ──────────────────────────────────────────────────────────────────

    private JTabbedPane buildTabs() {
        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
        tabs.setFont(tabs.getFont().deriveFont(Font.BOLD, 12f));
        tabs.addTab("  Sales  ",       buildSalesTab());
        tabs.addTab("  Inventory  ",   buildInventoryTab());
        tabs.addTab("  Customers  ",   buildCustomersTab());
        tabs.addTab("  Finance  ",     buildFinanceTab());
        tabs.addTab("  Returns  ",     buildReturnsTab());
        return tabs;
    }

    // =========================================================================
    // Tab 1 — Sales
    // =========================================================================

    private JScrollPane buildSalesTab() {
        JPanel c = tabContent();

        c.add(sectionLabel("SALES ANALYTICS  (period-scoped)"));
        c.add(vgap(6));

        JPanel statRow = statRowPanel(6);
        revenueVal   = addStatCard(statRow, "Revenue",       "Rs. 0.00", GREEN);
        txVal        = addStatCard(statRow, "Transactions",  "0",        NAVY);
        itemsSoldVal = addStatCard(statRow, "Items Sold",    "0",        BLUE);
        avgSaleVal   = addStatCard(statRow, "Avg. Sale",     "Rs. 0.00", PURPLE);
        profitVal    = addStatCard(statRow, "Gross Profit",  "Rs. 0.00", TEAL);
        returnsVal   = addStatCard(statRow, "Returns",       "0",        RED);
        c.add(statRow);
        c.add(vgap(14));

        JPanel mid = midPanel();
        mid.add(buildTopItemsCard());
        mid.add(buildCategoryRevenueCard());
        c.add(mid);
        c.add(vgap(12));

        c.add(buildPayMethodCard());
        c.add(vgap(8));

        return scrollWrap(c);
    }

    private CardPanel buildTopItemsCard() {
        CardPanel card = card(new BorderLayout(0, 8));
        card.add(cardHeader("TOP SELLING ITEMS"), BorderLayout.NORTH);

        topItemsBars = new JPanel();
        topItemsBars.setOpaque(false);
        topItemsBars.setLayout(new BoxLayout(topItemsBars, BoxLayout.Y_AXIS));
        topItemsBars.add(loadingLbl());

        JScrollPane sp = plainScroll(topItemsBars);
        sp.setPreferredSize(new Dimension(0, 240));
        card.add(sp, BorderLayout.CENTER);

        JPanel south = linkRow();
        JButton lnk = linkButton("View Full Sales History →");
        lnk.addActionListener(e -> Home.navigate(Home.CARD_HIST));
        south.add(lnk);
        card.add(south, BorderLayout.SOUTH);
        return card;
    }

    private CardPanel buildCategoryRevenueCard() {
        CardPanel card = card(new BorderLayout(0, 8));
        card.add(cardHeader("REVENUE BY CATEGORY"), BorderLayout.NORTH);

        categoryRevModel = tableModel("Category", "Revenue (Rs.)", "Profit (Rs.)");
        JTable table = styledTable(categoryRevModel);
        alignRight(table, 1, 2);
        JScrollPane sp = new JScrollPane(table);
        sp.setPreferredSize(new Dimension(0, 240));
        card.add(sp, BorderLayout.CENTER);
        return card;
    }

    private CardPanel buildPayMethodCard() {
        CardPanel card = card(new BorderLayout(0, 8));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(cardHeader("PAYMENT METHOD BREAKDOWN"), BorderLayout.NORTH);

        payMethodModel = tableModel("Method", "Invoices", "Total (Rs.)");
        JTable table = styledTable(payMethodModel);
        table.setPreferredScrollableViewportSize(new Dimension(0, 90));
        alignRight(table, 1, 2);

        JPanel south = linkRow();
        JButton lnk = linkButton("View Cashflow →");
        lnk.addActionListener(e -> Home.navigate(Home.CARD_CASHFLOW));
        south.add(lnk);

        card.add(new JScrollPane(table), BorderLayout.CENTER);
        card.add(south, BorderLayout.SOUTH);
        return card;
    }

    // =========================================================================
    // Tab 2 — Inventory
    // =========================================================================

    private JScrollPane buildInventoryTab() {
        JPanel c = tabContent();

        c.add(sectionLabel("INVENTORY OVERVIEW  (real-time)"));
        c.add(vgap(6));

        JPanel statRow = statRowPanel(4);
        totalItemsVal = addStatCard(statRow, "Total Items",   "0",      NAVY);
        lowStockVal   = addStatCard(statRow, "Low Stock",     "0",      AMBER);
        outOfStockVal = addStatCard(statRow, "Out of Stock",  "0",      RED);
        stockValueVal = addStatCard(statRow, "Stock Value",   "Rs. 0",  GREEN);
        c.add(statRow);
        c.add(vgap(14));

        JPanel mid = midPanel();
        mid.add(buildCategoryStockCard());
        mid.add(buildExpiringBatchesCard());
        c.add(mid);
        c.add(vgap(8));

        JPanel links = linkRow();
        JButton b1 = linkButton("View Stock →");         b1.addActionListener(e -> Home.navigate(Home.CARD_STOCK));
        JButton b2 = linkButton("Low Stock Alerts →");   b2.addActionListener(e -> Home.navigate(Home.CARD_LOW_STOCK));
        JButton b3 = linkButton("GRN History →");        b3.addActionListener(e -> Home.navigate(Home.CARD_GRN_HIST));
        links.add(b1); links.add(b2); links.add(b3);
        c.add(links);

        return scrollWrap(c);
    }

    private CardPanel buildCategoryStockCard() {
        CardPanel card = card(new BorderLayout(0, 8));
        card.add(cardHeader("STOCK BY CATEGORY"), BorderLayout.NORTH);

        categoryStockModel = tableModel("Category", "Items", "Total Qty", "Value (Rs.)");
        JTable table = styledTable(categoryStockModel);
        alignRight(table, 1, 2, 3);
        JScrollPane sp = new JScrollPane(table);
        sp.setPreferredSize(new Dimension(0, 260));
        card.add(sp, BorderLayout.CENTER);
        return card;
    }

    private CardPanel buildExpiringBatchesCard() {
        CardPanel card = card(new BorderLayout(0, 8));

        JPanel hdrRow = new JPanel(new BorderLayout(8, 0));
        hdrRow.setOpaque(false);
        hdrRow.add(cardHeader("EXPIRING BATCHES  (next 30 days)"), BorderLayout.WEST);
        expiringCountLbl = new JLabel("");
        expiringCountLbl.setForeground(RED);
        expiringCountLbl.setFont(expiringCountLbl.getFont().deriveFont(Font.BOLD, 11f));
        hdrRow.add(expiringCountLbl, BorderLayout.EAST);
        card.add(hdrRow, BorderLayout.NORTH);

        expiringModel = tableModel("Item", "Batch", "Expiry Date", "Qty", "Cost (Rs.)");
        JTable table  = styledTable(expiringModel);
        alignRight(table, 3, 4);
        JScrollPane sp = new JScrollPane(table);
        sp.setPreferredSize(new Dimension(0, 260));
        card.add(sp, BorderLayout.CENTER);
        return card;
    }

    // =========================================================================
    // Tab 3 — Customers & Credit
    // =========================================================================

    private JScrollPane buildCustomersTab() {
        JPanel c = tabContent();

        c.add(sectionLabel("CUSTOMERS & CREDIT ANALYTICS"));
        c.add(vgap(6));

        JPanel statRow = statRowPanel(4);
        customerCountVal = addStatCard(statRow, "Total Customers",    "0",      NAVY);
        totalDebtVal     = addStatCard(statRow, "Outstanding Credit", "Rs. 0",  RED);
        overdueVal       = addStatCard(statRow, "Overdue Invoices",   "0",      AMBER);
        avgTxVal         = addStatCard(statRow, "Avg. Sale (period)", "Rs. 0",  PURPLE);
        c.add(statRow);
        c.add(vgap(14));

        JPanel mid = midPanel();
        mid.add(buildDebtCard());
        mid.add(buildTopCustomersCard());
        c.add(mid);
        c.add(vgap(8));

        JPanel links = linkRow();
        JButton b1 = linkButton("View All Customers →");    b1.addActionListener(e -> Home.navigate(Home.CARD_CUSTOMERS));
        JButton b2 = linkButton("View Credit Invoices →");  b2.addActionListener(e -> Home.navigate(Home.CARD_CREDIT_INVOICES));
        links.add(b1); links.add(b2);
        c.add(links);

        return scrollWrap(c);
    }

    private CardPanel buildDebtCard() {
        CardPanel card = card(new BorderLayout(0, 8));
        card.add(cardHeader("TOP DEBTORS  (real-time)"), BorderLayout.NORTH);

        debtorsList = new JPanel();
        debtorsList.setOpaque(false);
        debtorsList.setLayout(new BoxLayout(debtorsList, BoxLayout.Y_AXIS));

        JScrollPane sp = plainScroll(debtorsList);
        sp.setPreferredSize(new Dimension(0, 260));
        card.add(sp, BorderLayout.CENTER);
        return card;
    }

    private CardPanel buildTopCustomersCard() {
        CardPanel card = card(new BorderLayout(0, 8));
        card.add(cardHeader("TOP CUSTOMERS BY SPEND  (period-scoped)"), BorderLayout.NORTH);

        topCustomersModel = tableModel("Customer", "Invoices", "Total Spent (Rs.)");
        JTable table = styledTable(topCustomersModel);
        alignRight(table, 1, 2);
        JScrollPane sp = new JScrollPane(table);
        sp.setPreferredSize(new Dimension(0, 260));
        card.add(sp, BorderLayout.CENTER);
        return card;
    }

    // =========================================================================
    // Tab 4 — Finance
    // =========================================================================

    private JScrollPane buildFinanceTab() {
        JPanel c = tabContent();

        c.add(sectionLabel("FINANCE SUMMARY  (period-scoped)"));
        c.add(vgap(6));

        JPanel statRow = statRowPanel(5);
        finRevenueVal = addStatCard(statRow, "Sales Revenue", "Rs. 0.00", GREEN);
        finIncomeVal  = addStatCard(statRow, "Other Income",  "Rs. 0.00", TEAL);
        finGrnVal     = addStatCard(statRow, "GRN Purchases", "Rs. 0.00", AMBER);
        finExpenseVal = addStatCard(statRow, "Expenses",      "Rs. 0.00", RED);
        finNetVal     = addStatCard(statRow, "Net Position",  "Rs. 0.00", GREEN);
        c.add(statRow);
        c.add(vgap(14));

        JPanel mid = midPanel();
        mid.add(buildFinPayMethodCard());
        mid.add(buildFinExtrasCard());
        c.add(mid);
        c.add(vgap(8));

        JPanel links = linkRow();
        JButton b1 = linkButton("View Cashflow Detail →");  b1.addActionListener(e -> Home.navigate(Home.CARD_CASHFLOW));
        JButton b2 = linkButton("View Cash Accounts →");    b2.addActionListener(e -> Home.navigate(Home.CARD_CASH_ACCOUNTS));
        links.add(b1); links.add(b2);
        c.add(links);

        return scrollWrap(c);
    }

    private CardPanel buildFinPayMethodCard() {
        CardPanel card = card(new BorderLayout(0, 8));
        card.add(cardHeader("PAYMENT METHOD BREAKDOWN"), BorderLayout.NORTH);

        finPayMethodModel = tableModel("Method", "Invoices", "Revenue (Rs.)");
        JTable table = styledTable(finPayMethodModel);
        alignRight(table, 1, 2);
        JScrollPane sp = new JScrollPane(table);
        sp.setPreferredSize(new Dimension(0, 260));
        card.add(sp, BorderLayout.CENTER);
        return card;
    }

    private CardPanel buildFinExtrasCard() {
        CardPanel card = card(new BorderLayout(0, 10));
        card.add(cardHeader("OTHER FINANCIAL METRICS"), BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(2, 2, 10, 8));
        grid.setOpaque(false);
        finDiscountVal        = addMiniCard(grid, "Discounts Given",     "Rs. 0", AMBER);
        finRefundVal          = addMiniCard(grid, "Cash Refunds Paid",   "Rs. 0", RED);
        finGrnCountVal        = addMiniCard(grid, "GRN Orders",          "0",     NAVY);
        finCreditOutstandingVal = addMiniCard(grid, "Credit Outstanding","Rs. 0", RED);
        card.add(grid, BorderLayout.CENTER);
        return card;
    }

    // =========================================================================
    // Tab 5 — Returns
    // =========================================================================

    private JScrollPane buildReturnsTab() {
        JPanel c = tabContent();

        c.add(sectionLabel("RETURNS & WRITE-OFFS  (period-scoped)"));
        c.add(vgap(6));

        JPanel statRow = statRowPanel(4);
        retCountVal  = addStatCard(statRow, "Total Returns",  "0",      RED);
        retQtyVal    = addStatCard(statRow, "Qty Returned",   "0.0",    AMBER);
        retRefundVal = addStatCard(statRow, "Cash Refunds",   "Rs. 0",  RED);
        writeOffVal  = addStatCard(statRow, "Write-offs",     "0",      PURPLE);
        c.add(statRow);
        c.add(vgap(14));

        CardPanel reasonCard = card(new BorderLayout(0, 8));
        reasonCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        reasonCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        reasonCard.add(cardHeader("RETURNS BREAKDOWN BY REASON"), BorderLayout.NORTH);

        returnReasonsModel = tableModel("Reason", "Count", "Total Qty");
        JTable table = styledTable(returnReasonsModel);
        alignRight(table, 1, 2);
        table.setPreferredScrollableViewportSize(new Dimension(0, 200));
        reasonCard.add(new JScrollPane(table), BorderLayout.CENTER);
        c.add(reasonCard);
        c.add(vgap(8));

        JPanel links = linkRow();
        JButton b1 = linkButton("View All Returns →");   b1.addActionListener(e -> Home.navigate(Home.CARD_RETURNS_LIST));
        JButton b2 = linkButton("Process Return →");     b2.addActionListener(e -> Home.navigate(Home.CARD_RETURNS));
        links.add(b1); links.add(b2);
        c.add(links);

        return scrollWrap(c);
    }

    // =========================================================================
    // Data loading
    // =========================================================================

    private void loadDataAsync() {
        Date[] range = getDateRange();
        new SwingWorker<Void, Void>() {
            ReportService.PeriodStats              stats;
            List<ReportService.TopItem>            topItems;
            List<ReportService.CategoryStat>       categoryStats;
            ReportService.CashflowData             cashflowData;
            ReportService.StockSummary             stockSummary;
            List<ReportService.CategoryStock>      categoryStock;
            List<ReportService.ExpiringBatch>      expiringBatches;
            ReportService.DebtSummary              debtSummary;
            List<ReportService.TopCustomer>        topCustomers;
            long                                   customerCount;
            ReportService.FinanceSummary           financeSummary;
            ReportService.ReturnsSummary           returnsSummary;
            List<ReportService.ReturnReasonStat>   returnReasons;

            @Override
            protected Void doInBackground() {
                try {
                    stats           = reportService.getStats(range[0], range[1]);
                    topItems        = reportService.getTopItems(range[0], range[1], 8);
                    categoryStats   = reportService.getCategoryStats(range[0], range[1]);
                    cashflowData    = reportService.getCashflowData(range[0], range[1]);
                    stockSummary    = reportService.getStockSummary();
                    categoryStock   = reportService.getCategoryStock();
                    expiringBatches = reportService.getExpiringBatches(30);
                    debtSummary     = reportService.getDebtSummary();
                    topCustomers    = reportService.getTopCustomers(range[0], range[1], 8);
                    customerCount   = reportService.getCustomerCount();
                    financeSummary  = reportService.getFinanceSummary(range[0], range[1]);
                    returnsSummary  = reportService.getReturnsSummary(range[0], range[1]);
                    returnReasons   = reportService.getReturnsByReason(range[0], range[1]);
                } catch (Exception e) {
                    System.err.println("ReportsPanel.loadDataAsync: " + e.getMessage());
                }
                return null;
            }

            @Override
            protected void done() {
                // ── Tab 1: Sales ──────────────────────────────────────────────
                if (stats != null) {
                    revenueVal  .setText(rs(stats.revenue()));
                    txVal       .setText(String.valueOf(stats.transactions()));
                    itemsSoldVal.setText(String.valueOf(stats.itemsSold()));
                    avgSaleVal  .setText(rs(stats.avgSale()));
                    profitVal   .setText(rs(stats.profit()));
                    returnsVal  .setText(String.valueOf(stats.returns()));
                }

                topItemsBars.removeAll();
                if (topItems != null && !topItems.isEmpty()) {
                    int max = topItems.stream().mapToInt(ReportService.TopItem::qtySold).max().orElse(1);
                    for (var item : topItems) {
                        topItemsBars.add(buildBarRow(item.name(), item.qtySold(), max, rs0(item.revenue())));
                        topItemsBars.add(vgap(6));
                    }
                } else {
                    topItemsBars.add(emptyLbl("No sales data for selected period."));
                }
                topItemsBars.revalidate(); topItemsBars.repaint();

                categoryRevModel.setRowCount(0);
                if (categoryStats != null)
                    for (var cs : categoryStats)
                        categoryRevModel.addRow(new Object[]{ cs.category(), rs0(cs.revenue()), rs0(cs.profit()) });

                payMethodModel.setRowCount(0);
                if (cashflowData != null) {
                    payMethodModel.addRow(new Object[]{ "Cash",   cashflowData.cash().count(),         rs0(cashflowData.cash().total()) });
                    payMethodModel.addRow(new Object[]{ "Card",   cashflowData.card().count(),         rs0(cashflowData.card().total()) });
                    payMethodModel.addRow(new Object[]{ "Cheque", cashflowData.cheque().count(),       rs0(cashflowData.cheque().total()) });
                    payMethodModel.addRow(new Object[]{ "Credit", cashflowData.creditIssued().count(), rs0(cashflowData.creditIssued().total()) });
                }

                // ── Tab 2: Inventory ──────────────────────────────────────────
                if (stockSummary != null) {
                    totalItemsVal.setText(String.valueOf(stockSummary.totalItems()));
                    lowStockVal  .setText(String.valueOf(stockSummary.lowStock()));
                    outOfStockVal.setText(String.valueOf(stockSummary.outOfStock()));
                    stockValueVal.setText(rs0(stockSummary.totalStockValue()));
                }

                categoryStockModel.setRowCount(0);
                if (categoryStock != null)
                    for (var cs : categoryStock)
                        categoryStockModel.addRow(new Object[]{ cs.category(), cs.itemCount(),
                            String.format("%.1f", cs.totalQty()), rs0(cs.totalValue()) });

                expiringModel.setRowCount(0);
                if (expiringBatches != null) {
                    expiringCountLbl.setText(expiringBatches.isEmpty() ? "" : expiringBatches.size() + " batch(es)!");
                    for (var eb : expiringBatches)
                        expiringModel.addRow(new Object[]{ eb.itemName(), eb.batchNumber(),
                            eb.expiryDate() != null ? SDF.format(eb.expiryDate()) : "—",
                            eb.qtyRemaining(), rs0(eb.costPrice()) });
                }

                // ── Tab 3: Customers ──────────────────────────────────────────
                customerCountVal.setText(String.valueOf(customerCount));
                if (stats != null) avgTxVal.setText(rs(stats.avgSale()));

                if (debtSummary != null) {
                    totalDebtVal.setText(rs(debtSummary.totalOutstanding()));
                    overdueVal  .setText(String.valueOf(debtSummary.overdueCount()));
                    debtorsList.removeAll();
                    if (debtSummary.topDebtors().isEmpty()) {
                        JLabel ok = new JLabel("  No outstanding credit balances");
                        ok.setForeground(GREEN);
                        debtorsList.add(ok);
                    } else {
                        for (var d : debtSummary.topDebtors()) {
                            debtorsList.add(debtorRow(d.name(), d.outstanding()));
                            debtorsList.add(vgap(4));
                        }
                    }
                    debtorsList.revalidate(); debtorsList.repaint();
                }

                topCustomersModel.setRowCount(0);
                if (topCustomers != null)
                    for (var tc : topCustomers)
                        topCustomersModel.addRow(new Object[]{ tc.name(), tc.invoiceCount(), rs0(tc.totalSpent()) });

                // ── Tab 4: Finance ────────────────────────────────────────────
                if (financeSummary != null) {
                    double grossIn  = financeSummary.salesRevenue() + financeSummary.otherIncome();
                    double grossOut = financeSummary.grnCost() + financeSummary.expenses() + financeSummary.refundsGiven();
                    double net      = grossIn - grossOut;
                    finRevenueVal.setText(rs(financeSummary.salesRevenue()));
                    finIncomeVal .setText(rs(financeSummary.otherIncome()));
                    finGrnVal    .setText(rs(financeSummary.grnCost()));
                    finExpenseVal.setText(rs(financeSummary.expenses()));
                    finNetVal    .setText(rs(net));
                    finNetVal    .setForeground(net >= 0 ? GREEN : RED);
                    finDiscountVal.setText(rs(financeSummary.discountGiven()));
                    finRefundVal  .setText(rs(financeSummary.refundsGiven()));
                }
                if (cashflowData != null) {
                    finGrnCountVal.setText(String.valueOf(cashflowData.grnCount()));
                    finCreditOutstandingVal.setText(rs(cashflowData.creditOutstanding()));
                    finPayMethodModel.setRowCount(0);
                    finPayMethodModel.addRow(new Object[]{ "Cash",          cashflowData.cash().count(),         rs0(cashflowData.cash().total()) });
                    finPayMethodModel.addRow(new Object[]{ "Card",          cashflowData.card().count(),         rs0(cashflowData.card().total()) });
                    finPayMethodModel.addRow(new Object[]{ "Cheque",        cashflowData.cheque().count(),       rs0(cashflowData.cheque().total()) });
                    finPayMethodModel.addRow(new Object[]{ "Credit issued", cashflowData.creditIssued().count(), rs0(cashflowData.creditIssued().total()) });
                }

                // ── Tab 5: Returns ────────────────────────────────────────────
                if (returnsSummary != null) {
                    retCountVal .setText(String.valueOf(returnsSummary.totalCount()));
                    retQtyVal   .setText(String.format("%.1f", returnsSummary.totalQty()));
                    retRefundVal.setText(rs(returnsSummary.totalRefundValue()));
                    writeOffVal .setText(String.valueOf(returnsSummary.writeOffCount()));
                }
                returnReasonsModel.setRowCount(0);
                if (returnReasons != null)
                    for (var rr : returnReasons)
                        returnReasonsModel.addRow(new Object[]{ rr.reason(), rr.count(),
                            String.format("%.1f", rr.qty()) });
            }
        }.execute();
    }

    // =========================================================================
    // Date range helpers
    // =========================================================================

    private Date[] getDateRange() {
        String period = (String) periodCombo.getSelectedItem();
        LocalDate today = LocalDate.now();
        return switch (period) {
            case "Today"      -> new Date[]{ toStartOfDay(today),                        toEndOfDay(today) };
            case "This Week"  -> new Date[]{ toStartOfDay(today.with(DayOfWeek.MONDAY)), toEndOfDay(today) };
            case "This Month" -> new Date[]{ toStartOfDay(today.withDayOfMonth(1)),       toEndOfDay(today) };
            case "Custom"     -> new Date[]{ (Date) fromSpinner.getValue(), (Date) toSpinner.getValue() };
            default           -> new Date[]{ toStartOfDay(today),                        toEndOfDay(today) };
        };
    }

    private static Date toStartOfDay(LocalDate d) {
        return Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static Date toEndOfDay(LocalDate d) {
        return Date.from(d.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant());
    }

    // =========================================================================
    // Component factory helpers
    // =========================================================================

    private JPanel tabContent() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(12, 8, 12, 8));
        return p;
    }

    private JPanel statRowPanel(int cols) {
        JPanel row = new JPanel(new GridLayout(1, cols, 10, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    private JPanel midPanel() {
        JPanel mid = new JPanel(new GridLayout(1, 2, 14, 0));
        mid.setOpaque(false);
        mid.setAlignmentX(Component.LEFT_ALIGNMENT);
        return mid;
    }

    private CardPanel card(LayoutManager layout) {
        CardPanel c = new CardPanel(layout);
        c.setBorder(new EmptyBorder(14, 14, 14, 14));
        return c;
    }

    private JScrollPane scrollWrap(JPanel content) {
        JScrollPane sp = new JScrollPane(content);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return sp;
    }

    private JScrollPane plainScroll(Component content) {
        JScrollPane sp = new JScrollPane(content);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        return sp;
    }

    private Component vgap(int h) { return Box.createVerticalStrut(h); }

    private JLabel sectionLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 11f));
        lbl.setForeground(TEXT2);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        return lbl;
    }

    private JLabel cardHeader(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 11f));
        lbl.setForeground(TEXT2);
        return lbl;
    }

    private JLabel loadingLbl() {
        JLabel lbl = new JLabel("  Loading…");
        lbl.setForeground(TEXT2);
        return lbl;
    }

    private JLabel emptyLbl(String text) {
        JLabel lbl = new JLabel("  " + text);
        lbl.setForeground(TEXT2);
        return lbl;
    }

    private JPanel linkRow() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

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

    private DefaultTableModel tableModel(String... cols) {
        return new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
    }

    private JTable styledTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.setRowHeight(24);
        table.setGridColor(new Color(0xE0, 0xE0, 0xE0));
        table.setSelectionBackground(new Color(0xBB, 0xDE, 0xFB));
        table.setFont(table.getFont().deriveFont(12f));
        JTableHeader header = table.getTableHeader();
        header.setBackground(NAVY);
        header.setForeground(Color.WHITE);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 11f));
        header.setReorderingAllowed(false);
        return table;
    }

    private void alignRight(JTable table, int... cols) {
        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        for (int col : cols)
            table.getColumnModel().getColumn(col).setCellRenderer(right);
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

    private static String rs(double v)  { return String.format("Rs. %.2f", v); }
    private static String rs0(double v) { return String.format("Rs. %.0f", v); }
}
