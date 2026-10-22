package com.olympus.system.hawkdeskpos.frontend.stock;

import com.olympus.system.hawkdeskpos.db.dao.Brands;
import com.olympus.system.hawkdeskpos.db.dao.Category;
import com.olympus.system.hawkdeskpos.dto.ItemDto;
import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.service.CategoryService;
import com.olympus.system.hawkdeskpos.service.ItemService;
import com.olympus.system.hawkdeskpos.service.UomService;
import com.olympus.system.hawkdeskpos.session.SessionContext;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Edit Item screen.
 * Left column: Basic Information, Pricing, Stock Settings panels.
 * Right sidebar: Current Qty display with admin-only manual adjustment.
 */
public class EditItemPanel extends JPanel {

    private static final Color BG    = new Color(0xF0, 0xF2, 0xF5);
    private static final Color NAVY  = new Color(0x1E, 0x3A, 0x5F);
    private static final Color TEXT2 = new Color(0x5A, 0x60, 0x70);
    private static final Color AMBER = new Color(0xE6, 0x51, 0x00);

    private final ItemService     itemService;
    private final CategoryService categoryService;
    private final UomService      uomService;

    private int currentItemId;
    private double currentQtyValue = 0.0;

    private JTextField nameField, skuField, priceField, costField, convFactorField;
    private JComboBox<String> unitCombo, sellUnitCombo;
    private JComboBox<String> categoryCombo, brandCombo;
    private JSpinner   minLevel, maxLevel;
    private JComboBox<String> statusCombo;
    private JLabel     qtyDisplay;
    private JLabel     unsavedBadge;
    private List<Category> categoryList = new ArrayList<>();
    private List<Brands>   brandList    = new ArrayList<>();

    public EditItemPanel(ItemService itemService, CategoryService categoryService, UomService uomService) {
        this.itemService     = itemService;
        this.categoryService = categoryService;
        this.uomService      = uomService;
        setBackground(BG);
        setLayout(new BorderLayout());
        buildUI();
    }

    public void loadItem(int itemId) {
        this.currentItemId = itemId;
        // Load UOM presets, categories, and brands in parallel, then fetch the item
        new SwingWorker<Object[], Void>() {
            @Override protected Object[] doInBackground() {
                java.util.List<String> presets = uomService != null
                        ? uomService.listPresetNames() : java.util.List.of("pcs");
                java.util.List<Category> cats = categoryService.listCategories();
                java.util.List<Brands>   brs  = categoryService.listBrands();
                return new Object[]{presets, cats, brs};
            }
            @Override protected void done() {
                try {
                    Object[] results = get();
                    @SuppressWarnings("unchecked")
                    java.util.List<String>   presets = (java.util.List<String>)   results[0];
                    @SuppressWarnings("unchecked")
                    java.util.List<Category> cats    = (java.util.List<Category>) results[1];
                    @SuppressWarnings("unchecked")
                    java.util.List<Brands>   brs     = (java.util.List<Brands>)   results[2];

                    categoryList = cats;
                    brandList    = brs;

                    unitCombo.removeAllItems();
                    sellUnitCombo.removeAllItems();
                    sellUnitCombo.addItem("");
                    for (String p : presets) { unitCombo.addItem(p); sellUnitCombo.addItem(p); }

                    categoryCombo.removeAllItems();
                    categoryCombo.addItem("— None —");
                    for (Category cat : cats) categoryCombo.addItem(cat.getCategoryName());

                    brandCombo.removeAllItems();
                    brandCombo.addItem("— None —");
                    for (Brands b : brs) brandCombo.addItem(b.getBrandName());

                } catch (Exception ignored) {}

                new SwingWorker<ItemDto, Void>() {
                    @Override protected ItemDto doInBackground() { return itemService.findById(itemId); }
                    @Override protected void done() {
                        try {
                            ItemDto d = get();
                            if (d == null) return;
                            nameField.setText(d.itemName());
                            skuField.setText(d.sku());
                            String primaryUnit   = d.unit()     != null ? d.unit()     : "";
                            String secondaryUnit = d.sellUnit() != null ? d.sellUnit() : "";
                            unitCombo.setSelectedItem(primaryUnit);
                            if (unitCombo.getSelectedIndex() < 0) {
                                unitCombo.addItem(primaryUnit); unitCombo.setSelectedItem(primaryUnit);
                            }
                            sellUnitCombo.setSelectedItem(secondaryUnit);
                            if (!secondaryUnit.isBlank() && sellUnitCombo.getSelectedIndex() < 0) {
                                sellUnitCombo.addItem(secondaryUnit); sellUnitCombo.setSelectedItem(secondaryUnit);
                            }
                            convFactorField.setText(String.valueOf(d.conversionFactor()));
                            priceField.setText(String.valueOf(d.sellingPrice()));
                            costField.setText(String.valueOf(d.costPrice()));
                            minLevel.setValue(d.minLevel());
                            maxLevel.setValue(d.maxLevel());
                            statusCombo.setSelectedItem(d.stat());
                            // Select category and brand by name
                            selectComboByName(categoryCombo, d.categoryName());
                            selectComboByName(brandCombo, d.brandName());
                            currentQtyValue = d.currentQty();
                            String qtyStr  = fmtQty(currentQtyValue);
                            String qtyUnit = !primaryUnit.isEmpty() ? " " + primaryUnit : "";
                            qtyDisplay.setText(qtyStr + qtyUnit);
                            unsavedBadge.setVisible(false);
                        } catch (Exception ignored) {}
                    }
                }.execute();
            }
        }.execute();
    }

