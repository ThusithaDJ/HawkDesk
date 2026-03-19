package com.olympus.system.hawkdeskpos.frontend.stock;

import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.service.CategoryService;
import com.olympus.system.hawkdeskpos.service.ItemService;
import com.olympus.system.hawkdeskpos.session.SessionContext;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
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
        grid.add(labeled("Category *", catCombo));
        grid.add(labeled("Brand", brandCombo));
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
            java.util.List<String> cats, brands;
            @Override protected Void doInBackground() {
                cats   = categoryService.listCategories().stream().map(c -> c.getCategoryName()).collect(Collectors.toList());
                brands = categoryService.listBrands().stream().map(b -> b.getBrandName()).collect(Collectors.toList());
                return null;
            }
            @Override protected void done() {
                catCombo.removeAllItems();
                cats.forEach(catCombo::addItem);
                brandCombo.removeAllItems();
                brandCombo.addItem("");
                brands.forEach(brandCombo::addItem);
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

    private void saveItem() {
        // Validate
        if (nameField.getText().trim().isEmpty()) { warn("Item name is required."); return; }
        if (skuField.getText().trim().isEmpty())  { warn("SKU is required."); return; }
        double cost = 0, price = 0;
        try { cost = Double.parseDouble(costField.getText().trim()); } catch (Exception e) { warn("Invalid cost price."); return; }
        try { price = Double.parseDouble(priceField.getText().trim()); } catch (Exception e) { warn("Invalid selling price."); return; }

        // Check SKU uniqueness
        if (!itemService.isSkuUnique(skuField.getText().trim(), null)) {
            warn("SKU already exists. Please use a different SKU."); return;
        }

        Integer catId   = null;
        Integer brandId = null;
        // (would look up IDs from names in a real scenario — simplified here)

        final double fc = cost, fp = price;
        new SwingWorker<Integer, Void>() {
            @Override protected Integer doInBackground() {
                Long empId = SessionContext.current() != null ? SessionContext.current().getEmployee().id() : null;
                return itemService.createItem(
                        nameField.getText().trim(), skuField.getText().trim(),
                        catId, brandId, unitField.getText().trim(),
                        fc, fp,
                        (int) openingQtySpinner.getValue(),
                        (int) minLevelSpinner.getValue(),
                        (int) maxLevelSpinner.getValue(),
                        empId);
            }
            @Override protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(AddItemPanel.this, "Item saved successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                    Home.navigate(Home.CARD_STOCK);
                } catch (Exception ex) {
                    warn("Failed to save: " + ex.getMessage());
                }
            }
        }.execute();
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
