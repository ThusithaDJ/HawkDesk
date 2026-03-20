package com.olympus.system.hawkdeskpos.frontend.sale;

import com.olympus.system.hawkdeskpos.dto.ItemDto;
import com.olympus.system.hawkdeskpos.dto.SaleLineDto;
import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.frontend.components.StatusPill;
import com.olympus.system.hawkdeskpos.service.ItemService;
import com.olympus.system.hawkdeskpos.service.SaleService;
import com.olympus.system.hawkdeskpos.service.SettingsService;
import com.olympus.system.hawkdeskpos.session.SessionContext;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * New Sale screen.
 * SearchDropdown → cart table → payment panel → success overlay.
 */
public class NewSalePanel extends JPanel {

    private static final Color BG    = new Color(0xF0, 0xF2, 0xF5);
    private static final Color NAVY  = new Color(0x1E, 0x3A, 0x5F);
    private static final Color TEXT2 = new Color(0x5A, 0x60, 0x70);
    private static final Color GREEN = new Color(0x2E, 0x7D, 0x32);
    private static final Color RED   = new Color(0xC6, 0x28, 0x28);

    private final ItemService itemService;
    private final SaleService saleService;

    private final List<ItemDto> cartItems = new ArrayList<>();
    private final List<Integer> cartQtys  = new ArrayList<>();
    private DefaultTableModel   cartModel;
    private JTable              cartTable;

    private JLabel     subtotalLabel, totalLabel, changeLabel;
    private JTextField discountField, amountRecField;
    private JToggleButton btnCash, btnCard, btnCredit;
    private String     paymentMethod = "CASH";

    // Inline search
    private JTextField searchField;
    private JPanel     resultsPanel;
    private Timer      searchDebounce;

    public NewSalePanel(ItemService itemService, SaleService saleService, SettingsService settingsService) {
        this.itemService = itemService;
        this.saleService = saleService;
        setBackground(BG);
        setLayout(new BorderLayout());
        buildUI();
    }

    public void resetCart() {
        cartItems.clear();
        cartQtys.clear();
        cartModel.setRowCount(0);
        discountField.setText("0");
        amountRecField.setText("");
        updateTotals();
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(14, 0));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("New Sale");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        header.add(title, BorderLayout.WEST);
        JButton back = new JButton("← Back");
        back.addActionListener(e -> Home.navigate(Home.CARD_DASH));
        JPanel hBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        hBtns.setOpaque(false);
        hBtns.add(back);
        header.add(hBtns, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout(14, 0));
        content.setOpaque(false);

        JPanel left = new JPanel(new BorderLayout(0, 10));
        left.setOpaque(false);
        left.add(buildSearchSection(), BorderLayout.NORTH);
        left.add(buildCartSection(),   BorderLayout.CENTER);

        content.add(left,                BorderLayout.CENTER);
        content.add(buildPaymentPanel(), BorderLayout.EAST);

