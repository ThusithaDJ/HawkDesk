package com.olympus.system.hawkdeskpos.frontend.stock;

import com.olympus.system.hawkdeskpos.dto.AdjustmentDto;
import com.olympus.system.hawkdeskpos.dto.ItemDto;
import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.frontend.components.ConfirmDialog;
import com.olympus.system.hawkdeskpos.frontend.components.SearchDropdown;
import com.olympus.system.hawkdeskpos.service.ItemService;
import com.olympus.system.hawkdeskpos.service.StockService;
import com.olympus.system.hawkdeskpos.session.SessionContext;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Stock Adjustment screen.
 * Type selector (Add/Remove/Set/Write-off) + item search + before→after preview.
 */
public class StockAdjustmentPanel extends JPanel {

    private static final Color BG    = new Color(0xF0, 0xF2, 0xF5);
    private static final Color TEXT2 = new Color(0x5A, 0x60, 0x70);
    private static final Color NAVY  = new Color(0x1E, 0x3A, 0x5F);
    private static final Color GREEN = new Color(0x2E, 0x7D, 0x32);
    private static final Color RED   = new Color(0xC6, 0x28, 0x28);
    private static final Color BLUE  = new Color(0x18, 0x5F, 0xA5);
    private static final Color ORANGE= new Color(0xE6, 0x51, 0x00);

    private final ItemService  itemService;
    private final StockService stockService;

    // State
    private String selectedType = "ADD";
    private ItemDto selectedItem = null;

    // UI
    private JButton btnAdd, btnRemove, btnSet, btnWriteoff;
    private JLabel selectedItemLabel, currentQtyLabel, afterQtyLabel;
    private JSpinner qtySpinner;
    private JComboBox<String> reasonCombo;
    private JTextArea notesArea;
    private JPanel previewPanel;

    public StockAdjustmentPanel(ItemService itemService, StockService stockService) {
        this.itemService  = itemService;
        this.stockService = stockService;
        setBackground(BG);
        setLayout(new BorderLayout());
        buildUI();
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(14, 0));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Stock Adjustment");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        header.add(title, BorderLayout.WEST);
        JButton back = new JButton("← Back");
        back.addActionListener(e -> Home.navigate(Home.CARD_STOCK));
        JButton clearBtn = new JButton("↺ Clear");
        clearBtn.addActionListener(e -> resetForm());
        JPanel hBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        hBtns.setOpaque(false);
        hBtns.add(clearBtn);
        hBtns.add(back);
        header.add(hBtns, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        // Content
        JPanel content = new JPanel(new BorderLayout(14, 0));
        content.setOpaque(false);

        // Left form
        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.add(buildTypeSection());
        left.add(Box.createVerticalStrut(12));
        left.add(buildItemSection());
        left.add(Box.createVerticalStrut(12));
        left.add(buildDetailsSection());
        left.add(Box.createVerticalStrut(16));
        left.add(buildActionRow());

        JScrollPane scroll = new JScrollPane(left);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        content.add(scroll, BorderLayout.CENTER);

        // Right preview
        JPanel right = buildPreviewPanel();
        right.setPreferredSize(new Dimension(240, 0));
        content.add(right, BorderLayout.EAST);

        root.add(content, BorderLayout.CENTER);
        add(root);

        selectType("ADD");
    }

    private CardPanel buildTypeSection() {
        CardPanel c = sectionCard("ADJUSTMENT TYPE");
        JPanel grid = new JPanel(new GridLayout(1, 4, 10, 0));
        grid.setOpaque(false);

        btnAdd      = typeTile("Add Stock",   "+",  GREEN,  "ADD");
        btnRemove   = typeTile("Remove Stock","-",  RED,    "REMOVE");
        btnSet      = typeTile("Set Qty",     "=",  BLUE,   "SET");
        btnWriteoff = typeTile("Write-off",   "✗",  ORANGE, "WRITEOFF");

        grid.add(btnAdd);
        grid.add(btnRemove);
        grid.add(btnSet);
        grid.add(btnWriteoff);
        ((JPanel)c).add(grid, BorderLayout.CENTER);
        return c;
    }

