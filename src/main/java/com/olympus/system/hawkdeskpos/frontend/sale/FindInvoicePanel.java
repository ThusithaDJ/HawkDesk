package com.olympus.system.hawkdeskpos.frontend.sale;

import com.olympus.system.hawkdeskpos.dto.InvoiceDto;
import com.olympus.system.hawkdeskpos.dto.ReturnDto;
import com.olympus.system.hawkdeskpos.dto.SaleLineDto;
import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.frontend.components.StatusPill;
import com.olympus.system.hawkdeskpos.service.ReturnService;
import com.olympus.system.hawkdeskpos.service.SaleService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Find Invoice — search + date/cashier filters + detail pane with returns history.
 */
public class FindInvoicePanel extends JPanel
        implements com.olympus.system.hawkdeskpos.frontend.components.Refreshable {

    private static final Color BG    = new Color(0xF0, 0xF2, 0xF5);
    private static final Color NAVY  = new Color(0x1E, 0x3A, 0x5F);
    private static final Color TEXT1 = new Color(0x1A, 0x1D, 0x23);
    private static final Color TEXT2 = new Color(0x5A, 0x60, 0x70);
    private static final Color GREEN = new Color(0x2E, 0x7D, 0x32);
    private static final Color RED   = new Color(0xC6, 0x28, 0x28);
    private static final Color AMBER = new Color(0xE6, 0x51, 0x00);

    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("dd MMM yyyy  HH:mm");

    private final SaleService   saleService;
    private final ReturnService returnService;

    // Search & filter
    private JTextField searchField;
    private JTextField cashierField;
    private JSpinner   fromSpinner, toSpinner;
    private JCheckBox  useDateFilterCheck;
    private Timer      debounce;
    private JLabel     resultCount;

    // Results table
    private DefaultTableModel tableModel;
    private JTable            table;
    private List<InvoiceDto>  allResults    = new ArrayList<>();
    private List<InvoiceDto>  filteredResults = new ArrayList<>();

    // Detail pane
    private JPanel detailContent;
    private JPanel detailPane;

    public FindInvoicePanel(SaleService saleService, ReturnService returnService) {
        this.saleService   = saleService;
        this.returnService = returnService;
        setBackground(BG);
        setLayout(new BorderLayout());
        buildUI();
    }

    @Override public void refresh() { loadRecent(); }

    // ── UI construction ───────────────────────────────────────────────────────

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(16, 16, 16, 16));
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildBody(),   BorderLayout.CENTER);
        add(root);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Find Invoice");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        header.add(title, BorderLayout.WEST);
        JButton refreshBtn = new JButton("↺ Refresh");
        refreshBtn.addActionListener(e -> { searchField.setText(""); cashierField.setText(""); loadRecent(); });
        JButton back = new JButton("← Back");
        back.addActionListener(e -> Home.navigate(Home.CARD_DASH));
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        btns.add(refreshBtn);
        btns.add(back);
        header.add(btns, BorderLayout.EAST);
        return header;
    }

    private JPanel buildBody() {
        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setOpaque(false);

        JPanel filters = new JPanel();
        filters.setOpaque(false);
        filters.setLayout(new BoxLayout(filters, BoxLayout.Y_AXIS));
        filters.add(buildSearchBar());
        filters.add(Box.createVerticalStrut(4));
        filters.add(buildFilterBar());
        body.add(filters, BorderLayout.NORTH);

        JPanel split = new JPanel(new BorderLayout(14, 0));
        split.setOpaque(false);
        split.add(buildResultsTable(), BorderLayout.CENTER);
        split.add(buildDetailPane(),   BorderLayout.EAST);
        body.add(split, BorderLayout.CENTER);
        return body;
    }

    // ── Search bar ────────────────────────────────────────────────────────────

    private CardPanel buildSearchBar() {
        CardPanel card = new CardPanel(new BorderLayout(10, 0));
        ((JPanel) card).setBorder(new EmptyBorder(10, 14, 10, 14));

        JLabel hint = new JLabel("Invoice #:");
        hint.setForeground(TEXT2);
        hint.setFont(hint.getFont().deriveFont(13f));
        ((JPanel) card).add(hint, BorderLayout.WEST);

        searchField = new JTextField();
        searchField.setFont(searchField.getFont().deriveFont(14f));
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xC8, 0xCD, 0xD6)),
                new EmptyBorder(6, 10, 6, 10)));
        searchField.setToolTipText("Type full or partial invoice number…");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { scheduleSearch(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { scheduleSearch(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) {}
        });
        searchField.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DOWN && table.getRowCount() > 0) {
                    table.setRowSelectionInterval(0, 0);
                    table.requestFocusInWindow();
                }
            }
        });
        ((JPanel) card).add(searchField, BorderLayout.CENTER);

        resultCount = new JLabel("Loading…");
        resultCount.setForeground(TEXT2);
        resultCount.setFont(resultCount.getFont().deriveFont(12f));
        ((JPanel) card).add(resultCount, BorderLayout.EAST);
        return card;
    }

    // ── Date + cashier filter bar ─────────────────────────────────────────────

    private CardPanel buildFilterBar() {
        CardPanel card = new CardPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        ((JPanel) card).setBorder(new EmptyBorder(4, 14, 4, 14));

        useDateFilterCheck = new JCheckBox("Filter by date");
        useDateFilterCheck.setOpaque(false);
        useDateFilterCheck.addActionListener(e -> {
            boolean on = useDateFilterCheck.isSelected();
            fromSpinner.setEnabled(on);
            toSpinner.setEnabled(on);
            loadRecent();
        });

        fromSpinner = new JSpinner(new SpinnerDateModel());
        fromSpinner.setEditor(new JSpinner.DateEditor(fromSpinner, "yyyy-MM-dd"));
        fromSpinner.setPreferredSize(new Dimension(120, 28));
        fromSpinner.setValue(toStartOfDay(LocalDate.now().minusMonths(1)));
        fromSpinner.setEnabled(false);

        toSpinner = new JSpinner(new SpinnerDateModel());
        toSpinner.setEditor(new JSpinner.DateEditor(toSpinner, "yyyy-MM-dd"));
        toSpinner.setPreferredSize(new Dimension(120, 28));
        toSpinner.setValue(toEndOfDay(LocalDate.now()));
        toSpinner.setEnabled(false);

        JButton apply = new JButton("Apply");
        apply.addActionListener(e -> loadRecent());

        cashierField = new JTextField(12);
        cashierField.putClientProperty("JTextField.placeholderText", "Cashier name…");
        cashierField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { applyInMemoryFilter(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { applyInMemoryFilter(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) {}
        });

        ((JPanel) card).add(useDateFilterCheck);
        ((JPanel) card).add(new JLabel("From:"));
        ((JPanel) card).add(fromSpinner);
        ((JPanel) card).add(new JLabel("To:"));
        ((JPanel) card).add(toSpinner);
        ((JPanel) card).add(apply);
        ((JPanel) card).add(new JLabel("  Cashier:"));
        ((JPanel) card).add(cashierField);
        return card;
    }

    // ── Results table ─────────────────────────────────────────────────────────

    private CardPanel buildResultsTable() {
        String[] cols = {"Invoice No", "Date", "Cashier", "Total (Rs.)", "Payment", "Status"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        table = new JTable(tableModel);
        table.setRowHeight(38);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setFont(table.getFont().deriveFont(Font.BOLD, 12f));
        table.getTableHeader().setBackground(new Color(0xF7, 0xF8, 0xFA));
        table.getTableHeader().setForeground(TEXT2);
        table.setFont(table.getFont().deriveFont(13f));

        table.getColumnModel().getColumn(0).setPreferredWidth(150);
        table.getColumnModel().getColumn(1).setPreferredWidth(160);
        table.getColumnModel().getColumn(2).setPreferredWidth(110);
        table.getColumnModel().getColumn(3).setPreferredWidth(110);
        table.getColumnModel().getColumn(4).setPreferredWidth(80);
        table.getColumnModel().getColumn(5).setPreferredWidth(90);

        DefaultTableCellRenderer rightAlign = new DefaultTableCellRenderer();
        rightAlign.setHorizontalAlignment(SwingConstants.RIGHT);
        table.getColumnModel().getColumn(3).setCellRenderer(rightAlign);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && table.getSelectedRow() >= 0) {
                int modelRow = table.convertRowIndexToModel(table.getSelectedRow());
                if (modelRow < filteredResults.size()) showDetail(filteredResults.get(modelRow));
            }
        });
        table.setAutoCreateRowSorter(true);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(null);

        CardPanel card = new CardPanel(new BorderLayout(0, 0));
        ((JPanel) card).setBorder(new EmptyBorder(0, 0, 0, 0));
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.setBorder(new EmptyBorder(10, 14, 8, 14));
        JLabel lbl = new JLabel("RESULTS");
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 11f));
        lbl.setForeground(TEXT2);
        titleRow.add(lbl, BorderLayout.WEST);
        ((JPanel) card).add(titleRow, BorderLayout.NORTH);
        ((JPanel) card).add(scroll,   BorderLayout.CENTER);
        return card;
    }

    // ── Detail pane ───────────────────────────────────────────────────────────

    private CardPanel buildDetailPane() {
        detailPane = new CardPanel(new BorderLayout(0, 0));
        ((JPanel) detailPane).setBorder(new EmptyBorder(14, 14, 14, 14));
        ((JPanel) detailPane).setPreferredSize(new Dimension(360, 0));

        detailContent = new JPanel();
        detailContent.setOpaque(false);
        detailContent.setLayout(new BoxLayout(detailContent, BoxLayout.Y_AXIS));
        showPlaceholder();

        JScrollPane scroll = new JScrollPane(detailContent);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        ((JPanel) detailPane).add(scroll, BorderLayout.CENTER);
        return (CardPanel) detailPane;
    }

    private void showPlaceholder() {
        detailContent.removeAll();
        JLabel ph = new JLabel("<html><center>Select an invoice<br>to view details</center></html>");
        ph.setForeground(TEXT2);
        ph.setHorizontalAlignment(SwingConstants.CENTER);
        ph.setAlignmentX(Component.CENTER_ALIGNMENT);
        detailContent.add(Box.createVerticalGlue());
        detailContent.add(ph);
        detailContent.add(Box.createVerticalGlue());
        detailContent.revalidate();
        detailContent.repaint();
    }

    private void showDetail(InvoiceDto inv) {
        detailContent.removeAll();

        // ── Invoice number + status ───────────────────────────────────────────
        JPanel invRow = new JPanel(new BorderLayout(8, 0));
        invRow.setOpaque(false);
        invRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        invRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        JLabel invNoLbl = new JLabel(inv.invoiceNo());
        invNoLbl.setFont(invNoLbl.getFont().deriveFont(Font.BOLD, 16f));
        invNoLbl.setForeground(NAVY);
        invRow.add(invNoLbl, BorderLayout.WEST);
        invRow.add(StatusPill.forStatus(inv.stat()), BorderLayout.EAST);
        detailContent.add(invRow);
        detailContent.add(Box.createVerticalStrut(12));

        // ── Header fields ─────────────────────────────────────────────────────
        JPanel info = new JPanel(new GridLayout(0, 2, 6, 6));
        info.setOpaque(false);
        info.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.setMaximumSize(new Dimension(Integer.MAX_VALUE, 999));
        addPair(info, "Date",    inv.date() != null ? DATE_FMT.format(inv.date()) : "—");
        addPair(info, "Cashier", inv.cashierName() != null ? inv.cashierName() : "—");
        addPair(info, "Payment", inv.paymentMethod());
        detailContent.add(info);
        detailContent.add(Box.createVerticalStrut(14));
        detailContent.add(separator());
        detailContent.add(Box.createVerticalStrut(10));

        // ── Line items ────────────────────────────────────────────────────────
        detailContent.add(sectionLabel("ITEMS"));
        detailContent.add(Box.createVerticalStrut(6));

        if (inv.lines().isEmpty()) {
            JLabel noItems = new JLabel("No line items recorded.");
            noItems.setForeground(TEXT2);
            noItems.setFont(noItems.getFont().deriveFont(12f));
            noItems.setAlignmentX(Component.LEFT_ALIGNMENT);
            detailContent.add(noItems);
        } else {
            for (SaleLineDto line : inv.lines()) {
                JPanel row = new JPanel(new BorderLayout(8, 0));
                row.setOpaque(false);
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
                JLabel nameQty = new JLabel(line.itemName() + "  ×" + line.qty());
                nameQty.setFont(nameQty.getFont().deriveFont(13f));
                JLabel price = new JLabel("Rs. " + fmt(line.lineTotal()));
                price.setFont(price.getFont().deriveFont(Font.BOLD, 13f));
                price.setForeground(NAVY);
                row.add(nameQty, BorderLayout.CENTER);
                row.add(price,   BorderLayout.EAST);
                detailContent.add(row);
                detailContent.add(Box.createVerticalStrut(2));
            }
        }

        detailContent.add(Box.createVerticalStrut(10));
        detailContent.add(separator());
        detailContent.add(Box.createVerticalStrut(10));

        // ── Totals ────────────────────────────────────────────────────────────
        JPanel totals = new JPanel(new GridLayout(0, 2, 6, 4));
        totals.setOpaque(false);
        totals.setAlignmentX(Component.LEFT_ALIGNMENT);
        totals.setMaximumSize(new Dimension(Integer.MAX_VALUE, 999));
        if (inv.discount() > 0)
            addPair(totals, "Discount", "Rs. " + fmt(inv.discount()));
        addAmountPair(totals, "TOTAL", "Rs. " + fmt(inv.total()), true);
        if (inv.paid() > 0) {
            addAmountPair(totals, "Paid", "Rs. " + fmt(inv.paid()), false);
            double change = inv.paid() - inv.total();
            if (change > 0) addAmountPair(totals, "Change", "Rs. " + fmt(change), false);
        }
        detailContent.add(totals);
        detailContent.add(Box.createVerticalStrut(14));

        // ── Returns history (async) ───────────────────────────────────────────
        detailContent.add(separator());
        detailContent.add(Box.createVerticalStrut(10));
        detailContent.add(sectionLabel("RETURNS"));
        detailContent.add(Box.createVerticalStrut(6));

        JLabel returnsPlaceholder = new JLabel("Loading returns…");
        returnsPlaceholder.setForeground(TEXT2);
        returnsPlaceholder.setFont(returnsPlaceholder.getFont().deriveFont(11f));
        returnsPlaceholder.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailContent.add(returnsPlaceholder);
        detailContent.add(Box.createVerticalStrut(10));

        // ── Actions ───────────────────────────────────────────────────────────
        JButton reprint = new JButton("Reprint Receipt");
        reprint.setAlignmentX(Component.LEFT_ALIGNMENT);
        reprint.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        reprint.addActionListener(e ->
                JOptionPane.showMessageDialog(this, "Print coming soon.", "Print",
                        JOptionPane.INFORMATION_MESSAGE));
        detailContent.add(reprint);

        boolean canReturn = !"Full Return".equals(inv.stat()) && !"Void".equals(inv.stat());
        if (canReturn) {
            detailContent.add(Box.createVerticalStrut(6));
            JButton ret = new JButton("Process Return");
            ret.setAlignmentX(Component.LEFT_ALIGNMENT);
            ret.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
            ret.setForeground(RED);
            ret.addActionListener(e -> Home.navigateToReturnWithInvoice(inv.invoiceNo()));
            detailContent.add(ret);
        }

        detailContent.revalidate();
        detailContent.repaint();

        // Load returns asynchronously and replace placeholder
        new SwingWorker<List<ReturnDto>, Void>() {
            @Override protected List<ReturnDto> doInBackground() {
                return returnService.getReturnsForInvoice(inv.invoiceNo());
            }
            @Override protected void done() {
                try {
                    List<ReturnDto> returns = get();
                    // Replace placeholder with actual return rows
                    int idx = getComponentIndex(detailContent, returnsPlaceholder);
                    if (idx >= 0) detailContent.remove(idx);
                    if (returns.isEmpty()) {
                        JLabel none = new JLabel("No returns for this invoice.");
                        none.setForeground(TEXT2);
                        none.setFont(none.getFont().deriveFont(11f));
                        none.setAlignmentX(Component.LEFT_ALIGNMENT);
                        detailContent.add(none, idx < 0 ? detailContent.getComponentCount() : idx);
                    } else {
                        SimpleDateFormat sf = new SimpleDateFormat("dd MMM yyyy HH:mm");
                        int insertAt = idx < 0 ? detailContent.getComponentCount() : idx;
                        for (int i = returns.size() - 1; i >= 0; i--) {
                            ReturnDto r = returns.get(i);
                            JPanel rrow = buildReturnRow(r, sf);
                            detailContent.add(rrow, insertAt);
                        }
                    }
                    detailContent.revalidate();
                    detailContent.repaint();
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private JPanel buildReturnRow(ReturnDto r, SimpleDateFormat sf) {
        JPanel row = new JPanel(new GridLayout(0, 1, 0, 1));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, AMBER),
                new EmptyBorder(4, 8, 4, 4)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 999));

        String dateStr = r.returnDate() != null ? sf.format(r.returnDate()) : "—";
        JLabel line1 = new JLabel(r.itemName() + "  ×" + r.qty()
                + "  —  " + r.refundMethod());
        line1.setFont(line1.getFont().deriveFont(Font.BOLD, 12f));
        JLabel line2 = new JLabel(r.reason() + "  ·  " + dateStr
                + "  ·  by " + r.cashierName());
        line2.setFont(line2.getFont().deriveFont(11f));
        line2.setForeground(TEXT2);

        row.add(line1);
        row.add(line2);
        return row;
    }

    /** Returns the index of a component in a container, or -1 if not found. */
    private static int getComponentIndex(Container parent, Component target) {
        for (int i = 0; i < parent.getComponentCount(); i++) {
            if (parent.getComponent(i) == target) return i;
        }
        return -1;
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private void scheduleSearch() {
        if (debounce != null && debounce.isRunning()) debounce.stop();
        debounce = new Timer(250, e -> loadRecent());
        debounce.setRepeats(false);
        debounce.start();
    }

    private void loadRecent() {
        String query   = searchField.getText().trim();
        Date   from    = useDateFilterCheck.isSelected() ? (Date) fromSpinner.getValue() : null;
        Date   to      = useDateFilterCheck.isSelected() ? (Date) toSpinner.getValue()   : null;
        resultCount.setText("Searching…");

        new SwingWorker<List<InvoiceDto>, Void>() {
            @Override protected List<InvoiceDto> doInBackground() {
                return saleService.listInvoices(query, from, to, null);
            }
            @Override protected void done() {
                try {
                    allResults = get();
                } catch (Exception ex) {
                    allResults = new ArrayList<>();
                }
                applyInMemoryFilter();
            }
        }.execute();
    }

    private void applyInMemoryFilter() {
        String cashier = cashierField.getText().trim().toLowerCase();
        if (cashier.isEmpty()) {
            filteredResults = allResults;
        } else {
            filteredResults = allResults.stream()
                    .filter(inv -> inv.cashierName() != null
                            && inv.cashierName().toLowerCase().contains(cashier))
                    .collect(Collectors.toList());
        }
        populateTable();
    }

    private void populateTable() {
        tableModel.setRowCount(0);
        showPlaceholder();
        for (InvoiceDto inv : filteredResults) {
            tableModel.addRow(new Object[]{
                    inv.invoiceNo(),
                    inv.date() != null ? DATE_FMT.format(inv.date()) : "—",
                    inv.cashierName() != null ? inv.cashierName() : "—",
                    fmt(inv.total()),
                    inv.paymentMethod(),
                    inv.stat()
            });
        }
        int n = filteredResults.size();
        resultCount.setText(n == 0 ? "No results" : n + " invoice" + (n == 1 ? "" : "s"));
    }

    // ── Date helpers ──────────────────────────────────────────────────────────

    private static Date toStartOfDay(LocalDate d) {
        return Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static Date toEndOfDay(LocalDate d) {
        return Date.from(d.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant());
    }

    // ── Number formatting ─────────────────────────────────────────────────────

    /** Format with thousands separator and 2 decimal places. e.g. 12345.6 → "12,345.60" */
    private static String fmt(double v) {
        return String.format("%,.2f", v);
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private void addPair(JPanel panel, String label, String value) {
        JLabel l = new JLabel(label);
        l.setForeground(TEXT2);
        l.setFont(l.getFont().deriveFont(12f));
        JLabel v = new JLabel(value);
        v.setFont(v.getFont().deriveFont(13f));
        v.setForeground(TEXT1);
        panel.add(l);
        panel.add(v);
    }

    private void addAmountPair(JPanel panel, String label, String value, boolean bold) {
        JLabel l = new JLabel(label);
        l.setForeground(bold ? TEXT1 : TEXT2);
        l.setFont(l.getFont().deriveFont(bold ? Font.BOLD : Font.PLAIN, 13f));
        JLabel v = new JLabel(value);
        v.setFont(v.getFont().deriveFont(Font.BOLD, bold ? 15f : 13f));
        v.setForeground(bold ? NAVY : TEXT1);
        panel.add(l);
        panel.add(v);
    }

    private JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 11f));
        l.setForeground(TEXT2);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JSeparator separator() {
        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        return sep;
    }
}
