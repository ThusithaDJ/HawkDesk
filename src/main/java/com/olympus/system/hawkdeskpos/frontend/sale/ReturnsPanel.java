package com.olympus.system.hawkdeskpos.frontend.sale;

import com.olympus.system.hawkdeskpos.dto.ReturnDto;
import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.frontend.components.Refreshable;
import com.olympus.system.hawkdeskpos.service.ReturnService;
import com.olympus.system.hawkdeskpos.session.SessionContext;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;
import java.awt.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.EventObject;
import java.util.List;

/**
 * Returns management panel.
 * Searchable/filterable table with per-row "Manage" button to resolve returns.
 * Manage is only enabled when Resolved As is "Pending" or "Return to Seller".
 */
public class ReturnsPanel extends JPanel implements Refreshable {

    private static final Color BG    = new Color(0xF0, 0xF2, 0xF5);
    private static final Color NAVY  = new Color(0x1E, 0x3A, 0x5F);
    private static final Color TEXT2 = new Color(0x5A, 0x60, 0x70);
    private static final Color GREEN = new Color(0x2E, 0x7D, 0x32);
    private static final Color RED   = new Color(0xC6, 0x28, 0x28);

    private static final String[] COLS = {
        "Return ID", "Invoice No", "Date", "Item", "Qty",
        "Reason", "Refund Method", "Status", "Resolved As", "Cashier", "Reference", "Action"
    };

    private static final String[] REASONS       = { "All", "Damaged", "Wrong item", "Customer change of mind", "Other" };
    private static final String[] REFUND_METHODS = { "All", "Cash", "Exchange", "Store Credit", "Void" };
    private static final String[] STATUSES       = { "All", "Cash", "Exchange", "Store Credit", "Void" };
    private static final String[] RESOLVED_AS    = { "All", "Pending", "Return to Seller",
                                                      "Resolved - Same Stock", "Resolved - New Batch", "GRN", "Exchange" };

    private final ReturnService returnService;
    private DefaultTableModel   tableModel;
    private JTable              table;
    private JTextField          searchField;
    private JTextField          dateFromField, dateToField;
    private JComboBox<String>   reasonCombo, refundCombo, statusCombo, resolvedCombo;

    private List<ReturnDto>     returns          = new ArrayList<>();
    private List<ReturnDto>     displayedReturns = new ArrayList<>();

    public ReturnsPanel(ReturnService returnService) {
        this.returnService = returnService;
        setBackground(BG);
        setLayout(new BorderLayout());
        buildUI();
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Returns");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        header.add(title, BorderLayout.WEST);
        JPanel hBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        hBtns.setOpaque(false);
        JButton back = new JButton("← Back");
        back.addActionListener(e -> Home.navigate(Home.CARD_DASH));
        hBtns.add(back);
        header.add(hBtns, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        // Filter bar
        JPanel filterCard = buildFilterBar();

        // Table
        tableModel = new DefaultTableModel(COLS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return c == 11; }
            @Override public Class<?> getColumnClass(int c)       { return c == 11 ? JButton.class : Object.class; }
        };
        table = new JTable(tableModel);
        table.setRowHeight(40);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.getTableHeader().setFont(table.getFont().deriveFont(Font.BOLD, 12f));
        table.getTableHeader().setBackground(new Color(0xE2, 0xE5, 0xEA));
        table.getTableHeader().setForeground(TEXT2);

        int[] widths = {70, 130, 120, 160, 40, 130, 110, 100, 140, 100, 140, 90};
        for (int i = 0; i < widths.length; i++)
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);

