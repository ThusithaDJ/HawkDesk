package com.olympus.system.hawkdeskpos.frontend.admin;

import com.olympus.system.hawkdeskpos.db.dao.Brands;
import com.olympus.system.hawkdeskpos.db.dao.Category;
import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.frontend.components.Refreshable;
import com.olympus.system.hawkdeskpos.service.CategoryService;
import com.olympus.system.hawkdeskpos.session.SessionContext;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * Manage Categories & Brands screen — inline editing with colour picker.
 */
public class ManageCategoriesPanel extends JPanel implements Refreshable {

    // 10-swatch colour palette
    public static final String[] PALETTE = {
        "#FFFFFF","#94A3B8","#FCA5A5","#F9A8D4","#86EFAC",
        "#6EE7B7","#7DD3FC","#A5B4FC","#DDD6FE","#FDE68A"
    };

    private static final Color BG    = new Color(0xF0, 0xF2, 0xF5);
    private static final Color TEXT2 = new Color(0x5A, 0x60, 0x70);
    private static final Color NAVY  = new Color(0x1E, 0x3A, 0x5F);
    private static final Color RED   = new Color(0xC6, 0x28, 0x28);

    private final CategoryService categoryService;

    private JPanel catList, brandList;
    private JLabel catCountLabel, brandCountLabel;

    public ManageCategoriesPanel(CategoryService categoryService) {
        this.categoryService = categoryService;
        setBackground(BG);
        setLayout(new BorderLayout());
        buildUI();
    }

    @Override
    public void refresh() { loadDataAsync(); }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Manage Categories & Brands");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        header.add(title, BorderLayout.WEST);
        JButton back = new JButton("← Back");
        back.addActionListener(e -> Home.navigate(Home.CARD_SETTINGS));
        JPanel hBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        hBtns.setOpaque(false);
        hBtns.add(back);
        header.add(hBtns, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        // Two columns
        JPanel columns = new JPanel(new GridLayout(1, 2, 14, 0));
        columns.setOpaque(false);
        columns.add(buildCategoryColumn());
        columns.add(buildBrandColumn());

        root.add(columns, BorderLayout.CENTER);
        add(root);
        loadDataAsync();
    }

    // ── Category column ───────────────────────────────────────────────────────

    private CardPanel buildCategoryColumn() {
        CardPanel c = new CardPanel(new BorderLayout(0, 0));
        ((JPanel)c).setBorder(new EmptyBorder(14, 14, 14, 14));

        JPanel hdr = new JPanel(new BorderLayout(8, 0));
        hdr.setOpaque(false);
        hdr.setBorder(new EmptyBorder(0, 0, 12, 0));
        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        titleRow.setOpaque(false);
        JLabel t = new JLabel("CATEGORIES");
        t.setFont(t.getFont().deriveFont(Font.BOLD, 11f));
        t.setForeground(TEXT2);
        catCountLabel = new JLabel("(0)");
        catCountLabel.setFont(catCountLabel.getFont().deriveFont(11f));
        catCountLabel.setForeground(TEXT2);
        titleRow.add(t);
        titleRow.add(catCountLabel);
        hdr.add(titleRow, BorderLayout.WEST);

        catList = new JPanel();
        catList.setOpaque(false);
        catList.setLayout(new BoxLayout(catList, BoxLayout.Y_AXIS));

        // "+ Add category" button at bottom
        JButton addBtn = new JButton("+ Add category");
        addBtn.setFont(addBtn.getFont().deriveFont(12f));
        addBtn.addActionListener(e -> showAddCategoryForm());
        JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        addRow.setOpaque(false);
        addRow.add(addBtn);

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);

        JScrollPane scroll = new JScrollPane(catList);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        body.add(scroll, BorderLayout.CENTER);
        body.add(addRow, BorderLayout.SOUTH);

