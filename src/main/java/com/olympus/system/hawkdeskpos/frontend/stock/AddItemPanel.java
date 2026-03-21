package com.olympus.system.hawkdeskpos.frontend.stock;

import com.olympus.system.hawkdeskpos.db.dao.Brands;
import com.olympus.system.hawkdeskpos.db.dao.Category;
import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.admin.ManageCategoriesPanel;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.service.CategoryService;
import com.olympus.system.hawkdeskpos.service.ItemService;
import com.olympus.system.hawkdeskpos.session.SessionContext;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Add New Item screen.
 * Three sections: Basic info, Pricing, Stock settings.
 * Right sidebar: live profit margin + search preview.
 */
public class AddItemPanel extends JPanel {

    private static final Color BG   = new Color(0xF0, 0xF2, 0xF5);
    private static final Color NAVY = new Color(0x1E, 0x3A, 0x5F);

    private final ItemService     itemService;
    private final CategoryService categoryService;

    // Form fields
    private JTextField nameField, skuField, unitField;
    private JComboBox<String> catCombo, brandCombo;
    private JComboBox<String> statusCombo;
    private JTextField costField, priceField, taxField;
    private JSpinner   openingQtySpinner, minLevelSpinner, maxLevelSpinner;

    // Category/brand name-to-id maps (populated with list data)
    private List<Category> catList;
    private List<Brands>   brandList;

    // Sidebar
    private JProgressBar marginBar;
    private JLabel marginLabel;
    private JLabel previewName, previewPrice, previewStatus;

    public AddItemPanel(ItemService itemService, CategoryService categoryService) {
        this.itemService     = itemService;
        this.categoryService = categoryService;
        setBackground(BG);
        setLayout(new BorderLayout(0, 0));
        buildUI();
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(14, 0));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        // ── Page header ───────────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Add New Item");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        header.add(title, BorderLayout.WEST);
        JButton back = new JButton("← Back");
        back.addActionListener(e -> Home.navigate(Home.CARD_STOCK));
        JPanel hBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        hBtns.setOpaque(false);
        hBtns.add(back);
        header.add(hBtns, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        // ── Main form + sidebar ───────────────────────────────────────────────
        JPanel content = new JPanel(new BorderLayout(14, 0));
        content.setOpaque(false);

        // Form
        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(buildSection1());
        form.add(Box.createVerticalStrut(12));
        form.add(buildSection2());
        form.add(Box.createVerticalStrut(12));
        form.add(buildSection3());
        form.add(Box.createVerticalStrut(16));
        form.add(buildSaveRow());

        JScrollPane formScroll = new JScrollPane(form);
        formScroll.setBorder(null);
        formScroll.setOpaque(false);
        formScroll.getViewport().setOpaque(false);
        content.add(formScroll, BorderLayout.CENTER);

        // Sidebar
        JPanel sidebar = buildSidebar();
        sidebar.setPreferredSize(new Dimension(240, 0));
        content.add(sidebar, BorderLayout.EAST);

        root.add(content, BorderLayout.CENTER);
        add(root);

        // Populate combos
        loadCombosAsync();
        generateSku();
    }

    private CardPanel buildSection1() {
        CardPanel c = section("1 — BASIC INFORMATION");
        JPanel grid = new JPanel(new GridLayout(3, 2, 10, 10));
        grid.setOpaque(false);

        nameField   = field();
        skuField    = field();
        catCombo    = new JComboBox<>();
        brandCombo  = new JComboBox<>();
        unitField   = field();
        unitField.setText("pcs");
        statusCombo = new JComboBox<>(new String[]{"Active","Inactive"});

        grid.add(labeled("Item Name *", nameField));
        grid.add(labeled("SKU *", skuField));
        grid.add(labeledWithAdd("Category *", catCombo, this::quickAddCategory));
        grid.add(labeledWithAdd("Brand", brandCombo, this::quickAddBrand));
        grid.add(labeled("Unit of measure", unitField));
        grid.add(labeled("Status", statusCombo));

        ((JPanel)c).add(grid, BorderLayout.CENTER);

        // Live updates
        nameField.getDocument().addDocumentListener(docListener(() -> updatePreview()));
        costField = field(); priceField = field(); // init early for margin listener
        return c;
    }

