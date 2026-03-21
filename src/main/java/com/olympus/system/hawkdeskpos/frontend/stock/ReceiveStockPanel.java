package com.olympus.system.hawkdeskpos.frontend.stock;

import com.olympus.system.hawkdeskpos.dto.GrnDto;
import com.olympus.system.hawkdeskpos.dto.ItemDto;
import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.frontend.components.SearchDropdown;
import com.olympus.system.hawkdeskpos.service.ItemService;
import com.olympus.system.hawkdeskpos.service.SettingsService;
import com.olympus.system.hawkdeskpos.service.StockService;
import com.olympus.system.hawkdeskpos.session.SessionContext;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Receive Stock (GRN) screen.
 *
 * Table columns:
 *   0  Item Name      (read-only)
 *   1  Current SKU    (read-only)
 *   2  Qty Received   [−] n [+]
 *   3  Cost Price     [−] cost [+]
 *   4  Sell Price     [−] price [+]
 *   5  Variant SKU    editable — leave blank to update existing stock; fill to create a named variant
 *   6  Batch          editable — custom batch/lot label; defaults to GRN number if blank
 *   7  Current Stock  (read-only)
 *   8  New Stock      (read-only, auto-calculated)
 */
public class ReceiveStockPanel extends JPanel {

    private static final Color BG    = new Color(0xF0, 0xF2, 0xF5);
    private static final Color NAVY  = new Color(0x1E, 0x3A, 0x5F);
    private static final Color TEXT2 = new Color(0x5A, 0x60, 0x70);

    private final ItemService    itemService;
    private final StockService   stockService;

    private JTextField grnNoField, supplierField, referenceField;
    private DefaultTableModel deliveryModel;
    private JTable deliveryTable;
    private final List<ItemDto> deliveryItems = new ArrayList<>();
    private JLabel summaryGrn, summaryLines, summaryQty, summaryCost;

    public ReceiveStockPanel(ItemService itemService, StockService stockService, SettingsService settingsService) {
        this.itemService  = itemService;
        this.stockService = stockService;
        setBackground(BG);
        setLayout(new BorderLayout());
        buildUI();
    }

    public void reset() {
        supplierField.setText("");
        referenceField.setText("");
        deliveryModel.setRowCount(0);
        deliveryItems.clear();
        updateSummary();
        generateGrnNo();
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(14, 0));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Receive Stock (GRN)");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        header.add(title, BorderLayout.WEST);
        JButton back = new JButton("← Back");
        back.addActionListener(e -> Home.navigate(Home.CARD_STOCK));
        JPanel hBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        hBtns.setOpaque(false);
        hBtns.add(back);
        header.add(hBtns, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout(14, 0));
        content.setOpaque(false);

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.add(buildGrnHeader());
        left.add(Box.createVerticalStrut(12));
        left.add(buildDeliverySection());
        left.add(Box.createVerticalStrut(16));
        left.add(buildActionRow());

        JScrollPane leftScroll = new JScrollPane(left);
        leftScroll.setBorder(null);
        leftScroll.setOpaque(false);
        leftScroll.getViewport().setOpaque(false);
        content.add(leftScroll, BorderLayout.CENTER);

        JPanel sidebar = buildSidebar();
        sidebar.setPreferredSize(new Dimension(240, 0));
        content.add(sidebar, BorderLayout.EAST);