        root.add(content, BorderLayout.CENTER);
        add(root);
    }

    private JPanel buildSearchSection() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 0));
        wrapper.setOpaque(false);

        // ── Search bar ────────────────────────────────────────────────────────
        CardPanel bar = new CardPanel(new BorderLayout(8, 0));
        ((JPanel)bar).setBorder(new EmptyBorder(10, 14, 10, 14));

        JLabel hint = new JLabel("Search item by name or SKU:");
        hint.setForeground(TEXT2);
        hint.setFont(hint.getFont().deriveFont(13f));

        searchField = new JTextField();
        searchField.setFont(searchField.getFont().deriveFont(14f));
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xC8, 0xCD, 0xD6)),
                new EmptyBorder(6, 10, 6, 10)));
        searchField.setToolTipText("Type item name or SKU…");

        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { scheduleSearch(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { scheduleSearch(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) {}
        });
        searchField.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) clearResults();
                if (e.getKeyCode() == KeyEvent.VK_DOWN)   focusFirstResult();
            }
        });

        ((JPanel)bar).add(hint,        BorderLayout.WEST);
        ((JPanel)bar).add(searchField, BorderLayout.CENTER);
        wrapper.add(bar, BorderLayout.NORTH);

        // ── Inline results panel (hidden until results arrive) ────────────────
        resultsPanel = new JPanel();
        resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
        resultsPanel.setBackground(Color.WHITE);

        // Scroll container — max 5 rows tall (54 px each + padding)
        JScrollPane resultScroll = new JScrollPane(resultsPanel);
        resultScroll.setBorder(BorderFactory.createMatteBorder(0, 1, 1, 1, new Color(0xC8, 0xCD, 0xD6)));
        resultScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54 * 5 + 8));
        resultScroll.setPreferredSize(new Dimension(0, 54 * 5 + 8));
        resultScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        resultScroll.setVisible(false);
        // Keep resultsPanel ref but track visibility on the scroll pane
        resultsPanel.putClientProperty("scrollPane", resultScroll);

        wrapper.add(resultScroll, BorderLayout.CENTER);
        return wrapper;
    }

    private void scheduleSearch() {
        if (searchDebounce != null && searchDebounce.isRunning()) searchDebounce.stop();
        searchDebounce = new Timer(220, e -> performSearch());
        searchDebounce.setRepeats(false);
        searchDebounce.start();
    }

    private JScrollPane getResultScroll() {
        return (JScrollPane) resultsPanel.getClientProperty("scrollPane");
    }

    private void performSearch() {
        String q = searchField.getText().trim();
        if (q.isEmpty()) { clearResults(); return; }
        new SwingWorker<List<ItemDto>, Void>() {
            @Override protected List<ItemDto> doInBackground() { return itemService.search(q); }
            @Override protected void done() {
                try {
                    List<ItemDto> items = get();
                    resultsPanel.removeAll();
                    if (items.isEmpty()) { getResultScroll().setVisible(false); triggerLayout(); return; }
                    for (ItemDto item : items) resultsPanel.add(buildResultRow(item));
                    getResultScroll().setVisible(true);
                    triggerLayout();
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void triggerLayout() {
        Container c = (getResultScroll() != null ? getResultScroll() : resultsPanel).getParent();
        while (c != null) { c.revalidate(); c.repaint(); c = c.getParent(); }
    }

    private JPanel buildResultRow(ItemDto item) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setBackground(Color.WHITE);
        row.setOpaque(true);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xF0, 0xF2, 0xF5)),
                new EmptyBorder(8, 14, 8, 14)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Left: name + sku · category
        JPanel left = new JPanel(new GridLayout(2, 1, 0, 2));
        left.setOpaque(false);
        JLabel nameLbl = new JLabel(item.itemName());
        nameLbl.setFont(nameLbl.getFont().deriveFont(Font.BOLD, 13f));
        JLabel subLbl = new JLabel(item.sku() + "  ·  " + item.categoryName());
        subLbl.setFont(subLbl.getFont().deriveFont(11f));
        subLbl.setForeground(TEXT2);
        left.add(nameLbl);
        left.add(subLbl);
        row.add(left, BorderLayout.CENTER);

        // Right: stock pill + price
        JPanel right = new JPanel(new GridLayout(2, 1, 0, 2));
        right.setOpaque(false);
        StatusPill pill = StatusPill.forStatus(item.stockStatus());
        pill.setHorizontalAlignment(SwingConstants.RIGHT);
        JLabel priceLbl = new JLabel(String.format("Rs. %.2f", item.sellingPrice()));
        priceLbl.setFont(priceLbl.getFont().deriveFont(Font.BOLD, 12f));
        priceLbl.setForeground(NAVY);
        priceLbl.setHorizontalAlignment(SwingConstants.RIGHT);
        right.add(pill);
        right.add(priceLbl);
        row.add(right, BorderLayout.EAST);

        // Hover highlight
        row.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) { row.setBackground(new Color(0xF7, 0xF8, 0xFA)); }
            @Override public void mouseExited(java.awt.event.MouseEvent e)  { row.setBackground(Color.WHITE); }
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { addToCart(item); clearResults(); }
        });

        // Keyboard select when row gains focus
        row.setFocusable(true);
        row.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER)  { addToCart(item); clearResults(); }
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) { clearResults(); searchField.requestFocusInWindow(); }
            }
        });

        return row;
    }

    private void clearResults() {
        resultsPanel.removeAll();
        getResultScroll().setVisible(false);
        triggerLayout();
        searchField.setText("");
    }

    private void focusFirstResult() {
        if (getResultScroll().isVisible() && resultsPanel.getComponentCount() > 0)
            resultsPanel.getComponent(0).requestFocusInWindow();
    }

    private JPanel buildCartSection() {
        String[] cols = {"Item Name", "SKU", "Qty", "Unit Price (Rs.)", "Total (Rs.)", ""};
        cartModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return c == 2; }
            @Override public Class<?> getColumnClass(int c) { return c == 2 ? Integer.class : String.class; }
        };
        cartModel.addTableModelListener(e -> {
            int row = e.getFirstRow(); int col = e.getColumn();
            if (col == 2 && row >= 0 && row < cartQtys.size()) {
                Object val = cartModel.getValueAt(row, 2);
                int qty = Math.max(1, val instanceof Number n ? n.intValue() : 1);
                cartQtys.set(row, qty);
                double price = cartItems.get(row).sellingPrice();
                cartModel.setValueAt(String.format("%.2f", price * qty), row, 4);
                updateTotals();
            }
        });

        cartTable = new JTable(cartModel);
        cartTable.setRowHeight(40);
        cartTable.setShowGrid(false);
        cartTable.setIntercellSpacing(new Dimension(0, 0));
        cartTable.getTableHeader().setFont(cartTable.getFont().deriveFont(Font.BOLD, 12f));
        cartTable.getTableHeader().setBackground(new Color(0xF7, 0xF8, 0xFA));
        cartTable.getTableHeader().setForeground(TEXT2);
        cartTable.getColumnModel().getColumn(5).setMaxWidth(60);
        cartTable.getColumnModel().getColumn(2).setMaxWidth(80);

        cartTable.getColumn("").setCellRenderer((table, value, isSel, hasFocus, row, col) -> {
            JButton btn = new JButton("✕");
            btn.setForeground(RED);
            btn.setBorderPainted(false);
            btn.setContentAreaFilled(false);
            return btn;
        });
        cartTable.getColumn("").setCellEditor(new DefaultCellEditor(new JCheckBox()) {
            @Override public boolean isCellEditable(java.util.EventObject e) {
                if (e instanceof java.awt.event.MouseEvent me) {
                    int row = cartTable.rowAtPoint(me.getPoint());
                    if (row >= 0) removeFromCart(row);
                }
                return false;
            }
        });

        JScrollPane scroll = new JScrollPane(cartTable);
        scroll.setBorder(null);

        CardPanel c = new CardPanel(new BorderLayout());
        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setOpaque(false);
        headerRow.setBorder(new EmptyBorder(10, 14, 8, 14));
        JLabel t = new JLabel("CART");
        t.setFont(t.getFont().deriveFont(Font.BOLD, 11f));
        t.setForeground(TEXT2);
        headerRow.add(t, BorderLayout.WEST);
        JButton clearAll = new JButton("Clear All");
        clearAll.addActionListener(e -> resetCart());
        headerRow.add(clearAll, BorderLayout.EAST);
        ((JPanel)c).add(headerRow, BorderLayout.NORTH);
        ((JPanel)c).add(scroll,    BorderLayout.CENTER);
        return c;
    }

    private JPanel buildPaymentPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setPreferredSize(new Dimension(300, 0));

        CardPanel c = new CardPanel(new BorderLayout(0, 0));
        ((JPanel)c).setBorder(new EmptyBorder(14, 14, 14, 14));
        ((JPanel)c).setLayout(new BoxLayout((JPanel)c, BoxLayout.Y_AXIS));

        JLabel t = new JLabel("PAYMENT");
        t.setFont(t.getFont().deriveFont(Font.BOLD, 11f));
        t.setForeground(TEXT2);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        ((JPanel)c).add(t);
        ((JPanel)c).add(Box.createVerticalStrut(12));

        subtotalLabel = new JLabel("Rs. 0.00");
        totalLabel    = new JLabel("Rs. 0.00");
        totalLabel.setFont(totalLabel.getFont().deriveFont(Font.BOLD, 18f));
        totalLabel.setForeground(NAVY);

        JPanel rows = new JPanel(new GridLayout(3, 2, 4, 8));
        rows.setOpaque(false);
        rows.setAlignmentX(Component.LEFT_ALIGNMENT);
        rows.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        rows.add(lbl("Subtotal")); rows.add(subtotalLabel);

        discountField = new JTextField("0");
        discountField.getDocument().addDocumentListener(docListener(() -> updateTotals()));
        rows.add(lbl("Discount (Rs.)")); rows.add(discountField);
        rows.add(lbl("TOTAL"));          rows.add(totalLabel);

        ((JPanel)c).add(rows);
        ((JPanel)c).add(Box.createVerticalStrut(12));
        ((JPanel)c).add(new JSeparator());
        ((JPanel)c).add(Box.createVerticalStrut(12));

        // Payment method
        JLabel pmLabel = new JLabel("Payment Method");
        pmLabel.setForeground(TEXT2);
        pmLabel.setFont(pmLabel.getFont().deriveFont(12f));
        pmLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        ((JPanel)c).add(pmLabel);
        ((JPanel)c).add(Box.createVerticalStrut(6));
        ((JPanel)c).add(buildPaymentToggle());
        ((JPanel)c).add(Box.createVerticalStrut(12));

        // Quick tender
        JLabel qlabel = new JLabel("Quick Tender");
        qlabel.setForeground(TEXT2);
        qlabel.setFont(qlabel.getFont().deriveFont(12f));
        qlabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        ((JPanel)c).add(qlabel);
        ((JPanel)c).add(Box.createVerticalStrut(6));
        ((JPanel)c).add(buildQuickTender());
        ((JPanel)c).add(Box.createVerticalStrut(12));

        // Amount received + change
        amountRecField = new JTextField();
        amountRecField.getDocument().addDocumentListener(docListener(() -> updateChange()));
        changeLabel = new JLabel("Rs. 0.00");
        changeLabel.setFont(changeLabel.getFont().deriveFont(Font.BOLD, 16f));
        changeLabel.setForeground(GREEN);

        JPanel rcvRow = new JPanel(new GridLayout(2, 2, 4, 6));
        rcvRow.setOpaque(false);
        rcvRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        rcvRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        rcvRow.add(lbl("Amount Received (Rs.)")); rcvRow.add(amountRecField);
        rcvRow.add(lbl("Change"));               rcvRow.add(changeLabel);
        ((JPanel)c).add(rcvRow);
        ((JPanel)c).add(Box.createVerticalStrut(16));

        JButton chargeBtn = new JButton("CHARGE");
        chargeBtn.setBackground(GREEN);
        chargeBtn.setForeground(Color.WHITE);
        chargeBtn.setOpaque(true);
        chargeBtn.setBorderPainted(false);
        chargeBtn.setFont(chargeBtn.getFont().deriveFont(Font.BOLD, 16f));
        chargeBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        chargeBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        chargeBtn.addActionListener(e -> chargeSale());
        ((JPanel)c).add(chargeBtn);
        ((JPanel)c).add(Box.createVerticalStrut(8));

        JPanel secondary = new JPanel(new GridLayout(1, 2, 8, 0));
        secondary.setOpaque(false);
        secondary.setAlignmentX(Component.LEFT_ALIGNMENT);
        secondary.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        JButton voidBtn = new JButton("Void");
        voidBtn.setForeground(RED);
        voidBtn.addActionListener(e -> resetCart());
        secondary.add(new JButton("Hold"));
        secondary.add(voidBtn);
        ((JPanel)c).add(secondary);

        panel.add(c);
        return panel;
    }

    private JPanel buildPaymentToggle() {
        ButtonGroup bg = new ButtonGroup();
        btnCash   = toggleBtn("Cash");   btnCash.setSelected(true);
        btnCard   = toggleBtn("Card");
        btnCredit = toggleBtn("Credit");
        bg.add(btnCash); bg.add(btnCard); bg.add(btnCredit);
        btnCash.addActionListener(e   -> paymentMethod = "CASH");
        btnCard.addActionListener(e   -> paymentMethod = "CARD");
        btnCredit.addActionListener(e -> paymentMethod = "CREDIT");

        JPanel p = new JPanel(new GridLayout(1, 3, 4, 0));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        p.add(btnCash); p.add(btnCard); p.add(btnCredit);
        return p;
    }

    private JToggleButton toggleBtn(String text) {
        JToggleButton btn = new JToggleButton(text);
        btn.setFocusPainted(false);
        return btn;
    }

    private JPanel buildQuickTender() {
        JPanel p = new JPanel(new GridLayout(1, 4, 4, 0));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        for (int amt : new int[]{500, 1000, 2000, 5000}) {
            JButton btn = new JButton(String.valueOf(amt));
            btn.addActionListener(e -> amountRecField.setText(String.valueOf(amt)));
            p.add(btn);
        }
        return p;
    }

    private void addToCart(ItemDto item) {
        for (int i = 0; i < cartItems.size(); i++) {
            if (cartItems.get(i).itemId() == item.itemId()) {
                int nq = cartQtys.get(i) + 1;
                cartQtys.set(i, nq);
                cartModel.setValueAt(nq, i, 2);
                cartModel.setValueAt(String.format("%.2f", item.sellingPrice() * nq), i, 4);
                updateTotals();
                return;
            }
        }
        cartItems.add(item);
        cartQtys.add(1);
        cartModel.addRow(new Object[]{
                item.itemName(), item.sku(), 1,
                String.format("%.2f", item.sellingPrice()),
                String.format("%.2f", item.sellingPrice()), "✕"
        });
        updateTotals();
    }

    private void removeFromCart(int row) {
        if (row < 0 || row >= cartItems.size()) return;
        cartItems.remove(row);
        cartQtys.remove(row);
        cartModel.removeRow(row);
        updateTotals();
    }

    private void updateTotals() {
        double subtotal = 0;
        for (int i = 0; i < cartItems.size(); i++)
            subtotal += cartItems.get(i).sellingPrice() * cartQtys.get(i);
        double discount = 0;
        try { discount = Double.parseDouble(discountField.getText().trim()); } catch (Exception ignored) {}
        double total = Math.max(0, subtotal - discount);
        subtotalLabel.setText(String.format("Rs. %.2f", subtotal));
        totalLabel.setText(String.format("Rs. %.2f", total));
        updateChange();
    }

    private void updateChange() {
        try {
            double total = parseCurrency(totalLabel.getText());
            double rcv   = Double.parseDouble(amountRecField.getText().trim());
            double ch    = rcv - total;
            changeLabel.setText(String.format("Rs. %.2f", ch));
            changeLabel.setForeground(ch >= 0 ? GREEN : RED);
        } catch (Exception ignored) {}
    }

    private void chargeSale() {
        if (cartItems.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Cart is empty.", "Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        double amountPaid = 0;
        if ("CASH".equals(paymentMethod)) {
            try {
                double total = parseCurrency(totalLabel.getText());
                amountPaid   = Double.parseDouble(amountRecField.getText().trim());
                if (amountPaid < total) {
                    JOptionPane.showMessageDialog(this, "Amount received is less than total.", "Error", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Enter amount received.", "Error", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }

        double discount = 0;
        try { discount = Double.parseDouble(discountField.getText().trim()); } catch (Exception ignored) {}

        List<SaleLineDto> lines = new ArrayList<>();
        for (int i = 0; i < cartItems.size(); i++) {
            ItemDto item = cartItems.get(i);
            int     qty  = cartQtys.get(i);
            lines.add(new SaleLineDto(item.itemId(), 0, item.itemName(), item.sku(), qty,
                    item.sellingPrice(), qty * item.sellingPrice()));
        }

        final double fd = discount, fp = amountPaid;
        Long empId = SessionContext.current() != null ? SessionContext.current().getEmployee().id() : null;

        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() {
                return saleService.createInvoice(lines, fd, paymentMethod, fp, empId);
            }
            @Override protected void done() {
                try {
                    showSuccessOverlay(get());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(NewSalePanel.this,
                            "Sale failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void showSuccessOverlay(String invoiceNo) {
        JDialog overlay = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), true);
        overlay.setUndecorated(true);
        overlay.setSize(360, 280);
        overlay.setLocationRelativeTo(this);

        JPanel panel = new JPanel();
        panel.setBackground(Color.WHITE);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(30, 30, 30, 30));

        JLabel tick = new JLabel("✓");
        tick.setFont(tick.getFont().deriveFont(Font.BOLD, 60f));
        tick.setForeground(GREEN);
        tick.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel msg = new JLabel("Sale Complete");
        msg.setFont(msg.getFont().deriveFont(Font.BOLD, 20f));
        msg.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel inv = new JLabel(invoiceNo);
        inv.setFont(inv.getFont().deriveFont(14f));
        inv.setForeground(TEXT2);
        inv.setAlignmentX(Component.CENTER_ALIGNMENT);

        double change = 0;
        try {
            double total = parseCurrency(totalLabel.getText());
            double rcv   = Double.parseDouble(amountRecField.getText().trim());
            change = rcv - total;
        } catch (Exception ignored) {}

        JLabel changeLbl = new JLabel("Change: Rs. " + String.format("%.2f", change));
        changeLbl.setFont(changeLbl.getFont().deriveFont(Font.BOLD, 16f));
        changeLbl.setForeground(NAVY);
        changeLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        btnRow.setOpaque(false);
        JButton newSale = new JButton("New Sale");
        newSale.setBackground(NAVY);
        newSale.setForeground(Color.WHITE);
        newSale.setOpaque(true);
        newSale.setBorderPainted(false);
        newSale.addActionListener(e -> { overlay.dispose(); resetCart(); });
        JButton goHome = new JButton("← Dashboard");
        goHome.addActionListener(e -> { overlay.dispose(); resetCart(); Home.navigate(Home.CARD_DASH); });
        btnRow.add(newSale); btnRow.add(goHome);

        panel.add(tick);
        panel.add(Box.createVerticalStrut(8));
        panel.add(msg);
        panel.add(Box.createVerticalStrut(4));
        panel.add(inv);
        panel.add(Box.createVerticalStrut(12));
        panel.add(changeLbl);
        panel.add(Box.createVerticalStrut(20));
        panel.add(btnRow);

        overlay.add(panel);
        overlay.setVisible(true);
    }

    private JLabel lbl(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT2);
        l.setFont(l.getFont().deriveFont(12f));
        return l;
    }

    private double parseCurrency(String text) {
        return Double.parseDouble(text.replace("Rs.", "").trim());
    }

    private javax.swing.event.DocumentListener docListener(Runnable r) {
        return new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) {}
        };
    }
}
