package com.olympus.system.hawkdeskpos.frontend.sale;

import com.olympus.system.hawkdeskpos.dto.InvoiceDto;
import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.service.SaleService;

import com.olympus.system.hawkdeskpos.dto.SaleLineDto;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * Sales History screen.
 * Period selector (Today default) + search + date range + sortable table + detail panel.
 */
public class SalesHistoryPanel extends JPanel implements com.olympus.system.hawkdeskpos.frontend.components.Refreshable {

    private static final Color BG    = new Color(0xF0, 0xF2, 0xF5);
    private static final Color TEXT2 = new Color(0x5A, 0x60, 0x70);
    private static final Color NAVY  = new Color(0x1E, 0x3A, 0x5F);

    private static final String[] PERIODS = {
            "Today", "This Week", "Last Week", "This Month", "Last Month", "This Year", "Custom"
    };

    private final SaleService saleService;

    private JLabel todayRevLabel, txCountLabel, weekRevLabel, returnsLabel;
    private JTextField searchField;
    private JComboBox<String> paymentFilter;
    private JComboBox<String> periodCombo;
    private JPanel  customDatePanel;
    private JSpinner fromSpinner, toSpinner;
    private DefaultTableModel tableModel;
    private JTable table;
    private List<InvoiceDto> allInvoices;
    private List<InvoiceDto> displayedInvoices;
    private JPanel detailPanel;

    private static final String[] COLS = {
            "Invoice #", "Date/Time", "Cashier", "Items", "Payment", "Amount (Rs.)", "Status"
    };

    public SalesHistoryPanel(SaleService saleService) {
        this.saleService = saleService;
        setBackground(BG);
        setLayout(new BorderLayout());
        buildUI();
    }

    public void refresh() { loadDataAsync(); }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        // Top bar
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        JLabel title = new JLabel("Sales History");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        topBar.add(title, BorderLayout.WEST);
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        JButton refreshBtn = new JButton("↺ Refresh");
        refreshBtn.addActionListener(e -> {
            searchField.setText("");
            periodCombo.setSelectedItem("Today");
            loadDataAsync();
        });
        JButton back = new JButton("← Back");
        back.addActionListener(e -> Home.navigate(Home.CARD_DASH));
        btns.add(refreshBtn);
        btns.add(back);
        topBar.add(btns, BorderLayout.EAST);
        root.add(topBar, BorderLayout.NORTH);

        // Stat bar
        JPanel statBar = new JPanel(new GridLayout(1, 4, 10, 0));
        statBar.setOpaque(false);
        statBar.add(statCard("Today's Revenue", "—"));
        todayRevLabel = valueLabel(statBar);
        statBar.add(statCard("Today's Transactions", "—"));
        txCountLabel  = valueLabel(statBar);
        statBar.add(statCard("This Week Revenue", "—"));
        weekRevLabel  = valueLabel(statBar);
        statBar.add(statCard("Returns (Total)", "—"));
        returnsLabel  = valueLabel(statBar);

        // Main split
        JPanel content = new JPanel(new BorderLayout(10, 0));
        content.setOpaque(false);

        // Left: filters + table
        JPanel left = new JPanel(new BorderLayout(0, 8));
        left.setOpaque(false);
        left.add(buildFilters(), BorderLayout.NORTH);