    private JButton typeTile(String label, String icon, Color color, String type) {
        JButton btn = new JButton("<html><center><span style='font-size:20px'>" + icon +
                "</span><br><b>" + label + "</b></center></html>");
        btn.setFocusPainted(false);
        btn.setBorderPainted(true);
        btn.setOpaque(true);
        btn.setBackground(Color.WHITE);
        btn.setForeground(color);
        btn.setPreferredSize(new Dimension(0, 80));
        btn.addActionListener(e -> selectType(type));
        return btn;
    }

    private void selectType(String type) {
        selectedType = type;
        // Reset borders
        for (JButton b : new JButton[]{btnAdd, btnRemove, btnSet, btnWriteoff}) {
            b.setBackground(Color.WHITE);
            b.setBorder(BorderFactory.createLineBorder(new Color(0xE2, 0xE5, 0xEA), 2));
        }
        // Highlight selected
        JButton sel = switch (type) {
            case "REMOVE"   -> btnRemove;
            case "SET"      -> btnSet;
            case "WRITEOFF" -> btnWriteoff;
            default         -> btnAdd;
        };
        Color selColor = switch (type) {
            case "REMOVE"   -> RED;
            case "SET"      -> BLUE;
            case "WRITEOFF" -> ORANGE;
            default         -> GREEN;
        };
        sel.setBackground(new Color(selColor.getRed(), selColor.getGreen(), selColor.getBlue(), 30));
        sel.setBorder(BorderFactory.createLineBorder(selColor, 2));

        // Update reason options
        String[] reasons = switch (type) {
            case "ADD"      -> new String[]{"Purchase", "Return to stock", "Count correction", "Other"};
            case "REMOVE"   -> new String[]{"Damaged", "Theft", "Expired", "Given free", "Count correction", "Other"};
            case "SET"      -> new String[]{"Physical count", "System correction", "Other"};
            case "WRITEOFF" -> new String[]{"Expired", "Damaged beyond use", "Lost", "Other"};
            default         -> new String[]{"Other"};
        };
        if (reasonCombo != null) {
            reasonCombo.setModel(new DefaultComboBoxModel<>(reasons));
        }
        updatePreview();
    }

    private CardPanel buildItemSection() {
        CardPanel c = sectionCard("ITEM SELECTION");

        selectedItemLabel = new JLabel("No item selected — search above");
        selectedItemLabel.setForeground(TEXT2);

        SearchDropdown search = new SearchDropdown(itemService::search, item -> {
            selectedItem = item;
            selectedItemLabel.setText(item.itemName() + "  [" + item.sku() + "]");
            currentQtyLabel.setText(String.valueOf(item.currentQty()));
            updatePreview();
        });
        search.setAlignmentX(Component.LEFT_ALIGNMENT);
        search.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        JPanel inner = new JPanel();
        inner.setOpaque(false);
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.add(search);
        inner.add(Box.createVerticalStrut(8));
        inner.add(selectedItemLabel);

        ((JPanel)c).add(inner, BorderLayout.CENTER);
        return c;
    }