        table.getColumnModel().getColumn(11).setCellRenderer(new ManageRenderer());
        table.getColumnModel().getColumn(11).setCellEditor(new ManageEditor());

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(0xD1, 0xD5, 0xDB)));

        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setOpaque(false);
        body.add(filterCard, BorderLayout.NORTH);
        body.add(scroll,     BorderLayout.CENTER);
        root.add(body, BorderLayout.CENTER);

        add(root);
    }

    private JPanel buildFilterBar() {
        JPanel outer = new JPanel();
        outer.setOpaque(false);
        outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));

        // Row 1: text search + date range + refresh
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row1.setOpaque(false);

        searchField = new JTextField(22);
        searchField.putClientProperty("JTextField.placeholderText", "Search invoice, item, cashier…");
        javax.swing.event.DocumentListener dl = new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { applyFilter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { applyFilter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) {}
        };
        searchField.getDocument().addDocumentListener(dl);

        dateFromField = new JTextField(9);
        dateFromField.putClientProperty("JTextField.placeholderText", "From yyyy-MM-dd");
        dateToField   = new JTextField(9);
        dateToField.putClientProperty("JTextField.placeholderText", "To yyyy-MM-dd");
        dateFromField.getDocument().addDocumentListener(dl);
        dateToField.getDocument().addDocumentListener(dl);

        JButton refreshBtn = new JButton("↺ Refresh");
        refreshBtn.addActionListener(e -> loadData());

        row1.add(new JLabel("Search:"));   row1.add(searchField);
        row1.add(new JLabel("From:"));     row1.add(dateFromField);
        row1.add(new JLabel("To:"));       row1.add(dateToField);
        row1.add(refreshBtn);

        // Row 2: combo filters
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row2.setOpaque(false);

        reasonCombo  = combo(REASONS);
        refundCombo  = combo(REFUND_METHODS);
        statusCombo  = combo(STATUSES);
        resolvedCombo = combo(RESOLVED_AS);

        row2.add(new JLabel("Reason:"));         row2.add(reasonCombo);
        row2.add(new JLabel("Refund Method:"));  row2.add(refundCombo);
        row2.add(new JLabel("Status:"));         row2.add(statusCombo);
        row2.add(new JLabel("Resolved As:"));    row2.add(resolvedCombo);

        outer.add(row1);
        outer.add(Box.createVerticalStrut(4));
        outer.add(row2);
        outer.add(Box.createVerticalStrut(4));
        return outer;
    }

    private JComboBox<String> combo(String[] items) {
        JComboBox<String> cb = new JComboBox<>(items);
        cb.setPreferredSize(new Dimension(140, 26));
        cb.addActionListener(e -> applyFilter());
        return cb;
    }

    private void applyFilter() {
        String    text       = searchField.getText().trim().toLowerCase();
        String    fromStr    = dateFromField.getText().trim();
        String    toStr      = dateToField.getText().trim();
        String    reason     = (String) reasonCombo.getSelectedItem();
        String    refund     = (String) refundCombo.getSelectedItem();
        String    status     = (String) statusCombo.getSelectedItem();
        String    resolved   = (String) resolvedCombo.getSelectedItem();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Date fromDate = null, toDate = null;
        try { if (!fromStr.isEmpty()) fromDate = sdf.parse(fromStr); } catch (ParseException ignored) {}
        try { if (!toStr.isEmpty())   toDate   = sdf.parse(toStr);   } catch (ParseException ignored) {}

        final Date finalFrom = fromDate;
        final Date finalTo   = toDate;

        tableModel.setRowCount(0);
        displayedReturns.clear();
        SimpleDateFormat displaySdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        for (ReturnDto r : returns) {
            // Text search
            if (!text.isEmpty()) {
                boolean matches = r.invoiceNo().toLowerCase().contains(text)
                        || r.itemName().toLowerCase().contains(text)
                        || r.cashierName().toLowerCase().contains(text)
                        || r.reason().toLowerCase().contains(text);
                if (!matches) continue;
            }
            // Date range
            if (finalFrom != null && r.returnDate() != null && r.returnDate().before(finalFrom)) continue;
            if (finalTo   != null && r.returnDate() != null) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(finalTo);
                cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59);
                if (r.returnDate().after(cal.getTime())) continue;
            }
            // Combo filters (skip "All")
            if (!"All".equals(reason)   && !reason.equals(r.reason()))        continue;
            if (!"All".equals(refund)   && !refund.equals(r.refundMethod()))   continue;
            if (!"All".equals(status)   && !status.equals(r.stat()))           continue;
            String ra = r.resolveAction() != null ? r.resolveAction() : "Pending";
            if (!"All".equals(resolved) && !resolved.equals(ra))               continue;

            displayedReturns.add(r);
            addRow(r, displaySdf);
        }
    }

    private void addRow(ReturnDto r, SimpleDateFormat sdf) {
        String resolvedAs = r.resolveAction() != null ? r.resolveAction() : "Pending";
        // Reference: customer name for Exchange returns, or GRN# if linked
        String reference = "";
        if (r.customerRef() != null && !r.customerRef().isEmpty()) {
            reference = r.customerRef();
        } else if (r.linkedGrnNo() != null) {
            reference = "GRN#" + r.linkedGrnNo();
        } else if (r.resolvedInvoiceNo() != null && !r.resolvedInvoiceNo().isEmpty()) {
            reference = r.resolvedInvoiceNo();
        }
        tableModel.addRow(new Object[]{
            r.returnId(),
            r.invoiceNo(),
            r.returnDate() != null ? sdf.format(r.returnDate()) : "—",
            r.itemName(),
            r.qty(),
            r.reason(),
            r.refundMethod(),
            r.stat(),
            resolvedAs,
            r.cashierName(),
            reference,
            "Manage"
        });
    }

    private void loadData() {
        new SwingWorker<List<ReturnDto>, Void>() {
            @Override protected List<ReturnDto> doInBackground() {
                return returnService.listAllReturns();
            }
            @Override protected void done() {
                try {
                    returns = get();
                    applyFilter();
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    @Override public void refresh() { loadData(); }

    /** Returns true when the Manage action is applicable for the given row. */
    private boolean isManageable(int row) {
        if (row < 0 || row >= displayedReturns.size()) return false;
        String ra = displayedReturns.get(row).resolveAction();
        return ra == null || "Pending".equals(ra) || "Return to Seller".equals(ra);
    }

    // ── Manage button renderer ────────────────────────────────────────────────

    private class ManageRenderer implements TableCellRenderer {
        private final JButton active   = new JButton("Manage");
        private final JButton inactive = new JButton("Manage");
        {
            active.setFocusPainted(false);
            inactive.setFocusPainted(false);
            inactive.setEnabled(false);
        }
        @Override public Component getTableCellRendererComponent(JTable t, Object v,
                boolean isSel, boolean hasFocus, int row, int col) {
            JButton btn = isManageable(row) ? active : inactive;
            btn.setBackground(isSel ? t.getSelectionBackground() : Color.WHITE);
            return btn;
        }
    }

    // ── Manage button editor ──────────────────────────────────────────────────

    private class ManageEditor extends AbstractCellEditor implements TableCellEditor {
        private final JButton btn = new JButton("Manage");
        ManageEditor() {
            btn.setFocusPainted(false);
            btn.addActionListener(e -> {
                int row = table.getEditingRow();
                fireEditingStopped();
                if (isManageable(row)) {
                    openManageDialog(displayedReturns.get(row));
                }
            });
        }
        @Override public Object getCellEditorValue()            { return "Manage"; }
        @Override public boolean isCellEditable(EventObject ev) { return true; }
        @Override public Component getTableCellEditorComponent(JTable t, Object v,
                boolean isSel, int row, int col) { return btn; }
    }

    // ── Manage dialog ─────────────────────────────────────────────────────────

    private void openManageDialog(ReturnDto r) {
        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                "Manage Return — " + r.invoiceNo(), true);
        dlg.setSize(520, 480);
        dlg.setLocationRelativeTo(this);
        dlg.setLayout(new BorderLayout(0, 0));

        JPanel main = new JPanel();
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
        main.setBorder(new EmptyBorder(16, 16, 8, 16));

        CardPanel summaryCard = new CardPanel(new BorderLayout(0, 6));
        ((JPanel)summaryCard).setBorder(new EmptyBorder(12, 14, 12, 14));
        ((JPanel)summaryCard).setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        ((JPanel)summaryCard).setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel cardTitle = new JLabel("RETURN SUMMARY");
        cardTitle.setFont(cardTitle.getFont().deriveFont(Font.BOLD, 10f));
        cardTitle.setForeground(TEXT2);

        JPanel grid = new JPanel(new GridLayout(0, 2, 8, 6));
        grid.setOpaque(false);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        grid.add(infoLabel("Invoice")); grid.add(bold(r.invoiceNo()));
        grid.add(infoLabel("Item"));    grid.add(bold(r.itemName()));
        grid.add(infoLabel("Qty"));     grid.add(bold(String.valueOf(r.qty())));
        grid.add(infoLabel("Reason"));  grid.add(bold(r.reason()));
        grid.add(infoLabel("Refund"));  grid.add(bold(r.refundMethod()));
        grid.add(infoLabel("Date"));    grid.add(bold(r.returnDate() != null ? sdf.format(r.returnDate()) : "—"));
        if (r.originalSaleCost() > 0) {
            grid.add(infoLabel("Sale Cost")); grid.add(bold("Rs. " + String.format("%,.2f", r.originalSaleCost())));
        }
        String curStatus = r.resolveAction() != null ? r.resolveAction() : "Pending";
        grid.add(infoLabel("Current Status")); grid.add(bold(curStatus));

        ((JPanel)summaryCard).add(cardTitle, BorderLayout.NORTH);
        ((JPanel)summaryCard).add(grid,      BorderLayout.CENTER);
        main.add(summaryCard);
        main.add(Box.createVerticalStrut(12));

        CardPanel actionCard = new CardPanel(new BorderLayout(0, 8));
        ((JPanel)actionCard).setBorder(new EmptyBorder(12, 14, 12, 14));
        ((JPanel)actionCard).setAlignmentX(Component.LEFT_ALIGNMENT);
        ((JPanel)actionCard).setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));

        JLabel actionTitle = new JLabel("SELECT ACTION");
        actionTitle.setFont(actionTitle.getFont().deriveFont(Font.BOLD, 10f));
        actionTitle.setForeground(TEXT2);

        String[] actionOptions = {
            "— Choose action —",
            "Add back to same stock/batch",
            "Add back to stock with different batch",
            "Return to seller"
        };
        JComboBox<String> actionCombo = new JComboBox<>(actionOptions);

        JPanel extraPanel = new JPanel();
        extraPanel.setOpaque(false);
        extraPanel.setLayout(new BoxLayout(extraPanel, BoxLayout.Y_AXIS));

        JFormattedTextField costField  = new JFormattedTextField(java.text.NumberFormat.getNumberInstance());
        JFormattedTextField priceField = new JFormattedTextField(java.text.NumberFormat.getNumberInstance());
        costField.setValue(0.0); priceField.setValue(0.0);

        JTextField grnField = new JTextField();
        JLabel     grnCostDiffLabel = new JLabel(" ");
        grnCostDiffLabel.setFont(grnCostDiffLabel.getFont().deriveFont(11f));
        grnCostDiffLabel.setForeground(TEXT2);

        actionCombo.addActionListener(e -> {
            extraPanel.removeAll();
            int sel = actionCombo.getSelectedIndex();
            if (sel == 2) {
                JPanel bp = new JPanel(new GridLayout(2, 2, 8, 6));
                bp.setOpaque(false);
                bp.add(infoLabel("New Cost (Rs.)"));  bp.add(costField);
                bp.add(infoLabel("New Price (Rs.)")); bp.add(priceField);
                extraPanel.add(bp);
            } else if (sel == 3) {
                JPanel gp = new JPanel(new BorderLayout(6, 4));
                gp.setOpaque(false);
                JPanel gRow = new JPanel(new BorderLayout(6, 0));
                gRow.setOpaque(false);
                gRow.add(infoLabel("Link GRN # (optional):"), BorderLayout.WEST);
                gRow.add(grnField, BorderLayout.CENTER);
                JButton findGrn = new JButton("Lookup");
                findGrn.addActionListener(ev -> {
                    String grnText = grnField.getText().trim();
                    if (grnText.isEmpty()) return;
                    grnCostDiffLabel.setText("GRN #" + grnText + " linked.");
                });
                gRow.add(findGrn, BorderLayout.EAST);
                gp.add(gRow,             BorderLayout.NORTH);
                gp.add(grnCostDiffLabel, BorderLayout.CENTER);
                extraPanel.add(gp);
            }
            extraPanel.revalidate();
            extraPanel.repaint();
            dlg.pack();
            dlg.setSize(Math.max(dlg.getWidth(), 520), Math.max(dlg.getHeight(), 480));
        });

        ((JPanel)actionCard).add(actionTitle,   BorderLayout.NORTH);
        ((JPanel)actionCard).add(actionCombo,   BorderLayout.CENTER);
        ((JPanel)actionCard).add(extraPanel,    BorderLayout.SOUTH);
        main.add(actionCard);

        JScrollPane scrollMain = new JScrollPane(main);
        scrollMain.setBorder(null);
        dlg.add(scrollMain, BorderLayout.CENTER);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton cancel  = new JButton("Cancel");
        cancel.addActionListener(e -> dlg.dispose());

        JButton confirm = new JButton("Confirm");
        confirm.setBackground(GREEN);
        confirm.setForeground(Color.WHITE);
        confirm.setOpaque(true);
        confirm.setBorderPainted(false);
        confirm.addActionListener(e -> {
            int sel = actionCombo.getSelectedIndex();
            if (sel == 0) {
                JOptionPane.showMessageDialog(dlg, "Please select an action.", "Action Required",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            Long empId = SessionContext.current() != null
                    ? SessionContext.current().getEmployee().id() : null;
            try {
                switch (sel) {
                    case 1 -> returnService.resolveReturnSameStock(r.returnId(), empId);
                    case 2 -> {
                        double cost  = ((Number) costField.getValue()).doubleValue();
                        double price = ((Number) priceField.getValue()).doubleValue();
                        if (cost <= 0 || price <= 0) {
                            JOptionPane.showMessageDialog(dlg, "Cost and price must be greater than 0.",
                                    "Validation", JOptionPane.WARNING_MESSAGE);
                            return;
                        }
                        returnService.resolveReturnNewBatch(r.returnId(), cost, price, empId);
                    }
                    case 3 -> {
                        String grnText = grnField.getText().trim();
                        Integer grnNo = null;
                        if (!grnText.isEmpty()) {
                            try { grnNo = Integer.parseInt(grnText); } catch (NumberFormatException ignored) {}
                        }
                        returnService.markReturnToSeller(r.returnId(), grnNo, empId);
                    }
                }
                dlg.dispose();
                loadData();
                JOptionPane.showMessageDialog(this, "Return updated successfully.", "Done",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dlg, "Error: " + ex.getMessage(), "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        btnRow.add(cancel);
        btnRow.add(confirm);
        dlg.add(btnRow, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    private JLabel infoLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(12f));
        l.setForeground(TEXT2);
        return l;
    }

    private JLabel bold(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 12f));
        return l;
    }
}