    private CardPanel buildSection2() {
        CardPanel c = section("2 — PRICING");
        JPanel grid = new JPanel(new GridLayout(1, 3, 10, 0));
        grid.setOpaque(false);

        if (costField == null)  costField  = field();
        if (priceField == null) priceField = field();
        taxField = field();
        taxField.setText("0");

        costField.getDocument().addDocumentListener(docListener(() -> updateMargin()));
        priceField.getDocument().addDocumentListener(docListener(() -> { updateMargin(); updatePreview(); }));

        grid.add(labeled("Cost Price (Rs.) *", costField));
        grid.add(labeled("Selling Price (Rs.) *", priceField));
        grid.add(labeled("Tax Rate (%)", taxField));
        ((JPanel)c).add(grid, BorderLayout.CENTER);
        return c;
    }

    private CardPanel buildSection3() {
        CardPanel c = section("3 — STOCK SETTINGS");
        JPanel grid = new JPanel(new GridLayout(1, 3, 10, 0));
        grid.setOpaque(false);

        openingQtySpinner = spinner(0, 0, 999999);
        minLevelSpinner   = spinner(5, 0, 999999);
        maxLevelSpinner   = spinner(100, 0, 999999);

        grid.add(labeled("Opening Quantity",  openingQtySpinner));
        grid.add(labeled("Minimum Level *",   minLevelSpinner));
        grid.add(labeled("Maximum Level",     maxLevelSpinner));
        ((JPanel)c).add(grid, BorderLayout.CENTER);
        return c;
    }

