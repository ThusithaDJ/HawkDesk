package com.olympus.system.hawkdeskpos.frontend.sale;

import com.olympus.system.hawkdeskpos.dto.CustomerDto;
import com.olympus.system.hawkdeskpos.dto.ItemDto;
import com.olympus.system.hawkdeskpos.dto.ReturnDto;
import com.olympus.system.hawkdeskpos.dto.SaleLineDto;
import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.frontend.components.StatusPill;
import com.olympus.system.hawkdeskpos.service.CashAccountService;
import com.olympus.system.hawkdeskpos.service.CustomerService;
import com.olympus.system.hawkdeskpos.service.ItemService;
import com.olympus.system.hawkdeskpos.service.ReturnService;
import com.olympus.system.hawkdeskpos.service.SaleService;
import com.olympus.system.hawkdeskpos.service.SettingsService;
import com.olympus.system.hawkdeskpos.session.SessionContext;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * New Sale screen with tab-based hold support.
 * Each tab is a SaleSession (cart + payment state).
 * Hold saves the current session and opens a new one.
 * Void asks for confirmation before clearing.
 */
public class NewSalePanel extends JPanel
        implements com.olympus.system.hawkdeskpos.frontend.components.Refreshable {

    private static final Color BG    = new Color(0xF0, 0xF2, 0xF5);
    private static final Color NAVY  = new Color(0x1E, 0x3A, 0x5F);
    private static final Color TEXT2 = new Color(0x5A, 0x60, 0x70);
    private static final Color GREEN = new Color(0x2E, 0x7D, 0x32);
    private static final Color RED   = new Color(0xC6, 0x28, 0x28);

    private final ItemService          itemService;
    private final SaleService          saleService;
    private final CustomerService      customerService;
    private final CashAccountService   cashAccountService;
    private final ReturnService        returnService;

    // Customer selection (for credit / optional for cash/card)
    private CustomerDto selectedCustomer;
    private JButton     customerSelectBtn;
    private JLabel      customerLabel;
    private JPanel      creditResolveRow;
    private SpinnerDateModel resolveDateModel;
    private JSpinner    resolveDateSpinner;

    private JButton chargeBtn;
    private JPanel  stockWarnPanel;
    private JLabel  stockWarnLbl;
    private JButton switchBatchBtn;
    private final Set<Integer> blockedRows = new HashSet<>();

    // ── Per-session state ─────────────────────────────────────────────────────

    private static class SaleSession {
        final List<ItemDto> cartItems    = new ArrayList<>();
        final List<Integer> cartQtys     = new ArrayList<>();
        final List<Boolean> useSellUnit  = new ArrayList<>(); // per-row: true = qty is in sell unit
        String discount     = "0";
        String paymentMethod = "CASH";
        String amountRec    = "";
        final String label;
        SaleSession(int num) { this.label = "Sale " + num; }
    }

    private final List<SaleSession> sessions = new ArrayList<>();
    private int activeIdx = 0;

    // ── UI references (reflect the active session) ────────────────────────────

    private List<ItemDto> cartItems;      // alias → active session's list
    private List<Integer> cartQtys;       // alias → active session's list
    private List<Boolean> cartUseSellUnit; // alias → active session's list
    private DefaultTableModel cartModel;
    private JTable            cartTable;

    private JLabel        subtotalLabel, totalLabel, changeLabel, itemsCostLabel;
    private JLabel        discountWarnLabel;
    private JTextField    discountField, amountRecField;
    private JToggleButton btnCash, btnCard, btnCredit;
    private String        paymentMethod = "CASH";

    // Inline search
    private JTextField searchField;
    private JPanel     resultsPanel;
    private Timer      searchDebounce;

    // Session tab strip
    private JPanel sessionStrip;
    private int    sessionCounter = 1;

    // Exchange returns (for Exchange-refunded items offered as credit)
    private List<ReturnDto>   pendingExchangeReturns = new ArrayList<>();
    private final Set<Integer> selectedExchangeIds   = new HashSet<>();
    private double            exchangeCredit         = 0.0;
    private JPanel            exchangeCard;           // shown only when returns exist
    private JLabel            exchangeCreditLabel;    // shows total credit selected
    private JPanel            exchangeRowsPanel;      // inner content rebuilt on load

    public NewSalePanel(ItemService itemService, SaleService saleService, SettingsService ignored) {
        this(itemService, saleService, ignored, null, null, null);
    }

    public NewSalePanel(ItemService itemService, SaleService saleService,
                        SettingsService ignored, CustomerService customerService) {
        this(itemService, saleService, ignored, customerService, null, null);
    }

    public NewSalePanel(ItemService itemService, SaleService saleService,
                        SettingsService ignored, CustomerService customerService,
                        CashAccountService cashAccountService) {
        this(itemService, saleService, ignored, customerService, cashAccountService, null);
    }

    public NewSalePanel(ItemService itemService, SaleService saleService,
                        SettingsService ignored, CustomerService customerService,
                        CashAccountService cashAccountService, ReturnService returnService) {
        this.itemService          = itemService;
        this.saleService          = saleService;
        this.customerService      = customerService;
        this.cashAccountService   = cashAccountService;
        this.returnService        = returnService;
        setBackground(BG);
        setLayout(new BorderLayout());

        SaleSession first = new SaleSession(sessionCounter);
        sessions.add(first);
        cartItems      = first.cartItems;
        cartQtys       = first.cartQtys;
        cartUseSellUnit = first.useSellUnit;

        buildUI();
    }

    @Override
    public void refresh() { loadExchangeReturns(); }

    // ── Session management ────────────────────────────────────────────────────

    private void saveActiveSession() {
        SaleSession s = sessions.get(activeIdx);
        s.discount      = discountField.getText();
        s.paymentMethod = paymentMethod;
        s.amountRec     = amountRecField.getText();
        // cartItems / cartQtys are already the session's own lists — no copy needed
    }

    private void loadActiveSession() {
        SaleSession s = sessions.get(activeIdx);
        cartItems       = s.cartItems;
        cartQtys        = s.cartQtys;
        cartUseSellUnit = s.useSellUnit;
        paymentMethod   = s.paymentMethod;

        cartModel.setRowCount(0);
        for (int i = 0; i < s.cartItems.size(); i++) {
            ItemDto item       = s.cartItems.get(i);
            int     qty        = s.cartQtys.get(i);
            boolean inSellUnit = i < s.useSellUnit.size() && s.useSellUnit.get(i);
            double  factor     = item.conversionFactor() > 0 ? item.conversionFactor() : 1.0;
            // inSellUnit=true → secondary unit (cm); inSellUnit=false → primary unit (m)
            String  unitLabel;
            double  linePrice;
            if (inSellUnit && item.sellUnit() != null) {
                unitLabel = item.sellUnit();                   // "cm"
                linePrice = item.sellingPrice() / factor;      // price per cm
            } else {
                unitLabel = item.unit() != null ? item.unit() : "";
                if (item.sellUnit() != null) unitLabel = unitLabel + " ⇄";  // "m ⇄"
                linePrice = item.sellingPrice();               // price per m
            }
            double  unitCost = (inSellUnit && item.sellUnit() != null)
                    ? item.costPrice() / factor : item.costPrice();
            cartModel.addRow(new Object[]{
                    item.itemName(), item.sku(),
                    unitLabel,
                    (item.batchLabel() != null && !item.batchLabel().isBlank()) ? item.batchLabel() : "—",
                    qty,
                    String.format("%.2f", linePrice),
                    String.format("%.2f", unitCost),
                    String.format("%.2f", linePrice * qty), "✕"
            });
        }

        discountField.setText(s.discount);
        amountRecField.setText(s.amountRec);
        switch (s.paymentMethod) {
            case "CASH"   -> { if (btnCash   != null) btnCash.setSelected(true); }
            case "CARD"   -> { if (btnCard   != null) btnCard.setSelected(true); }
            case "CREDIT" -> { if (btnCredit != null) btnCredit.setSelected(true); }
        }
        blockedRows.clear();
        if (stockWarnPanel != null) hideStockWarning();
        if (chargeBtn != null) updateChargeState();
        for (int i = 0; i < s.cartItems.size(); i++) checkStockForRow(i);
        updateTotals();
    }

    private void switchSession(int newIdx) {
        if (newIdx == activeIdx) return;
        saveActiveSession();
        activeIdx = newIdx;
        loadActiveSession();
        rebuildSessionStrip();
    }

    private void holdCurrentSale() {
        if (cartItems.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Cart is empty — nothing to hold.", "Hold", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        saveActiveSession();
        sessionCounter++;
        SaleSession next = new SaleSession(sessionCounter);
        sessions.add(next);
        activeIdx = sessions.size() - 1;
        cartItems       = next.cartItems;
        cartQtys        = next.cartQtys;
        cartUseSellUnit = next.useSellUnit;
        paymentMethod   = "CASH";
        cartModel.setRowCount(0);
        discountField.setText("0");
        amountRecField.setText("");
        if (btnCash != null) btnCash.setSelected(true);
        updateTotals();
        rebuildSessionStrip();
    }

    private void voidCurrentSale() {
        int res = JOptionPane.showConfirmDialog(this,
                "Void this sale and clear all items?",
                "Void Sale", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (res != JOptionPane.YES_OPTION) return;

        if (sessions.size() > 1) {
            // Remove this tab and switch to the previous one
            sessions.remove(activeIdx);
            if (activeIdx >= sessions.size()) activeIdx = sessions.size() - 1;
            cartItems       = sessions.get(activeIdx).cartItems;
            cartQtys        = sessions.get(activeIdx).cartQtys;
            cartUseSellUnit = sessions.get(activeIdx).useSellUnit;
            loadActiveSession();
        } else {
            resetCart();
        }
        rebuildSessionStrip();
    }

    private void closeSession(int idx) {
        SaleSession s = sessions.get(idx);
        if (!s.cartItems.isEmpty()) {
            int res = JOptionPane.showConfirmDialog(this,
                    "Discard \"" + s.label + "\"? All items will be lost.",
                    "Close Tab", JOptionPane.YES_NO_OPTION);
            if (res != JOptionPane.YES_OPTION) return;
        }
        sessions.remove(idx);
        if (activeIdx >= sessions.size()) activeIdx = sessions.size() - 1;
        cartItems       = sessions.get(activeIdx).cartItems;
        cartQtys        = sessions.get(activeIdx).cartQtys;
        cartUseSellUnit = sessions.get(activeIdx).useSellUnit;
        loadActiveSession();
        rebuildSessionStrip();
    }

    private void rebuildSessionStrip() {
        sessionStrip.removeAll();
        for (int i = 0; i < sessions.size(); i++) {
            final int idx = i;
            SaleSession s = sessions.get(i);
            boolean active = (i == activeIdx);

            JPanel tab = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0)) {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(active ? Color.WHITE : new Color(0xE2, 0xE5, 0xEA));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    g2.setColor(new Color(0xC8, 0xCC, 0xD5));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                    g2.dispose();
                }
            };
            tab.setOpaque(false);
            tab.setBorder(new EmptyBorder(4, 10, 4, 6));
            tab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JLabel lbl = new JLabel(s.label);
            lbl.setFont(lbl.getFont().deriveFont(active ? Font.BOLD : Font.PLAIN, 12f));
            lbl.setForeground(active ? NAVY : TEXT2);
            lbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            tab.add(lbl);

            if (sessions.size() > 1) {
                JLabel close = new JLabel("  ×");
                close.setFont(close.getFont().deriveFont(Font.BOLD, 12f));
                close.setForeground(TEXT2);
                close.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                close.addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) { e.consume(); closeSession(idx); }
                });
                tab.add(close);
            }

            MouseAdapter tabClick = new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { switchSession(idx); }
            };
            tab.addMouseListener(tabClick);
            lbl.addMouseListener(tabClick);

            sessionStrip.add(tab);
        }
        sessionStrip.revalidate();
        sessionStrip.repaint();
    }

    /** Called by Home.navigateToNewSaleWithItem to pre-load an item into the active cart. */
    public void addItemDirectly(ItemDto item) {
        addToCart(item);
    }

    public void resetCart() {
        cartItems.clear();
        cartQtys.clear();
        cartUseSellUnit.clear();
        cartModel.setRowCount(0);
        discountField.setText("0");
        amountRecField.setText("");
        changeLabel.setText("Rs. 0.00");
        changeLabel.setForeground(GREEN);
        paymentMethod = "CASH";
        if (btnCash != null) btnCash.setSelected(true);
        blockedRows.clear();
        selectedCustomer = null;
        if (customerLabel != null) { customerLabel.setText("No customer selected"); customerLabel.setForeground(TEXT2); }
        if (creditResolveRow != null) creditResolveRow.setVisible(false);
        if (stockWarnPanel != null) hideStockWarning();
        if (chargeBtn != null) updateChargeState();
        selectedExchangeIds.clear();
        exchangeCredit = 0.0;
        loadExchangeReturns();
        updateTotals();
    }

    // ── UI construction ───────────────────────────────────────────────────────

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        // ── North: header + session strip ─────────────────────────────────────
        JPanel north = new JPanel();
        north.setOpaque(false);
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));

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
        north.add(header);
        north.add(Box.createVerticalStrut(6));

        sessionStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        sessionStrip.setOpaque(false);
        rebuildSessionStrip();
        north.add(sessionStrip);

        root.add(north, BorderLayout.NORTH);

        // ── Centre: search+cart | payment ─────────────────────────────────────
        JPanel content = new JPanel(new BorderLayout(14, 0));
        content.setOpaque(false);

        JPanel left = new JPanel(new BorderLayout(0, 10));
        left.setOpaque(false);

        // Top of left column: search + exchange returns card stacked vertically
        JPanel leftTop = new JPanel();
        leftTop.setOpaque(false);
        leftTop.setLayout(new BoxLayout(leftTop, BoxLayout.Y_AXIS));
        leftTop.add(buildSearchSection());
        leftTop.add(Box.createVerticalStrut(6));
        exchangeCard = buildExchangeReturnsCard();
        leftTop.add(exchangeCard);

        left.add(leftTop,              BorderLayout.NORTH);
        left.add(buildCartSection(),   BorderLayout.CENTER);
        stockWarnPanel = buildStockWarnPanel();
        left.add(stockWarnPanel,       BorderLayout.SOUTH);

        if (returnService != null) loadExchangeReturns();

        content.add(left,                BorderLayout.CENTER);
        content.add(buildPaymentPanel(), BorderLayout.EAST);

        root.add(content, BorderLayout.CENTER);
        add(root);
    }

    // ── Search section ────────────────────────────────────────────────────────

    private JPanel buildSearchSection() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 0));
        wrapper.setOpaque(false);

        // ── Search bar card containing both item search and customer row ──────
        CardPanel bar = new CardPanel(new BorderLayout(0, 6));
        ((JPanel) bar).setBorder(new EmptyBorder(10, 14, 10, 14));

        // Item search row
        JPanel searchRow = new JPanel(new BorderLayout(8, 0));
        searchRow.setOpaque(false);
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
        searchRow.add(hint,        BorderLayout.WEST);
        searchRow.add(searchField, BorderLayout.CENTER);

        // Customer row (inline, below search field)
        JPanel custRow = new JPanel(new BorderLayout(8, 0));
        custRow.setOpaque(false);
        JLabel custHint = new JLabel("Customer (optional):");
        custHint.setForeground(TEXT2);
        custHint.setFont(custHint.getFont().deriveFont(13f));

        customerLabel = new JLabel("No customer selected");
        customerLabel.setFont(customerLabel.getFont().deriveFont(13f));
        customerLabel.setForeground(TEXT2);

        customerSelectBtn = new JButton("Select");
        customerSelectBtn.setFocusPainted(false);
        customerSelectBtn.addActionListener(e -> showCustomerPicker());
        JButton clearCustBtn = new JButton("X");
        clearCustBtn.setFocusPainted(false);
        clearCustBtn.setToolTipText("Clear customer");
        clearCustBtn.addActionListener(e -> {
            selectedCustomer = null;
            customerLabel.setText("No customer selected");
            customerLabel.setForeground(TEXT2);
            selectedExchangeIds.clear();
            exchangeCredit = 0.0;
            if (returnService != null) loadExchangeReturns();
            updateTotals();
        });
        JPanel custBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        custBtns.setOpaque(false);
        custBtns.add(customerSelectBtn);
        custBtns.add(clearCustBtn);
        custRow.add(custHint,      BorderLayout.WEST);
        custRow.add(customerLabel, BorderLayout.CENTER);
        custRow.add(custBtns,      BorderLayout.EAST);

        JPanel barInner = new JPanel();
        barInner.setOpaque(false);
        barInner.setLayout(new BoxLayout(barInner, BoxLayout.Y_AXIS));
        barInner.add(searchRow);
        barInner.add(Box.createVerticalStrut(6));
        barInner.add(custRow);

        ((JPanel) bar).add(barInner, BorderLayout.CENTER);
        wrapper.add(bar, BorderLayout.NORTH);

        resultsPanel = new JPanel();
        resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
        resultsPanel.setBackground(Color.WHITE);

        JScrollPane resultScroll = new JScrollPane(resultsPanel);
        resultScroll.setBorder(BorderFactory.createMatteBorder(0, 1, 1, 1, new Color(0xC8, 0xCD, 0xD6)));
        resultScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64 * 5 + 8));
        resultScroll.setPreferredSize(new Dimension(0, 64 * 5 + 8));
        resultScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        resultScroll.setVisible(false);
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
            @Override protected List<ItemDto> doInBackground() { return itemService.searchForSale(q); }
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
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel left = new JPanel(new GridLayout(2, 1, 0, 2));
        left.setOpaque(false);
        JLabel nameLbl = new JLabel(item.itemName());
        nameLbl.setFont(nameLbl.getFont().deriveFont(Font.BOLD, 13f));
        String batchInfo = (item.batchLabel() != null && !item.batchLabel().isBlank())
                ? "  ·  Batch: " + item.batchLabel() + "  ·  Qty: " + item.currentQty()
                : "  ·  Qty: " + item.currentQty();
        JLabel subLbl = new JLabel(item.sku() + "  ·  " + item.categoryName() + batchInfo);
        subLbl.setFont(subLbl.getFont().deriveFont(11f));
        subLbl.setForeground(TEXT2);
        left.add(nameLbl);
        left.add(subLbl);
        row.add(left, BorderLayout.CENTER);

        JPanel right = new JPanel(new GridLayout(3, 1, 0, 1));
        right.setOpaque(false);
        StatusPill pill = StatusPill.forStatus(item.stockStatus());
        pill.setHorizontalAlignment(SwingConstants.RIGHT);
        JLabel priceLbl = new JLabel(String.format("Rs. %.2f", item.sellingPrice()));
        priceLbl.setFont(priceLbl.getFont().deriveFont(Font.BOLD, 12f));
        priceLbl.setForeground(NAVY);
        priceLbl.setHorizontalAlignment(SwingConstants.RIGHT);
        JLabel costLbl = new JLabel(String.format("Cost: Rs. %.2f", item.costPrice()));
        costLbl.setFont(costLbl.getFont().deriveFont(10f));
        costLbl.setForeground(TEXT2);
        costLbl.setHorizontalAlignment(SwingConstants.RIGHT);
        right.add(pill);
        right.add(priceLbl);
        right.add(costLbl);
        row.add(right, BorderLayout.EAST);

        MouseAdapter rowClick = new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { row.setBackground(new Color(0xF7, 0xF8, 0xFA)); }
            @Override public void mouseExited(MouseEvent e)  { row.setBackground(Color.WHITE); }
            @Override public void mouseClicked(MouseEvent e) { addToCart(item); clearResults(); }
        };
        row.addMouseListener(rowClick);
        left.addMouseListener(rowClick);
        right.addMouseListener(rowClick);

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

    // ── Cart section ──────────────────────────────────────────────────────────

    private JPanel buildCartSection() {
        String[] cols = {"Item Name", "SKU", "Unit", "Batch", "Qty", "Unit Price (Rs.)", "Unit Cost (Rs.)", "Total (Rs.)", ""};
        cartModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) { return c == 4 ? Integer.class : String.class; }
        };

        cartTable = new JTable(cartModel);
        cartTable.setRowHeight(40);
        cartTable.setShowGrid(false);
        cartTable.setIntercellSpacing(new Dimension(0, 0));
        cartTable.getTableHeader().setFont(cartTable.getFont().deriveFont(Font.BOLD, 12f));
        cartTable.getTableHeader().setBackground(new Color(0xF7, 0xF8, 0xFA));
        cartTable.getTableHeader().setForeground(TEXT2);
        cartTable.getColumnModel().getColumn(8).setMaxWidth(48);
        cartTable.getColumnModel().getColumn(8).setMinWidth(48);
        cartTable.getColumnModel().getColumn(6).setPreferredWidth(110); // Unit Cost
        cartTable.getColumnModel().getColumn(2).setMaxWidth(70);
        cartTable.getColumnModel().getColumn(2).setPreferredWidth(60);
        cartTable.getColumnModel().getColumn(3).setPreferredWidth(110);
        cartTable.getColumnModel().getColumn(4).setPreferredWidth(100);
        cartTable.getColumnModel().getColumn(4).setMinWidth(90);

        // Qty column: custom [-] qty [+] renderer
        cartTable.getColumnModel().getColumn(4).setCellRenderer(new QtyButtonRenderer());

        // Remove (✕) column renderer
        cartTable.getColumn("").setCellRenderer((table, value, isSel, hasFocus, row, col) -> {
            JButton btn = new JButton("X");
            btn.setForeground(RED);
            btn.setBorderPainted(false);
            btn.setContentAreaFilled(false);
            btn.setFocusPainted(false);
            return btn;
        });

        // Single mouse listener handles both qty +/- clicks and remove-row clicks
        cartTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int col = cartTable.columnAtPoint(e.getPoint());
                int row = cartTable.rowAtPoint(e.getPoint());
                if (row < 0) return;

                if (col == 2) {  // unit column — toggle sell unit if configured
                    if (row < cartItems.size() && cartItems.get(row).sellUnit() != null) {
                        toggleUnit(row);
                    }
                } else if (col == 4) {  // qty column
                    Rectangle cell = cartTable.getCellRect(row, col, false);
                    int relX = e.getX() - cell.x;
                    if (relX <= 30) {
                        adjustQty(row, -1);
                    } else if (relX >= cell.width - 30) {
                        adjustQty(row, +1);
                    } else {
                        // Click on the number — prompt for direct entry
                        String input = JOptionPane.showInputDialog(
                                NewSalePanel.this, "Enter quantity:", cartQtys.get(row));
                        if (input != null) {
                            try {
                                int qty = Math.max(1, Integer.parseInt(input.trim()));
                                adjustQty(row, qty - cartQtys.get(row));
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                } else if (col == cartModel.getColumnCount() - 1) {  // ✕ column
                    removeFromCart(row);
                }
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
        clearAll.addActionListener(e -> {
            if (cartItems.isEmpty()) return;
            int res = JOptionPane.showConfirmDialog(this, "Clear all items from cart?",
                    "Clear Cart", JOptionPane.YES_NO_OPTION);
            if (res == JOptionPane.YES_OPTION) resetCart();
        });
        headerRow.add(clearAll, BorderLayout.EAST);
        ((JPanel) c).add(headerRow, BorderLayout.NORTH);
        ((JPanel) c).add(scroll,    BorderLayout.CENTER);
        return c;
    }

    private void adjustQty(int row, int delta) {
        if (row < 0 || row >= cartQtys.size()) return;
        int newQty = Math.max(1, cartQtys.get(row) + delta);
        cartQtys.set(row, newQty);
        cartModel.setValueAt(newQty, row, 4);
        ItemDto item       = cartItems.get(row);
        boolean inSellUnit = row < cartUseSellUnit.size() && cartUseSellUnit.get(row);
        double  factor     = item.conversionFactor() > 0 ? item.conversionFactor() : 1.0;
        // inSellUnit=true: qty in secondary unit (cm), price per cm = sellingPrice/factor
        // inSellUnit=false: qty in primary unit (m), price = sellingPrice
        double  linePrice  = (inSellUnit && item.sellUnit() != null) ? item.sellingPrice() / factor : item.sellingPrice();
        cartModel.setValueAt(String.format("%.2f", linePrice * newQty), row, 7);
        updateTotals();
        checkStockForRow(row);
    }

    /**
     * Toggles a cart row between the item's stock unit and its configured sell unit.
     * Converts the displayed quantity so the logical amount stays the same.
     * Example: row shows "2 m" → toggle → "200 cm" (factor = 100).
     */
    private void toggleUnit(int row) {
        if (row < 0 || row >= cartItems.size()) return;
        ItemDto item = cartItems.get(row);
        if (item.sellUnit() == null) return;

        boolean wasSellUnit = row < cartUseSellUnit.size() && cartUseSellUnit.get(row);
        int     oldQty      = cartQtys.get(row);
        double  factor      = item.conversionFactor() > 0 ? item.conversionFactor() : 1.0;

        int    newQty;
        String newUnitLabel;
        double newLinePrice;
        if (wasSellUnit) {
            // secondary (cm) → primary (m): divide qty by factor
            newQty       = Math.max(1, (int) Math.round(oldQty / factor));
            newUnitLabel = (item.unit() != null ? item.unit() : "") + " ⇄";  // "m ⇄"
            newLinePrice = item.sellingPrice();             // price per primary (m)
        } else {
            // primary (m) → secondary (cm): multiply qty by factor
            newQty       = Math.max(1, (int) Math.round(oldQty * factor));
            newUnitLabel = item.sellUnit();                  // "cm"
            newLinePrice = item.sellingPrice() / factor;     // price per secondary (cm)
        }

        while (cartUseSellUnit.size() <= row) cartUseSellUnit.add(false);
        cartUseSellUnit.set(row, !wasSellUnit);
        cartQtys.set(row, newQty);

        double newUnitCost = wasSellUnit ? item.costPrice() : item.costPrice() / factor;
        cartModel.setValueAt(newUnitLabel,                                row, 2);
        cartModel.setValueAt(newQty,                                      row, 4);
        cartModel.setValueAt(String.format("%.2f", newLinePrice),         row, 5);
        cartModel.setValueAt(String.format("%.2f", newUnitCost),          row, 6);
        cartModel.setValueAt(String.format("%.2f", newLinePrice * newQty), row, 7);

        updateTotals();
        checkStockForRow(row);
    }

    // ── Payment panel ─────────────────────────────────────────────────────────

    private JPanel buildPaymentPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setPreferredSize(new Dimension(300, 0));

        CardPanel c = new CardPanel(new BorderLayout(0, 0));
        ((JPanel) c).setBorder(new EmptyBorder(14, 14, 14, 14));
        ((JPanel) c).setLayout(new BoxLayout((JPanel) c, BoxLayout.Y_AXIS));

        JLabel t = new JLabel("PAYMENT");
        t.setFont(t.getFont().deriveFont(Font.BOLD, 11f));
        t.setForeground(TEXT2);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        ((JPanel) c).add(t);
        ((JPanel) c).add(Box.createVerticalStrut(12));

        subtotalLabel = new JLabel("Rs. 0.00");
        totalLabel    = new JLabel("Rs. 0.00");
        totalLabel.setFont(totalLabel.getFont().deriveFont(Font.BOLD, 18f));
        totalLabel.setForeground(NAVY);

        itemsCostLabel = new JLabel("Rs. 0.00");
        itemsCostLabel.setForeground(TEXT2);

        JPanel rows = new JPanel(new GridLayout(4, 2, 4, 8));
        rows.setOpaque(false);
        rows.setAlignmentX(Component.LEFT_ALIGNMENT);
        rows.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));
        rows.add(lbl("Subtotal")); rows.add(subtotalLabel);
        rows.add(lbl("Items Cost")); rows.add(itemsCostLabel);

        discountField = new JTextField("0");
        discountField.getDocument().addDocumentListener(docListener(this::updateTotals));
        rows.add(lbl("Discount (Rs.)")); rows.add(discountField);
        rows.add(lbl("TOTAL"));          rows.add(totalLabel);
        ((JPanel) c).add(rows);

        // Discount warning — shown when net total falls below items cost
        discountWarnLabel = new JLabel("⚠ Discount makes net sale lower than items cost");
        discountWarnLabel.setForeground(new Color(0xC6, 0x28, 0x28));
        discountWarnLabel.setFont(discountWarnLabel.getFont().deriveFont(11f));
        discountWarnLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        discountWarnLabel.setVisible(false);
        ((JPanel) c).add(discountWarnLabel);
        ((JPanel) c).add(Box.createVerticalStrut(12));
        ((JPanel) c).add(new JSeparator());
        ((JPanel) c).add(Box.createVerticalStrut(12));

        JLabel pmLabel = new JLabel("Payment Method");
        pmLabel.setForeground(TEXT2);
        pmLabel.setFont(pmLabel.getFont().deriveFont(12f));
        pmLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        ((JPanel) c).add(pmLabel);
        ((JPanel) c).add(Box.createVerticalStrut(6));
        ((JPanel) c).add(buildPaymentToggle());
        ((JPanel) c).add(Box.createVerticalStrut(10));

        // Credit resolve date (only shown when Credit selected)
        creditResolveRow = buildCreditResolveRow();
        creditResolveRow.setVisible(false);
        ((JPanel) c).add(creditResolveRow);
        ((JPanel) c).add(Box.createVerticalStrut(2));

        JLabel qlabel = new JLabel("Quick Tender");
        qlabel.setForeground(TEXT2);
        qlabel.setFont(qlabel.getFont().deriveFont(12f));
        qlabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        ((JPanel) c).add(qlabel);
        ((JPanel) c).add(Box.createVerticalStrut(6));
        ((JPanel) c).add(buildQuickTender());
        ((JPanel) c).add(Box.createVerticalStrut(12));

        amountRecField = new JTextField();
        amountRecField.getDocument().addDocumentListener(docListener(this::updateChange));
        changeLabel = new JLabel("Rs. 0.00");
        changeLabel.setFont(changeLabel.getFont().deriveFont(Font.BOLD, 16f));
        changeLabel.setForeground(GREEN);

        JPanel rcvRow = new JPanel(new GridLayout(2, 2, 4, 6));
        rcvRow.setOpaque(false);
        rcvRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        rcvRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        rcvRow.add(lbl("Amount Received (Rs.)")); rcvRow.add(amountRecField);
        rcvRow.add(lbl("Change"));                rcvRow.add(changeLabel);
        ((JPanel) c).add(rcvRow);
        ((JPanel) c).add(Box.createVerticalStrut(16));

        chargeBtn = new JButton("CHARGE");
        chargeBtn.setBackground(GREEN);
        chargeBtn.setForeground(Color.WHITE);
        chargeBtn.setOpaque(true);
        chargeBtn.setBorderPainted(false);
        chargeBtn.setFont(chargeBtn.getFont().deriveFont(Font.BOLD, 16f));
        chargeBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        chargeBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        chargeBtn.addActionListener(e -> chargeSale());
        ((JPanel) c).add(chargeBtn);
        ((JPanel) c).add(Box.createVerticalStrut(8));

        JPanel secondary = new JPanel(new GridLayout(1, 2, 8, 0));
        secondary.setOpaque(false);
        secondary.setAlignmentX(Component.LEFT_ALIGNMENT);
        secondary.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        JButton holdBtn = new JButton("Hold");
        holdBtn.addActionListener(e -> holdCurrentSale());

        JButton voidBtn = new JButton("Void");
        voidBtn.setForeground(RED);
        voidBtn.addActionListener(e -> voidCurrentSale());

        secondary.add(holdBtn);
        secondary.add(voidBtn);
        ((JPanel) c).add(secondary);

        panel.add(c);
        return panel;
    }

    private JPanel buildPaymentToggle() {
        ButtonGroup bg = new ButtonGroup();
        btnCash   = toggleBtn("Cash");   btnCash.setSelected(true);
        btnCard   = toggleBtn("Card");
        btnCredit = toggleBtn("Credit");
        bg.add(btnCash); bg.add(btnCard); bg.add(btnCredit);
        btnCash.addActionListener(e   -> { paymentMethod = "CASH";   onPaymentMethodChanged(); });
        btnCard.addActionListener(e   -> { paymentMethod = "CARD";   onPaymentMethodChanged(); });
        btnCredit.addActionListener(e -> { paymentMethod = "CREDIT"; onPaymentMethodChanged(); });

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

    private JPanel buildCreditResolveRow() {
        JPanel p = new JPanel(new BorderLayout(6, 0));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));

        JLabel lbl = new JLabel("Resolve By");
        lbl.setForeground(TEXT2);
        lbl.setFont(lbl.getFont().deriveFont(12f));

        // Default: 30 days from today
        resolveDateModel = new SpinnerDateModel(
                java.sql.Date.valueOf(LocalDate.now().plusDays(30)),
                null, null, java.util.Calendar.DAY_OF_MONTH);
        resolveDateSpinner = new JSpinner(resolveDateModel);
        resolveDateSpinner.setEditor(new JSpinner.DateEditor(resolveDateSpinner, "dd/MM/yyyy"));

        p.add(lbl,                BorderLayout.WEST);
        p.add(resolveDateSpinner, BorderLayout.CENTER);
        return p;
    }

    private void onPaymentMethodChanged() {
        boolean isCredit = "CREDIT".equals(paymentMethod);
        if (creditResolveRow != null) creditResolveRow.setVisible(isCredit);
        // For credit, amount received is irrelevant — clear it
        if (isCredit && amountRecField != null) amountRecField.setText("0");
        updateChargeState();
        // Trigger layout recalculation
        Container top = getParent();
        while (top != null) { top.revalidate(); top.repaint(); top = top.getParent(); }
    }

    private void showCustomerPicker() {
        if (customerService == null) {
            JOptionPane.showMessageDialog(this,
                    "Customer service not available.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Select Customer", true);
        dlg.setSize(480, 360);
        dlg.setLocationRelativeTo(this);
        dlg.setLayout(new BorderLayout(0, 8));

        JTextField search = new JTextField();
        search.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xC8, 0xCD, 0xD6)),
                new EmptyBorder(6, 10, 6, 10)));

        String[] cols = {"Name", "Phone", "Address"};
        DefaultTableModel m = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable tbl = new JTable(m);
        tbl.setRowHeight(32);
        tbl.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        java.util.concurrent.atomic.AtomicReference<List<CustomerDto>> resultRef =
                new java.util.concurrent.atomic.AtomicReference<>();

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
        top.add(new JLabel("Search:"), BorderLayout.WEST);
        top.add(search, BorderLayout.CENTER);
        dlg.add(top, BorderLayout.NORTH);
        dlg.add(new JScrollPane(tbl), BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton clear = new JButton("Clear Selection");
        clear.addActionListener(e -> {
            selectedCustomer = null;
            customerLabel.setText("No customer selected");
            customerLabel.setForeground(TEXT2);
            selectedExchangeIds.clear();
            exchangeCredit = 0.0;
            if (returnService != null) loadExchangeReturns();
            updateChargeState();
            updateTotals();
            dlg.dispose();
        });
        JButton select = new JButton("Select");
        select.setBackground(NAVY);
        select.setForeground(Color.WHITE);
        select.setOpaque(true);
        select.setBorderPainted(false);
        select.addActionListener(e -> {
            int sel = tbl.getSelectedRow();
            List<CustomerDto> list = resultRef.get();
            if (sel >= 0 && list != null && sel < list.size()) {
                selectedCustomer = list.get(sel);
                customerLabel.setText(selectedCustomer.name() +
                        (selectedCustomer.phone().isEmpty() ? "" : "  ·  " + selectedCustomer.phone()));
                customerLabel.setForeground(NAVY);
                // Reload exchange returns filtered by this customer
                selectedExchangeIds.clear();
                exchangeCredit = 0.0;
                if (returnService != null) loadExchangeReturns();
                updateTotals();
            }
            updateChargeState();
            dlg.dispose();
        });
        btns.add(clear);
        btns.add(select);
        dlg.add(btns, BorderLayout.SOUTH);

        doSearch.run();
        dlg.setVisible(true);
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

    // ── Cart logic ────────────────────────────────────────────────────────────

    private void addToCart(ItemDto item) {
        for (int i = 0; i < cartItems.size(); i++) {
            ItemDto existing = cartItems.get(i);
            // Match by specific stockId if available, otherwise by itemId
            boolean match = (item.stockId() > 0 && existing.stockId() > 0)
                    ? existing.stockId() == item.stockId()
                    : existing.itemId() == item.itemId() && existing.stockId() == item.stockId();
            if (match) {
                adjustQty(i, +1);
                return;
            }
        }
        cartItems.add(item);
        cartQtys.add(1);
        // Default: primary unit mode (inSellUnit=false means primary "m"; toggle goes to secondary "cm")
        cartUseSellUnit.add(false);
        String unitLabel = item.unit() != null ? item.unit() : "";
        // Hint the cashier that a big unit toggle is available
        if (item.sellUnit() != null) unitLabel = unitLabel + " ⇄";
        cartModel.addRow(new Object[]{
                item.itemName(), item.sku(),
                unitLabel,
                (item.batchLabel() != null && !item.batchLabel().isBlank()) ? item.batchLabel() : "—",
                1,
                String.format("%.2f", item.sellingPrice()),
                String.format("%.2f", item.costPrice()),
                String.format("%.2f", item.sellingPrice()), "✕"
        });
        updateTotals();
        checkStockForRow(cartItems.size() - 1);
    }

    private void removeFromCart(int row) {
        if (row < 0 || row >= cartItems.size()) return;
        cartItems.remove(row);
        cartQtys.remove(row);
        if (row < cartUseSellUnit.size()) cartUseSellUnit.remove(row);
        cartModel.removeRow(row);
        Set<Integer> shifted = new HashSet<>();
        for (int r : blockedRows) {
            if (r < row) shifted.add(r);
            else if (r > row) shifted.add(r - 1);
        }
        blockedRows.clear();
        blockedRows.addAll(shifted);
        if (blockedRows.isEmpty()) hideStockWarning();
        updateChargeState();
        updateTotals();
    }

    private void updateTotals() {
        double subtotal   = 0;
        double itemsCost  = 0;
        for (int i = 0; i < cartItems.size(); i++) {
            ItemDto item       = cartItems.get(i);
            int     displayQty = cartQtys.get(i);
            boolean inSellUnit = i < cartUseSellUnit.size() && cartUseSellUnit.get(i);
            double  factor     = item.conversionFactor() > 0 ? item.conversionFactor() : 1.0;
            double primaryQty = (inSellUnit && item.sellUnit() != null) ? (double) displayQty / factor : (double) displayQty;
            subtotal  += item.sellingPrice() * primaryQty;
            double unitCost = (inSellUnit && item.sellUnit() != null) ? item.costPrice() / factor : item.costPrice();
            itemsCost += unitCost * displayQty;
        }
        double discount = 0;
        try { discount = Double.parseDouble(discountField.getText().trim()); } catch (Exception ignored) {}
        double total = Math.max(0, subtotal - discount - exchangeCredit);
        subtotalLabel.setText(String.format("Rs. %.2f", subtotal));
        if (itemsCostLabel != null) itemsCostLabel.setText(String.format("Rs. %.2f", itemsCost));
        totalLabel.setText(String.format("Rs. %.2f", total));
        if (discountWarnLabel != null) {
            discountWarnLabel.setVisible(discount > 0 && total < itemsCost);
        }
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

    // ── Charge / success ──────────────────────────────────────────────────────

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
                    JOptionPane.showMessageDialog(this,
                            "Amount received is less than total.", "Error", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                        "Enter amount received.", "Error", JOptionPane.WARNING_MESSAGE);
                return;
            }
        } else if ("CARD".equals(paymentMethod)) {
            amountPaid = parseCurrency(totalLabel.getText());
        } else if ("CREDIT".equals(paymentMethod)) {
            if (selectedCustomer == null) {
                JOptionPane.showMessageDialog(this,
                        "Select a customer for credit sales.", "Error", JOptionPane.WARNING_MESSAGE);
                return;
            }
            // Check max debt limit
            if (selectedCustomer.maxDebtAmount() > 0 && customerService != null) {
                double outstanding = customerService.totalOutstandingDebt(selectedCustomer.customerId());
                double total       = parseCurrency(totalLabel.getText());
                if (outstanding + total > selectedCustomer.maxDebtAmount()) {
                    JOptionPane.showMessageDialog(this,
                            String.format("Credit limit exceeded for %s.\n" +
                                    "Max: Rs. %.2f  |  Current debt: Rs. %.2f  |  This sale: Rs. %.2f",
                                    selectedCustomer.name(),
                                    selectedCustomer.maxDebtAmount(), outstanding, total),
                            "Credit Limit Exceeded", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }
            amountPaid = 0;
        }

        double discount = 0;
        try { discount = Double.parseDouble(discountField.getText().trim()); } catch (Exception ignored) {}

        List<SaleLineDto> lines = new ArrayList<>();
        for (int i = 0; i < cartItems.size(); i++) {
            ItemDto item       = cartItems.get(i);
            int     displayQty = cartQtys.get(i);
            boolean inSellUnit = i < cartUseSellUnit.size() && cartUseSellUnit.get(i);
            double  factor     = item.conversionFactor() > 0 ? item.conversionFactor() : 1.0;
            // Convert display qty to PRIMARY unit quantity for stock deduction + invoice record
            // inSellUnit=true: secondary (cm) → primary = displayQty / factor (e.g. 10cm / 100 = 0.1m)
            // inSellUnit=false: primary (m) → already primary
            double deductQty = (inSellUnit && item.sellUnit() != null)
                               ? (double) displayQty / factor : (double) displayQty;
            double lineTotal  = item.sellingPrice() * deductQty;
            lines.add(new SaleLineDto(item.itemId(), item.stockId(), item.itemName(), item.sku(), deductQty,
                    item.sellingPrice(), lineTotal, "", item.unit(), item.costPrice()));
        }

        final double fd = discount, fp = amountPaid;
        // Net total for income recording — sum of line totals minus discount minus exchange credit
        final double saleNet = Math.max(0,
                lines.stream().mapToDouble(SaleLineDto::lineTotal).sum() - discount - exchangeCredit);
        Long empId = SessionContext.current() != null ? SessionContext.current().getEmployee().id() : null;
        Integer custId = selectedCustomer != null ? selectedCustomer.customerId() : null;
        LocalDate resolveDate = null;
        if ("CREDIT".equals(paymentMethod) && resolveDateSpinner != null) {
            java.util.Date picked = (java.util.Date) resolveDateSpinner.getValue();
            resolveDate = picked.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        }
        final LocalDate finalResolveDate = resolveDate;
        final List<Integer> exchangeIdsToLink = new ArrayList<>(selectedExchangeIds);

        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() {
                String invNo = saleService.createInvoice(lines, fd, paymentMethod, fp, empId, custId, finalResolveDate);
                // Auto-record income on cash drawer (credit sales excluded — they are not cash yet)
                if (cashAccountService != null && !"CREDIT".equalsIgnoreCase(paymentMethod)) {
                    cashAccountService.recordSaleIncome(saleNet, invNo, empId);
                }
                // Link any selected exchange returns to this invoice
                if (returnService != null && !exchangeIdsToLink.isEmpty()) {
                    returnService.linkExchangeReturnsToInvoice(exchangeIdsToLink, invNo, empId);
                }
                return invNo;
            }
            @Override protected void done() {
                try {
                    showSuccessOverlay(get());
                    selectedCustomer = null;
                    if (customerLabel != null) { customerLabel.setText("No customer selected"); customerLabel.setForeground(TEXT2); }
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

        JLabel tick = new JLabel("√");
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

    // ── Stock availability ────────────────────────────────────────────────────

    private JPanel buildStockWarnPanel() {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setBackground(new Color(0xFF, 0xF3, 0xE0));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xFF, 0xA0, 0x00)),
                new EmptyBorder(8, 12, 8, 12)));
        stockWarnLbl = new JLabel();
        stockWarnLbl.setForeground(new Color(0xE6, 0x51, 0x00));
        stockWarnLbl.setFont(stockWarnLbl.getFont().deriveFont(12f));
        switchBatchBtn = new JButton("View Batches");
        switchBatchBtn.addActionListener(e -> {
            Integer r    = (Integer) switchBatchBtn.getClientProperty("row");
            ItemDto item = (ItemDto) switchBatchBtn.getClientProperty("item");
            if (r != null && item != null) showBatchPicker(r, item);
        });
        p.add(stockWarnLbl,   BorderLayout.CENTER);
        p.add(switchBatchBtn, BorderLayout.EAST);
        p.setVisible(false);
        return p;
    }

    private void checkStockForRow(int row) {
        if (row < 0 || row >= cartItems.size()) return;
        ItemDto item       = cartItems.get(row);
        int     displayQty = cartQtys.get(row);
        boolean inSellUnit = row < cartUseSellUnit.size() && cartUseSellUnit.get(row);
        double  factor     = item.conversionFactor() > 0 ? item.conversionFactor() : 1.0;
        // Convert to primary unit qty for stock availability comparison
        // inSellUnit=true (cm): primaryRequested = displayQty / factor; inSellUnit=false (m): = displayQty
        double requested = (inSellUnit && item.sellUnit() != null)
                           ? (double) displayQty / factor : (double) displayQty;
        int stockId   = item.stockId();
        if (stockId <= 0) return; // aggregated row — skip
        new SwingWorker<Double, Void>() {
            @Override protected Double doInBackground() {
                return itemService.getAvailableQtyForStock(stockId);
            }
            @Override protected void done() {
                try {
                    double available = get();
                    if (requested > available) {
                        blockedRows.add(row);
                        showStockWarning(row, item, available);
                    } else {
                        blockedRows.remove(row);
                        if (blockedRows.isEmpty()) hideStockWarning();
                    }
                    updateChargeState();
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void showStockWarning(int row, ItemDto item, double available) {
        if (available <= 0) {
            stockWarnLbl.setText("⚠  \"" + item.itemName() + "\" is out of stock. Reduce qty or remove item.");
            switchBatchBtn.setVisible(false);
        } else {
            stockWarnLbl.setText("⚠  Only " + fmtQty(available) + " " + item.unit() + " of \"" + item.itemName() + "\" in this batch.");
            switchBatchBtn.setVisible(true);
            switchBatchBtn.putClientProperty("row",  row);
            switchBatchBtn.putClientProperty("item", item);
        }
        stockWarnPanel.setVisible(true);
        triggerLayout();
    }

    private void hideStockWarning() {
        stockWarnPanel.setVisible(false);
        triggerLayout();
    }

    private void updateChargeState() {
        if (chargeBtn == null) return;
        boolean creditNeedsCustomer = "CREDIT".equals(paymentMethod) && selectedCustomer == null;
        boolean ok = blockedRows.isEmpty() && !creditNeedsCustomer;
        chargeBtn.setEnabled(ok);
        chargeBtn.setBackground(ok ? GREEN : new Color(0x9E, 0x9E, 0x9E));
        if (chargeBtn.getToolTipText() != null || creditNeedsCustomer) {
            chargeBtn.setToolTipText(creditNeedsCustomer ? "Select a customer for credit sales" : null);
        }
    }

    private void showBatchPicker(int row, ItemDto item) {
        new SwingWorker<List<ItemDto>, Void>() {
            @Override protected List<ItemDto> doInBackground() {
                return itemService.listBatchesForSale(item.itemId());
            }
            @Override protected void done() {
                try {
                    List<ItemDto> batches = get();
                    if (batches.isEmpty()) {
                        JOptionPane.showMessageDialog(NewSalePanel.this,
                                "No stock available for \"" + item.itemName() + "\".",
                                "Out of Stock", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    showBatchPickerDialog(row, batches);
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void swapCartBatch(int row, ItemDto chosen, int qty) {
        cartItems.set(row, chosen);
        cartQtys.set(row, qty);
        cartModel.setValueAt(chosen.sku(), row, 1);
        cartModel.setValueAt(chosen.unit() != null ? chosen.unit() : "", row, 2);
        cartModel.setValueAt((chosen.batchLabel() != null && !chosen.batchLabel().isBlank()) ? chosen.batchLabel() : "—", row, 3);
        cartModel.setValueAt(qty, row, 4);
        cartModel.setValueAt(String.format("%.2f", chosen.sellingPrice()), row, 5);
        cartModel.setValueAt(String.format("%.2f", chosen.costPrice()), row, 6);
        cartModel.setValueAt(String.format("%.2f", chosen.sellingPrice() * qty), row, 7);
        updateTotals();
        blockedRows.remove(row);
        if (blockedRows.isEmpty()) hideStockWarning();
        updateChargeState();
    }

    private void showBatchPickerDialog(int row, List<ItemDto> batches) {
        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Choose Batch", true);
        dlg.setSize(480, 300);
        dlg.setLocationRelativeTo(this);
        dlg.setLayout(new BorderLayout());

        String[] cols = {"Batch / Label", "Available Qty", "Price (Rs.)"};
        DefaultTableModel m = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (ItemDto b : batches) {
            String label = (b.batchLabel() != null && !b.batchLabel().isBlank()) ? b.batchLabel() : "(no batch)";
            m.addRow(new Object[]{label, b.currentQty(), String.format("%.2f", b.sellingPrice())});
        }
        JTable tbl = new JTable(m);
        tbl.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tbl.setRowHeight(32);
        if (tbl.getRowCount() > 0) tbl.setRowSelectionInterval(0, 0);

        JButton select = new JButton("Select");
        select.addActionListener(e -> {
            int sel = tbl.getSelectedRow();
            if (sel < 0) return;
            ItemDto chosen  = batches.get(sel);
            int    requested  = cartQtys.get(row);
            double available  = chosen.currentQty();
            if (available >= requested) {
                swapCartBatch(row, chosen, requested);
                dlg.dispose();
            } else {
                int displayAvail = (int) Math.floor(available);
                int res = JOptionPane.showConfirmDialog(dlg,
                        "Only " + fmtQty(available) + " " + chosen.unit() + " available in this batch.\n" +
                        "Reduce the quantity from " + requested + " to " + displayAvail + "?",
                        "Insufficient Stock", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (res == JOptionPane.YES_OPTION) {
                    swapCartBatch(row, chosen, displayAvail);
                    dlg.dispose();
                }
                // NO: keep dialog open so the user can choose a different batch
            }
        });
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dlg.dispose());

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        btns.add(cancel); btns.add(select);
        dlg.add(new JScrollPane(tbl), BorderLayout.CENTER);
        dlg.add(btns, BorderLayout.SOUTH);
        dlg.setVisible(true);
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

    // ── Exchange Returns ──────────────────────────────────────────────────────

    /**
     * Builds the exchange returns card. Initially hidden; shown when pending exchange
     * returns are available. Content is rebuilt by loadExchangeReturns().
     */
    private JPanel buildExchangeReturnsCard() {
        CardPanel card = new CardPanel(new BorderLayout(0, 6));
        ((JPanel) card).setBorder(new EmptyBorder(10, 14, 10, 14));
        ((JPanel) card).setAlignmentX(Component.LEFT_ALIGNMENT);
        ((JPanel) card).setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        JLabel titleLbl = new JLabel("EXCHANGE CREDITS");
        titleLbl.setFont(titleLbl.getFont().deriveFont(Font.BOLD, 11f));
        titleLbl.setForeground(new Color(0xB4, 0x5B, 0x00));
        exchangeCreditLabel = new JLabel("No credit applied");
        exchangeCreditLabel.setFont(exchangeCreditLabel.getFont().deriveFont(Font.BOLD, 12f));
        exchangeCreditLabel.setForeground(new Color(0x2E, 0x7D, 0x32));
        titleRow.add(titleLbl,          BorderLayout.WEST);
        titleRow.add(exchangeCreditLabel, BorderLayout.EAST);
        ((JPanel) card).add(titleRow, BorderLayout.NORTH);

        exchangeRowsPanel = new JPanel();
        exchangeRowsPanel.setOpaque(false);
        exchangeRowsPanel.setLayout(new BoxLayout(exchangeRowsPanel, BoxLayout.Y_AXIS));
        JLabel loadingLbl = new JLabel("  Loading…");
        loadingLbl.setForeground(TEXT2);
        loadingLbl.setFont(loadingLbl.getFont().deriveFont(12f));
        exchangeRowsPanel.add(loadingLbl);

        JScrollPane scroll = new JScrollPane(exchangeRowsPanel);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setPreferredSize(new Dimension(0, 80));
        ((JPanel) card).add(scroll, BorderLayout.CENTER);

        ((JPanel) card).setVisible(false); // hidden until returns are loaded
        return (JPanel) card;
    }

    /**
     * Asynchronously loads pending exchange returns (filtered by selected customer if any)
     * and rebuilds the exchange card content.
     */
    private void loadExchangeReturns() {
        if (returnService == null) return;
        Integer cid = selectedCustomer != null ? selectedCustomer.customerId() : null;

        new SwingWorker<List<ReturnDto>, Void>() {
            @Override protected List<ReturnDto> doInBackground() {
                return cid != null
                        ? returnService.listPendingExchangeReturnsByCustomer(cid)
                        : returnService.listPendingExchangeReturns();
            }
            @Override protected void done() {
                try {
                    pendingExchangeReturns = get();
                    // Remove returns that are no longer pending (e.g. just linked)
                    selectedExchangeIds.retainAll(
                            pendingExchangeReturns.stream()
                                    .map(r -> r.returnId())
                                    .collect(Collectors.toSet()));
                    rebuildExchangeRows();
                    recalcExchangeCredit();
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void rebuildExchangeRows() {
        exchangeRowsPanel.removeAll();
        if (pendingExchangeReturns.isEmpty()) {
            exchangeCard.setVisible(false);
            exchangeRowsPanel.revalidate();
            exchangeRowsPanel.repaint();
            return;
        }

        exchangeCard.setVisible(true);
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yy");

        for (ReturnDto r : pendingExchangeReturns) {
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setOpaque(false);
            row.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xF0, 0xF2, 0xF5)),
                    new EmptyBorder(4, 0, 4, 0)));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);

            JCheckBox cb = new JCheckBox();
            cb.setOpaque(false);
            cb.setSelected(selectedExchangeIds.contains(r.returnId()));
            cb.addActionListener(e -> {
                if (cb.isSelected()) selectedExchangeIds.add(r.returnId());
                else                 selectedExchangeIds.remove(r.returnId());
                recalcExchangeCredit();
            });

            JLabel info = new JLabel(
                    "<html><b>" + r.itemName() + "</b>  ×" + fmtQty(r.qty()) +
                    "  ·  " + r.invoiceNo() +
                    (r.returnDate() != null ? "  ·  " + sdf.format(r.returnDate()) : "") +
                    (r.customerRef() != null ? "  ·  <i>" + r.customerRef() + "</i>" : "") +
                    "</html>");
            info.setFont(info.getFont().deriveFont(11f));
            info.setForeground(TEXT2);

            JLabel creditLbl = new JLabel(String.format("Rs. %.2f", r.originalSaleCost()));
            creditLbl.setFont(creditLbl.getFont().deriveFont(Font.BOLD, 11f));
            creditLbl.setForeground(new Color(0x2E, 0x7D, 0x32));

            row.add(cb,        BorderLayout.WEST);
            row.add(info,      BorderLayout.CENTER);
            row.add(creditLbl, BorderLayout.EAST);
            exchangeRowsPanel.add(row);
        }

        exchangeRowsPanel.revalidate();
        exchangeRowsPanel.repaint();
        triggerLayout();
    }

    private void recalcExchangeCredit() {
        exchangeCredit = pendingExchangeReturns.stream()
                .filter(r -> selectedExchangeIds.contains(r.returnId()))
                .mapToDouble(ReturnDto::originalSaleCost)
                .sum();
        if (exchangeCreditLabel != null) {
            if (exchangeCredit > 0) {
                exchangeCreditLabel.setText(String.format("Credit: Rs. %.2f", exchangeCredit));
                exchangeCreditLabel.setForeground(new Color(0x2E, 0x7D, 0x32));
            } else {
                exchangeCreditLabel.setText("No credit applied");
                exchangeCreditLabel.setForeground(TEXT2);
            }
        }
        updateTotals();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

    private static String fmtQty(double q) {
        return q == Math.floor(q)
            ? String.valueOf((long) q)
            : String.format("%.4f", q).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
