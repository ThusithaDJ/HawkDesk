package com.olympus.system.hawkdeskpos.frontend.admin;

import com.olympus.system.hawkdeskpos.db.dao.Brands;
import com.olympus.system.hawkdeskpos.db.dao.Category;
import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.service.CategoryService;
import com.olympus.system.hawkdeskpos.session.SessionContext;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * Manage Categories & Brands screen.
 * Two-column layout: Categories | Brands.
 * Each column: header with count, Add button, scrollable list with inline rename/delete.
 */
public class ManageCategoriesPanel extends JPanel {

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

    public void refresh() { loadDataAsync(); }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Manage Categories");
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

    private CardPanel buildCategoryColumn() {
        CardPanel c = new CardPanel(new BorderLayout(0, 0));
        ((JPanel)c).setBorder(new EmptyBorder(14, 14, 14, 14));

        // Header row
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
        JButton addBtn = new JButton("+ Add");
        addBtn.addActionListener(e -> addCategory());
        hdr.add(addBtn, BorderLayout.EAST);

        catList = new JPanel();
        catList.setOpaque(false);
        catList.setLayout(new BoxLayout(catList, BoxLayout.Y_AXIS));

        JScrollPane scroll = new JScrollPane(catList);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);

        ((JPanel)c).add(hdr,    BorderLayout.NORTH);
        ((JPanel)c).add(scroll, BorderLayout.CENTER);
        return c;
    }

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
        JButton addBtn = new JButton("+ Add");
        addBtn.addActionListener(e -> addBrand());
        hdr.add(addBtn, BorderLayout.EAST);

        brandList = new JPanel();
        brandList.setOpaque(false);
        brandList.setLayout(new BoxLayout(brandList, BoxLayout.Y_AXIS));

        JScrollPane scroll = new JScrollPane(brandList);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);

        ((JPanel)c).add(hdr,    BorderLayout.NORTH);
        ((JPanel)c).add(scroll, BorderLayout.CENTER);
        return c;
    }

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
                    catList.add(Box.createVerticalStrut(4));
                }
                catList.revalidate();

                brandList.removeAll();
                brandCountLabel.setText("(" + brands.size() + ")");
                for (Brands brand : brands) {
                    brandList.add(buildBrandRow(brand));
                    brandList.add(Box.createVerticalStrut(4));
                }
                brandList.revalidate();
                brandList.repaint();
            }
        }.execute();
    }

    private JPanel buildCategoryRow(Category cat) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xF0, 0xF2, 0xF5)),
                new EmptyBorder(8, 4, 8, 4)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Colour swatch
        JPanel swatch = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                try {
                    g2.setColor(Color.decode(cat.getColour() != null ? cat.getColour() : "#1E3A5F"));
                } catch (Exception e) {
                    g2.setColor(NAVY);
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2.dispose();
            }
        };
        swatch.setPreferredSize(new Dimension(16, 16));
        swatch.setOpaque(false);

        JLabel nameLbl = new JLabel(cat.getCategoryName());
        nameLbl.setFont(nameLbl.getFont().deriveFont(13f));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);
        left.add(swatch);
        left.add(nameLbl);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actions.setOpaque(false);
        JButton rename = new JButton("Rename");
        rename.setFont(rename.getFont().deriveFont(11f));
        rename.addActionListener(e -> renameCategory(cat));
        JButton delete = new JButton("Delete");
        delete.setFont(delete.getFont().deriveFont(11f));
        delete.setForeground(RED);
        delete.addActionListener(e -> deleteCategory(cat));
        actions.add(rename);
        actions.add(delete);

        row.add(left,    BorderLayout.WEST);
        row.add(actions, BorderLayout.EAST);
        return row;
    }

    private JPanel buildBrandRow(Brands brand) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xF0, 0xF2, 0xF5)),
                new EmptyBorder(8, 4, 8, 4)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel nameLbl = new JLabel(brand.getBrandName());
        nameLbl.setFont(nameLbl.getFont().deriveFont(13f));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actions.setOpaque(false);
        JButton rename = new JButton("Rename");
        rename.setFont(rename.getFont().deriveFont(11f));
        rename.addActionListener(e -> renameBrand(brand));
        JButton delete = new JButton("Delete");
        delete.setFont(delete.getFont().deriveFont(11f));
        delete.setForeground(RED);
        delete.addActionListener(e -> deleteBrand(brand));
        actions.add(rename);
        actions.add(delete);

        row.add(nameLbl, BorderLayout.WEST);
        row.add(actions, BorderLayout.EAST);
        return row;
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void addCategory() {
        String name = JOptionPane.showInputDialog(this, "Category name:", "Add Category", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) return;
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                Long actor = SessionContext.current() != null ? SessionContext.current().getEmployee().id() : null;
                categoryService.addCategory(name.trim(), null, actor);
                return null;
            }
            @Override protected void done() { loadDataAsync(); }
        }.execute();
    }

    private void addBrand() {
        String name = JOptionPane.showInputDialog(this, "Brand name:", "Add Brand", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) return;
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                Long actor = SessionContext.current() != null ? SessionContext.current().getEmployee().id() : null;
                categoryService.addBrand(name.trim(), actor);
                return null;
            }
            @Override protected void done() { loadDataAsync(); }
        }.execute();
    }

    private void renameCategory(Category cat) {
        String name = (String) JOptionPane.showInputDialog(this, "New name:", "Rename Category",
                JOptionPane.PLAIN_MESSAGE, null, null, cat.getCategoryName());
        if (name == null || name.trim().isEmpty()) return;
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                Long actor = SessionContext.current() != null ? SessionContext.current().getEmployee().id() : null;
                categoryService.renameCategory(cat.getCatId(), name.trim(), actor);
                return null;
            }
            @Override protected void done() { loadDataAsync(); }
        }.execute();
    }

    private void renameBrand(Brands brand) {
        String name = (String) JOptionPane.showInputDialog(this, "New name:", "Rename Brand",
                JOptionPane.PLAIN_MESSAGE, null, null, brand.getBrandName());
        if (name == null || name.trim().isEmpty()) return;
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                Long actor = SessionContext.current() != null ? SessionContext.current().getEmployee().id() : null;
                categoryService.renameBrand(brand.getBrandId(), name.trim(), actor);
                return null;
            }
            @Override protected void done() { loadDataAsync(); }
        }.execute();
    }

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
                    if (!get()) {
                        JOptionPane.showMessageDialog(ManageCategoriesPanel.this,
                                "Cannot delete: items are assigned to this category.", "Delete Failed",
                                JOptionPane.WARNING_MESSAGE);
                    }
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
                    if (!get()) {
                        JOptionPane.showMessageDialog(ManageCategoriesPanel.this,
                                "Cannot delete: items are assigned to this brand.", "Delete Failed",
                                JOptionPane.WARNING_MESSAGE);
                    }
                    loadDataAsync();
                } catch (Exception ignored) {}
            }
        }.execute();
    }
}
