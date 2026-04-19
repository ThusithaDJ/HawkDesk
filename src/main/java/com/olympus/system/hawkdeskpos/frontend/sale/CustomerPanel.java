package com.olympus.system.hawkdeskpos.frontend.sale;

import com.olympus.system.hawkdeskpos.dto.CustomerDto;
import com.olympus.system.hawkdeskpos.dto.InvoiceDto;
import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.frontend.components.Refreshable;
import com.olympus.system.hawkdeskpos.service.CustomerService;
import com.olympus.system.hawkdeskpos.service.SaleService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;

public class CustomerPanel extends JPanel implements Refreshable {

    private static final Color BG    = new Color(0xF0, 0xF2, 0xF5);
    private static final Color NAVY  = new Color(0x1E, 0x3A, 0x5F);
    private static final Color TEXT2 = new Color(0x5A, 0x60, 0x70);
    private static final Color GREEN = new Color(0x2E, 0x7D, 0x32);
    private static final Color AMBER = new Color(0xE6, 0x51, 0x00);
    private static final Color RED   = new Color(0xC6, 0x28, 0x28);

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd MMM yyyy");

    private final CustomerService customerService;
    private final SaleService     saleService;

    private CustomerTableModel tableModel;
    private JTable             table;
    private List<CustomerDto>  customers = new ArrayList<>();

    private JTextField searchField;
    private Timer      searchDebounce;

    // Invoice detail panel (bottom)
    private InvoiceTableModel invoiceModel;
    private JTable            invoiceTable;
    private List<InvoiceDto>  customerInvoices = new ArrayList<>();
    private JLabel            invoiceHeader;
    private JPanel            invoiceCard;

    public CustomerPanel(CustomerService customerService, SaleService saleService) {
        this.customerService = customerService;
        this.saleService     = saleService;
        setBackground(BG);
        setLayout(new BorderLayout());
        buildUI();
    }

    /** Backward-compat constructor for cases where SaleService is not yet wired. */
    public CustomerPanel(CustomerService customerService) {
        this(customerService, null);
    }