        root.add(content, BorderLayout.CENTER);
        add(root);
        generateGrnNo();
    }

    private CardPanel buildGrnHeader() {
        CardPanel c = sectionCard("GRN DETAILS");
        JPanel grid = new JPanel(new GridLayout(1, 3, 10, 0));
        grid.setOpaque(false);

        grnNoField = new JTextField();
        grnNoField.setEditable(false);
        grnNoField.setBackground(new Color(0xF7, 0xF8, 0xFA));
        supplierField  = new JTextField();
        referenceField = new JTextField();

        grid.add(labeled("GRN Number",         grnNoField));
        grid.add(labeled("Supplier Name *",    supplierField));
        grid.add(labeled("Supplier Reference", referenceField));
        ((JPanel)c).add(grid, BorderLayout.CENTER);
        return c;
    }

    private CardPanel buildDeliverySection() {
        CardPanel c = sectionCard("DELIVERY ITEMS");

        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        searchRow.setOpaque(false);
        SearchDropdown search = new SearchDropdown(itemService::search, this::addItemToDelivery);
        search.setPreferredSize(new Dimension(320, 32));
        JLabel hint = new JLabel("  Search and select an item to add");
        hint.setFont(hint.getFont().deriveFont(12f));
        hint.setForeground(TEXT2);
        searchRow.add(search);
        searchRow.add(hint);

        // Cols 5 (Variant SKU) and 6 (Batch) are editable; others use custom renderers + mouse listener
        String[] cols = {"Item Name", "SKU", "Qty", "Cost (Rs.)", "Sell (Rs.)", "Variant SKU", "Batch", "Current", "New Stock"};
        deliveryModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return c == 5 || c == 6; }
        };
        // NOTE: no TableModelListener — updateSummary() writes col 7 which would cause
        // infinite recursion. updateSummary() is called explicitly after each modification.

        deliveryTable = new JTable(deliveryModel);
        deliveryTable.setRowHeight(40);
        deliveryTable.setShowGrid(false);
        deliveryTable.setIntercellSpacing(new Dimension(0, 0));
        deliveryTable.getTableHeader().setFont(deliveryTable.getFont().deriveFont(Font.BOLD, 12f));
        deliveryTable.getTableHeader().setBackground(new Color(0xF7, 0xF8, 0xFA));

        // Qty column
        deliveryTable.getColumnModel().getColumn(2).setCellRenderer(new QtyButtonRenderer());
        deliveryTable.getColumnModel().getColumn(2).setPreferredWidth(110);
        deliveryTable.getColumnModel().getColumn(2).setMinWidth(100);

        // Cost column
        deliveryTable.getColumnModel().getColumn(3).setCellRenderer(new CostButtonRenderer());
        deliveryTable.getColumnModel().getColumn(3).setPreferredWidth(130);
        deliveryTable.getColumnModel().getColumn(3).setMinWidth(110);

        // Sell Price column
        deliveryTable.getColumnModel().getColumn(4).setCellRenderer(new CostButtonRenderer());
        deliveryTable.getColumnModel().getColumn(4).setPreferredWidth(130);
        deliveryTable.getColumnModel().getColumn(4).setMinWidth(110);

        // Variant SKU column — normal text editing
        deliveryTable.getColumnModel().getColumn(5).setPreferredWidth(110);

        // Batch column — normal text editing
        deliveryTable.getColumnModel().getColumn(6).setPreferredWidth(110);

        // Current / New Stock — narrow
        deliveryTable.getColumnModel().getColumn(7).setPreferredWidth(70);
        deliveryTable.getColumnModel().getColumn(8).setPreferredWidth(80);

        // Mouse listener for qty (col 2), cost (col 3), sell price (col 4)
        deliveryTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int col = deliveryTable.columnAtPoint(e.getPoint());
                int row = deliveryTable.rowAtPoint(e.getPoint());
                if (row < 0) return;

                if (col == 2) {
                    Rectangle cell = deliveryTable.getCellRect(row, col, false);
                    int relX = e.getX() - cell.x;
                    if (relX <= 30) {
                        adjustQty(row, -1);
                    } else if (relX >= cell.width - 30) {
                        adjustQty(row, +1);
                    } else {
                        Object cur = deliveryModel.getValueAt(row, 2);
                        String input = JOptionPane.showInputDialog(
                                ReceiveStockPanel.this, "Enter quantity:",
                                cur instanceof Number n ? n.intValue() : 1);
                        if (input != null) {
                            try {
                                int qty = Math.max(1, Integer.parseInt(input.trim()));
                                int current = ((Number) deliveryModel.getValueAt(row, 2)).intValue();
                                adjustQty(row, qty - current);
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                } else if (col == 3) {
                    handleCostClick(row, col, e, "Enter cost price (Rs.):");
                } else if (col == 4) {
                    handleCostClick(row, col, e, "Enter selling price (Rs.):");
                }
            }
        });

        JButton removeBtn = new JButton("Remove Selected");
        removeBtn.addActionListener(e -> {
            int row = deliveryTable.getSelectedRow();
            if (row >= 0) { deliveryItems.remove(row); deliveryModel.removeRow(row); updateSummary(); }
        });
        JPanel tableActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
        tableActions.setOpaque(false);
        tableActions.add(removeBtn);

        JScrollPane scroll = new JScrollPane(deliveryTable);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(0xE2, 0xE5, 0xEA)));
        scroll.setPreferredSize(new Dimension(0, 220));

        JPanel inner = new JPanel(new BorderLayout(0, 8));
        inner.setOpaque(false);
        inner.add(searchRow,    BorderLayout.NORTH);
        inner.add(scroll,       BorderLayout.CENTER);
        inner.add(tableActions, BorderLayout.SOUTH);
        ((JPanel)c).add(inner, BorderLayout.CENTER);
        return c;
    }

    private void handleCostClick(int row, int col, MouseEvent e, String prompt) {
        Rectangle cell = deliveryTable.getCellRect(row, col, false);
        int relX = e.getX() - cell.x;
        if (relX <= 30) {
            adjustCost(row, col, -1.0);
        } else if (relX >= cell.width - 30) {
            adjustCost(row, col, +1.0);
        } else {
            Object cur = deliveryModel.getValueAt(row, col);
            double curVal = cur instanceof Number n ? n.doubleValue() : 0;
            String input = JOptionPane.showInputDialog(
                    ReceiveStockPanel.this, prompt, String.format("%.2f", curVal));
            if (input != null) {
                try {
                    double val = Math.max(0, Double.parseDouble(input.trim()));
                    deliveryModel.setValueAt(val, row, col);
                    updateSummary();
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    private void addItemToDelivery(ItemDto item) {
        for (int i = 0; i < deliveryItems.size(); i++) {
            if (deliveryItems.get(i).itemId() == item.itemId()) {
                deliveryTable.setRowSelectionInterval(i, i);
                return;
            }
        }
        deliveryItems.add(item);
        deliveryModel.addRow(new Object[]{
                item.itemName(), item.sku(), 1,
                item.costPrice(), item.sellingPrice(),
                "",              // Variant SKU (col 5)
                "",              // Batch       (col 6)
                item.currentQty(), item.currentQty() + 1
        });
        updateSummary();
    }

    private JPanel buildActionRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        row.setOpaque(false);
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> Home.navigate(Home.CARD_STOCK));
        JButton save = new JButton("Save GRN");
        save.setBackground(NAVY);
        save.setForeground(Color.WHITE);
        save.setOpaque(true);
        save.setBorderPainted(false);
        save.addActionListener(e -> saveGrn());
        row.add(cancel);
        row.add(save);
        return row;
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setOpaque(false);
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));

        CardPanel c = new CardPanel(new BorderLayout(0, 10));
        ((JPanel)c).setBorder(new EmptyBorder(14, 14, 14, 14));
        JLabel title = new JLabel("GRN SUMMARY");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 11f));
        title.setForeground(TEXT2);
        ((JPanel)c).add(title, BorderLayout.NORTH);

        JPanel stats = new JPanel(new GridLayout(4, 1, 0, 12));
        stats.setOpaque(false);
        summaryGrn   = new JLabel("—");
        summaryLines = new JLabel("0 lines");
        summaryQty   = new JLabel("0 units");
        summaryCost  = new JLabel("Rs. 0.00");
        summaryGrn.setFont(summaryGrn.getFont().deriveFont(Font.BOLD, 13f));
        summaryCost.setFont(summaryCost.getFont().deriveFont(Font.BOLD, 16f));
        summaryCost.setForeground(NAVY);
        stats.add(statRow("GRN No.", summaryGrn));
        stats.add(statRow("Lines",   summaryLines));
        stats.add(statRow("Total Qty",summaryQty));
        stats.add(statRow("Est. Cost",summaryCost));
        ((JPanel)c).add(stats, BorderLayout.CENTER);

        JLabel variantNote = new JLabel("<html><font color='#5A6070' size='2'>" +
                "<b>Variant SKU</b> — fill to create a new<br>price variant for the same item.<br><br>" +
                "<b>Batch</b> — optional label (e.g. LOT-001,<br>expiry date). Defaults to GRN number." +
                "</font></html>");
        variantNote.setBorder(new EmptyBorder(10, 0, 0, 0));
        ((JPanel)c).add(variantNote, BorderLayout.SOUTH);

        sidebar.add(c);
        return sidebar;
    }

    private JPanel statRow(String label, JLabel value) {
        JPanel p = new JPanel(new GridLayout(2, 1, 0, 2));
        p.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setFont(l.getFont().deriveFont(11f));
        l.setForeground(TEXT2);
        p.add(l); p.add(value);
        return p;
    }

    private void updateSummary() {
        summaryGrn.setText(grnNoField != null && !grnNoField.getText().isEmpty() ? grnNoField.getText() : "—");
        summaryLines.setText(deliveryModel.getRowCount() + " lines");
        int totalQty = 0; double totalCost = 0;
        for (int i = 0; i < deliveryModel.getRowCount(); i++) {
            Object qtyObj  = deliveryModel.getValueAt(i, 2);
            Object costObj = deliveryModel.getValueAt(i, 3);
            int    qty  = qtyObj  instanceof Number n ? n.intValue()   : 0;
            double cost = costObj instanceof Number n ? n.doubleValue() : 0;
            totalQty  += qty;
            totalCost += qty * cost;
            if (i < deliveryItems.size()) {
                int current = deliveryItems.get(i).currentQty();
                deliveryModel.setValueAt(current + qty, i, 8); // col 8 = New Stock
            }
        }
        summaryQty.setText(totalQty + " units");
        summaryCost.setText(String.format("Rs. %.2f", totalCost));
    }

    private void saveGrn() {
        if (supplierField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Supplier name is required.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (deliveryModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "Add at least one item.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (deliveryTable.isEditing()) deliveryTable.getCellEditor().stopCellEditing();

        List<GrnDto.GrnLineDto> lines = new ArrayList<>();
        double totalCost = 0;
        for (int i = 0; i < deliveryModel.getRowCount(); i++) {
            if (i >= deliveryItems.size()) continue;
            ItemDto item       = deliveryItems.get(i);
            int    qty         = ((Number) deliveryModel.getValueAt(i, 2)).intValue();
            double cost        = ((Number) deliveryModel.getValueAt(i, 3)).doubleValue();
            double sellPrice   = ((Number) deliveryModel.getValueAt(i, 4)).doubleValue();
            String variantSku  = deliveryModel.getValueAt(i, 5) != null
                                 ? deliveryModel.getValueAt(i, 5).toString().trim() : "";
            String batchName   = deliveryModel.getValueAt(i, 6) != null
                                 ? deliveryModel.getValueAt(i, 6).toString().trim() : "";
            totalCost += qty * cost;
            lines.add(new GrnDto.GrnLineDto(item.itemId(), item.itemName(), qty, cost,
                    sellPrice, item.currentQty(),
                    variantSku.isEmpty() ? null : variantSku,
                    batchName.isEmpty()  ? null : batchName));
        }

        String grnNo    = grnNoField.getText().trim();
        String supplier = supplierField.getText().trim();
        String ref      = referenceField.getText().trim();
        double fc       = totalCost;
        Long   empId    = SessionContext.current() != null ? SessionContext.current().getEmployee().id() : null;

        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                GrnDto dto = new GrnDto(grnNo, new java.util.Date(), supplier, ref, fc, lines);
                stockService.saveGrn(dto, empId);
                return null;
            }
            @Override protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(ReceiveStockPanel.this,
                            "GRN " + grnNo + " saved.", "GRN Saved", JOptionPane.INFORMATION_MESSAGE);
                    Home.navigate(Home.CARD_STOCK);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(ReceiveStockPanel.this,
                            "Failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void generateGrnNo() {
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() { return stockService.generateGrnNo(); }
            @Override protected void done() {
                try { grnNoField.setText(get()); updateSummary(); } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void adjustQty(int row, int delta) {
        if (row < 0 || row >= deliveryModel.getRowCount()) return;
        int cur = ((Number) deliveryModel.getValueAt(row, 2)).intValue();
        deliveryModel.setValueAt(Math.max(1, cur + delta), row, 2);
        updateSummary();
    }

    private void adjustCost(int row, int col, double delta) {
        if (row < 0 || row >= deliveryModel.getRowCount()) return;
        double cur = ((Number) deliveryModel.getValueAt(row, col)).doubleValue();
        deliveryModel.setValueAt(Math.max(0, Math.round((cur + delta) * 100.0) / 100.0), row, col);
        updateSummary();
    }

    // ── Qty button renderer ───────────────────────────────────────────────────

    private static class QtyButtonRenderer extends JPanel implements TableCellRenderer {
        private final JButton minus = new JButton("−");
        private final JLabel  value = new JLabel("1", SwingConstants.CENTER);
        private final JButton plus  = new JButton("+");

        QtyButtonRenderer() {
            setLayout(new BorderLayout(2, 0));
            setBorder(new EmptyBorder(4, 4, 4, 4));
            minus.setFont(minus.getFont().deriveFont(Font.BOLD, 12f));
            minus.setFocusPainted(false);
            minus.setPreferredSize(new Dimension(28, 26));
            plus.setFont(plus.getFont().deriveFont(Font.BOLD, 12f));
            plus.setFocusPainted(false);
            plus.setPreferredSize(new Dimension(28, 26));
            value.setFont(value.getFont().deriveFont(Font.BOLD, 13f));
            add(minus, BorderLayout.WEST);
            add(value, BorderLayout.CENTER);
            add(plus,  BorderLayout.EAST);
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object val, boolean isSel, boolean hasFocus, int row, int col) {
            value.setText(val != null ? val.toString() : "1");
            setBackground(isSel ? table.getSelectionBackground() : table.getBackground());
            setOpaque(true);
            return this;
        }
    }

    // ── Cost / Sell-price button renderer ────────────────────────────────────

    private static class CostButtonRenderer extends JPanel implements TableCellRenderer {
        private final JButton minus = new JButton("−");
        private final JLabel  value = new JLabel("0.00", SwingConstants.CENTER);
        private final JButton plus  = new JButton("+");

        CostButtonRenderer() {
            setLayout(new BorderLayout(2, 0));
            setBorder(new EmptyBorder(4, 4, 4, 4));
            minus.setFont(minus.getFont().deriveFont(Font.BOLD, 12f));
            minus.setFocusPainted(false);
            minus.setPreferredSize(new Dimension(28, 26));
            plus.setFont(plus.getFont().deriveFont(Font.BOLD, 12f));
            plus.setFocusPainted(false);
            plus.setPreferredSize(new Dimension(28, 26));
            value.setFont(value.getFont().deriveFont(Font.BOLD, 13f));
            add(minus, BorderLayout.WEST);
            add(value, BorderLayout.CENTER);
            add(plus,  BorderLayout.EAST);
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object val, boolean isSel, boolean hasFocus, int row, int col) {
            double d = val instanceof Number n ? n.doubleValue() : 0;
            value.setText(String.format("%.2f", d));
            setBackground(isSel ? table.getSelectionBackground() : table.getBackground());
            setOpaque(true);
            return this;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CardPanel sectionCard(String title) {
        CardPanel c = new CardPanel(new BorderLayout(0, 10));
        ((JPanel)c).setBorder(new EmptyBorder(14, 14, 14, 14));
        ((JPanel)c).setMaximumSize(new Dimension(Integer.MAX_VALUE, 400));
        ((JPanel)c).setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel t = new JLabel(title);
        t.setFont(t.getFont().deriveFont(Font.BOLD, 11f));
        t.setForeground(TEXT2);
        ((JPanel)c).add(t, BorderLayout.NORTH);
        return c;
    }

    private JPanel labeled(String lbl, JComponent comp) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        JLabel l = new JLabel(lbl);
        l.setFont(l.getFont().deriveFont(12f));
        l.setForeground(TEXT2);
        p.add(l, BorderLayout.NORTH);
        p.add(comp, BorderLayout.CENTER);
        return p;
    }
}