    private JPanel buildSaveRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        row.setOpaque(false);
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> Home.navigate(Home.CARD_STOCK));
        JButton save = new JButton("Save Item");
        save.setBackground(NAVY);
        save.setForeground(Color.WHITE);
        save.setOpaque(true);
        save.setBorderPainted(false);
        save.addActionListener(e -> saveItem());
        row.add(cancel);
        row.add(save);
        return row;
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setOpaque(false);
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));

        // Margin card
        CardPanel marginCard = new CardPanel(new BorderLayout(0, 10));
        ((JPanel)marginCard).setBorder(new EmptyBorder(14, 14, 14, 14));

        JLabel mt = new JLabel("PROFIT MARGIN");
        mt.setFont(mt.getFont().deriveFont(Font.BOLD, 11f));
        mt.setForeground(new Color(0x5A, 0x60, 0x70));
        ((JPanel)marginCard).add(mt, BorderLayout.NORTH);

        marginBar = new JProgressBar(0, 100);
        marginBar.setValue(0);
        marginBar.setStringPainted(false);
        marginBar.setPreferredSize(new Dimension(0, 12));

        marginLabel = new JLabel("0 %");
        marginLabel.setFont(marginLabel.getFont().deriveFont(Font.BOLD, 18f));

        JPanel mg = new JPanel(new BorderLayout(0, 6));
        mg.setOpaque(false);
        mg.add(marginLabel, BorderLayout.NORTH);
        mg.add(marginBar,   BorderLayout.CENTER);
        ((JPanel)marginCard).add(mg, BorderLayout.CENTER);
        sidebar.add(marginCard);
        sidebar.add(Box.createVerticalStrut(10));

        // Preview card
        CardPanel previewCard = new CardPanel(new BorderLayout(0, 6));
        ((JPanel)previewCard).setBorder(new EmptyBorder(14, 14, 14, 14));

        JLabel pt = new JLabel("SALE SEARCH PREVIEW");
        pt.setFont(pt.getFont().deriveFont(Font.BOLD, 11f));
        pt.setForeground(new Color(0x5A, 0x60, 0x70));
        ((JPanel)previewCard).add(pt, BorderLayout.NORTH);

        JPanel preview = new JPanel(new GridLayout(3, 1, 0, 4));
        preview.setOpaque(false);
        previewName   = new JLabel("Item name will appear here");
        previewName.setFont(previewName.getFont().deriveFont(Font.BOLD, 13f));
        previewPrice  = new JLabel("Rs. 0.00");
        previewPrice.setFont(previewPrice.getFont().deriveFont(Font.BOLD, 12f));
        previewPrice.setForeground(NAVY);
        previewStatus = new JLabel("OK");
        previewStatus.setFont(previewStatus.getFont().deriveFont(11f));
        preview.add(previewName);
        preview.add(previewPrice);
        preview.add(previewStatus);
        ((JPanel)previewCard).add(preview, BorderLayout.CENTER);
        sidebar.add(previewCard);

        return sidebar;
    }

    // ── Async helpers ─────────────────────────────────────────────────────────

    private void loadCombosAsync() {
        new SwingWorker<Void, Void>() {
            List<Category> cats;
            List<Brands>   brands;
            @Override protected Void doInBackground() {
                cats   = categoryService.listCategories();
                brands = categoryService.listBrands();
                return null;
            }
            @Override protected void done() {
                catList   = cats;
                brandList = brands;
                String selectedCat   = (String) catCombo.getSelectedItem();
                String selectedBrand = (String) brandCombo.getSelectedItem();
                catCombo.removeAllItems();
                cats.forEach(c -> catCombo.addItem(c.getCategoryName()));
                if (selectedCat != null) catCombo.setSelectedItem(selectedCat);
                brandCombo.removeAllItems();
                brandCombo.addItem("");
                brands.forEach(b -> brandCombo.addItem(b.getBrandName()));
                if (selectedBrand != null) brandCombo.setSelectedItem(selectedBrand);
            }
        }.execute();
    }

    private void generateSku() {
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() { return itemService.generateSku(); }
            @Override protected void done() {
                try { skuField.setText(get()); } catch (Exception ignored) {}
            }
        }.execute();
    }

    // ── Quick-create dialogs ──────────────────────────────────────────────────

    private void quickAddCategory() {
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this), "New Category", Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setSize(360, 230);
        dlg.setLocationRelativeTo(this);
        dlg.setLayout(new BorderLayout());

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(14, 16, 14, 16));

        JLabel nameLbl = new JLabel("Category name");
        nameLbl.setFont(nameLbl.getFont().deriveFont(12f));
        nameLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(nameLbl);
        body.add(Box.createVerticalStrut(4));

        JTextField nameField = new JTextField();
        nameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        nameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(nameField);
        body.add(Box.createVerticalStrut(10));

        JLabel colLbl = new JLabel("Colour");
        colLbl.setFont(colLbl.getFont().deriveFont(12f));
        colLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(colLbl);
        body.add(Box.createVerticalStrut(4));

        final String[] selectedColour = { ManageCategoriesPanel.PALETTE[6] };
        JPanel swatchRow = ManageCategoriesPanel.buildSwatchRow(selectedColour);
        swatchRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(swatchRow);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dlg.dispose());
        JButton create = new JButton("Create");
        create.setBackground(NAVY);
        create.setForeground(Color.WHITE);
        create.setOpaque(true);
        create.setBorderPainted(false);
        create.addActionListener(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) return;
            final String finalName = name;
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() {
                    Long actor = SessionContext.current() != null ? SessionContext.current().getEmployee().id() : null;
                    categoryService.addCategory(finalName, selectedColour[0], actor);
                    return null;
                }
                @Override protected void done() {
                    dlg.dispose();
                    new SwingWorker<Void, Void>() {
                        List<Category> cats; List<Brands> brands;
                        @Override protected Void doInBackground() {
                            cats   = categoryService.listCategories();
                            brands = categoryService.listBrands();
                            return null;
                        }
                        @Override protected void done() {
                            catList   = cats;
                            brandList = brands;
                            catCombo.removeAllItems();
                            cats.forEach(c -> catCombo.addItem(c.getCategoryName()));
                            catCombo.setSelectedItem(finalName);
                        }
                    }.execute();
                }
            }.execute();
        });
        btns.add(cancel);
        btns.add(create);

        dlg.add(body, BorderLayout.CENTER);
        dlg.add(btns, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    private void quickAddBrand() {
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this), "New Brand", Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setSize(300, 150);
        dlg.setLocationRelativeTo(this);
        dlg.setLayout(new BorderLayout());

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(14, 16, 14, 16));

        JLabel nameLbl = new JLabel("Brand name");
        nameLbl.setFont(nameLbl.getFont().deriveFont(12f));
        nameLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(nameLbl);
        body.add(Box.createVerticalStrut(4));

        JTextField nameField = new JTextField();
        nameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        nameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(nameField);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dlg.dispose());
        JButton create = new JButton("Create");
        create.setBackground(NAVY);
        create.setForeground(Color.WHITE);
        create.setOpaque(true);
        create.setBorderPainted(false);
        create.addActionListener(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) return;
            final String finalName = name;
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() {
                    Long actor = SessionContext.current() != null ? SessionContext.current().getEmployee().id() : null;
                    categoryService.addBrand(finalName, actor);
                    return null;
                }
                @Override protected void done() {
                    dlg.dispose();
                    new SwingWorker<Void, Void>() {
                        List<Category> cats; List<Brands> brands;
                        @Override protected Void doInBackground() {
                            cats   = categoryService.listCategories();
                            brands = categoryService.listBrands();
                            return null;
                        }
                        @Override protected void done() {
                            catList   = cats;
                            brandList = brands;
                            brandCombo.removeAllItems();
                            brandCombo.addItem("");
                            brands.forEach(b -> brandCombo.addItem(b.getBrandName()));
                            brandCombo.setSelectedItem(finalName);
                        }
                    }.execute();
                }
            }.execute();
        });
        btns.add(cancel);
        btns.add(create);

        dlg.add(body, BorderLayout.CENTER);
        dlg.add(btns, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    // ── Margin / preview updates ──────────────────────────────────────────────

    private void updateMargin() {
        try {
            double cost  = Double.parseDouble(costField.getText().trim());
            double price = Double.parseDouble(priceField.getText().trim());
            if (price > 0) {
                double margin = (price - cost) / price * 100;
                marginLabel.setText(String.format("%.1f %%", margin));
                marginBar.setValue((int) Math.min(100, margin));
                marginBar.setForeground(margin >= 30 ? new Color(0x2E,0x7D,0x32)
                        : margin >= 15 ? new Color(0xE6,0x51,0x00)
                        : new Color(0xC6,0x28,0x28));
            }
        } catch (NumberFormatException ignored) {}
    }

    private void updatePreview() {
        previewName.setText(nameField.getText().isEmpty() ? "Item name will appear here" : nameField.getText());
        try {
            double price = Double.parseDouble(priceField.getText().trim());
            previewPrice.setText(String.format("Rs. %.2f", price));
        } catch (NumberFormatException ignored) {}
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private void saveItem() {
        if (nameField.getText().trim().isEmpty()) { warn("Item name is required."); return; }
        if (skuField.getText().trim().isEmpty())  { warn("SKU is required."); return; }
        double cost = 0, price = 0;
        try { cost = Double.parseDouble(costField.getText().trim()); } catch (Exception e) { warn("Invalid cost price."); return; }
        try { price = Double.parseDouble(priceField.getText().trim()); } catch (Exception e) { warn("Invalid selling price."); return; }

        if (!itemService.isSkuUnique(skuField.getText().trim(), null)) {
            warn("SKU already exists. Please use a different SKU."); return;
        }

        // Resolve category and brand IDs from selected names
        String selectedCat   = (String) catCombo.getSelectedItem();
        String selectedBrand = (String) brandCombo.getSelectedItem();
        Integer catId   = null;
        Integer brandId = null;
        if (catList != null && selectedCat != null) {
            catId = catList.stream()
                    .filter(c -> c.getCategoryName().equals(selectedCat))
                    .map(Category::getCatId)
                    .findFirst().orElse(null);
        }
        if (brandList != null && selectedBrand != null && !selectedBrand.isEmpty()) {
            brandId = brandList.stream()
                    .filter(b -> b.getBrandName().equals(selectedBrand))
                    .map(Brands::getBrandId)
                    .findFirst().orElse(null);
        }

        final double fc = cost, fp = price;
        final Integer finalCatId = catId, finalBrandId = brandId;
        new SwingWorker<Integer, Void>() {
            @Override protected Integer doInBackground() {
                Long empId = SessionContext.current() != null ? SessionContext.current().getEmployee().id() : null;
                return itemService.createItem(
                        nameField.getText().trim(), skuField.getText().trim(),
                        finalCatId, finalBrandId, unitField.getText().trim(),
                        fc, fp,
                        (int) openingQtySpinner.getValue(),
                        (int) minLevelSpinner.getValue(),
                        (int) maxLevelSpinner.getValue(),
                        empId);
            }
            @Override protected void done() {
                try {
                    get();
                    int res = JOptionPane.showConfirmDialog(AddItemPanel.this,
                            "Item saved successfully.\n\nAdd another item?",
                            "Saved", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                    if (res == JOptionPane.YES_OPTION) {
                        clearForm();
                    } else {
                        Home.navigate(Home.CARD_STOCK);
                    }
                } catch (Exception ex) {
                    warn("Failed to save: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void clearForm() {
        nameField.setText("");
        unitField.setText("pcs");
        costField.setText("");
        priceField.setText("");
        taxField.setText("0");
        openingQtySpinner.setValue(0);
        minLevelSpinner.setValue(5);
        maxLevelSpinner.setValue(100);
        statusCombo.setSelectedIndex(0);
        if (catCombo.getItemCount() > 0)   catCombo.setSelectedIndex(0);
        if (brandCombo.getItemCount() > 0) brandCombo.setSelectedIndex(0);
        marginBar.setValue(0);
        marginLabel.setText("0 %");
        updatePreview();
        generateSku();          // fresh SKU for the new item
        nameField.requestFocusInWindow();
    }

    private void warn(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Validation Error", JOptionPane.WARNING_MESSAGE);
    }

    // ── UI factory helpers ────────────────────────────────────────────────────

    private CardPanel section(String title) {
        CardPanel c = new CardPanel(new BorderLayout(0, 10));
        ((JPanel)c).setBorder(new EmptyBorder(14, 14, 14, 14));
        ((JPanel)c).setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        ((JPanel)c).setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel t = new JLabel(title);
        t.setFont(t.getFont().deriveFont(Font.BOLD, 11f));
        t.setForeground(new Color(0x5A, 0x60, 0x70));
        ((JPanel)c).add(t, BorderLayout.NORTH);
        return c;
    }

    private JTextField field() { return new JTextField(); }

    private JPanel labeled(String lbl, JComponent comp) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        JLabel l = new JLabel(lbl);
        l.setFont(l.getFont().deriveFont(12f));
        l.setForeground(new Color(0x5A, 0x60, 0x70));
        p.add(l, BorderLayout.NORTH);
        p.add(comp, BorderLayout.CENTER);
        return p;
    }

    /** Wraps a combo box with a "+" button on the right. */
    private JPanel labeledWithAdd(String lbl, JComboBox<String> combo, Runnable onAdd) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        JLabel l = new JLabel(lbl);
        l.setFont(l.getFont().deriveFont(12f));
        l.setForeground(new Color(0x5A, 0x60, 0x70));
        p.add(l, BorderLayout.NORTH);

        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setOpaque(false);
        row.add(combo, BorderLayout.CENTER);
        JButton addBtn = new JButton("+");
        addBtn.setFont(addBtn.getFont().deriveFont(Font.BOLD, 14f));
        addBtn.setForeground(NAVY);
        addBtn.setPreferredSize(new Dimension(30, 0));
        addBtn.setToolTipText("Add new " + lbl.replace(" *", "").toLowerCase());
        addBtn.addActionListener(e -> onAdd.run());
        row.add(addBtn, BorderLayout.EAST);
        p.add(row, BorderLayout.CENTER);
        return p;
    }

    private JSpinner spinner(int val, int min, int max) {
        return new JSpinner(new SpinnerNumberModel(val, min, max, 1));
    }

    private javax.swing.event.DocumentListener docListener(Runnable r) {
        return new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) {}
        };
    }
}
