package com.olympus.system.hawkdeskpos.frontend.stock;

import com.olympus.system.hawkdeskpos.dto.GrnDto;
import com.olympus.system.hawkdeskpos.dto.ItemDto;
import com.olympus.system.hawkdeskpos.dto.ReturnDto;
import com.olympus.system.hawkdeskpos.dto.StockBatchDto;
import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.frontend.components.SearchDropdown;
import com.olympus.system.hawkdeskpos.service.CashAccountService;
import com.olympus.system.hawkdeskpos.service.ItemService;
import com.olympus.system.hawkdeskpos.service.ReturnService;
import com.olympus.system.hawkdeskpos.service.SettingsService;
import com.olympus.system.hawkdeskpos.service.StockService;
import com.olympus.system.hawkdeskpos.session.SessionContext;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * Receive Stock (GRN) screen.
 *
 * Table columns:
 *   0  Item Name      (read-only)
 *   1  Current SKU    (read-only)
 *   2  Qty Received   [−] n [+]
 *   3  UoM            (read-only)
 *   4  Cost Price     [−] cost [+]
 *   5  Sell Price     [−] price [+]
 *   6  Variant SKU    editable
 *   7  Batch #        editable — auto-generated; can be overridden
 *   8  Expiry         editable — optional expiry date (YYYY-MM-DD)
 *   9  Current Stock  (read-only)
 *  10  New Stock      (read-only, auto-calculated)
 */
public class ReceiveStockPanel extends JPanel implements com.olympus.system.hawkdeskpos.frontend.components.Refreshable {

    private static final Color BG    = new Color(0xF0, 0xF2, 0xF5);
    private static final Color NAVY  = new Color(0x1E, 0x3A, 0x5F);
    private static final Color TEXT2 = new Color(0x5A, 0x60, 0x70);
    private static final Color AMBER = new Color(0xB4, 0x5B, 0x00);

    private final ItemService          itemService;
    private final StockService         stockService;
    private final CashAccountService   cashAccountService;
    private final ReturnService        returnService;

    // GRN header fields
    private JTextField grnNoField, supplierField, referenceField;

    // Delivery items table
    private DefaultTableModel deliveryModel;
    private JTable deliveryTable;
    private final List<ItemDto>    deliveryItems           = new ArrayList<>();
    private final List<Integer>    deliveryExistingStockIds = new ArrayList<>(); // null = new batch

    // Summary
    private JLabel    summaryGrn, summaryLines, summaryQty, summaryCost;
    private JLabel    summaryReturnsDed;   // returns-to-seller deduction
    private JTextField discountField;      // user-entered discount
    private JTextField estCostField;       // auto-calculated net cost
    private JButton saveGrnBtn;
    private JLabel  costWarnLabel;

    // Returns-to-Seller panel
    private List<ReturnDto>    returnToSellerList  = new ArrayList<>();
    private final Set<Integer> selectedReturnIds   = new HashSet<>();
    private JPanel             returnsListContent; // rebuilt by refreshReturnsUI()
    private JPanel             returnDetailContent; // right panel, updated on click
    private JPanel             sidebarOuter;        // holds summary (NORTH) + return detail (CENTER)

    public ReceiveStockPanel(ItemService itemService, StockService stockService,
                             SettingsService settingsService) {
        this(itemService, stockService, settingsService, null, null);
    }

    public ReceiveStockPanel(ItemService itemService, StockService stockService,
                             SettingsService settingsService, CashAccountService cashAccountService) {
        this(itemService, stockService, settingsService, cashAccountService, null);
    }

    public ReceiveStockPanel(ItemService itemService, StockService stockService,
                             SettingsService settingsService, CashAccountService cashAccountService,
                             ReturnService returnService) {
        this.itemService        = itemService;
        this.stockService       = stockService;
        this.cashAccountService = cashAccountService;
        this.returnService      = returnService;
        setBackground(BG);
        setLayout(new BorderLayout());
        buildUI();
    }

    @Override
    public void refresh() { reset(); }

    public void reset() {
        supplierField.setText("");
        referenceField.setText("");
        deliveryModel.setRowCount(0);
        deliveryItems.clear();
        deliveryExistingStockIds.clear();
        selectedReturnIds.clear();
        if (discountField != null) discountField.setText("0");
        updateSummary();
        generateGrnNo();
        if (returnService != null) loadReturnToSeller();
    }