        tableModel = new DefaultTableModel(COLS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel);
        table.setRowHeight(38);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.getTableHeader().setFont(table.getFont().deriveFont(Font.BOLD, 12f));
        table.getTableHeader().setBackground(new Color(0xF7, 0xF8, 0xFA));
        table.getTableHeader().setForeground(TEXT2);
        table.setAutoCreateRowSorter(true);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && table.getSelectedRow() >= 0) {
                int modelRow = table.convertRowIndexToModel(table.getSelectedRow());
                if (displayedInvoices != null && modelRow < displayedInvoices.size()) {
                    showDetail(displayedInvoices.get(modelRow));
                }
            }
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(null);
        CardPanel tableCard = new CardPanel(new BorderLayout());
        ((JPanel)tableCard).setBorder(new EmptyBorder(0, 0, 0, 0));
        ((JPanel)tableCard).add(scroll, BorderLayout.CENTER);
        left.add(tableCard, BorderLayout.CENTER);
        content.add(left, BorderLayout.CENTER);

        // Right: detail panel
        detailPanel = buildDetailPanel();
        detailPanel.setPreferredSize(new Dimension(360, 0));
        detailPanel.setVisible(false);
        content.add(detailPanel, BorderLayout.EAST);

        JPanel main = new JPanel(new BorderLayout(0, 10));
        main.setOpaque(false);
        main.add(statBar, BorderLayout.NORTH);
        main.add(content, BorderLayout.CENTER);

        root.add(main, BorderLayout.CENTER);
        add(root);
        loadDataAsync();
    }

    private JPanel buildFilters() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 6));
        wrapper.setOpaque(false);

        // Row 1: period + payment + search
        CardPanel row1 = new CardPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        ((JPanel)row1).setBorder(new EmptyBorder(4, 10, 4, 10));

        periodCombo = new JComboBox<>(PERIODS);
        periodCombo.setSelectedItem("Today");
        periodCombo.addActionListener(e -> {
            boolean custom = "Custom".equals(periodCombo.getSelectedItem());
            customDatePanel.setVisible(custom);
            if (!custom) loadDataAsync();
        });

        paymentFilter = new JComboBox<>(new String[]{"All Methods", "CASH", "CARD", "CREDIT"});
        paymentFilter.addActionListener(e -> applyFilter());

        searchField = new JTextField(16);
        searchField.putClientProperty("JTextField.placeholderText", "Invoice # …");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) {}
        });

        ((JPanel)row1).add(new JLabel("Period:"));
        ((JPanel)row1).add(periodCombo);
        ((JPanel)row1).add(new JLabel("Payment:"));
        ((JPanel)row1).add(paymentFilter);
        ((JPanel)row1).add(new JLabel("Search:"));
        ((JPanel)row1).add(searchField);
        wrapper.add(row1, BorderLayout.NORTH);

        // Row 2: custom date range (hidden by default)
        customDatePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        customDatePanel.setOpaque(false);
        customDatePanel.setVisible(false);

        SpinnerDateModel fromModel = new SpinnerDateModel();
        fromSpinner = new JSpinner(fromModel);
        fromSpinner.setEditor(new JSpinner.DateEditor(fromSpinner, "yyyy-MM-dd"));
        fromSpinner.setPreferredSize(new Dimension(130, 28));

        SpinnerDateModel toModel = new SpinnerDateModel();
        toSpinner = new JSpinner(toModel);
        toSpinner.setEditor(new JSpinner.DateEditor(toSpinner, "yyyy-MM-dd"));
        toSpinner.setPreferredSize(new Dimension(130, 28));

        // Default from/to to today
        fromSpinner.setValue(toStartOfDay(LocalDate.now()));
        toSpinner.setValue(toEndOfDay(LocalDate.now()));

        JButton applyCustom = new JButton("Apply");
        applyCustom.addActionListener(e -> loadDataAsync());

        customDatePanel.add(new JLabel("From:"));
        customDatePanel.add(fromSpinner);
        customDatePanel.add(new JLabel("To:"));
        customDatePanel.add(toSpinner);
        customDatePanel.add(applyCustom);
        wrapper.add(customDatePanel, BorderLayout.CENTER);

        return wrapper;
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private void loadDataAsync() {
        Date[] range = getDateRange();
        Date from = range[0];
        Date to   = range[1];

        new SwingWorker<List<InvoiceDto>, Void>() {
            private double todayRev;
            private int    todayTx;
            private double weekRev;
            private long   returns;

            @Override protected List<InvoiceDto> doInBackground() {
                SaleService.TodaySummary s = saleService.getTodaySummary();
                todayRev = s.revenue();
                todayTx  = s.transactions();
                weekRev  = saleService.getWeekRevenue();
                returns  = saleService.getReturnCount();
                return saleService.listInvoices(null, from, to, null);
            }
            @Override protected void done() {
                try {
                    allInvoices = get();
                    todayRevLabel.setText(String.format("Rs. %.2f", todayRev));
                    txCountLabel.setText(String.valueOf(todayTx));
                    weekRevLabel.setText(String.format("Rs. %.2f", weekRev));
                    returnsLabel.setText(String.valueOf(returns));
                    applyFilter();
                } catch (Exception e) {
                    System.err.println("SalesHistoryPanel.loadDataAsync: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void applyFilter() {
        if (allInvoices == null) return;
        String q      = searchField.getText().trim().toLowerCase();
        String method = (String) paymentFilter.getSelectedItem();

        var filtered = allInvoices.stream().filter(inv -> {
            boolean matchQ = q.isEmpty()
                    || inv.invoiceNo().toLowerCase().contains(q)
                    || (inv.cashierName() != null && inv.cashierName().toLowerCase().contains(q));
            boolean matchM = "All Methods".equals(method) || method.equals(inv.paymentMethod());
            return matchQ && matchM;
        }).toList();
        populateTable(filtered);
    }

    private void populateTable(List<InvoiceDto> invs) {
        displayedInvoices = invs;
        tableModel.setRowCount(0);
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        for (InvoiceDto inv : invs) {
            tableModel.addRow(new Object[]{
                    inv.invoiceNo(),
                    inv.date() != null ? fmt.format(inv.date()) : "—",
                    inv.cashierName() != null ? inv.cashierName() : "—",
                    inv.lines().size(),
                    inv.paymentMethod(),
                    String.format("%.2f", inv.total()),
                    inv.stat()
            });
        }
    }

    // ── Date range helpers ────────────────────────────────────────────────────

    private Date[] getDateRange() {
        String period = (String) periodCombo.getSelectedItem();
        LocalDate today = LocalDate.now();
        return switch (period) {
            case "Today"      -> new Date[]{ toStartOfDay(today),                          toEndOfDay(today) };
            case "This Week"  -> new Date[]{ toStartOfDay(today.with(DayOfWeek.MONDAY)),   toEndOfDay(today) };
            case "Last Week"  -> {
                LocalDate mon = today.minusWeeks(1).with(DayOfWeek.MONDAY);
                yield new Date[]{ toStartOfDay(mon), toEndOfDay(mon.with(DayOfWeek.SUNDAY)) };
            }
            case "This Month" -> new Date[]{ toStartOfDay(today.withDayOfMonth(1)),        toEndOfDay(today) };
            case "Last Month" -> {
                LocalDate first = today.minusMonths(1).withDayOfMonth(1);
                yield new Date[]{ toStartOfDay(first), toEndOfDay(first.withDayOfMonth(first.lengthOfMonth())) };
            }
            case "This Year"  -> new Date[]{ toStartOfDay(today.withDayOfYear(1)),         toEndOfDay(today) };
            case "Custom"     -> new Date[]{ (Date) fromSpinner.getValue(), (Date) toSpinner.getValue() };
            default           -> new Date[]{ null, null };
        };
    }

    private static Date toStartOfDay(LocalDate d) {
        return Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static Date toEndOfDay(LocalDate d) {
        return Date.from(d.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant());
    }

    // ── Detail panel ──────────────────────────────────────────────────────────

    private JPanel buildDetailPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);

        CardPanel c = new CardPanel(new BorderLayout(0, 10));
        ((JPanel)c).setBorder(new EmptyBorder(14, 14, 14, 14));
        JLabel title = new JLabel("INVOICE DETAIL");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 11f));
        title.setForeground(TEXT2);
        ((JPanel)c).add(title, BorderLayout.NORTH);
        JLabel placeholder = new JLabel("<html><center>Select a row<br>to view details</center></html>");
        placeholder.setForeground(TEXT2);
        placeholder.setHorizontalAlignment(SwingConstants.CENTER);
        ((JPanel)c).add(placeholder, BorderLayout.CENTER);
        panel.add(c, BorderLayout.CENTER);
        return panel;
    }

    private void showDetail(InvoiceDto inv) {
        detailPanel.removeAll();
        detailPanel.setVisible(true);

        CardPanel c = new CardPanel(new BorderLayout(0, 8));
        ((JPanel)c).setBorder(new EmptyBorder(14, 14, 14, 14));

        // ── Title ────────────────────────────────────────────────────────────
        JLabel title = new JLabel("INVOICE DETAIL");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 11f));
        title.setForeground(TEXT2);
        ((JPanel)c).add(title, BorderLayout.NORTH);

        // ── Scrollable body (header + items table + totals) ───────────────────
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        // Header fields
        JPanel info = new JPanel(new GridLayout(0, 1, 0, 5));
        info.setOpaque(false);
        info.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        info.setBorder(new EmptyBorder(0, 0, 10, 0));
        info.add(detailRow("Invoice #", inv.invoiceNo()));
        info.add(detailRow("Date",      inv.date() != null
                ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(inv.date()) : "—"));
        info.add(detailRow("Cashier",   inv.cashierName() != null ? inv.cashierName() : "—"));
        info.add(detailRow("Payment",   inv.paymentMethod()));
        info.add(detailRow("Status",    inv.stat()));
        body.add(info);

        // Items section label
        JLabel itemsTitle = new JLabel("ITEMS");
        itemsTitle.setFont(itemsTitle.getFont().deriveFont(Font.BOLD, 10f));
        itemsTitle.setForeground(TEXT2);
        itemsTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(itemsTitle);
        body.add(Box.createVerticalStrut(4));

        // Items table
        String[] lineCols = {"Item", "SKU", "Batch", "Qty", "Price", "Total"};
        DefaultTableModel lineModel = new DefaultTableModel(lineCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable lineTable = new JTable(lineModel);
        lineTable.setRowHeight(26);
        lineTable.setShowGrid(false);
        lineTable.setIntercellSpacing(new Dimension(0, 0));
        lineTable.getTableHeader().setFont(lineTable.getFont().deriveFont(Font.BOLD, 10f));
        lineTable.getTableHeader().setBackground(new Color(0xF7, 0xF8, 0xFA));
        lineTable.setFont(lineTable.getFont().deriveFont(11f));

        for (SaleLineDto line : inv.lines()) {
            lineModel.addRow(new Object[]{
                    line.itemName(),
                    line.sku() != null ? line.sku() : "",
                    line.batch() != null ? line.batch() : "",
                    line.qty(),
                    String.format("%.2f", line.unitPrice()),
                    String.format("%.2f", line.lineTotal())
            });
        }

        // Show up to 6 rows then scroll — fix height, let BoxLayout stretch width
        int visRows = Math.min(Math.max(inv.lines().size(), 1), 6);
        int scrollH  = 24 + visRows * 26 + 2; // header + rows
        JScrollPane lineScroll = new JScrollPane(lineTable);
        lineScroll.setPreferredSize(new Dimension(300, scrollH));
        lineScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, scrollH));
        lineScroll.setBorder(BorderFactory.createLineBorder(new Color(0xE2, 0xE5, 0xEA)));
        lineScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(lineScroll);
        body.add(Box.createVerticalStrut(10));

        // Totals
        JPanel totals = new JPanel(new GridLayout(0, 1, 0, 4));
        totals.setOpaque(false);
        totals.setAlignmentX(Component.LEFT_ALIGNMENT);
        totals.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        totals.setBorder(new EmptyBorder(2, 0, 0, 0));
        if (inv.discount() > 0) totals.add(detailRow("Discount", "Rs. " + String.format("%.2f", inv.discount())));
        totals.add(detailRow("Total",   "Rs. " + String.format("%.2f", inv.total())));
        totals.add(detailRow("Paid",    "Rs. " + String.format("%.2f", inv.paid())));
        body.add(totals);

        JScrollPane bodyScroll = new JScrollPane(body);
        bodyScroll.setBorder(null);
        bodyScroll.setOpaque(false);
        bodyScroll.getViewport().setOpaque(false);
        ((JPanel)c).add(bodyScroll, BorderLayout.CENTER);

        // ── Buttons ───────────────────────────────────────────────────────────
        JPanel btnRow = new JPanel(new GridLayout(1, 2, 6, 0));
        btnRow.setOpaque(false);
        JButton reprint = new JButton("Reprint");
        reprint.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "Print feature coming soon.", "Print", JOptionPane.INFORMATION_MESSAGE));
        JButton processReturn = new JButton("Return");
        processReturn.addActionListener(e -> Home.navigate(Home.CARD_RETURNS));
        btnRow.add(reprint);
        btnRow.add(processReturn);
        ((JPanel)c).add(btnRow, BorderLayout.SOUTH);

        detailPanel.add(c, BorderLayout.CENTER);
        detailPanel.revalidate();
        detailPanel.repaint();
    }

    private JPanel detailRow(String label, String value) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        JLabel l = new JLabel(label + ":");
        l.setFont(l.getFont().deriveFont(11f));
        l.setForeground(TEXT2);
        l.setPreferredSize(new Dimension(70, 0));
        JLabel v = new JLabel(value);
        v.setFont(v.getFont().deriveFont(Font.BOLD, 12f));
        row.add(l, BorderLayout.WEST);
        row.add(v, BorderLayout.CENTER);
        return row;
    }

    // ── Stat card helpers ─────────────────────────────────────────────────────

    private CardPanel statCard(String label, String value) {
        CardPanel c = new CardPanel(new BorderLayout(0, 4));
        c.setBorder(new EmptyBorder(12, 14, 12, 14));
        JLabel l = new JLabel(label);
        l.setFont(l.getFont().deriveFont(12f));
        l.setForeground(TEXT2);
        JLabel v = new JLabel(value);
        v.setFont(v.getFont().deriveFont(Font.BOLD, 18f));
        c.add(l, BorderLayout.NORTH);
        c.add(v, BorderLayout.CENTER);
        return c;
    }

    private JLabel valueLabel(JPanel parent) {
        CardPanel card = (CardPanel) parent.getComponent(parent.getComponentCount() - 1);
        return (JLabel) card.getComponent(1);
    }
}
