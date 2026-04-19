package com.olympus.system.hawkdeskpos.frontend.sale;

import com.olympus.system.hawkdeskpos.dto.CustomerDto;
import com.olympus.system.hawkdeskpos.dto.InvoiceDto;
import com.olympus.system.hawkdeskpos.dto.ReturnDto;
import com.olympus.system.hawkdeskpos.dto.SaleLineDto;
import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.frontend.components.ConfirmDialog;
import com.olympus.system.hawkdeskpos.service.CashAccountService;
import com.olympus.system.hawkdeskpos.service.CustomerService;
import com.olympus.system.hawkdeskpos.service.ReturnService;
import com.olympus.system.hawkdeskpos.service.SaleService;
import com.olympus.system.hawkdeskpos.service.SettingsService;
import com.olympus.system.hawkdeskpos.session.SessionContext;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * Goods Return screen.
 *
 * Layout:
 *   NORTH  — page header (title + Clear + Back)
 *   CENTER — scrollable: [Invoice & Items card] + [Previous Returns card, only when applicable]
 *   SOUTH  — docked bar: Refund Method | Total | Cancel | Process Return
 */
public class GoodsReturnPanel extends JPanel {

    private static final Color BG        = new Color(0xF0, 0xF2, 0xF5);
    private static final Color TEXT2     = new Color(0x5A, 0x60, 0x70);
    private static final Color NAVY      = new Color(0x1E, 0x3A, 0x5F);
    private static final Color RED       = new Color(0xC6, 0x28, 0x28);
    private static final Color AMBER     = new Color(0xB4, 0x5B, 0x00);
    private static final Color AMBER_BG  = new Color(0xFF, 0xF3, 0xCD);

    private static final String[] REASONS = {"Damaged", "Wrong item", "Customer change of mind", "Other"};
    private static final String[] ITEM_COLS = {"", "Item", "Unit", "Orig. Qty", "Remaining", "Return Qty", "Reason"};

    private final SaleService        saleService;
    private final ReturnService      returnService;
    private final SettingsService    settingsService;
    private final CashAccountService cashAccountService;
    private final CustomerService    customerService;

    // State
    private InvoiceDto      currentInvoice;
    private List<ReturnDto> previousReturns = new ArrayList<>();
    /** Active lines (remaining > 0), parallel to table rows. */
    private final List<SaleLineDto> activeLines     = new ArrayList<>();
    private final List<Double>      remainingQtys   = new ArrayList<>();

    // Search widgets
    private JTextField invoiceField;
    private JLabel     invoiceInfoLabel;

    // Items table
    private DefaultTableModel itemsModel;
    private JTable            itemsTable;
    private JScrollPane       itemsScroll;
    private JLabel            itemsPlaceholder;
    private JPanel            itemsBodyPanel; // BorderLayout; CENTER swaps between placeholder/scroll

    // Previous returns card (hidden until needed)
    private JPanel prevReturnsCard;
    private JPanel contentColumn;

    // Bottom bar widgets
    private JComboBox<String> refundMethodCombo;
    private JLabel            totalRefundLabel;
    private JButton           processBtn;

    // ── Constructor ───────────────────────────────────────────────────────────

    public GoodsReturnPanel(SaleService saleService, ReturnService returnService,
                            SettingsService settingsService) {
        this(saleService, returnService, settingsService, null, null);
    }

    public GoodsReturnPanel(SaleService saleService, ReturnService returnService,
                            SettingsService settingsService, CashAccountService cashAccountService) {
        this(saleService, returnService, settingsService, cashAccountService, null);
    }

    public GoodsReturnPanel(SaleService saleService, ReturnService returnService,
                            SettingsService settingsService, CashAccountService cashAccountService,
                            CustomerService customerService) {
        this.saleService        = saleService;
        this.returnService      = returnService;
        this.settingsService    = settingsService;
        this.cashAccountService = cashAccountService;
        this.customerService    = customerService;
        setBackground(BG);
        setLayout(new BorderLayout(0, 0));
        buildUI();
    }

    /** Pre-loads an invoice when navigating from Sales History. */
    public void loadInvoice(String invoiceNo) {
        invoiceField.setText(invoiceNo);
        lookupInvoice();
    }

    // ── Build UI ──────────────────────────────────────────────────────────────