    public void preload(ItemDto item) {
        reset();
        addItemToDelivery(item);
    }

    public void preloadMultiple(java.util.List<ItemDto> items) {
        reset();
        for (ItemDto item : items) addItemToDelivery(item);
    }

    private static Font symbolFont(char c, float size) {
        java.util.Set<String> avail = new java.util.HashSet<>(java.util.Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String family : new String[]{"Segoe UI Symbol", "Apple Symbols", "Noto Sans Symbols", "DejaVu Sans"}) {
            if (avail.contains(family)) {
                Font f = new Font(family, Font.PLAIN, 1);
                if (f.canDisplay(c)) return f.deriveFont(size);
            }
        }
        return null;
    }

    // ── Build UI ──────────────────────────────────────────────────────────────

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
        JButton refreshBtn = new JButton();
        Font font = symbolFont('↺', 12f);
        refreshBtn.setText(font== null ? "Refresh": "↺ Refresh");
        refreshBtn.setFont(font);
        refreshBtn.addActionListener(e -> reset());
        JPanel hBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        hBtns.setOpaque(false);
        hBtns.add(refreshBtn);
        hBtns.add(back);
        header.add(hBtns, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        // ── Left: GRN Details (NORTH) + Returns+Delivery split (CENTER) + actions (SOUTH)
        JPanel left = new JPanel(new BorderLayout(0, 12));
        left.setOpaque(false);
        left.add(buildGrnHeader(), BorderLayout.NORTH);

        // Middle: Returns selection panel (small, top) + Delivery items (bottom)
        JSplitPane centerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                buildReturnsSelectionCard(), buildDeliverySection());
        centerSplit.setResizeWeight(0.30);
        centerSplit.setBorder(null);
        centerSplit.setDividerSize(5);
        centerSplit.setOpaque(false);
        centerSplit.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0
                    && centerSplit.isShowing()) {
                SwingUtilities.invokeLater(() -> centerSplit.setDividerLocation(0.30));
            }
        });
        left.add(centerSplit, BorderLayout.CENTER);
        left.add(buildActionRow(), BorderLayout.SOUTH);

        // ── Right: GRN Summary (NORTH) + Return Detail (CENTER)
        sidebarOuter = new JPanel(new BorderLayout(0, 8));
        sidebarOuter.setOpaque(false);
        sidebarOuter.add(buildSidebar(), BorderLayout.NORTH);

        returnDetailContent = buildReturnDetailPlaceholder();
        sidebarOuter.add(returnDetailContent, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, sidebarOuter);
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

        root.add(split, BorderLayout.CENTER);
        add(root);
        generateGrnNo();
        if (returnService != null) loadReturnToSeller();
    }

    // ── GRN Details card ──────────────────────────────────────────────────────

    private CardPanel buildGrnHeader() {
        CardPanel c = sectionCard("GRN DETAILS");

        grnNoField = new JTextField();
        grnNoField.setEditable(false);
        grnNoField.setBackground(new Color(0xF7, 0xF8, 0xFA));
        grnNoField.setPreferredSize(new Dimension(0, 28));
        supplierField  = new JTextField();
        supplierField.setPreferredSize(new Dimension(0, 28));
        referenceField = new JTextField();
        referenceField.setPreferredSize(new Dimension(0, 28));

        JPanel grid = new JPanel(new GridLayout(1, 3, 10, 0));
        grid.setOpaque(false);
        grid.add(labeled("GRN Number",         grnNoField));
        grid.add(labeled("Supplier Name *",    supplierField));
        grid.add(labeled("Supplier Reference", referenceField));
        ((JPanel)c).add(grid, BorderLayout.CENTER);
        return c;
    }

    // ── Returns-to-Seller selection card ─────────────────────────────────────

    private JPanel buildReturnsSelectionCard() {
        CardPanel c = sectionCard("PENDING RETURNS — RETURN TO SELLER");
        returnsListContent = new JPanel(new BorderLayout());
        returnsListContent.setOpaque(false);
        JLabel placeholder = new JLabel("  Loading returns…");
        placeholder.setForeground(TEXT2);
        placeholder.setFont(placeholder.getFont().deriveFont(12f));
        returnsListContent.add(placeholder, BorderLayout.CENTER);
        ((JPanel)c).add(returnsListContent, BorderLayout.CENTER);
        return (JPanel) c;
    }

    private void loadReturnToSeller() {
        new SwingWorker<List<ReturnDto>, Void>() {
            @Override protected List<ReturnDto> doInBackground() {
                return returnService.listReturnToSellerPending();
            }
            @Override protected void done() {
                try {
                    returnToSellerList = get();
                    refreshReturnsUI();
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void refreshReturnsUI() {
        returnsListContent.removeAll();
        if (returnToSellerList.isEmpty()) {
            JLabel none = new JLabel("  No pending 'Return to Seller' returns.");
            none.setForeground(TEXT2);
            none.setFont(none.getFont().deriveFont(12f));
            returnsListContent.add(none, BorderLayout.CENTER);
        } else {
            JPanel listPanel = new JPanel();
            listPanel.setOpaque(false);
            listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            for (ReturnDto r : returnToSellerList) {
                listPanel.add(buildReturnRow(r, sdf));
                listPanel.add(Box.createVerticalStrut(3));
            }
            JScrollPane scroll = new JScrollPane(listPanel);
            scroll.setBorder(null);
            scroll.setOpaque(false);
            scroll.getViewport().setOpaque(false);
            returnsListContent.add(scroll, BorderLayout.CENTER);
        }
        returnsListContent.revalidate();
        returnsListContent.repaint();
    }

    private JPanel buildReturnRow(ReturnDto r, SimpleDateFormat sdf) {
        boolean selected = selectedReturnIds.contains(r.returnId());
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(true);
        row.setBackground(selected ? new Color(0xE3, 0xF2, 0xFD) : Color.WHITE);
        row.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 3, 0, 0, selected ? NAVY : AMBER),
                new EmptyBorder(6, 8, 6, 8)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Checkbox + info
        JCheckBox chk = new JCheckBox();
        chk.setSelected(selected);
        chk.setOpaque(false);
        chk.addActionListener(e -> {
            if (chk.isSelected()) selectedReturnIds.add(r.returnId());
            else selectedReturnIds.remove(r.returnId());
            refreshReturnsUI();
            updateSummary();
        });

        JLabel info = new JLabel(String.format("<html><b>%s</b> — %s  <small>(Qty: %.0f | %s)</small></html>",
                r.itemName(), r.invoiceNo(), r.qty(),
                r.returnDate() != null ? sdf.format(r.returnDate()) : "—"));
        info.setFont(info.getFont().deriveFont(12f));

        row.add(chk,  BorderLayout.WEST);
        row.add(info, BorderLayout.CENTER);

        // Click → show detail on right
        MouseAdapter clickDetail = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { showReturnDetail(r); }
        };
        row.addMouseListener(clickDetail);
        info.addMouseListener(clickDetail);

        return row;
    }

    private void showReturnDetail(ReturnDto r) {
        sidebarOuter.remove(returnDetailContent);
        returnDetailContent = buildReturnDetailCard(r);
        sidebarOuter.add(returnDetailContent, BorderLayout.CENTER);
        sidebarOuter.revalidate();
        sidebarOuter.repaint();
    }

    private JPanel buildReturnDetailPlaceholder() {
        CardPanel c = new CardPanel(new BorderLayout());
        ((JPanel)c).setBorder(new EmptyBorder(12, 14, 12, 14));
        JLabel hint = new JLabel("<html><center><br>Click a return to<br>view details</center></html>");
        hint.setForeground(TEXT2);
        hint.setHorizontalAlignment(SwingConstants.CENTER);
        ((JPanel)c).add(hint, BorderLayout.CENTER);
        return (JPanel) c;
    }

    private JPanel buildReturnDetailCard(ReturnDto r) {
        CardPanel c = new CardPanel(new BorderLayout(0, 8));
        ((JPanel)c).setBorder(new EmptyBorder(12, 14, 12, 14));

        JLabel title = new JLabel("RETURN DETAIL");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 10f));
        title.setForeground(TEXT2);
        ((JPanel)c).add(title, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(0, 2, 6, 5));
        grid.setOpaque(false);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        grid.add(detailLabel("Return ID")); grid.add(detailBold(String.valueOf(r.returnId())));
        grid.add(detailLabel("Invoice"));   grid.add(detailBold(r.invoiceNo()));
        grid.add(detailLabel("Item"));      grid.add(detailBold(r.itemName()));
        grid.add(detailLabel("Qty"));       grid.add(detailBold(String.valueOf(r.qty())));
        grid.add(detailLabel("Reason"));    grid.add(detailBold(r.reason()));
        grid.add(detailLabel("Method"));    grid.add(detailBold(r.refundMethod()));
        grid.add(detailLabel("Date"));      grid.add(detailBold(
                r.returnDate() != null ? sdf.format(r.returnDate()) : "—"));
        if (r.originalSaleCost() > 0) {
            grid.add(detailLabel("Sale Cost"));
            grid.add(detailBold("Rs. " + String.format("%,.2f", r.originalSaleCost())));
        }

        // Include in GRN checkbox (mirrors the selection state)
        JCheckBox inclChk = new JCheckBox("Include in this GRN");
        inclChk.setOpaque(false);
        inclChk.setSelected(selectedReturnIds.contains(r.returnId()));
        inclChk.addActionListener(e -> {
            if (inclChk.isSelected()) selectedReturnIds.add(r.returnId());
            else selectedReturnIds.remove(r.returnId());
            refreshReturnsUI();
            updateSummary();
        });

        ((JPanel)c).add(grid,    BorderLayout.CENTER);
        ((JPanel)c).add(inclChk, BorderLayout.SOUTH);
        return (JPanel) c;
    }

    private JLabel detailLabel(String t) {
        JLabel l = new JLabel(t); l.setFont(l.getFont().deriveFont(11f)); l.setForeground(TEXT2); return l;
    }
    private JLabel detailBold(String t) {
        JLabel l = new JLabel(t); l.setFont(l.getFont().deriveFont(Font.BOLD, 11f)); return l;
    }

    // ── Delivery items card ───────────────────────────────────────────────────

    private JPanel buildDeliverySection() {
        CardPanel c = sectionCard("DELIVERY ITEMS");

        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        searchRow.setOpaque(false);
        // showPricing = false → shows qty + batch count instead of prices
        SearchDropdown search = new SearchDropdown(itemService::search, this::addItemToDelivery, false);
        search.setPreferredSize(new Dimension(320, 32));
        JLabel hint = new JLabel("  Search and select an item to add");
        hint.setFont(hint.getFont().deriveFont(12f));
        hint.setForeground(TEXT2);
        searchRow.add(search);
        searchRow.add(hint);

        String[] cols = {"Item Name", "SKU", "Qty", "UoM", "Cost (Rs.)", "Sell (Rs.)",
                         "Variant SKU", "Batch #", "Expiry (YYYY-MM-DD)", "Current", "New Stock"};
        deliveryModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return c == 6 || c == 7 || c == 8; }
        };

        deliveryTable = new JTable(deliveryModel);
        deliveryTable.setRowHeight(40);
        deliveryTable.setShowGrid(false);
        deliveryTable.setIntercellSpacing(new Dimension(0, 0));
        deliveryTable.getTableHeader().setFont(deliveryTable.getFont().deriveFont(Font.BOLD, 12f));
        deliveryTable.getTableHeader().setBackground(new Color(0xF7, 0xF8, 0xFA));

        deliveryTable.getColumnModel().getColumn(2).setCellRenderer(new QtyButtonRenderer());
        deliveryTable.getColumnModel().getColumn(2).setPreferredWidth(110);
        deliveryTable.getColumnModel().getColumn(2).setMinWidth(100);
        deliveryTable.getColumnModel().getColumn(3).setPreferredWidth(60);
        deliveryTable.getColumnModel().getColumn(3).setMaxWidth(80);
        deliveryTable.getColumnModel().getColumn(4).setCellRenderer(new CostButtonRenderer());
        deliveryTable.getColumnModel().getColumn(4).setPreferredWidth(130);
        deliveryTable.getColumnModel().getColumn(4).setMinWidth(110);
        deliveryTable.getColumnModel().getColumn(5).setCellRenderer(new CostButtonRenderer());
        deliveryTable.getColumnModel().getColumn(5).setPreferredWidth(130);
        deliveryTable.getColumnModel().getColumn(5).setMinWidth(110);
        deliveryTable.getColumnModel().getColumn(6).setPreferredWidth(100);
        deliveryTable.getColumnModel().getColumn(7).setPreferredWidth(100);
        deliveryTable.getColumnModel().getColumn(8).setPreferredWidth(120);
        deliveryTable.getColumnModel().getColumn(9).setPreferredWidth(65);
        deliveryTable.getColumnModel().getColumn(10).setPreferredWidth(75);

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
                } else if (col == 4) {
                    handleCostClick(row, col, e, "Enter cost price (Rs.):");
                } else if (col == 5) {
                    handleCostClick(row, col, e, "Enter selling price (Rs.):");
                }
            }
        });

        JButton removeBtn = new JButton("Remove Selected");
        removeBtn.addActionListener(e -> {
            int row = deliveryTable.getSelectedRow();
            if (row >= 0) {
                deliveryItems.remove(row);
                deliveryExistingStockIds.remove(row);
                deliveryModel.removeRow(row);
                updateSummary();
            }
        });
        JPanel tableActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
        tableActions.setOpaque(false);
        tableActions.add(removeBtn);

        JScrollPane scroll = new JScrollPane(deliveryTable);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(0xE2, 0xE5, 0xEA)));

        JPanel inner = new JPanel(new BorderLayout(0, 8));
        inner.setOpaque(false);
        inner.add(searchRow,    BorderLayout.NORTH);
        inner.add(scroll,       BorderLayout.CENTER);
        inner.add(tableActions, BorderLayout.SOUTH);
        ((JPanel)c).add(inner, BorderLayout.CENTER);
        return (JPanel) c;
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
                    refreshSaveState();
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    /**
     * Called when user selects an item from the search dropdown.
     * Shows a batch picker dialog if batches exist; otherwise adds a new batch row directly.
     */
    private void addItemToDelivery(ItemDto item) {
        new SwingWorker<List<StockBatchDto>, Void>() {
            @Override protected List<StockBatchDto> doInBackground() {
                return stockService.getBatchesForItem(item.itemId());
            }
            @Override protected void done() {
                try {
                    List<StockBatchDto> batches = get();
                    SwingUtilities.invokeLater(() -> showBatchPickerDialog(item, batches));
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> doAddItemNewBatch(item));
                }
            }
        }.execute();
    }

    /** Shows a dialog to select an existing batch or create a new one. */
    private void showBatchPickerDialog(ItemDto item, List<StockBatchDto> batches) {
        JDialog dlg = new JDialog(
                (Frame) SwingUtilities.getWindowAncestor(this),
                "Select Batch — " + item.itemName(), true);
        dlg.setSize(600, 360);
        dlg.setLocationRelativeTo(this);
        dlg.setLayout(new BorderLayout(0, 0));

        JLabel hint = new JLabel("  Choose an existing batch or create a new batch for this item:");
        hint.setFont(hint.getFont().deriveFont(12f));
        hint.setForeground(TEXT2);
        hint.setBorder(new EmptyBorder(10, 10, 6, 10));
        dlg.add(hint, BorderLayout.NORTH);

        // Table of existing batches
        String[] cols = {"Batch #", "Current Qty", "Cost (Rs.)", "Sell (Rs.)", "Status"};
        DefaultTableModel bm = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        for (StockBatchDto b : batches) {
            bm.addRow(new Object[]{
                b.batch().isEmpty() ? "(no label)" : b.batch(),
                String.format("%.0f", b.qty()),
                String.format("%.2f", b.costPrice()),
                String.format("%.2f", b.sellingPrice()),
                b.stockStatus()
            });
        }
        JTable batchTable = new JTable(bm);
        batchTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        batchTable.setRowHeight(28);
        batchTable.setShowGrid(false);
        batchTable.setIntercellSpacing(new Dimension(0, 0));
        batchTable.getTableHeader().setFont(batchTable.getFont().deriveFont(Font.BOLD, 11f));
        JScrollPane scroll = new JScrollPane(batchTable);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(0xD1, 0xD5, 0xDB)));
        scroll.setBorder(new EmptyBorder(0, 10, 0, 10));
        dlg.add(scroll, BorderLayout.CENTER);

        // Buttons
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        JButton cancelBtn   = new JButton("Cancel");
        JButton newBatchBtn = new JButton("+ New Batch");
        JButton selectBtn   = new JButton("Select Existing");
        selectBtn.setBackground(NAVY);
        selectBtn.setForeground(Color.WHITE);
        selectBtn.setOpaque(true);
        selectBtn.setBorderPainted(false);
        selectBtn.setEnabled(!batches.isEmpty());

        cancelBtn.addActionListener(e -> dlg.dispose());

        newBatchBtn.addActionListener(e -> {
            dlg.dispose();
            doAddItemNewBatch(item);
        });

        selectBtn.addActionListener(e -> {
            int row = batchTable.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(dlg, "Select a batch from the table first.",
                        "No Selection", JOptionPane.WARNING_MESSAGE);
                return;
            }
            StockBatchDto selected = batches.get(row);
            dlg.dispose();
            doAddItemExistingBatch(item, selected);
        });

        btnRow.add(cancelBtn);
        btnRow.add(newBatchBtn);
        btnRow.add(selectBtn);
        dlg.add(btnRow, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    /** Adds a row for a brand-new batch. */
    private void doAddItemNewBatch(ItemDto item) {
        // Allow same item with different batches — only deduplicate by existingStockId
        deliveryItems.add(item);
        deliveryExistingStockIds.add(null); // null = create new batch
        int rowIndex = deliveryItems.size() - 1;
        deliveryModel.addRow(new Object[]{
                item.itemName(), item.sku(), 1,
                item.unit() != null ? item.unit() : "",
                item.costPrice(), item.sellingPrice(),
                "",    // Variant SKU
                "…",   // Batch # — placeholder while generating
                "",    // Expiry
                item.currentQty(), item.currentQty() + 1
        });
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() { return stockService.generateBatchNo(); }
            @Override protected void done() {
                try {
                    String num = get();
                    if (rowIndex < deliveryModel.getRowCount()
                            && "…".equals(deliveryModel.getValueAt(rowIndex, 7))) {
                        deliveryModel.setValueAt(num, rowIndex, 7);
                    }
                } catch (Exception ignored) {}
            }
        }.execute();
        updateSummary();
    }

    /** Adds a row that targets an existing stock record (will increase its qty). */
    private void doAddItemExistingBatch(ItemDto item, StockBatchDto batch) {
        // Deduplicate: if this exact stock record is already in the table, select that row
        for (int i = 0; i < deliveryExistingStockIds.size(); i++) {
            Integer eid = deliveryExistingStockIds.get(i);
            if (eid != null && eid == batch.stockId()) {
                deliveryTable.setRowSelectionInterval(i, i);
                return;
            }
        }
        deliveryItems.add(item);
        deliveryExistingStockIds.add(batch.stockId());
        deliveryModel.addRow(new Object[]{
                item.itemName(), batch.displaySku(), 1,
                item.unit() != null ? item.unit() : "",
                batch.costPrice(), batch.sellingPrice(),
                "",                 // Variant SKU — not used for existing batches
                batch.batch(),      // Batch # — pre-filled, editable
                "",                 // Expiry
                batch.qty(), batch.qty() + 1
        });
        updateSummary();
    }

    // ── Action row ────────────────────────────────────────────────────────────

    private JPanel buildActionRow() {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);

        costWarnLabel = new JLabel();
        costWarnLabel.setFont(costWarnLabel.getFont().deriveFont(Font.BOLD, 12f));
        costWarnLabel.setForeground(new Color(0xCC, 0x44, 0x00));
        row.add(costWarnLabel, BorderLayout.WEST);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btns.setOpaque(false);
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> Home.navigate(Home.CARD_STOCK));
        saveGrnBtn = new JButton("Save GRN");
        saveGrnBtn.setBackground(NAVY);
        saveGrnBtn.setForeground(Color.WHITE);
        saveGrnBtn.setOpaque(true);
        saveGrnBtn.setBorderPainted(false);
        saveGrnBtn.addActionListener(e -> saveGrn());
        btns.add(cancel);
        btns.add(saveGrnBtn);
        row.add(btns, BorderLayout.EAST);
        return row;
    }

    private void refreshSaveState() {
        boolean hasCostIssue = false;
        for (int i = 0; i < deliveryModel.getRowCount(); i++) {
            double cost = ((Number) deliveryModel.getValueAt(i, 4)).doubleValue();
            double sell = ((Number) deliveryModel.getValueAt(i, 5)).doubleValue();
            if (cost > sell) { hasCostIssue = true; break; }
        }
        if (hasCostIssue) {
            costWarnLabel.setText("⚠ Cost price exceeds selling price — fix before saving.");
            saveGrnBtn.setEnabled(false);
        } else {
            costWarnLabel.setText("");
            saveGrnBtn.setEnabled(true);
        }
    }

    // ── GRN Summary sidebar ───────────────────────────────────────────────────

    private JPanel buildSidebar() {
        CardPanel c = new CardPanel(new BorderLayout(0, 10));
        ((JPanel)c).setBorder(new EmptyBorder(14, 14, 14, 14));

        JLabel title = new JLabel("GRN SUMMARY");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 11f));
        title.setForeground(TEXT2);
        ((JPanel)c).add(title, BorderLayout.NORTH);

        JPanel stats = new JPanel(new GridLayout(6, 1, 0, 8));
        stats.setOpaque(false);

        summaryGrn   = new JLabel("—");
        summaryLines = new JLabel("0 lines");
        summaryQty   = new JLabel("0 units");
        summaryCost  = new JLabel("Rs. 0.00");
        summaryReturnsDed = new JLabel("Rs. 0.00");
        summaryGrn.setFont(summaryGrn.getFont().deriveFont(Font.BOLD, 13f));
        summaryCost.setFont(summaryCost.getFont().deriveFont(Font.BOLD, 13f));
        summaryReturnsDed.setForeground(new Color(0x0A, 0x7A, 0x3E));

        discountField = new JTextField("0");
        discountField.setPreferredSize(new Dimension(0, 26));
        discountField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateSummary(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateSummary(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) {}
        });

        estCostField = new JTextField("Rs. 0.00");
        estCostField.setEditable(false);
        estCostField.setBackground(new Color(0xF0, 0xF2, 0xF5));
        estCostField.setFont(estCostField.getFont().deriveFont(Font.BOLD, 14f));
        estCostField.setForeground(NAVY);
        estCostField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xC8, 0xCC, 0xD4)),
                new EmptyBorder(2, 6, 2, 6)));
        estCostField.setPreferredSize(new Dimension(0, 30));

        stats.add(statRow("GRN No.",           summaryGrn));
        stats.add(statRow("Lines",             summaryLines));
        stats.add(statRow("Total Qty",         summaryQty));
        stats.add(statRow("Gross Cost",        summaryCost));
        stats.add(statRow("Returns Deduction", summaryReturnsDed));
        stats.add(statRowComp("Est. Cost",     estCostField));
        ((JPanel)c).add(stats, BorderLayout.CENTER);

        JLabel hint = new JLabel("<html><font color='#5A6070' size='2'>" +
                "<b>Variant SKU</b> — create a new price<br>variant for the same item.<br>" +
                "<b>Batch #</b> — auto-generated; override<br>if needed.<br>" +
                "<b>Expiry</b> — YYYY-MM-DD (optional)." +
                "</font></html>");
        hint.setBorder(new EmptyBorder(8, 0, 0, 0));
        ((JPanel)c).add(hint, BorderLayout.SOUTH);

        return (JPanel) c;
    }

    private JPanel statRowComp(String label, JComponent comp) {
        JPanel p = new JPanel(new GridLayout(2, 1, 0, 2));
        p.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setFont(l.getFont().deriveFont(11f));
        l.setForeground(TEXT2);
        p.add(l); p.add(comp);
        return p;
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
        int totalQty = 0; double grossCost = 0;
        for (int i = 0; i < deliveryModel.getRowCount(); i++) {
            Object qtyObj  = deliveryModel.getValueAt(i, 2);
            Object costObj = deliveryModel.getValueAt(i, 4);
            int    qty  = qtyObj  instanceof Number n ? n.intValue()   : 0;
            double cost = costObj instanceof Number n ? n.doubleValue() : 0;
            totalQty  += qty;
            grossCost += qty * cost;
            if (i < deliveryItems.size()) {
                double current = deliveryItems.get(i).currentQty();
                deliveryModel.setValueAt(current + qty, i, 10);
            }
        }
        summaryQty.setText(totalQty + " units");
        summaryCost.setText(String.format("Rs. %,.2f", grossCost));

        // Returns deduction — sum of original sale cost for selected returns
        double returnsDed = 0;
        for (ReturnDto r : returnToSellerList) {
            if (selectedReturnIds.contains(r.returnId())) {
                returnsDed += r.originalSaleCost();
            }
        }
        if (summaryReturnsDed != null) {
            if (returnsDed > 0) {
                summaryReturnsDed.setText("− Rs. " + String.format("%,.2f", returnsDed)
                        + "  (" + selectedReturnIds.size() + " return" + (selectedReturnIds.size() == 1 ? "" : "s") + ")");
            } else {
                summaryReturnsDed.setText(selectedReturnIds.isEmpty() ? "None" : "Rs. 0.00");
            }
        }

        // Discount from field
        double discount = 0;
        if (discountField != null) {
            try { discount = Double.parseDouble(discountField.getText().trim()); }
            catch (NumberFormatException ignored) {}
            discount = Math.max(0, discount);
        }

        // Est. Cost = gross - returns - discount
        double estCost = Math.max(0, grossCost - returnsDed - discount);
        if (estCostField != null) {
            estCostField.setText(String.format("Rs. %,.2f", estCost));
        }

        if (saveGrnBtn != null) refreshSaveState();
    }

    // ── Save GRN ──────────────────────────────────────────────────────────────

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
            ItemDto item      = deliveryItems.get(i);
            Integer existSid  = deliveryExistingStockIds.get(i); // null or existing stock_id
            int    qty        = ((Number) deliveryModel.getValueAt(i, 2)).intValue();
            double cost       = ((Number) deliveryModel.getValueAt(i, 4)).doubleValue();
            double sellPrice  = ((Number) deliveryModel.getValueAt(i, 5)).doubleValue();
            String variantSku = deliveryModel.getValueAt(i, 6) != null
                                ? deliveryModel.getValueAt(i, 6).toString().trim() : "";
            String batchName  = deliveryModel.getValueAt(i, 7) != null
                                ? deliveryModel.getValueAt(i, 7).toString().trim() : "";
            String expiryStr  = deliveryModel.getValueAt(i, 8) != null
                                ? deliveryModel.getValueAt(i, 8).toString().trim() : "";
            java.util.Date expiryDate = null;
            if (!expiryStr.isEmpty()) {
                try { expiryDate = java.sql.Date.valueOf(expiryStr); } catch (Exception ignored) {}
            }
            totalCost += qty * cost;
            lines.add(new GrnDto.GrnLineDto(
                    item.itemId(), item.itemName(), qty, cost, sellPrice,
                    (int) item.currentQty(),
                    variantSku.isEmpty() ? null : variantSku,
                    batchName.isEmpty()  ? null : batchName,
                    expiryDate, existSid));
        }

        String grnNo    = grnNoField.getText().trim();
        String supplier = supplierField.getText().trim();
        String ref      = referenceField.getText().trim();
        double fc       = totalCost;

        // Est. cost = gross - returns deduction - discount (mirrors updateSummary logic)
        double returnsDed = 0;
        for (ReturnDto r : returnToSellerList) {
            if (selectedReturnIds.contains(r.returnId())) returnsDed += r.originalSaleCost();
        }
        double discount = 0;
        try { discount = Double.parseDouble(discountField.getText().trim()); } catch (NumberFormatException ignored) {}
        discount = Math.max(0, discount);
        double netCost = Math.max(0, fc - returnsDed - discount);

        Long   empId    = SessionContext.current() != null ? SessionContext.current().getEmployee().id() : null;
        List<Integer> linkedReturnIds = new ArrayList<>(selectedReturnIds);
        double finalNetCost = netCost;

        new SwingWorker<Integer, Void>() {
            @Override protected Integer doInBackground() {
                GrnDto dto = new GrnDto(grnNo, new java.util.Date(), supplier, ref, fc, lines);
                int grnInfoNo = stockService.saveGrn(dto, empId);
                if (cashAccountService != null && finalNetCost > 0) {
                    cashAccountService.recordGrnExpense(finalNetCost, grnNo, empId);
                }
                // Link selected returns to this GRN
                if (returnService != null && !linkedReturnIds.isEmpty()) {
                    returnService.linkGrnToReturns(linkedReturnIds, grnInfoNo, empId);
                }
                return grnInfoNo;
            }
            @Override protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(ReceiveStockPanel.this,
                            "GRN " + grnNo + " saved.", "GRN Saved", JOptionPane.INFORMATION_MESSAGE);
                    reset();
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
        refreshSaveState();
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