        ((JPanel)c).add(hdr,  BorderLayout.NORTH);
        ((JPanel)c).add(body, BorderLayout.CENTER);
        return c;
    }

    // ── Brand column ──────────────────────────────────────────────────────────

    private CardPanel buildBrandColumn() {
        CardPanel c = new CardPanel(new BorderLayout(0, 0));
        ((JPanel)c).setBorder(new EmptyBorder(14, 14, 14, 14));

        JPanel hdr = new JPanel(new BorderLayout(8, 0));
        hdr.setOpaque(false);
        hdr.setBorder(new EmptyBorder(0, 0, 12, 0));
        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        titleRow.setOpaque(false);
        JLabel t = new JLabel("BRANDS");
        t.setFont(t.getFont().deriveFont(Font.BOLD, 11f));
        t.setForeground(TEXT2);
        brandCountLabel = new JLabel("(0)");
        brandCountLabel.setFont(brandCountLabel.getFont().deriveFont(11f));
        brandCountLabel.setForeground(TEXT2);
        titleRow.add(t);
        titleRow.add(brandCountLabel);
        hdr.add(titleRow, BorderLayout.WEST);

        brandList = new JPanel();
        brandList.setOpaque(false);
        brandList.setLayout(new BoxLayout(brandList, BoxLayout.Y_AXIS));

        JButton addBtn = new JButton("+ Add brand");
        addBtn.setFont(addBtn.getFont().deriveFont(12f));
        addBtn.addActionListener(e -> showAddBrandForm());
        JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        addRow.setOpaque(false);
        addRow.add(addBtn);

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);

        JScrollPane scroll = new JScrollPane(brandList);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        body.add(scroll, BorderLayout.CENTER);
        body.add(addRow, BorderLayout.SOUTH);

        ((JPanel)c).add(hdr,  BorderLayout.NORTH);
        ((JPanel)c).add(body, BorderLayout.CENTER);
        return c;
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private void loadDataAsync() {
        new SwingWorker<Void, Void>() {
            List<Category> cats;
            List<Brands>   brands;
            @Override protected Void doInBackground() {
                cats   = categoryService.listCategories();
                brands = categoryService.listBrands();
                return null;
            }
            @Override protected void done() {
                catList.removeAll();
                catCountLabel.setText("(" + cats.size() + ")");
                for (Category cat : cats) {
                    catList.add(buildCategoryRow(cat));
                    catList.add(Box.createVerticalStrut(2));
                }
                catList.revalidate();
                catList.repaint();

                brandList.removeAll();
                brandCountLabel.setText("(" + brands.size() + ")");
                for (Brands brand : brands) {
                    brandList.add(buildBrandRow(brand));
                    brandList.add(Box.createVerticalStrut(2));
                }
                brandList.revalidate();
                brandList.repaint();
            }
        }.execute();
    }

    // ── Category row (normal state) ───────────────────────────────────────────

    private JPanel buildCategoryRow(Category cat) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(buildCategoryNormalState(cat, wrapper), BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel buildCategoryNormalState(Category cat, JPanel wrapper) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xE8, 0xEA, 0xED)),
                new EmptyBorder(8, 4, 8, 4)));

        // Left: avatar circle + name/count
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(buildAvatarCircle(cat.getCategoryName(), cat.getColour()));

        JPanel info = new JPanel(new GridLayout(2, 1, 0, 1));
        info.setOpaque(false);
        JLabel nameLbl = new JLabel(cat.getCategoryName());
        nameLbl.setFont(nameLbl.getFont().deriveFont(Font.BOLD, 13f));
        int count = categoryService.itemCountForCategory(cat.getCatId());
        JLabel countLbl = new JLabel(count + " item" + (count == 1 ? "" : "s"));
        countLbl.setFont(countLbl.getFont().deriveFont(11f));
        countLbl.setForeground(TEXT2);
        info.add(nameLbl);
        info.add(countLbl);
        left.add(info);
        row.add(left, BorderLayout.CENTER);

        // Right: actions
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actions.setOpaque(false);
        JButton rename = linkButton("Rename", NAVY);
        rename.addActionListener(e -> {
            wrapper.removeAll();
            wrapper.add(buildCategoryEditState(cat, wrapper), BorderLayout.CENTER);
            wrapper.revalidate();
            wrapper.repaint();
        });
        JButton delete = linkButton("Delete", RED);
        delete.addActionListener(e -> deleteCategory(cat));
        actions.add(rename);
        actions.add(delete);
        row.add(actions, BorderLayout.EAST);

        return row;
    }

    private JPanel buildCategoryEditState(Category cat, JPanel wrapper) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xA5, 0xB4, 0xFC), 1),
                new EmptyBorder(10, 10, 10, 10)));

        // Name field
        JTextField nameField = new JTextField(cat.getCategoryName());
        nameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        nameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(nameField);
        panel.add(Box.createVerticalStrut(8));

        // Colour swatches
        final String[] selectedColour = { cat.getColour() != null ? cat.getColour() : PALETTE[6] };
        JPanel swatchRow = buildSwatchRow(selectedColour);
        swatchRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(swatchRow);
        panel.add(Box.createVerticalStrut(10));

        // Save / Cancel
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        btns.setOpaque(false);
        btns.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton save = new JButton("Save");
        save.setBackground(NAVY);
        save.setForeground(Color.WHITE);
        save.setOpaque(true);
        save.setBorderPainted(false);
        save.addActionListener(e -> {
            String newName = nameField.getText().trim();
            if (newName.isEmpty()) return;
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() {
                    Long actor = SessionContext.current() != null ? SessionContext.current().getEmployee().id() : null;
                    categoryService.renameCategory(cat.getCatId(), newName, selectedColour[0], actor);
                    return null;
                }
                @Override protected void done() { loadDataAsync(); }
            }.execute();
        });
        JButton cancel = linkButton("Cancel", TEXT2);
        cancel.addActionListener(e -> {
            wrapper.removeAll();
            wrapper.add(buildCategoryNormalState(cat, wrapper), BorderLayout.CENTER);
            wrapper.revalidate();
            wrapper.repaint();
        });
        btns.add(save);
        btns.add(cancel);
        panel.add(btns);

        return panel;
    }

    // ── Brand row (normal state) ──────────────────────────────────────────────

    private JPanel buildBrandRow(Brands brand) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(buildBrandNormalState(brand, wrapper), BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel buildBrandNormalState(Brands brand, JPanel wrapper) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xE8, 0xEA, 0xED)),
                new EmptyBorder(8, 4, 8, 4)));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(buildAvatarCircle(brand.getBrandName(), null));

        JPanel info = new JPanel(new GridLayout(2, 1, 0, 1));
        info.setOpaque(false);
        JLabel nameLbl = new JLabel(brand.getBrandName());
        nameLbl.setFont(nameLbl.getFont().deriveFont(Font.BOLD, 13f));
        int count = categoryService.itemCountForBrand(brand.getBrandId());
        JLabel countLbl = new JLabel(count + " item" + (count == 1 ? "" : "s"));
        countLbl.setFont(countLbl.getFont().deriveFont(11f));
        countLbl.setForeground(TEXT2);
        info.add(nameLbl);
        info.add(countLbl);
        left.add(info);
        row.add(left, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actions.setOpaque(false);
        JButton rename = linkButton("Rename", NAVY);
        rename.addActionListener(e -> {
            wrapper.removeAll();
            wrapper.add(buildBrandEditState(brand, wrapper), BorderLayout.CENTER);
            wrapper.revalidate();
            wrapper.repaint();
        });
        JButton delete = linkButton("Delete", RED);
        delete.addActionListener(e -> deleteBrand(brand));
        actions.add(rename);
        actions.add(delete);
        row.add(actions, BorderLayout.EAST);

        return row;
    }

    private JPanel buildBrandEditState(Brands brand, JPanel wrapper) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xA5, 0xB4, 0xFC), 1),
                new EmptyBorder(10, 10, 10, 10)));

        JTextField nameField = new JTextField(brand.getBrandName());
        nameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        nameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(nameField);
        panel.add(Box.createVerticalStrut(10));

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        btns.setOpaque(false);
        btns.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton save = new JButton("Save");
        save.setBackground(NAVY);
        save.setForeground(Color.WHITE);
        save.setOpaque(true);
        save.setBorderPainted(false);
        save.addActionListener(e -> {
            String newName = nameField.getText().trim();
            if (newName.isEmpty()) return;
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() {
                    Long actor = SessionContext.current() != null ? SessionContext.current().getEmployee().id() : null;
                    categoryService.renameBrand(brand.getBrandId(), newName, actor);
                    return null;
                }
                @Override protected void done() { loadDataAsync(); }
            }.execute();
        });
        JButton cancel = linkButton("Cancel", TEXT2);
        cancel.addActionListener(e -> {
            wrapper.removeAll();
            wrapper.add(buildBrandNormalState(brand, wrapper), BorderLayout.CENTER);
            wrapper.revalidate();
            wrapper.repaint();
        });
        btns.add(save);
        btns.add(cancel);
        panel.add(btns);

        return panel;
    }

    // ── Add forms ─────────────────────────────────────────────────────────────

    private void showAddCategoryForm() {
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this), "New Category", Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setSize(360, 230);
        dlg.setLocationRelativeTo(this);
        dlg.setLayout(new BorderLayout());

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(14, 16, 14, 16));

        JLabel nameLbl = new JLabel("Category name");
        nameLbl.setFont(nameLbl.getFont().deriveFont(12f));
        nameLbl.setForeground(TEXT2);
        nameLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(nameLbl);
        body.add(Box.createVerticalStrut(4));

        JTextField nameField = new JTextField();
        nameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        nameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(nameField);
        body.add(Box.createVerticalStrut(12));

        JLabel colLbl = new JLabel("Colour");
        colLbl.setFont(colLbl.getFont().deriveFont(12f));
        colLbl.setForeground(TEXT2);
        colLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(colLbl);
        body.add(Box.createVerticalStrut(4));

        final String[] selectedColour = { PALETTE[6] };
        JPanel swatchRow = buildSwatchRow(selectedColour);
        swatchRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(swatchRow);
        body.add(Box.createVerticalStrut(14));

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dlg.dispose());
        JButton create = new JButton("Add category");
        create.setBackground(NAVY);
        create.setForeground(Color.WHITE);
        create.setOpaque(true);
        create.setBorderPainted(false);
        create.addActionListener(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) return;
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() {
                    Long actor = SessionContext.current() != null ? SessionContext.current().getEmployee().id() : null;
                    categoryService.addCategory(name, selectedColour[0], actor);
                    return null;
                }
                @Override protected void done() { dlg.dispose(); loadDataAsync(); }
            }.execute();
        });
        btns.add(cancel);
        btns.add(create);

        dlg.add(body, BorderLayout.CENTER);
        dlg.add(btns, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    private void showAddBrandForm() {
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this), "New Brand", Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setSize(300, 150);
        dlg.setLocationRelativeTo(this);
        dlg.setLayout(new BorderLayout());

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(14, 16, 14, 16));

        JLabel nameLbl = new JLabel("Brand name");
        nameLbl.setFont(nameLbl.getFont().deriveFont(12f));
        nameLbl.setForeground(TEXT2);
        nameLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(nameLbl);
        body.add(Box.createVerticalStrut(4));

        JTextField nameField = new JTextField();
        nameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        nameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(nameField);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dlg.dispose());
        JButton create = new JButton("Add brand");
        create.setBackground(NAVY);
        create.setForeground(Color.WHITE);
        create.setOpaque(true);
        create.setBorderPainted(false);
        create.addActionListener(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) return;
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() {
                    Long actor = SessionContext.current() != null ? SessionContext.current().getEmployee().id() : null;
                    categoryService.addBrand(name, actor);
                    return null;
                }
                @Override protected void done() { dlg.dispose(); loadDataAsync(); }
            }.execute();
        });
        btns.add(cancel);
        btns.add(create);

        dlg.add(body, BorderLayout.CENTER);
        dlg.add(btns, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    // ── Delete actions ────────────────────────────────────────────────────────

    private void deleteCategory(Category cat) {
        int res = JOptionPane.showConfirmDialog(this,
                "Delete category '" + cat.getCategoryName() + "'?\nThis cannot be undone if no items are assigned.",
                "Delete Category", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (res != JOptionPane.YES_OPTION) return;
        new SwingWorker<Boolean, Void>() {
            @Override protected Boolean doInBackground() {
                Long actor = SessionContext.current() != null ? SessionContext.current().getEmployee().id() : null;
                return categoryService.deleteCategory(cat.getCatId(), actor);
            }
            @Override protected void done() {
                try {
                    if (!get()) JOptionPane.showMessageDialog(ManageCategoriesPanel.this,
                            "Cannot delete: items are assigned to this category.", "Delete Failed",
                            JOptionPane.WARNING_MESSAGE);
                    loadDataAsync();
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void deleteBrand(Brands brand) {
        int res = JOptionPane.showConfirmDialog(this,
                "Delete brand '" + brand.getBrandName() + "'?",
                "Delete Brand", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (res != JOptionPane.YES_OPTION) return;
        new SwingWorker<Boolean, Void>() {
            @Override protected Boolean doInBackground() {
                Long actor = SessionContext.current() != null ? SessionContext.current().getEmployee().id() : null;
                return categoryService.deleteBrand(brand.getBrandId(), actor);
            }
            @Override protected void done() {
                try {
                    if (!get()) JOptionPane.showMessageDialog(ManageCategoriesPanel.this,
                            "Cannot delete: items are assigned to this brand.", "Delete Failed",
                            JOptionPane.WARNING_MESSAGE);
                    loadDataAsync();
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    /** Builds a row of 10 colour swatches; selectedColour[0] is updated when one is clicked. */
    public static JPanel buildSwatchRow(String[] selectedColour) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setOpaque(false);
        JPanel[] swatches = new JPanel[PALETTE.length];
        for (int i = 0; i < PALETTE.length; i++) {
            final String hex = PALETTE[i];
            final int idx = i;
            JPanel swatch = new JPanel() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    try { g2.setColor(Color.decode(hex)); } catch (Exception ex) { g2.setColor(Color.LIGHT_GRAY); }
                    g2.fillOval(2, 2, getWidth()-4, getHeight()-4);
                    if (hex.equals(selectedColour[0])) {
                        g2.setColor(new Color(0x1E, 0x3A, 0x5F));
                        g2.setStroke(new BasicStroke(2f));
                        g2.drawOval(1, 1, getWidth()-2, getHeight()-2);
                    } else {
                        g2.setColor(new Color(0xC8, 0xCD, 0xD6));
                        g2.drawOval(1, 1, getWidth()-2, getHeight()-2);
                    }
                    g2.dispose();
                }
            };
            swatch.setOpaque(false);
            swatch.setPreferredSize(new Dimension(24, 24));
            swatch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            swatches[i] = swatch;
            swatch.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    selectedColour[0] = hex;
                    for (JPanel s : swatches) s.repaint();
                }
            });
            row.add(swatch);
        }
        return row;
    }

    private static JPanel buildAvatarCircle(String name, String colourHex) {
        Color bg;
        try { bg = Color.decode(colourHex != null ? colourHex : "#1E3A5F"); }
        catch (Exception e) { bg = NAVY; }
        String initial = (name != null && !name.isEmpty()) ? name.substring(0, 1).toUpperCase() : "?";
        // Choose text colour based on brightness
        double brightness = (bg.getRed() * 299.0 + bg.getGreen() * 587.0 + bg.getBlue() * 114.0) / 1000.0;
        Color textColor = brightness > 160 ? new Color(0x1A, 0x1D, 0x23) : Color.WHITE;

        final Color finalBg = bg;
        JPanel circle = new JPanel(new GridBagLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(finalBg);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        circle.setOpaque(false);
        circle.setPreferredSize(new Dimension(34, 34));
        JLabel lbl = new JLabel(initial);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 14f));
        lbl.setForeground(textColor);
        circle.add(lbl);
        return circle;
    }

    private static JButton linkButton(String text, Color foreground) {
        JButton btn = new JButton(text);
        btn.setFont(btn.getFont().deriveFont(12f));
        btn.setForeground(foreground);
        btn.setBackground(new Color(0, 0, 0, 0));
        btn.setOpaque(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }
}
