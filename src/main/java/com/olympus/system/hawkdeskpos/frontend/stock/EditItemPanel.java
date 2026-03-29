package com.olympus.system.hawkdeskpos.frontend.stock;

import com.olympus.system.hawkdeskpos.dto.ItemDto;
import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.service.CategoryService;
import com.olympus.system.hawkdeskpos.service.ItemService;
import com.olympus.system.hawkdeskpos.session.SessionContext;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Edit Item screen.
 * Same layout as AddItemPanel; SKU and stock qty are read-only.
 */
public class EditItemPanel extends JPanel {

    private static final Color BG   = new Color(0xF0, 0xF2, 0xF5);
    private static final Color NAVY = new Color(0x1E, 0x3A, 0x5F);

    private final ItemService     itemService;
    private final CategoryService categoryService;

    private int currentItemId;
    private JTextField nameField, skuField, unitField, priceField, costField;
    private JSpinner   minLevel, maxLevel;
    private JComboBox<String> statusCombo;
    private JLabel     qtyDisplay;
    private JLabel     unsavedBadge;

    public EditItemPanel(ItemService itemService, CategoryService categoryService) {
        this.itemService     = itemService;
        this.categoryService = categoryService;
        setBackground(BG);
        setLayout(new BorderLayout());
        buildUI();
    }

    public void loadItem(int itemId) {
        this.currentItemId = itemId;
        new SwingWorker<ItemDto, Void>() {
            @Override protected ItemDto doInBackground() { return itemService.findById(itemId); }
            @Override protected void done() {
                try {
                    ItemDto d = get();
                    if (d == null) return;
                    nameField.setText(d.itemName());
                    skuField.setText(d.sku());
                    unitField.setText(d.unit());
                    priceField.setText(String.valueOf(d.sellingPrice()));
                    costField.setText(String.valueOf(d.costPrice()));
                    minLevel.setValue(d.minLevel());
                    maxLevel.setValue(d.maxLevel());
                    statusCombo.setSelectedItem(d.stat());
                    qtyDisplay.setText(String.valueOf(d.currentQty()) + "  (read-only — use Receive Stock to add)");
                    unsavedBadge.setVisible(false);
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(14, 0));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Edit Item");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        header.add(title, BorderLayout.WEST);
        unsavedBadge = new JLabel("Unsaved changes");
        unsavedBadge.setFont(unsavedBadge.getFont().deriveFont(11f));
        unsavedBadge.setForeground(new Color(0xE6, 0x51, 0x00));
        unsavedBadge.setBorder(new EmptyBorder(0, 10, 0, 0));
        unsavedBadge.setVisible(false);
        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        titleRow.setOpaque(false);
        titleRow.add(title);
        titleRow.add(unsavedBadge);
        header.add(titleRow, BorderLayout.WEST);
        JButton back = new JButton("← Back");
        back.addActionListener(e -> Home.navigate(Home.CARD_STOCK));
        JButton revertBtn = new JButton("↺ Revert");
        revertBtn.addActionListener(e -> { if (currentItemId > 0) loadItem(currentItemId); });
        JPanel hBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        hBtns.setOpaque(false);
        hBtns.add(revertBtn);
        hBtns.add(back);
        header.add(hBtns, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        // Form
        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        form.add(buildBasicSection());
        form.add(Box.createVerticalStrut(12));
        form.add(buildPricingSection());
        form.add(Box.createVerticalStrut(12));
        form.add(buildStockSection());
        form.add(Box.createVerticalStrut(16));
        form.add(buildActionRow());

        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        root.add(scroll, BorderLayout.CENTER);
        add(root);
    }

    private CardPanel buildBasicSection() {
        CardPanel c = sectionCard("BASIC INFORMATION");
        JPanel grid = new JPanel(new GridLayout(2, 3, 10, 10));
        grid.setOpaque(false);

        nameField   = tf(); markOnChange(nameField);
        skuField    = tf();
        skuField.setEditable(false);
        skuField.setBackground(new Color(0xF7, 0xF8, 0xFA));
        skuField.setToolTipText("SKU cannot be changed — it is referenced in all sales history");

        unitField   = tf(); markOnChange(unitField);
        statusCombo = new JComboBox<>(new String[]{"Active","Inactive"});
        statusCombo.addActionListener(e -> unsavedBadge.setVisible(true));

        grid.add(labeled("Item Name *",   nameField));
        grid.add(labeled("SKU (read-only)", skuField));
        grid.add(labeled("Unit",          unitField));
        grid.add(labeled("Status",        statusCombo));
        ((JPanel)c).add(grid, BorderLayout.CENTER);
        return c;
    }

    private CardPanel buildPricingSection() {
        CardPanel c = sectionCard("PRICING");
        JPanel grid = new JPanel(new GridLayout(1, 2, 10, 0));
        grid.setOpaque(false);
        priceField = tf(); markOnChange(priceField);
        costField  = tf(); markOnChange(costField);
        grid.add(labeled("Selling Price (Rs.) *", priceField));
        grid.add(labeled("Cost Price (Rs.) *",    costField));
        ((JPanel)c).add(grid, BorderLayout.CENTER);
        return c;
    }

    private CardPanel buildStockSection() {
        CardPanel c = sectionCard("STOCK SETTINGS");
        JPanel grid = new JPanel(new GridLayout(1, 3, 10, 0));
        grid.setOpaque(false);

        qtyDisplay = new JLabel("—");
        qtyDisplay.setToolTipText("Use Receive Stock to add qty");
        JPanel qtyPanel = new JPanel(new BorderLayout());
        qtyPanel.setOpaque(false);
        qtyPanel.add(new JLabel("<html><small style='color:#5A6070'>Current Qty (read-only)</small></html>"), BorderLayout.NORTH);
        qtyPanel.add(qtyDisplay, BorderLayout.CENTER);

        minLevel = spinner(5); minLevel.addChangeListener(e -> unsavedBadge.setVisible(true));
        maxLevel = spinner(100); maxLevel.addChangeListener(e -> unsavedBadge.setVisible(true));

        grid.add(qtyPanel);
        grid.add(labeled("Minimum Level *", minLevel));
        grid.add(labeled("Maximum Level",   maxLevel));
        ((JPanel)c).add(grid, BorderLayout.CENTER);
        return c;
    }

    private JPanel buildActionRow() {
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

    private void saveChanges() {
        try {
            double price = Double.parseDouble(priceField.getText().trim());
            double cost  = Double.parseDouble(costField.getText().trim());
            Long empId = SessionContext.current() != null ? SessionContext.current().getEmployee().id() : null;
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() {
                    itemService.updateItem(currentItemId, nameField.getText().trim(),
                            null, null, unitField.getText().trim(), price,
                            (int) minLevel.getValue(), (int) maxLevel.getValue(),
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
        ((JPanel)c).setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
        ((JPanel)c).setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel t = new JLabel(title);
        t.setFont(t.getFont().deriveFont(Font.BOLD, 11f));
        t.setForeground(new Color(0x5A, 0x60, 0x70));
        ((JPanel)c).add(t, BorderLayout.NORTH);
        return c;
    }

    private JTextField tf() { return new JTextField(); }

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
        l.setForeground(new Color(0x5A, 0x60, 0x70));
        p.add(l, BorderLayout.NORTH);
        p.add(comp, BorderLayout.CENTER);
        return p;
    }

    private JSpinner spinner(int val) {
        return new JSpinner(new SpinnerNumberModel(val, 0, 999999, 1));
    }
}