    private void selectComboByName(JComboBox<String> combo, String name) {
        if (name == null || name.isBlank()) { combo.setSelectedIndex(0); return; }
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (name.equalsIgnoreCase(combo.getItemAt(i))) { combo.setSelectedIndex(i); return; }
        }
        combo.setSelectedIndex(0);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        // ── Header ────────────────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Edit Item");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        unsavedBadge = new JLabel("Unsaved changes");
        unsavedBadge.setFont(unsavedBadge.getFont().deriveFont(11f));
        unsavedBadge.setForeground(AMBER);
        unsavedBadge.setBorder(new EmptyBorder(0, 10, 0, 0));
        unsavedBadge.setVisible(false);
        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        titleRow.setOpaque(false);
        titleRow.add(title);
        titleRow.add(unsavedBadge);
        header.add(titleRow, BorderLayout.WEST);

        JButton revertBtn = new JButton("↺ Revert");
        revertBtn.addActionListener(e -> { if (currentItemId > 0) loadItem(currentItemId); });
        JButton back = new JButton("← Back");
        back.addActionListener(e -> Home.navigate(Home.CARD_STOCK));
        JPanel hBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        hBtns.setOpaque(false);
        hBtns.add(revertBtn);
        hBtns.add(back);
        header.add(hBtns, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        // ── Left: form panels ─────────────────────────────────────────────────
        JPanel formInner = new JPanel();
        formInner.setOpaque(false);
        formInner.setLayout(new BoxLayout(formInner, BoxLayout.Y_AXIS));
        formInner.add(buildBasicSection());
        formInner.add(Box.createVerticalStrut(10));
        formInner.add(buildPricingSection());
        formInner.add(Box.createVerticalStrut(10));
        formInner.add(buildStockSection());

        JScrollPane formScroll = new JScrollPane(formInner);
        formScroll.setBorder(null);
        formScroll.setOpaque(false);
        formScroll.getViewport().setOpaque(false);

        JPanel left = new JPanel(new BorderLayout(0, 10));
        left.setOpaque(false);
        left.add(formScroll,     BorderLayout.CENTER);
        left.add(buildSaveRow(), BorderLayout.SOUTH);

        // ── Right: sidebar anchored to NORTH ──────────────────────────────────
        JPanel sidebarOuter = new JPanel(new BorderLayout());
        sidebarOuter.setOpaque(false);
        sidebarOuter.add(buildSidebar(), BorderLayout.NORTH);

        // ── 70/30 split ───────────────────────────────────────────────────────
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, sidebarOuter);
        split.setResizeWeight(0.70);
        split.setBorder(null);
        split.setDividerSize(5);
        split.setOpaque(false);
        split.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0
                    && split.isShowing()) {
                SwingUtilities.invokeLater(() -> split.setDividerLocation(0.70));
            }
        });

        root.add(split, BorderLayout.CENTER);
        add(root);
    }

    // ── Form sections ─────────────────────────────────────────────────────────

    private CardPanel buildBasicSection() {
        CardPanel c = sectionCard("BASIC INFORMATION");
        ((JPanel)c).setMaximumSize(new Dimension(Integer.MAX_VALUE, 290));
        JPanel grid = new JPanel(new GridLayout(4, 2, 10, 10));
        grid.setOpaque(false);

        nameField = tf(); markOnChange(nameField);
        skuField  = tf();
        skuField.setEditable(false);
        skuField.setBackground(new Color(0xF7, 0xF8, 0xFA));
        skuField.setToolTipText("SKU cannot be changed — it is referenced in all sales history");

        categoryCombo = new JComboBox<>(new String[]{"— None —"});
        categoryCombo.addActionListener(e -> unsavedBadge.setVisible(true));
        brandCombo = new JComboBox<>(new String[]{"— None —"});
        brandCombo.addActionListener(e -> unsavedBadge.setVisible(true));

        unitCombo    = new JComboBox<>(new String[]{"pcs"});
        unitCombo.setEditable(true);
        unitCombo.addActionListener(e -> unsavedBadge.setVisible(true));
        sellUnitCombo = new JComboBox<>(new String[]{""});
        sellUnitCombo.setEditable(true);
        sellUnitCombo.setToolTipText("Optional sub-unit customers can order in");
        sellUnitCombo.addActionListener(e -> unsavedBadge.setVisible(true));
        convFactorField = tf();
        convFactorField.setText("1.0");
        convFactorField.setToolTipText("1 stock unit = ? sell units");
        markOnChange(convFactorField);

        statusCombo = new JComboBox<>(new String[]{"Active","Inactive"});
        statusCombo.addActionListener(e -> unsavedBadge.setVisible(true));

        grid.add(labeled("Item Name *",              nameField));
        grid.add(labeled("SKU (read-only)",           skuField));
        grid.add(labeled("Category",                 categoryCombo));
        grid.add(labeled("Brand",                    brandCombo));
        grid.add(labeled("Stock Unit",               unitCombo));
        grid.add(labeled("Status",                   statusCombo));
        grid.add(labeled("Sell Unit (optional)",     sellUnitCombo));
        grid.add(labeled("1 Stock Unit = ? Sell Units", convFactorField));
        ((JPanel)c).add(grid, BorderLayout.CENTER);
        return c;
    }

    private CardPanel buildPricingSection() {
        CardPanel c = sectionCard("PRICING");
        JPanel grid = new JPanel(new GridLayout(1, 2, 10, 0));
        grid.setOpaque(false);
        priceField = tf(); markOnChange(priceField);
        costField  = tf(); markOnChange(costField);
        grid.add(labeled("Cost Price (Rs.) *",    costField));
        grid.add(labeled("Selling Price (Rs.) *", priceField));
        ((JPanel)c).add(grid, BorderLayout.CENTER);
        return c;
    }

    private CardPanel buildStockSection() {
        CardPanel c = sectionCard("STOCK SETTINGS");
        JPanel grid = new JPanel(new GridLayout(1, 2, 10, 0));
        grid.setOpaque(false);
        minLevel = spinner(5); minLevel.addChangeListener(e -> unsavedBadge.setVisible(true));
        maxLevel = spinner(100); maxLevel.addChangeListener(e -> unsavedBadge.setVisible(true));
        grid.add(labeled("Minimum Level *", minLevel));
        grid.add(labeled("Maximum Level",   maxLevel));
        ((JPanel)c).add(grid, BorderLayout.CENTER);
        return c;
    }

    private JPanel buildSaveRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        row.setOpaque(false);
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> Home.navigate(Home.CARD_STOCK));
        JButton save = new JButton("Save Changes");
        save.setBackground(NAVY);
        save.setForeground(Color.WHITE);
        save.setOpaque(true);
        save.setBorderPainted(false);
        save.addActionListener(e -> saveChanges());
        row.add(cancel);
        row.add(save);
        return row;
    }

    // ── Sidebar ───────────────────────────────────────────────────────────────

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setOpaque(false);
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));

        // Current qty card
        CardPanel qtyCard = new CardPanel(new BorderLayout(0, 8));
        ((JPanel)qtyCard).setBorder(new EmptyBorder(14, 14, 14, 14));
        JLabel cardTitle = new JLabel("CURRENT STOCK QTY");
        cardTitle.setFont(cardTitle.getFont().deriveFont(Font.BOLD, 11f));
        cardTitle.setForeground(TEXT2);
        ((JPanel)qtyCard).add(cardTitle, BorderLayout.NORTH);

        qtyDisplay = new JLabel("—");
        qtyDisplay.setFont(qtyDisplay.getFont().deriveFont(Font.BOLD, 24f));
        qtyDisplay.setForeground(NAVY);
        ((JPanel)qtyCard).add(qtyDisplay, BorderLayout.CENTER);

        JLabel hint = new JLabel("<html><font color='#5A6070' size='2'>Use Receive Stock (GRN) to add new batches.<br>" +
                "Use the Adjust button below to correct the<br>current count.</font></html>");
        hint.setBorder(new EmptyBorder(6, 0, 0, 0));
        ((JPanel)qtyCard).add(hint, BorderLayout.SOUTH);
        sidebar.add(qtyCard);
        sidebar.add(Box.createVerticalStrut(10));

        // Adjust qty card — shown only when role is OWNER or ADMIN
        boolean canAdjust = false;
        if (SessionContext.current() != null) {
            String role = SessionContext.current().getEmployee().role();
            canAdjust = "OWNER".equals(role) || "ADMIN".equals(role);
        }
        if (canAdjust) {
            CardPanel adjCard = new CardPanel(new BorderLayout(0, 8));
            ((JPanel)adjCard).setBorder(new EmptyBorder(14, 14, 14, 14));
            JLabel adjTitle = new JLabel("ADJUST QTY");
            adjTitle.setFont(adjTitle.getFont().deriveFont(Font.BOLD, 11f));
            adjTitle.setForeground(TEXT2);
            ((JPanel)adjCard).add(adjTitle, BorderLayout.NORTH);

            JLabel adjHint = new JLabel("<html><font color='#5A6070' size='2'>Set the correct total qty and provide<br>" +
                    "a reason and reference for auditing.</font></html>");
            adjHint.setBorder(new EmptyBorder(0, 0, 8, 0));

            JButton adjBtn = new JButton("Adjust Stock Qty…");
            adjBtn.setBackground(AMBER);
            adjBtn.setForeground(Color.WHITE);
            adjBtn.setOpaque(true);
            adjBtn.setBorderPainted(false);
            adjBtn.addActionListener(e -> showAdjustDialog());

            JPanel adjBody = new JPanel();
            adjBody.setOpaque(false);
            adjBody.setLayout(new BoxLayout(adjBody, BoxLayout.Y_AXIS));
            adjBody.add(adjHint);
            adjBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
            adjBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            adjBody.add(adjBtn);

            ((JPanel)adjCard).add(adjBody, BorderLayout.CENTER);
            sidebar.add(adjCard);
        }

        return sidebar;
    }

    // ── Adjust qty dialog ─────────────────────────────────────────────────────

    private void showAdjustDialog() {
        String currentText = qtyDisplay.getText().trim();
        // Strip unit suffix from display text
        double current = currentQtyValue;

        JSpinner newQtySpinner = new JSpinner(new SpinnerNumberModel(
                current, 0.0, 999999.0, 1.0));
        JSpinner.NumberEditor qtyEditor = new JSpinner.NumberEditor(newQtySpinner, "#.###");
        newQtySpinner.setEditor(qtyEditor);

        JTextField reasonField = new JTextField("Stock count correction");
        JTextField referenceField = new JTextField();
        referenceField.setToolTipText("e.g. COUNT-2024-001, GRN reference, etc.");

        JPanel form = new JPanel(new GridLayout(3, 2, 8, 8));
        form.add(new JLabel("New Total Qty:"));   form.add(newQtySpinner);
        form.add(new JLabel("Reason:"));          form.add(reasonField);
        form.add(new JLabel("Reference:"));       form.add(referenceField);

        int res = JOptionPane.showConfirmDialog(this, form,
                "Adjust Stock Quantity", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res != JOptionPane.OK_OPTION) return;

        double newQty = ((Number) newQtySpinner.getValue()).doubleValue();
        String reason    = reasonField.getText().trim();
        String reference = referenceField.getText().trim();

        if (reason.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a reason for the adjustment.",
                    "Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Long empId = SessionContext.current() != null ? SessionContext.current().getEmployee().id() : null;
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                itemService.adjustItemQty(currentItemId, newQty, reason, reference, empId);
                return null;
            }
            @Override protected void done() {
                currentQtyValue = newQty;
                String unit = getComboText(unitCombo);
                qtyDisplay.setText(fmtQty(newQty) + (!unit.isEmpty() ? " " + unit : ""));
                JOptionPane.showMessageDialog(EditItemPanel.this,
                        "Stock qty adjusted to " + fmtQty(newQty) + ".",
                        "Adjusted", JOptionPane.INFORMATION_MESSAGE);
            }
        }.execute();
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private void saveChanges() {
        try {
            double price      = Double.parseDouble(priceField.getText().trim());
            double convFactor = 1.0;
            try { convFactor = Double.parseDouble(convFactorField.getText().trim()); } catch (NumberFormatException ignored) {}
            if (convFactor <= 0) convFactor = 1.0;
            String sellUnit = getComboText(sellUnitCombo).isBlank() ? null : getComboText(sellUnitCombo);

            // Resolve category/brand IDs from selected combo index
            int catIdx   = categoryCombo.getSelectedIndex();
            int brandIdx = brandCombo.getSelectedIndex();
            // index 0 = "— None —" → null; index > 0 → list index offset by 1
            Integer catId   = (catIdx > 0 && catIdx - 1 < categoryList.size())
                              ? categoryList.get(catIdx - 1).getCatId()    : null;
            Integer brandId = (brandIdx > 0 && brandIdx - 1 < brandList.size())
                              ? brandList.get(brandIdx - 1).getBrandId()   : null;

            final double fp = price, fFactor = convFactor;
            final String fSellUnit = sellUnit;
            final Integer fCatId = catId, fBrandId = brandId;
            Long empId = SessionContext.current() != null ? SessionContext.current().getEmployee().id() : null;
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() {
                    itemService.updateItem(currentItemId, nameField.getText().trim(),
                            fCatId, fBrandId, getComboText(unitCombo), fSellUnit, fFactor,
                            fp, (int) minLevel.getValue(), (int) maxLevel.getValue(),
                            (String) statusCombo.getSelectedItem(), empId);
                    return null;
                }
                @Override protected void done() {
                    unsavedBadge.setVisible(false);
                    JOptionPane.showMessageDialog(EditItemPanel.this, "Item updated.", "Saved", JOptionPane.INFORMATION_MESSAGE);
                    Home.navigate(Home.CARD_STOCK);
                }
            }.execute();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid price value.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private CardPanel sectionCard(String title) {
        CardPanel c = new CardPanel(new BorderLayout(0, 10));
        ((JPanel)c).setBorder(new EmptyBorder(14, 14, 14, 14));
        ((JPanel)c).setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        ((JPanel)c).setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel t = new JLabel(title);
        t.setFont(t.getFont().deriveFont(Font.BOLD, 11f));
        t.setForeground(TEXT2);
        ((JPanel)c).add(t, BorderLayout.NORTH);
        return c;
    }

    private JTextField tf() { 
        JTextField tf =  new JTextField();
        tf.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                SwingUtilities.invokeLater(()-> {
                tf.selectAll();
                });
            }
            
        });
        return tf; 
    }

    private void markOnChange(JTextField f) {
        f.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { unsavedBadge.setVisible(true); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { unsavedBadge.setVisible(true); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) {}
        });
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

    private JSpinner spinner(int val) {
        JSpinner sp = new JSpinner(new SpinnerNumberModel(val, 0, 999999, 1));
        sp.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                SwingUtilities.invokeLater(() -> {
                    
                });
            }
            
        });
        return sp;
    }

    private String getComboText(JComboBox<String> combo) {
        Object val = combo.getEditor().getItem();
        return val != null ? val.toString().trim() : "";
    }

    private static String fmtQty(double q) {
        return q == Math.floor(q)
            ? String.valueOf((long) q)
            : String.format("%.4f", q).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