    private CardPanel buildDetailsSection() {
        CardPanel c = sectionCard("ADJUSTMENT DETAILS");
        JPanel grid = new JPanel(new GridLayout(2, 2, 10, 10));
        grid.setOpaque(false);

        qtySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 999999, 1));
        qtySpinner.addChangeListener(e -> updatePreview());

        reasonCombo = new JComboBox<>(new String[]{"Purchase", "Return to stock", "Count correction", "Other"});

        notesArea = new JTextArea(3, 20);
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        JScrollPane noteScroll = new JScrollPane(notesArea);
        noteScroll.setBorder(BorderFactory.createLineBorder(new Color(0xC8, 0xCD, 0xD6)));

        grid.add(labeled("Quantity *",  qtySpinner));
        grid.add(labeled("Reason *",    reasonCombo));
        grid.add(labeled("Notes (span 2)", noteScroll));

        ((JPanel)c).add(grid, BorderLayout.CENTER);
        return c;
    }

    private JPanel buildPreviewPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        CardPanel c = new CardPanel(new BorderLayout(0, 10));
        ((JPanel)c).setBorder(new EmptyBorder(14, 14, 14, 14));

        JLabel title = new JLabel("STOCK PREVIEW");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 11f));
        title.setForeground(TEXT2);
        ((JPanel)c).add(title, BorderLayout.NORTH);

        JPanel stats = new JPanel(new GridLayout(3, 1, 0, 16));
        stats.setOpaque(false);

        currentQtyLabel = new JLabel("—");
        currentQtyLabel.setFont(currentQtyLabel.getFont().deriveFont(Font.BOLD, 20f));

        afterQtyLabel = new JLabel("—");
        afterQtyLabel.setFont(afterQtyLabel.getFont().deriveFont(Font.BOLD, 20f));
        afterQtyLabel.setForeground(NAVY);

        JLabel arrow = new JLabel("→");
        arrow.setFont(arrow.getFont().deriveFont(24f));
        arrow.setForeground(TEXT2);
        arrow.setHorizontalAlignment(SwingConstants.CENTER);

        stats.add(statRow("Current Qty", currentQtyLabel));
        stats.add(arrow);
        stats.add(statRow("After Adjustment", afterQtyLabel));

        ((JPanel)c).add(stats, BorderLayout.CENTER);
        panel.add(c);
        previewPanel = panel;
        return panel;
    }

    private void updatePreview() {
        if (selectedItem == null || qtySpinner == null) return;
        int current = (int) selectedItem.currentQty();
        int qty     = ((Number) qtySpinner.getValue()).intValue();
        int after   = switch (selectedType) {
            case "REMOVE"   -> Math.max(0, current - qty);
            case "SET"      -> qty;
            case "WRITEOFF" -> Math.max(0, current - qty);
            default         -> current + qty;  // ADD
        };
        currentQtyLabel.setText(String.valueOf(current));
        afterQtyLabel.setText(String.valueOf(after));
        afterQtyLabel.setForeground(after < current ? RED : (after > current ? GREEN : TEXT2));
    }

    private JPanel buildActionRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        row.setOpaque(false);
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> Home.navigate(Home.CARD_STOCK));
        JButton apply = new JButton("Apply Adjustment");
        apply.setBackground(NAVY);
        apply.setForeground(Color.WHITE);
        apply.setOpaque(true);
        apply.setBorderPainted(false);
        apply.addActionListener(e -> applyAdjustment());
        row.add(cancel);
        row.add(apply);
        return row;
    }

    private void resetForm() {
        selectedItem = null;
        selectedItemLabel.setText("No item selected — search above");
        currentQtyLabel.setText("—");
        afterQtyLabel.setText("—");
        if (qtySpinner != null) qtySpinner.setValue(1);
        if (notesArea  != null) notesArea.setText("");
        selectType("ADD");
    }

    private void applyAdjustment() {
        if (selectedItem == null) {
            JOptionPane.showMessageDialog(this, "Please select an item.", "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int qty = ((Number) qtySpinner.getValue()).intValue();
        String reason = (String) reasonCombo.getSelectedItem();
        String notes  = notesArea.getText().trim();

        String confirmMsg = String.format(
                "Apply %s adjustment of %d unit(s) to '%s'?\nReason: %s",
                selectedType, qty, selectedItem.itemName(), reason);

        if (!ConfirmDialog.show(this, "Confirm Adjustment", confirmMsg, "Apply")) return;

        Long empId = SessionContext.current() != null ? SessionContext.current().getEmployee().id() : null;
        int current = (int) selectedItem.currentQty();
        int after = switch (selectedType) {
            case "REMOVE", "WRITEOFF" -> Math.max(0, current - qty);
            case "SET"                -> qty;
            default                   -> current + qty;
        };
        AdjustmentDto dto = new AdjustmentDto(selectedItem.itemId(), selectedType, current, qty, after, reason, notes, 0.0);

        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                stockService.adjustStock(dto, empId);
                return null;
            }
            @Override protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(StockAdjustmentPanel.this,
                            "Stock adjusted successfully.", "Done", JOptionPane.INFORMATION_MESSAGE);
                    selectedItem = null;
                    selectedItemLabel.setText("No item selected — search above");
                    currentQtyLabel.setText("—");
                    afterQtyLabel.setText("—");
                    qtySpinner.setValue(1);
                    notesArea.setText("");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(StockAdjustmentPanel.this,
                            "Failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

    private JPanel statRow(String label, JLabel value) {
        JPanel p = new JPanel(new GridLayout(2, 1, 0, 2));
        p.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setFont(l.getFont().deriveFont(11f));
        l.setForeground(TEXT2);
        p.add(l);
        p.add(value);
        return p;
    }
}