    private void buildUI() {
        // ── Page header ───────────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(16, 16, 8, 16));

        JLabel title = new JLabel("Process Return");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        header.add(title, BorderLayout.WEST);

        JPanel hBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        hBtns.setOpaque(false);
        JButton clearBtn = new JButton("↺ Clear");
        clearBtn.addActionListener(e -> clearAll());
        JButton back = new JButton("← Back");
        back.addActionListener(e -> Home.navigate(Home.CARD_HIST));
        hBtns.add(clearBtn);
        hBtns.add(back);
        header.add(hBtns, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // ── Scrollable center ─────────────────────────────────────────────────
        contentColumn = new JPanel();
        contentColumn.setOpaque(false);
        contentColumn.setLayout(new BoxLayout(contentColumn, BoxLayout.Y_AXIS));
        contentColumn.setBorder(new EmptyBorder(0, 16, 8, 16));

        contentColumn.add(buildInvoiceAndItemsCard());
        contentColumn.add(Box.createVerticalStrut(10));

        prevReturnsCard = buildPrevReturnsCard(List.of()); // hidden initially
        prevReturnsCard.setVisible(false);
        contentColumn.add(prevReturnsCard);

        JScrollPane centerScroll = new JScrollPane(contentColumn);
        centerScroll.setBorder(null);
        centerScroll.setOpaque(false);
        centerScroll.getViewport().setOpaque(false);
        add(centerScroll, BorderLayout.CENTER);

        // ── Docked bottom bar ─────────────────────────────────────────────────
        add(buildBottomBar(), BorderLayout.SOUTH);
    }

    // ── Card: Invoice + Items ─────────────────────────────────────────────────

    private JPanel buildInvoiceAndItemsCard() {
        CardPanel card = new CardPanel(new BorderLayout(0, 10));
        ((JPanel)card).setBorder(new EmptyBorder(14, 14, 14, 14));
        ((JPanel)card).setAlignmentX(Component.LEFT_ALIGNMENT);
        ((JPanel)card).setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel sectionTitle = new JLabel("INVOICE & ITEMS");
        sectionTitle.setFont(sectionTitle.getFont().deriveFont(Font.BOLD, 11f));
        sectionTitle.setForeground(TEXT2);
        ((JPanel)card).add(sectionTitle, BorderLayout.NORTH);

        // Search row
        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        searchRow.setOpaque(false);
        invoiceField = new JTextField(26);
        invoiceField.putClientProperty("JTextField.placeholderText", "Invoice # (e.g. INV-250101-0001)");
        invoiceField.addActionListener(e -> lookupInvoice());
        JButton loadBtn = new JButton("Load Invoice");
        loadBtn.setBackground(NAVY);
        loadBtn.setForeground(Color.WHITE);
        loadBtn.setOpaque(true);
        loadBtn.setBorderPainted(false);
        loadBtn.addActionListener(e -> lookupInvoice());
        invoiceInfoLabel = new JLabel();
        invoiceInfoLabel.setFont(invoiceInfoLabel.getFont().deriveFont(12f));
        invoiceInfoLabel.setForeground(TEXT2);
        searchRow.add(invoiceField);
        searchRow.add(loadBtn);
        searchRow.add(invoiceInfoLabel);

        // Items table
        itemsModel = buildItemsModel();
        itemsTable = new JTable(itemsModel);
        styleItemsTable();

        itemsScroll = new JScrollPane(itemsTable);
        itemsScroll.setBorder(BorderFactory.createLineBorder(new Color(0xE2, 0xE5, 0xEA)));

        itemsPlaceholder = new JLabel("  Load an invoice to see items.");
        itemsPlaceholder.setForeground(TEXT2);
        itemsPlaceholder.setFont(itemsPlaceholder.getFont().deriveFont(13f));
        itemsPlaceholder.setBorder(new EmptyBorder(20, 4, 20, 4));

        itemsBodyPanel = new JPanel(new BorderLayout());
        itemsBodyPanel.setOpaque(false);
        itemsBodyPanel.add(itemsPlaceholder, BorderLayout.CENTER);

        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setOpaque(false);
        body.add(searchRow,    BorderLayout.NORTH);
        body.add(itemsBodyPanel, BorderLayout.CENTER);
        ((JPanel)card).add(body, BorderLayout.CENTER);

        return (JPanel) card;
    }

    private DefaultTableModel buildItemsModel() {
        DefaultTableModel m = new DefaultTableModel(ITEM_COLS, 0) {
            @Override public Class<?> getColumnClass(int c) {
                return c == 0 ? Boolean.class : (c == 3 || c == 4 || c == 5) ? Double.class : String.class;
            }
            @Override public boolean isCellEditable(int r, int c) { return c == 0 || c == 5 || c == 6; }
            @Override public void setValueAt(Object val, int r, int c) {
                if (c == 5) {
                    double max = (r < remainingQtys.size()) ? remainingQtys.get(r) : 1.0;
                    double v = 1.0;
                    try { v = Double.parseDouble(val.toString().trim()); } catch (NumberFormatException ignored) {}
                    v = Math.max(0.001, Math.min(v, max));
                    super.setValueAt(v, r, c);
                    return;
                }
                super.setValueAt(val, r, c);
            }
        };
        m.addTableModelListener(e -> {
            if (e.getType() == TableModelEvent.UPDATE
                    && (e.getColumn() == 0 || e.getColumn() == 5)) {
                recalcTotal();
            }
        });
        return m;
    }

    private void styleItemsTable() {
        itemsTable.setRowHeight(32);
        itemsTable.setShowGrid(false);
        itemsTable.setIntercellSpacing(new Dimension(0, 0));
        itemsTable.getTableHeader().setFont(itemsTable.getFont().deriveFont(Font.BOLD, 11f));
        itemsTable.getTableHeader().setBackground(new Color(0xF0, 0xF2, 0xF5));
        itemsTable.getTableHeader().setForeground(TEXT2);

        // Column widths
        int[] widths = {30, 0, 60, 70, 80, 80, 160};
        for (int i = 0; i < widths.length; i++) {
            if (widths[i] > 0) itemsTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            if (widths[i] > 0 && i < 5) itemsTable.getColumnModel().getColumn(i).setMaxWidth(widths[i] + 20);
        }
        itemsTable.getColumnModel().getColumn(0).setMaxWidth(36);

        // Right-align numeric columns
        DefaultTableCellRenderer centerR = new DefaultTableCellRenderer();
        centerR.setHorizontalAlignment(SwingConstants.CENTER);
        for (int c : new int[]{2, 3, 4, 5}) itemsTable.getColumnModel().getColumn(c).setCellRenderer(centerR);

        // Reason column — JComboBox editor
        JComboBox<String> reasonBox = new JComboBox<>(REASONS);
        itemsTable.getColumnModel().getColumn(6).setCellEditor(new DefaultCellEditor(reasonBox));

        // Return Qty — text editor (validated in setValueAt)
        JTextField qtyEditor = new JTextField();
        qtyEditor.setHorizontalAlignment(SwingConstants.CENTER);
        itemsTable.getColumnModel().getColumn(5).setCellEditor(new DefaultCellEditor(qtyEditor));
    }

    // ── Card: Previous Returns ────────────────────────────────────────────────

    private JPanel buildPrevReturnsCard(List<ReturnDto> rets) {
        CardPanel card = new CardPanel(new BorderLayout(0, 8));
        ((JPanel)card).setBorder(new EmptyBorder(12, 14, 12, 14));
        ((JPanel)card).setAlignmentX(Component.LEFT_ALIGNMENT);
        ((JPanel)card).setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel sectionTitle = new JLabel("PREVIOUS RETURNS");
        sectionTitle.setFont(sectionTitle.getFont().deriveFont(Font.BOLD, 11f));
        sectionTitle.setForeground(AMBER);
        ((JPanel)card).add(sectionTitle, BorderLayout.NORTH);

        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        for (ReturnDto r : rets) {
            list.add(buildPrevReturnRow(r, sdf));
            list.add(Box.createVerticalStrut(4));
        }
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        int scrollH = Math.min(rets.size() * 46 + 8, 160);
        scroll.setPreferredSize(new Dimension(0, scrollH));
        ((JPanel)card).add(scroll, BorderLayout.CENTER);

        return (JPanel) card;
    }

    private JPanel buildPrevReturnRow(ReturnDto r, SimpleDateFormat sdf) {
        JPanel row = new JPanel(new GridLayout(1, 4, 8, 0));
        row.setOpaque(true);
        row.setBackground(AMBER_BG);
        row.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 3, 0, 0, AMBER),
                new EmptyBorder(6, 8, 6, 4)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel item   = new JLabel("<html><b>" + r.itemName() + "</b></html>");
        JLabel qty    = small("Qty: " + r.qty(), TEXT2);
        JLabel reason = small(r.reason(), AMBER);
        JLabel date   = small(r.returnDate() != null ? sdf.format(r.returnDate()) : "—", TEXT2);
        item.setFont(item.getFont().deriveFont(12f));

        row.add(item); row.add(qty); row.add(reason); row.add(date);
        return row;
    }

