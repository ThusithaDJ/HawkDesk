package com.olympus.system.hawkdeskpos.frontend.sale;

import com.olympus.system.hawkdeskpos.dto.InvoiceDto;
import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.service.SaleService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Sales History screen.
 * Stat bar + filters + sortable JTable with right-side detail panel on row click.
 */
public class SalesHistoryPanel extends JPanel {

    private static final Color BG    = new Color(0xF0, 0xF2, 0xF5);
    private static final Color TEXT2 = new Color(0x5A, 0x60, 0x70);
    private static final Color NAVY  = new Color(0x1E, 0x3A, 0x5F);

    private final SaleService saleService;

    private JLabel todayRevLabel, txCountLabel, weekRevLabel, returnsLabel;
    private JTextField searchField;
    private JComboBox<String> paymentFilter;
    private JSpinner fromDate, toDate;
    private DefaultTableModel tableModel;
    private JTable table;
    private List<InvoiceDto> allInvoices;
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
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> loadDataAsync());
        JButton back = new JButton("← Back");
        back.addActionListener(e -> Home.navigate(Home.CARD_DASH));
        btns.add(refresh);
        btns.add(back);
        topBar.add(btns, BorderLayout.EAST);
        root.add(topBar, BorderLayout.NORTH);

        // Stat bar
        JPanel statBar = new JPanel(new GridLayout(1, 4, 10, 0));
        statBar.setOpaque(false);
        statBar.add(statCard("Today's Revenue", "—"));
        todayRevLabel = valueLabel(statBar);
        statBar.add(statCard("Transactions", "—"));
        txCountLabel  = valueLabel(statBar);
        statBar.add(statCard("This Week", "—"));
        weekRevLabel  = valueLabel(statBar);
        statBar.add(statCard("Returns", "—"));
        returnsLabel  = valueLabel(statBar);

        // Main content split
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
                if (allInvoices != null && modelRow < allInvoices.size()) {
                    showDetail(allInvoices.get(modelRow));
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

        // Right: detail panel (hidden until row selected)
        detailPanel = buildDetailPanel();
        detailPanel.setPreferredSize(new Dimension(280, 0));
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
        CardPanel c = new CardPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        ((JPanel)c).setBorder(new EmptyBorder(4, 10, 4, 10));

        searchField = new JTextField(18);
        searchField.putClientProperty("JTextField.placeholderText", "Invoice # or item…");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) {}
        });

        paymentFilter = new JComboBox<>(new String[]{"All Methods", "CASH", "CARD", "CREDIT"});
        paymentFilter.addActionListener(e -> applyFilter());

        ((JPanel)c).add(searchField);
        ((JPanel)c).add(new JLabel("Payment:"));
        ((JPanel)c).add(paymentFilter);

        return c;
    }

    private JPanel buildDetailPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

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

        panel.add(c);
        return panel;
    }

    private void showDetail(InvoiceDto inv) {
        detailPanel.removeAll();
        detailPanel.setVisible(true);

        CardPanel c = new CardPanel(new BorderLayout(0, 8));
        ((JPanel)c).setBorder(new EmptyBorder(14, 14, 14, 14));

        JLabel title = new JLabel("INVOICE DETAIL");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 11f));
        title.setForeground(TEXT2);
        ((JPanel)c).add(title, BorderLayout.NORTH);

        JPanel info = new JPanel(new GridLayout(0, 1, 0, 6));
        info.setOpaque(false);
        info.add(detailRow("Invoice #", inv.invoiceNo()));
        info.add(detailRow("Date", inv.date() != null ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(inv.date()) : "—"));
        info.add(detailRow("Cashier", inv.cashierName() != null ? inv.cashierName() : "—"));
        info.add(detailRow("Payment", inv.paymentMethod()));
        info.add(detailRow("Items",   String.valueOf(inv.lines().size())));
        info.add(detailRow("Discount","Rs. " + String.format("%.2f", inv.discount())));
        info.add(detailRow("Total",   "Rs. " + String.format("%.2f", inv.total())));
        info.add(detailRow("Status",  inv.stat()));
        ((JPanel)c).add(info, BorderLayout.CENTER);

        JPanel btnRow = new JPanel(new GridLayout(2, 1, 0, 6));
        btnRow.setOpaque(false);
        JButton reprint = new JButton("Reprint Receipt");
        reprint.addActionListener(e -> JOptionPane.showMessageDialog(this, "Print feature coming soon.", "Print", JOptionPane.INFORMATION_MESSAGE));
        JButton processReturn = new JButton("Process Return");
        processReturn.addActionListener(e -> {
            Home.navigate(Home.CARD_RETURNS);
        });
        btnRow.add(reprint);
        btnRow.add(processReturn);
        ((JPanel)c).add(btnRow, BorderLayout.SOUTH);

        detailPanel.add(c);
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

    private void loadDataAsync() {
        new SwingWorker<List<InvoiceDto>, Void>() {
            private SaleService.TodaySummary summary;
            @Override protected List<InvoiceDto> doInBackground() {
                summary = saleService.getTodaySummary();
                return saleService.listInvoices(null, null, null, null);
            }
            @Override protected void done() {
                try {
                    allInvoices = get();
                    if (summary != null) {
                        todayRevLabel.setText(String.format("Rs. %.2f", summary.revenue()));
                        txCountLabel.setText(String.valueOf(summary.transactions()));
                        weekRevLabel.setText("—");
                        returnsLabel.setText("—");
                    }
                    populateTable(allInvoices);
                } catch (Exception ignored) {}
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

    // ── Helpers ───────────────────────────────────────────────────────────────

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
