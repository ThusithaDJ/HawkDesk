package com.olympus.system.hawkdeskpos.frontend.sale;

import com.olympus.system.hawkdeskpos.dto.InvoiceDto;
import com.olympus.system.hawkdeskpos.dto.ReturnDto;
import com.olympus.system.hawkdeskpos.dto.SaleLineDto;
import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.service.ReturnService;
import com.olympus.system.hawkdeskpos.service.SaleService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
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
 * 5 stat tiles (Revenue, Transactions, Items Sold, Profit, Returns) — all reflect
 * the current filter/search state. Detail panel shows item cost.
 */
public class SalesHistoryPanel extends JPanel
        implements com.olympus.system.hawkdeskpos.frontend.components.Refreshable {

    private static final Color BG       = new Color(0xF0, 0xF2, 0xF5);
    private static final Color TEXT2    = new Color(0x5A, 0x60, 0x70);
    private static final Color NAVY     = new Color(0x1E, 0x3A, 0x5F);
    private static final Color AMBER    = new Color(0xB4, 0x5B, 0x00);
    private static final Color AMBER_BG = new Color(0xFF, 0xF3, 0xCD);
    private static final Color RED      = new Color(0xC6, 0x28, 0x28);
    private static final Color GREEN    = new Color(0x2E, 0x7D, 0x32);

    private static final String[] PERIODS = {
            "Today", "This Week", "Last Week", "This Month", "Last Month", "This Year", "Custom"
    };

    private final SaleService   saleService;
    private final ReturnService returnService;

    // Stat tile labels
    private JLabel revenueLabel, txLabel, itemsLabel, profitLabel, returnsLabel;

    private JTextField         searchField;
    private JComboBox<String>  paymentFilter;
    private JComboBox<String>  periodCombo;
    private JPanel             customDatePanel;
    private JSpinner           fromSpinner, toSpinner;
    private DefaultTableModel  tableModel;
    private JTable             table;
    private List<InvoiceDto>   allInvoices;
    private List<InvoiceDto>   displayedInvoices;
    private JPanel             detailContent;

    private static final String[] COLS = {
            "Invoice #", "Date/Time", "Cashier", "Items", "Payment", "Amount (Rs.)", "Status"
    };

    public SalesHistoryPanel(SaleService saleService, ReturnService returnService) {
        this.saleService   = saleService;
        this.returnService = returnService;
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

        // ── 5 stat tiles ──────────────────────────────────────────────────────
        JPanel statBar = new JPanel(new GridLayout(1, 5, 10, 0));
        statBar.setOpaque(false);
        revenueLabel = addTile(statBar, "Revenue",       "Rs. 0.00", "#185FA5");
        txLabel      = addTile(statBar, "Transactions",  "0",        "#2E7D32");
        itemsLabel   = addTile(statBar, "Items Sold",    "0",        "#1E3A5F");
        profitLabel  = addTile(statBar, "Profit",        "Rs. 0.00", "#6A1B9A");
        returnsLabel = addTile(statBar, "Returns (Total)", "0",      "#C62828");

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

        // Right: detail panel
        detailContent = buildEmptyDetailContent();
        CardPanel rightCard = new CardPanel(new BorderLayout(0, 10));
        ((JPanel)rightCard).setBorder(new EmptyBorder(14, 14, 14, 14));
        ((JPanel)rightCard).add(detailContent, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, rightCard);
        split.setResizeWeight(0.75);
        split.setBorder(null);
        split.setDividerSize(5);
        split.setOpaque(false);
        split.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0
                    && split.isShowing()) {
                SwingUtilities.invokeLater(() -> split.setDividerLocation(0.75));
            }
        });

        JPanel main = new JPanel(new BorderLayout(0, 10));
        main.setOpaque(false);
        main.add(statBar, BorderLayout.NORTH);
        main.add(split,   BorderLayout.CENTER);

        root.add(main, BorderLayout.CENTER);
        add(root);
        loadDataAsync();
    }

    // ── Tile helper ───────────────────────────────────────────────────────────

    private JLabel addTile(JPanel parent, String label, String value, String hexColor) {
        CardPanel c = new CardPanel(new BorderLayout(0, 4));
        c.setBorder(new EmptyBorder(12, 14, 12, 14));
        JLabel l = new JLabel(label);
        l.setFont(l.getFont().deriveFont(12f));
        l.setForeground(TEXT2);
        JLabel v = new JLabel(value);
        v.setFont(v.getFont().deriveFont(Font.BOLD, 18f));
        try {
            v.setForeground(Color.decode(hexColor));
        } catch (NumberFormatException ignored) {}
        c.add(l, BorderLayout.NORTH);
        c.add(v, BorderLayout.CENTER);
        parent.add(c);
        return v;
    }

    // ── Filters ───────────────────────────────────────────────────────────────

    private JPanel buildFilters() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 6));
        wrapper.setOpaque(false);

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
            @Override protected List<InvoiceDto> doInBackground() {
                return saleService.listInvoices(null, from, to, null);
            }
            @Override protected void done() {
                try {
                    allInvoices = get();
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
        updateTiles(filtered);
    }

    /** Compute and display stat tiles from the currently displayed invoice list. */
    private void updateTiles(List<InvoiceDto> invs) {
        double revenue   = 0;
        int    tx        = 0;
        int    itemsSold = 0;
        double cogs      = 0;
        int    returns   = 0;

        for (InvoiceDto inv : invs) {
            if ("Void".equals(inv.stat())) continue;
            revenue += inv.netTotal();
            tx++;
            for (SaleLineDto line : inv.lines()) {
                itemsSold += line.qty();
                cogs += line.costPrice() * line.qty();
            }
            String s = inv.stat();
            if ("Full Return".equals(s) || "Partial Return".equals(s)) returns++;
        }

        double profit = revenue - cogs;
        revenueLabel.setText(String.format("Rs. %,.2f", revenue));
        txLabel     .setText(String.valueOf(tx));
        itemsLabel  .setText(String.valueOf(itemsSold));
        profitLabel .setText(String.format("Rs. %,.2f", profit));
        returnsLabel.setText(String.valueOf(returns));
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
                    String.format("%,.2f", inv.netTotal()),
                    inv.stat()
            });
        }
    }

    // ── Date range helpers ────────────────────────────────────────────────────

    private Date[] getDateRange() {
        String period = (String) periodCombo.getSelectedItem();
        LocalDate today = LocalDate.now();
        return switch (period) {
            case "Today"      -> new Date[]{ toStartOfDay(today), toEndOfDay(today) };
            case "This Week"  -> new Date[]{ toStartOfDay(today.with(DayOfWeek.MONDAY)), toEndOfDay(today) };
            case "Last Week"  -> {
                LocalDate mon = today.minusWeeks(1).with(DayOfWeek.MONDAY);
                yield new Date[]{ toStartOfDay(mon), toEndOfDay(mon.with(DayOfWeek.SUNDAY)) };
            }
            case "This Month" -> new Date[]{ toStartOfDay(today.withDayOfMonth(1)), toEndOfDay(today) };
            case "Last Month" -> {
                LocalDate first = today.minusMonths(1).withDayOfMonth(1);
                yield new Date[]{ toStartOfDay(first), toEndOfDay(first.withDayOfMonth(first.lengthOfMonth())) };
            }
            case "This Year"  -> new Date[]{ toStartOfDay(today.withDayOfYear(1)), toEndOfDay(today) };
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

    private JPanel buildEmptyDetailContent() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        JLabel title = new JLabel("INVOICE DETAIL");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 11f));
        title.setForeground(TEXT2);
        p.add(title, BorderLayout.NORTH);
        JLabel placeholder = new JLabel("<html><center>Select a row<br>to view details</center></html>");
        placeholder.setForeground(TEXT2);
        placeholder.setHorizontalAlignment(SwingConstants.CENTER);
        p.add(placeholder, BorderLayout.CENTER);
        return p;
    }

    private void showDetail(InvoiceDto inv) {
        Container rightCard = detailContent.getParent();
        if (rightCard == null) return;

        rightCard.removeAll();
        ((JPanel)rightCard).setBorder(new EmptyBorder(14, 14, 14, 14));

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);

        JLabel title = new JLabel("INVOICE DETAIL");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 11f));
        title.setForeground(TEXT2);
        panel.add(title, BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        JPanel info = new JPanel(new GridLayout(0, 1, 0, 5));
        info.setOpaque(false);
        info.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        info.setBorder(new EmptyBorder(0, 0, 10, 0));
        info.add(detailRow("Invoice #", inv.invoiceNo()));
        info.add(detailRow("Date",    inv.date() != null ? sdf.format(inv.date()) : "—"));
        info.add(detailRow("Cashier", inv.cashierName() != null ? inv.cashierName() : "—"));
        if (inv.customerName() != null)
            info.add(detailRow("Customer", inv.customerName()));
        info.add(detailRow("Payment", inv.paymentMethod()));
        info.add(detailRow("Status",  inv.stat()));
        body.add(info);

        // Items
        JLabel itemsTitle = new JLabel("ITEMS");
        itemsTitle.setFont(itemsTitle.getFont().deriveFont(Font.BOLD, 10f));
        itemsTitle.setForeground(TEXT2);
        itemsTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(itemsTitle);
        body.add(Box.createVerticalStrut(4));

        String[] lineCols = {"Item", "UoM", "Qty", "Unit Price", "Unit Cost", "Cost Total", "Sale Total"};
        DefaultTableModel lineModel = new DefaultTableModel(lineCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable lineTable = new JTable(lineModel);
        lineTable.setRowHeight(24);
        lineTable.setShowGrid(false);
        lineTable.setIntercellSpacing(new Dimension(0, 0));
        lineTable.getTableHeader().setFont(lineTable.getFont().deriveFont(Font.BOLD, 10f));
        lineTable.getTableHeader().setBackground(new Color(0xF7, 0xF8, 0xFA));
        lineTable.setFont(lineTable.getFont().deriveFont(11f));
        // Column widths
        lineTable.getColumnModel().getColumn(1).setPreferredWidth(40);
        lineTable.getColumnModel().getColumn(1).setMaxWidth(55);
        lineTable.getColumnModel().getColumn(2).setPreferredWidth(40);
        lineTable.getColumnModel().getColumn(2).setMaxWidth(55);

        for (SaleLineDto line : inv.lines()) {
            double lineCost = line.costPrice() * line.qty();
            String uom = (line.unit() != null && !line.unit().isBlank()) ? line.unit() : "pcs";
            lineModel.addRow(new Object[]{
                    line.itemName(),
                    uom,
                    line.qty(),
                    String.format("%,.2f", line.unitPrice()),
                    line.costPrice() > 0 ? String.format("%,.2f", line.costPrice()) : "—",
                    lineCost > 0 ? String.format("%,.2f", lineCost) : "—",
                    String.format("%,.2f", line.lineTotal())
            });
        }

        int visRows = Math.min(Math.max(inv.lines().size(), 1), 5);
        int scrollH = 22 + visRows * 24 + 2;
        JScrollPane lineScroll = new JScrollPane(lineTable);
        lineScroll.setPreferredSize(new Dimension(100, scrollH));
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
        if (inv.subTotal() > 0) totals.add(detailRow("Subtotal", "Rs. " + String.format("%,.2f", inv.subTotal())));
        if (inv.tax() > 0) totals.add(detailRow("Tax", "Rs. " + String.format("%,.2f", inv.tax())));
        if (inv.discount() > 0) totals.add(detailRow("Discount", "Rs. " + String.format("%,.2f", inv.discount())));
        totals.add(detailRow("Total", "Rs. " + String.format("%,.2f", inv.netTotal())));
        totals.add(detailRow("Paid",  "Rs. " + String.format("%,.2f", inv.paid())));
        // Profit from this invoice
        double invCogs = inv.lines().stream().mapToDouble(l -> l.costPrice() * l.qty()).sum();
        double invProfit = inv.netTotal() - invCogs;
        if (invCogs > 0) totals.add(detailRow("Profit", "Rs. " + String.format("%,.2f", invProfit)));
        body.add(totals);
        body.add(Box.createVerticalStrut(12));

        // Returns section
        JLabel returnsTitle = new JLabel("RETURNS");
        returnsTitle.setFont(returnsTitle.getFont().deriveFont(Font.BOLD, 10f));
        returnsTitle.setForeground(AMBER);
        returnsTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(returnsTitle);
        body.add(Box.createVerticalStrut(4));

        JLabel returnsPlaceholder = new JLabel("  Loading returns…");
        returnsPlaceholder.setFont(returnsPlaceholder.getFont().deriveFont(11f));
        returnsPlaceholder.setForeground(TEXT2);
        returnsPlaceholder.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(returnsPlaceholder);

        new SwingWorker<List<ReturnDto>, Void>() {
            @Override protected List<ReturnDto> doInBackground() {
                return returnService.getReturnsForInvoice(inv.invoiceNo());
            }
            @Override protected void done() {
                try {
                    List<ReturnDto> rets = get();
                    int idx = -1;
                    for (int i = 0; i < body.getComponentCount(); i++) {
                        if (body.getComponent(i) == returnsPlaceholder) { idx = i; break; }
                    }
                    if (idx >= 0) body.remove(idx);
                    if (rets.isEmpty()) {
                        JLabel none = new JLabel("  No returns for this invoice.");
                        none.setFont(none.getFont().deriveFont(11f));
                        none.setForeground(TEXT2);
                        none.setAlignmentX(Component.LEFT_ALIGNMENT);
                        body.add(none, idx >= 0 ? idx : body.getComponentCount());
                    } else {
                        SimpleDateFormat rsdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                        int insertAt = idx >= 0 ? idx : body.getComponentCount();
                        for (ReturnDto r : rets) {
                            body.add(buildReturnRow(r, rsdf), insertAt++);
                        }
                    }
                    body.revalidate();
                    body.repaint();
                } catch (Exception ignored) {}
            }
        }.execute();

        JScrollPane bodyScroll = new JScrollPane(body);
        bodyScroll.setBorder(null);
        bodyScroll.setOpaque(false);
        bodyScroll.getViewport().setOpaque(false);
        panel.add(bodyScroll, BorderLayout.CENTER);

        JPanel btnRow = new JPanel(new GridLayout(1, 2, 6, 0));
        btnRow.setOpaque(false);
        JButton reprint = new JButton("Reprint");
        reprint.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "Print feature coming soon.", "Print", JOptionPane.INFORMATION_MESSAGE));
        JButton processReturn = new JButton("Return");
        processReturn.addActionListener(e -> Home.navigateToReturnWithInvoice(inv.invoiceNo()));
        boolean canReturn = !"Full Return".equals(inv.stat()) && !"Void".equals(inv.stat());
        processReturn.setEnabled(canReturn);
        btnRow.add(reprint);
        btnRow.add(processReturn);
        panel.add(btnRow, BorderLayout.SOUTH);

        rightCard.add(panel, BorderLayout.CENTER);
        detailContent = panel;
        rightCard.revalidate();
        rightCard.repaint();
    }

    private JPanel buildReturnRow(ReturnDto r, SimpleDateFormat sdf) {
        JPanel row = new JPanel(new GridLayout(1, 4, 6, 0));
        row.setOpaque(true);
        row.setBackground(AMBER_BG);
        row.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 3, 0, 0, AMBER),
                new EmptyBorder(5, 6, 5, 4)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel item   = new JLabel("<html><b>" + r.itemName() + "</b></html>");
        item.setFont(item.getFont().deriveFont(11f));
        JLabel qty    = new JLabel("Qty: " + r.qty());
        qty.setFont(qty.getFont().deriveFont(11f));
        qty.setForeground(TEXT2);
        JLabel reason = new JLabel(r.reason());
        reason.setFont(reason.getFont().deriveFont(10f));
        reason.setForeground(AMBER);
        JLabel date   = new JLabel(r.returnDate() != null ? sdf.format(r.returnDate()) : "—");
        date.setFont(date.getFont().deriveFont(10f));
        date.setForeground(TEXT2);

        row.add(item); row.add(qty); row.add(reason); row.add(date);
        return row;
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
}