    @Override
    public void refresh() {
        loadData(searchField != null ? searchField.getText().trim() : "");
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        // ── Header ──────────────────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(0, 0, 12, 0));

        JLabel title = new JLabel("Customer Management");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        header.add(title, BorderLayout.WEST);

        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Search by name or phone…");
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xC8, 0xCD, 0xD6)),
                new EmptyBorder(6, 10, 6, 10)));
        searchField.setPreferredSize(new Dimension(260, 32));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { scheduleSearch(); }
            public void removeUpdate(DocumentEvent e)  { scheduleSearch(); }
            public void changedUpdate(DocumentEvent e) { scheduleSearch(); }
        });

        JPanel hRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        hRight.setOpaque(false);

        JButton back = new JButton("← Back");
        back.addActionListener(e -> Home.navigate(Home.CARD_DASH));

        JButton addBtn = new JButton("+ Add Customer");
        addBtn.setBackground(NAVY);
        addBtn.setForeground(Color.WHITE);
        addBtn.setOpaque(true);
        addBtn.setBorderPainted(false);
        addBtn.setFocusPainted(false);
        addBtn.addActionListener(e -> showEditDialog(null));

        hRight.add(searchField);
        hRight.add(back);
        hRight.add(addBtn);
        header.add(hRight, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        // ── Split pane ──────────────────────────────────────────────────────────
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                buildCustomerTableCard(), buildInvoiceCard());
        split.setOpaque(false);
        split.setResizeWeight(0.5);
        split.setDividerSize(6);
        split.setDividerLocation(300);
        root.add(split, BorderLayout.CENTER);

        add(root);
        loadData("");
    }

    // ── Customer table card ──────────────────────────────────────────────────

    private JPanel buildCustomerTableCard() {
        tableModel = new CustomerTableModel();
        table = new JTable(tableModel);
        table.setRowHeight(44);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.getTableHeader().setFont(table.getFont().deriveFont(Font.BOLD, 12f));
        table.getTableHeader().setBackground(new Color(0xF7, 0xF8, 0xFA));
        table.getTableHeader().setForeground(TEXT2);

        table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.getColumnModel().getColumn(4).setPreferredWidth(130);
        table.getColumnModel().getColumn(5).setPreferredWidth(150);
        table.getColumnModel().getColumn(6).setPreferredWidth(140);
        table.getColumnModel().getColumn(7).setPreferredWidth(160);

        ActionRenderer actionRenderer = new ActionRenderer();
        ActionEditor   actionEditor   = new ActionEditor();
        table.getColumnModel().getColumn(7).setCellRenderer(actionRenderer);
        table.getColumnModel().getColumn(7).setCellEditor(actionEditor);

        // When a row is selected, load that customer's invoices below
        table.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> {
            if (e.getValueIsAdjusting()) return;
            int row = table.getSelectedRow();
            if (row >= 0 && row < customers.size()) {
                loadCustomerInvoices(customers.get(row));
            }
        });

        CardPanel card = new CardPanel(new BorderLayout());
        card.add(new JScrollPane(table));
        return card;
    }

    // ── Invoice detail card ──────────────────────────────────────────────────

    private JPanel buildInvoiceCard() {
        invoiceCard = new JPanel(new BorderLayout(0, 6));
        invoiceCard.setOpaque(false);
        invoiceCard.setBorder(new EmptyBorder(8, 0, 0, 0));

        invoiceHeader = new JLabel("Select a customer to view their invoice history");
        invoiceHeader.setFont(invoiceHeader.getFont().deriveFont(Font.BOLD, 13f));
        invoiceHeader.setForeground(TEXT2);
        invoiceHeader.setBorder(new EmptyBorder(0, 4, 4, 0));
        invoiceCard.add(invoiceHeader, BorderLayout.NORTH);

        invoiceModel = new InvoiceTableModel();
        invoiceTable = new JTable(invoiceModel);
        invoiceTable.setRowHeight(36);
        invoiceTable.setShowGrid(false);
        invoiceTable.setIntercellSpacing(new Dimension(0, 0));
        invoiceTable.getTableHeader().setFont(invoiceTable.getFont().deriveFont(Font.BOLD, 12f));
        invoiceTable.getTableHeader().setBackground(new Color(0xF7, 0xF8, 0xFA));
        invoiceTable.getTableHeader().setForeground(TEXT2);

        invoiceTable.getColumnModel().getColumn(0).setPreferredWidth(120); // Invoice #
        invoiceTable.getColumnModel().getColumn(1).setPreferredWidth(100); // Date
        invoiceTable.getColumnModel().getColumn(2).setPreferredWidth(100); // Payment Type
        invoiceTable.getColumnModel().getColumn(3).setPreferredWidth(90);  // Total
        invoiceTable.getColumnModel().getColumn(4).setPreferredWidth(90);  // Balance
        invoiceTable.getColumnModel().getColumn(5).setPreferredWidth(90);  // Status
        invoiceTable.getColumnModel().getColumn(6).setPreferredWidth(100); // Resolve By
        invoiceTable.getColumnModel().getColumn(7).setPreferredWidth(100); // Resolved On
        invoiceTable.getColumnModel().getColumn(8).setPreferredWidth(80);  // Action

        // Status cell colouring
        invoiceTable.getColumnModel().getColumn(5).setCellRenderer(new StatusRenderer());

        // Action column
        InvoiceActionRenderer iar = new InvoiceActionRenderer();
        InvoiceActionEditor   iae = new InvoiceActionEditor();
        invoiceTable.getColumnModel().getColumn(8).setCellRenderer(iar);
        invoiceTable.getColumnModel().getColumn(8).setCellEditor(iae);

        CardPanel tableCard = new CardPanel(new BorderLayout());
        tableCard.add(new JScrollPane(invoiceTable));
        invoiceCard.add(tableCard, BorderLayout.CENTER);

        return invoiceCard;
    }

    // ── Data loading ─────────────────────────────────────────────────────────

    private void scheduleSearch() {
        if (searchDebounce != null && searchDebounce.isRunning()) searchDebounce.stop();
        searchDebounce = new Timer(280, e -> loadData(searchField.getText().trim()));
        searchDebounce.setRepeats(false);
        searchDebounce.start();
    }

    private void loadData(String query) {
        new SwingWorker<List<CustomerDto>, Void>() {
            @Override protected List<CustomerDto> doInBackground() {
                return query.isEmpty()
                        ? customerService.listAll()
                        : customerService.search(query);
            }
            @Override protected void done() {
                try {
                    List<CustomerDto> raw = get();
                    List<double[]> financials = new ArrayList<>();
                    for (CustomerDto c : raw) {
                        double outstanding = customerService.totalOutstandingDebt(c.customerId());
                        double purchases   = customerService.totalPurchases(c.customerId());
                        financials.add(new double[]{outstanding, purchases});
                    }
                    customers = raw;
                    tableModel.setData(customers, financials);
                    // Clear invoice panel when customer list reloads
                    customerInvoices.clear();
                    invoiceModel.setData(customerInvoices);
                    invoiceHeader.setText("Select a customer to view their invoice history");
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void loadCustomerInvoices(CustomerDto customer) {
        if (saleService == null) return;
        invoiceHeader.setText("Loading invoices for " + customer.name() + "…");
        new SwingWorker<List<InvoiceDto>, Void>() {
            @Override protected List<InvoiceDto> doInBackground() {
                return saleService.listInvoicesByCustomer(customer.customerId());
            }
            @Override protected void done() {
                try {
                    customerInvoices = get();
                    invoiceModel.setData(customerInvoices);
                    int count = customerInvoices.size();
                    invoiceHeader.setText(customer.name() + " — " + count
                            + (count == 1 ? " invoice" : " invoices"));
                } catch (Exception ignored) {
                    invoiceHeader.setText("Failed to load invoices");
                }
            }
        }.execute();
    }

    // ── Customer edit/details navigation ────────────────────────────────────

    private void openDetails(int row) {
        if (row < 0 || customers == null || row >= customers.size()) return;
        Home.navigateToCustomerDetails(customers.get(row));
    }

    private void editRow(int row) {
        if (row < 0 || customers == null || row >= customers.size()) return;
        showEditDialog(customers.get(row));
    }

    private void showEditDialog(CustomerDto existing) {
        boolean isNew = (existing == null);
        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                isNew ? "Add Customer" : "Edit Customer", true);
        dlg.setSize(420, 300);
        dlg.setLocationRelativeTo(this);
        dlg.setLayout(new BorderLayout());

        JPanel form = new JPanel(new GridLayout(5, 2, 8, 10));
        form.setBorder(new EmptyBorder(16, 16, 8, 16));

        JTextField nameField    = new JTextField(existing != null ? existing.name()    : "");
        JTextField phoneField   = new JTextField(existing != null ? existing.phone()   : "");
        JTextField addressField = new JTextField(existing != null ? existing.address() : "");
        JFormattedTextField maxDebtField = new JFormattedTextField(
                java.text.NumberFormat.getNumberInstance());
        maxDebtField.setValue(existing != null ? existing.maxDebtAmount() : 0.0);

        form.add(label("Name *"));         form.add(nameField);
        form.add(label("Phone"));          form.add(phoneField);
        form.add(label("Address"));        form.add(addressField);
        form.add(label("Max Debt (Rs.)")); form.add(maxDebtField);
        form.add(new JLabel());            form.add(new JLabel());

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dlg.dispose());
        JButton save = new JButton(isNew ? "Add" : "Save");
        save.setBackground(GREEN);
        save.setForeground(Color.WHITE);
        save.setOpaque(true);
        save.setBorderPainted(false);
        save.addActionListener(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(dlg, "Name is required.", "Validation",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            double maxDebt = 0;
            try { maxDebt = ((Number) maxDebtField.getValue()).doubleValue(); } catch (Exception ex) {}
            if (isNew) {
                customerService.create(name, phoneField.getText(), addressField.getText(), maxDebt);
            } else {
                customerService.update(existing.customerId(), name, phoneField.getText(),
                        addressField.getText(), maxDebt);
            }
            dlg.dispose();
            loadData(searchField.getText().trim());
        });
        btns.add(cancel);
        btns.add(save);

        dlg.add(form, BorderLayout.CENTER);
        dlg.add(btns, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT2);
        return l;
    }

    // ── Customer table model ─────────────────────────────────────────────────

    private class CustomerTableModel extends AbstractTableModel {
        private final String[] COLS = {"#", "Name", "Phone", "Address",
                "Max Debt (Rs.)", "Outstanding (Rs.)", "Total Purchases (Rs.)", "Action"};
        private List<CustomerDto> data = new ArrayList<>();
        private List<double[]>    fin  = new ArrayList<>();

        void setData(List<CustomerDto> data, List<double[]> fin) {
            this.data = data;
            this.fin  = fin;
            fireTableDataChanged();
        }

        @Override public int getRowCount()    { return data.size(); }
        @Override public int getColumnCount() { return COLS.length; }
        @Override public String getColumnName(int c) { return COLS[c]; }

        @Override public Class<?> getColumnClass(int c) {
            return c == 7 ? JPanel.class : Object.class;
        }

        @Override public boolean isCellEditable(int r, int c) { return c == 7; }

        @Override public Object getValueAt(int r, int c) {
            if (r >= data.size()) return null;
            CustomerDto cu = data.get(r);
            double outstanding = fin.size() > r ? fin.get(r)[0] : 0;
            double purchases   = fin.size() > r ? fin.get(r)[1] : 0;
            return switch (c) {
                case 0 -> r + 1;
                case 1 -> cu.name();
                case 2 -> cu.phone();
                case 3 -> cu.address();
                case 4 -> String.format("%.2f", cu.maxDebtAmount());
                case 5 -> String.format("%.2f", outstanding);
                case 6 -> String.format("%.2f", purchases);
                case 7 -> r;
                default -> null;
            };
        }
    }

    // ── Customer action renderer/editor ──────────────────────────────────────

    private class ActionRenderer implements TableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable tbl, Object value,
                boolean isSel, boolean hasFocus, int row, int col) {
            return makeActionPanel(row, false);
        }
    }

    private class ActionEditor extends AbstractCellEditor implements TableCellEditor {
        private int currentRow = -1;

        @Override
        public Component getTableCellEditorComponent(JTable tbl, Object value,
                boolean isSel, int row, int col) {
            currentRow = row;
            return makeActionPanel(row, true);
        }

        @Override public Object getCellEditorValue()            { return currentRow; }
        @Override public boolean isCellEditable(EventObject e)  { return true; }
        @Override public boolean shouldSelectCell(EventObject e){ return true; }
    }

    private JPanel makeActionPanel(int row, boolean live) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 4));
        p.setBackground(row % 2 == 0 ? Color.WHITE : new Color(0xF7, 0xF8, 0xFB));

        JButton editBtn = new JButton("Edit");
        editBtn.setFont(editBtn.getFont().deriveFont(11f));
        editBtn.setMargin(new Insets(2, 8, 2, 8));
        editBtn.setFocusPainted(false);

        JButton detailsBtn = new JButton("Details");
        detailsBtn.setFont(detailsBtn.getFont().deriveFont(11f));
        detailsBtn.setMargin(new Insets(2, 8, 2, 8));
        detailsBtn.setBackground(NAVY);
        detailsBtn.setForeground(Color.WHITE);
        detailsBtn.setOpaque(true);
        detailsBtn.setBorderPainted(false);
        detailsBtn.setFocusPainted(false);

        if (live) {
            editBtn.addActionListener(e -> {
                if (table.getCellEditor() != null) table.getCellEditor().cancelCellEditing();
                editRow(row);
            });
            detailsBtn.addActionListener(e -> {
                if (table.getCellEditor() != null) table.getCellEditor().cancelCellEditing();
                openDetails(row);
            });
        }

        p.add(editBtn);
        p.add(detailsBtn);
        return p;
    }

    // ── Invoice table model ──────────────────────────────────────────────────

    private class InvoiceTableModel extends AbstractTableModel {
        private final String[] COLS = {
                "Invoice #", "Date", "Payment Type", "Total (Rs.)",
                "Balance (Rs.)", "Status", "Resolve By", "Resolved On", "Action"
        };
        private List<InvoiceDto> data = new ArrayList<>();

        void setData(List<InvoiceDto> data) {
            this.data = data;
            fireTableDataChanged();
        }

        @Override public int getRowCount()    { return data.size(); }
        @Override public int getColumnCount() { return COLS.length; }
        @Override public String getColumnName(int c) { return COLS[c]; }

        @Override public Class<?> getColumnClass(int c) {
            return c == 8 ? JButton.class : Object.class;
        }

        @Override public boolean isCellEditable(int r, int c) { return c == 8; }

        @Override public Object getValueAt(int r, int c) {
            if (r >= data.size()) return null;
            InvoiceDto inv = data.get(r);
            double balance = Math.max(0, inv.grossTotal() - inv.paid());
            return switch (c) {
                case 0 -> inv.invoiceNo();
                case 1 -> inv.date() != null ? DATE_FMT.format(inv.date()) : "";
                case 2 -> inv.paymentMethod() != null ? inv.paymentMethod() : "";
                case 3 -> String.format("%.2f", inv.grossTotal());
                case 4 -> String.format("%.2f", balance);
                case 5 -> inv.stat() != null ? inv.stat() : "";
                case 6 -> inv.creditResolveDate() != null ? DATE_FMT.format(inv.creditResolveDate()) : "—";
                case 7 -> inv.resolvedDate()     != null ? DATE_FMT.format(inv.resolvedDate())     : "—";
                case 8 -> r;
                default -> null;
            };
        }
    }

    // ── Invoice status renderer ──────────────────────────────────────────────

    private class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable tbl, Object value,
                boolean isSel, boolean hasFocus, int row, int col) {
            super.getTableCellRendererComponent(tbl, value, isSel, hasFocus, row, col);
            String stat = value != null ? value.toString() : "";
            if ("Credit".equalsIgnoreCase(stat) || "Partial".equalsIgnoreCase(stat)) {
                setForeground(AMBER);
            } else if ("Paid".equalsIgnoreCase(stat)) {
                setForeground(GREEN);
            } else {
                setForeground(RED);
            }
            setFont(getFont().deriveFont(Font.BOLD));
            return this;
        }
    }

    // ── Invoice action renderer/editor ───────────────────────────────────────

    private class InvoiceActionRenderer implements TableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable tbl, Object value,
                boolean isSel, boolean hasFocus, int row, int col) {
            return makeViewButton(row, false);
        }
    }

    private class InvoiceActionEditor extends AbstractCellEditor implements TableCellEditor {
        private int currentRow = -1;

        @Override
        public Component getTableCellEditorComponent(JTable tbl, Object value,
                boolean isSel, int row, int col) {
            currentRow = row;
            return makeViewButton(row, true);
        }

        @Override public Object getCellEditorValue()            { return currentRow; }
        @Override public boolean isCellEditable(EventObject e)  { return true; }
        @Override public boolean shouldSelectCell(EventObject e){ return true; }
    }

    private JPanel makeViewButton(int row, boolean live) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
        p.setBackground(row % 2 == 0 ? Color.WHITE : new Color(0xF7, 0xF8, 0xFB));

        JButton btn = new JButton("View");
        btn.setFont(btn.getFont().deriveFont(11f));
        btn.setMargin(new Insets(2, 10, 2, 10));
        btn.setBackground(NAVY);
        btn.setForeground(Color.WHITE);
        btn.setOpaque(true);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);

        if (live && row < customerInvoices.size()) {
            String invoiceNo = customerInvoices.get(row).invoiceNo();
            btn.addActionListener(e -> {
                if (invoiceTable.getCellEditor() != null) invoiceTable.getCellEditor().cancelCellEditing();
                Home.navigateToFindInvoice(invoiceNo);
            });
        }

        p.add(btn);
        return p;
    }
}