    // ── Bottom bar ────────────────────────────────────────────────────────────

    private JPanel buildBottomBar() {
        JPanel bar = new JPanel(new BorderLayout(0, 0));
        bar.setOpaque(true);
        bar.setBackground(Color.WHITE);
        bar.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 0, 0, 0, new Color(0xD1, 0xD5, 0xDB)),
                new EmptyBorder(10, 16, 10, 16)));

        // Left: Refund Method + Total
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        left.setOpaque(false);

        refundMethodCombo = new JComboBox<>(new String[]{"Cash", "Exchange"});
        refundMethodCombo.setPreferredSize(new Dimension(140, 30));

        totalRefundLabel = new JLabel("Rs. 0.00");
        totalRefundLabel.setFont(totalRefundLabel.getFont().deriveFont(Font.BOLD, 18f));
        totalRefundLabel.setForeground(NAVY);

        left.add(labeled("Refund Method", refundMethodCombo));
        left.add(labeled("Total Refund",  totalRefundLabel));
        bar.add(left, BorderLayout.CENTER);

        // Right: Cancel + Process Return
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> Home.navigate(Home.CARD_HIST));
        processBtn = new JButton("Process Return");
        processBtn.setBackground(RED);
        processBtn.setForeground(Color.WHITE);
        processBtn.setOpaque(true);
        processBtn.setBorderPainted(false);
        processBtn.addActionListener(e -> processReturn());
        right.add(cancel);
        right.add(processBtn);
        bar.add(right, BorderLayout.EAST);

        return bar;
    }

    // ── Invoice lookup ────────────────────────────────────────────────────────

    private void lookupInvoice() {
        String invNo = invoiceField.getText().trim();
        if (invNo.isEmpty()) return;

        if (!settingsService.returnsEnabled()) {
            JOptionPane.showMessageDialog(this,
                    "Returns are currently disabled in Settings.",
                    "Returns Disabled", JOptionPane.WARNING_MESSAGE);
            return;
        }

        invoiceInfoLabel.setText("Loading…");
        processBtn.setEnabled(false);

        new SwingWorker<Object[], Void>() {
            @Override protected Object[] doInBackground() {
                InvoiceDto inv  = saleService.findByNumber(invNo);
                List<ReturnDto> rets = (inv != null)
                        ? returnService.getReturnsForInvoice(invNo) : List.of();
                return new Object[]{inv, rets};
            }
            @Override protected void done() {
                try {
                    Object[] res = get();
                    InvoiceDto invoice = (InvoiceDto) res[0];
                    @SuppressWarnings("unchecked")
                    List<ReturnDto> rets = (List<ReturnDto>) res[1];

                    if (invoice == null) {
                        showItemsError("Invoice not found.");
                        return;
                    }
                    if (invoice.date() != null) {
                        long diffDays = (new Date().getTime() - invoice.date().getTime())
                                / (1000L * 60 * 60 * 24);
                        int maxDays = settingsService.returnPeriodDays();
                        if (diffDays > maxDays) {
                            JOptionPane.showMessageDialog(GoodsReturnPanel.this,
                                    "Return period expired. Returns are allowed within "
                                    + maxDays + " day(s). This invoice is " + diffDays + " day(s) old.",
                                    "Return Period Expired", JOptionPane.WARNING_MESSAGE);
                            invoiceField.setText("");
                            invoiceInfoLabel.setText("");
                            return;
                        }
                    }
                    if ("Full Return".equals(invoice.stat()) || "Void".equals(invoice.stat())) {
                        JOptionPane.showMessageDialog(GoodsReturnPanel.this,
                                "This invoice has already been fully returned or voided.",
                                "Cannot Return", JOptionPane.WARNING_MESSAGE);
                        invoiceField.setText("");
                        invoiceInfoLabel.setText("");
                        return;
                    }

                    currentInvoice  = invoice;
                    previousReturns = rets;
                    populateItemsTable(invoice, rets);
                    refreshPrevReturnsCard(rets);
                    invoiceInfoLabel.setText(
                            invoice.cashierName() + "  ·  " + invoice.lines().size() + " item(s)  ·  "
                            + invoice.stat());
                    processBtn.setEnabled(true);

                } catch (Exception ignored) {
                    showItemsError("Error loading invoice.");
                }
            }
        }.execute();
    }

    // ── Populate items table ──────────────────────────────────────────────────

    private void populateItemsTable(InvoiceDto inv, List<ReturnDto> rets) {
        // Compute already-returned qty per stockId
        Map<Integer, Double> alreadyRet = new HashMap<>();
        for (ReturnDto r : rets) alreadyRet.merge(r.stockId(), r.qty(), Double::sum);

        activeLines.clear();
        remainingQtys.clear();
        itemsModel.setRowCount(0);

        for (SaleLineDto line : inv.lines()) {
            double returned  = alreadyRet.getOrDefault(line.stockId(), 0.0);
            double remaining = line.qty() - returned;
            if (remaining <= 0) continue;

            activeLines.add(line);
            remainingQtys.add(remaining);
            itemsModel.addRow(new Object[]{
                    Boolean.FALSE,
                    line.itemName(),
                    (line.unit() != null && !line.unit().isEmpty()) ? line.unit() : "—",
                    line.qty(),
                    remaining,
                    Math.min(1.0, remaining),
                    REASONS[0]
            });
        }

        // Switch placeholder → table
        itemsBodyPanel.removeAll();
        if (itemsModel.getRowCount() == 0) {
            JLabel allReturned = new JLabel("  All items in this invoice have already been returned.");
            allReturned.setForeground(AMBER);
            allReturned.setBorder(new EmptyBorder(16, 4, 16, 4));
            itemsBodyPanel.add(allReturned, BorderLayout.CENTER);
        } else {
            int tableH = Math.min(activeLines.size() * 32 + 26, 280);
            itemsScroll.setPreferredSize(new Dimension(0, tableH));
            itemsBodyPanel.add(itemsScroll, BorderLayout.CENTER);
        }
        itemsBodyPanel.revalidate();
        itemsBodyPanel.repaint();
        recalcTotal();
    }

    private void showItemsError(String msg) {
        invoiceInfoLabel.setText("");
        itemsBodyPanel.removeAll();
        JLabel err = new JLabel("  " + msg);
        err.setForeground(RED);
        err.setBorder(new EmptyBorder(16, 4, 16, 4));
        itemsBodyPanel.add(err, BorderLayout.CENTER);
        itemsBodyPanel.revalidate();
        itemsBodyPanel.repaint();
    }

    // ── Previous returns card management ─────────────────────────────────────

    private void refreshPrevReturnsCard(List<ReturnDto> rets) {
        // Remove the current prevReturnsCard from contentColumn
        contentColumn.remove(prevReturnsCard);

        if (rets.isEmpty()) {
            // Rebuild an empty placeholder (still hidden)
            prevReturnsCard = buildPrevReturnsCard(List.of());
            prevReturnsCard.setVisible(false);
        } else {
            prevReturnsCard = buildPrevReturnsCard(rets);
            prevReturnsCard.setVisible(true);
        }
        contentColumn.add(prevReturnsCard);
        contentColumn.revalidate();
        contentColumn.repaint();
    }

    // ── Recalculate refund total ──────────────────────────────────────────────

    private void recalcTotal() {
        double subTot = currentInvoice != null ? currentInvoice.subTotal() : 0;
        double netTot = currentInvoice != null ? currentInvoice.netTotal() : 0;
        // If subTotal is 0 (legacy row without new cols), fall back to 1:1 ratio
        double discountRatio = (subTot > 0) ? (netTot / subTot) : 1.0;

        double total = 0;
        for (int i = 0; i < itemsModel.getRowCount(); i++) {
            Object checked = itemsModel.getValueAt(i, 0);
            if (Boolean.TRUE.equals(checked) && i < activeLines.size()) {
                Object qtyObj = itemsModel.getValueAt(i, 5);
                double qty = (qtyObj instanceof Number n) ? n.doubleValue() : 1.0;
                total += activeLines.get(i).unitPrice() * qty * discountRatio;
            }
        }
        totalRefundLabel.setText(String.format("Rs. %,.2f", total));
    }

    // ── Process return ────────────────────────────────────────────────────────

    private void processReturn() {
        if (currentInvoice == null) {
            JOptionPane.showMessageDialog(this, "Load an invoice first.", "Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (itemsTable.isEditing()) itemsTable.getCellEditor().stopCellEditing();

        Map<Integer, Double> returnLines = new LinkedHashMap<>();
        String lastReason = REASONS[0];

        for (int i = 0; i < itemsModel.getRowCount(); i++) {
            if (Boolean.TRUE.equals(itemsModel.getValueAt(i, 0)) && i < activeLines.size()) {
                int    stockId = activeLines.get(i).stockId();
                double qty     = ((Number) itemsModel.getValueAt(i, 5)).doubleValue();
                returnLines.put(stockId, qty);
                lastReason     = (String) itemsModel.getValueAt(i, 6);
            }
        }
        if (returnLines.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select at least one item.", "Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String confirmMsg = "Process return of " + returnLines.size() + " item(s)?\nTotal: " + totalRefundLabel.getText();
        if (!ConfirmDialog.show(this, "Confirm Return", confirmMsg, "Process Return")) return;

        Long empId = SessionContext.current() != null ? SessionContext.current().getEmployee().id() : null;
        final String method = (String) refundMethodCombo.getSelectedItem();
        final String reason = lastReason;
        final Map<Integer, Double> lines = returnLines;

        // For Exchange returns: optionally assign to a customer
        Integer exchangeCustomerId = null;
        if ("Exchange".equals(method) && customerService != null) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Assign this exchange return to a customer?\n" +
                    "(This allows the customer's exchange credit to appear in the New Sale screen.)",
                    "Assign to Customer?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                exchangeCustomerId = pickExchangeCustomer();
                if (exchangeCustomerId == null) return; // cancelled
            }
        }
        final Integer finalCustomerId = exchangeCustomerId;

        // Calculate total refund amount for history logging (proportional discount applied)
        double subTot = currentInvoice.subTotal();
        double netTot = currentInvoice.netTotal();
        double discountRatio = (subTot > 0) ? (netTot / subTot) : 1.0;
        double totalRefundAmount = 0;
        for (int i = 0; i < itemsModel.getRowCount(); i++) {
            if (Boolean.TRUE.equals(itemsModel.getValueAt(i, 0)) && i < activeLines.size()) {
                double qty = ((Number) itemsModel.getValueAt(i, 5)).doubleValue();
                totalRefundAmount += activeLines.get(i).unitPrice() * qty * discountRatio;
            }
        }
        // Determine if full or partial return
        boolean allReturned = returnLines.size() == activeLines.size();
        final com.olympus.system.hawkdeskpos.db.dao.InvoiceHistory.EventType histEventType =
                allReturned ? com.olympus.system.hawkdeskpos.db.dao.InvoiceHistory.EventType.RETURNED
                            : com.olympus.system.hawkdeskpos.db.dao.InvoiceHistory.EventType.PARTIAL_RETURN;
        final double finalRefundAmt = totalRefundAmount;
        final String histNotes = method + " refund — " + reason;
        final String invNo = currentInvoice.invoiceNo();

        processBtn.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                returnService.processReturn(invNo, lines, reason, method, empId, finalCustomerId);
                saleService.logInvoiceEvent(invNo, histEventType, finalRefundAmt, histNotes, empId);
                // Record cash outflow for all Cash refunds
                if (cashAccountService != null && "Cash".equals(method) && finalRefundAmt > 0) {
                    cashAccountService.recordReturnRefund(finalRefundAmt, "Return Refund", invNo, empId);
                }
                return null;
            }
            @Override protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(GoodsReturnPanel.this,
                            "Return processed successfully.", "Return Complete", JOptionPane.INFORMATION_MESSAGE);
                    clearAll();
                } catch (Exception ex) {
                    String msg = (ex.getCause() != null) ? ex.getCause().getMessage() : ex.getMessage();
                    JOptionPane.showMessageDialog(GoodsReturnPanel.this,
                            "Failed: " + msg, "Error", JOptionPane.ERROR_MESSAGE);
                    processBtn.setEnabled(true);
                }
            }
        }.execute();
    }

    /**
     * Shows a customer selection dialog and returns the selected customer ID,
     * or null if the user cancels.
     */
    private Integer pickExchangeCustomer() {
        JDialog dlg = new JDialog((java.awt.Frame) SwingUtilities.getWindowAncestor(this),
                "Select Customer for Exchange", true);
        dlg.setSize(480, 360);
        dlg.setLocationRelativeTo(this);
        dlg.setLayout(new BorderLayout(0, 8));

        JTextField search = new JTextField();
        search.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xC8, 0xCD, 0xD6)),
                new EmptyBorder(6, 10, 6, 10)));

        String[] cols = {"Name", "Phone", "Address"};
        javax.swing.table.DefaultTableModel m = new javax.swing.table.DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable tbl = new JTable(m);
        tbl.setRowHeight(32);
        tbl.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);

        java.util.concurrent.atomic.AtomicReference<List<CustomerDto>> resultRef =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger selectedId = new java.util.concurrent.atomic.AtomicInteger(-1);

        Runnable doSearch = () -> new SwingWorker<List<CustomerDto>, Void>() {
            @Override protected List<CustomerDto> doInBackground() {
                String q = search.getText().trim();
                return q.isEmpty() ? customerService.listAll() : customerService.search(q);
            }
            @Override protected void done() {
                try {
                    List<CustomerDto> list = get();
                    resultRef.set(list);
                    m.setRowCount(0);
                    for (CustomerDto c : list) m.addRow(new Object[]{c.name(), c.phone(), c.address()});
                    if (!list.isEmpty()) tbl.setRowSelectionInterval(0, 0);
                } catch (Exception ignored) {}
            }
        }.execute();

        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { doSearch.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { doSearch.run(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) {}
        });

        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.setBorder(new EmptyBorder(8, 8, 0, 8));
        top.add(new JLabel("Search customer:"), BorderLayout.WEST);
        top.add(search, BorderLayout.CENTER);
        dlg.add(top, BorderLayout.NORTH);
        dlg.add(new JScrollPane(tbl), BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> { selectedId.set(-1); dlg.dispose(); });
        JButton select = new JButton("Select");
        select.setBackground(NAVY);
        select.setForeground(Color.WHITE);
        select.setOpaque(true);
        select.setBorderPainted(false);
        select.addActionListener(e -> {
            int row = tbl.getSelectedRow();
            List<CustomerDto> list = resultRef.get();
            if (row >= 0 && list != null && row < list.size()) {
                selectedId.set(list.get(row).customerId());
            }
            dlg.dispose();
        });
        btns.add(cancel);
        btns.add(select);
        dlg.add(btns, BorderLayout.SOUTH);

        doSearch.run();
        dlg.setVisible(true);

        int id = selectedId.get();
        return id > 0 ? id : null;
    }

    // ── Clear / reset ─────────────────────────────────────────────────────────

    private void clearAll() {
        currentInvoice = null;
        previousReturns.clear();
        activeLines.clear();
        remainingQtys.clear();

        invoiceField.setText("");
        invoiceInfoLabel.setText("");

        itemsModel.setRowCount(0);
        itemsBodyPanel.removeAll();
        itemsBodyPanel.add(itemsPlaceholder, BorderLayout.CENTER);
        itemsBodyPanel.revalidate();
        itemsBodyPanel.repaint();

        contentColumn.remove(prevReturnsCard);
        prevReturnsCard = buildPrevReturnsCard(List.of());
        prevReturnsCard.setVisible(false);
        contentColumn.add(prevReturnsCard);
        contentColumn.revalidate();
        contentColumn.repaint();

        totalRefundLabel.setText("Rs. 0.00");
        refundMethodCombo.setSelectedIndex(0);
        processBtn.setEnabled(false);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private JLabel small(String text, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(11f));
        l.setForeground(color);
        return l;
    }

    private JPanel labeled(String lbl, JComponent comp) {
        JPanel p = new JPanel(new BorderLayout(0, 3));
        p.setOpaque(false);
        JLabel l = new JLabel(lbl);
        l.setFont(l.getFont().deriveFont(11f));
        l.setForeground(TEXT2);
        p.add(l,    BorderLayout.NORTH);
        p.add(comp, BorderLayout.CENTER);
        return p;
    }
}
