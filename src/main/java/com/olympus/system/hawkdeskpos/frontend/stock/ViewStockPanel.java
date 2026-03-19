package com.olympus.system.hawkdeskpos.frontend.stock;

import com.olympus.system.hawkdeskpos.dto.StockLevelDto;
import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.frontend.components.StatusPill;
import com.olympus.system.hawkdeskpos.service.CategoryService;
import com.olympus.system.hawkdeskpos.service.ItemService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;
import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * View Stock screen — stat bar + filters + sortable table.
 */
public class ViewStockPanel extends JPanel {

    private static final Color BG    = new Color(0xF0, 0xF2, 0xF5);
    private static final Color TEXT2 = new Color(0x5A, 0x60, 0x70);
    private static final Color RED   = new Color(0xC6, 0x28, 0x28);
    private static final Color AMBER = new Color(0xE6, 0x51, 0x00);
    private static final Color NAVY  = new Color(0x1E, 0x3A, 0x5F);

    private final ItemService     itemService;
    private final CategoryService categoryService;

    private JLabel totalItems, outOfStock, lowStock, totalValue;
    private JTextField searchField;
    private JComboBox<String> catFilter, statusFilter;
    private DefaultTableModel tableModel;
    private JTable table;
    private List<StockLevelDto> allItems;

    private static final String[] COLUMNS = {
            "Item Name", "SKU", "Category", "Brand", "Qty", "Status", "Price (Rs.)", "Actions"
    };

    public ViewStockPanel(ItemService itemService, CategoryService categoryService) {
        this.itemService     = itemService;
        this.categoryService = categoryService;
        setBackground(BG);
        setLayout(new BorderLayout(0, 0));
        buildUI();
    }

    public void refresh() { loadDataAsync(); }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        // ── Top bar ───────────────────────────────────────────────────────────
        JPanel topBar = new JPanel(new BorderLayout(12, 0));
        topBar.setOpaque(false);

        JLabel pageTitle = new JLabel("View Stock");
        pageTitle.setFont(pageTitle.getFont().deriveFont(Font.BOLD, 20f));
        pageTitle.setForeground(new Color(0x1A, 0x1D, 0x23));
        topBar.add(pageTitle, BorderLayout.WEST);

        JPanel actionBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionBar.setOpaque(false);
        JButton addBtn = new JButton("+ Add New Item");
        addBtn.setBackground(NAVY);
        addBtn.setForeground(Color.WHITE);
        addBtn.setOpaque(true);
        addBtn.setBorderPainted(false);
        addBtn.addActionListener(e -> Home.navigate(Home.CARD_ADD_ITEM));
        actionBar.add(addBtn);
        JButton backBtn = new JButton("← Back");
        backBtn.addActionListener(e -> Home.navigate(Home.CARD_DASH));
        actionBar.add(backBtn);
        topBar.add(actionBar, BorderLayout.EAST);
        root.add(topBar, BorderLayout.NORTH);

        // ── Stat bar ──────────────────────────────────────────────────────────
        JPanel statBar = new JPanel(new GridLayout(1, 4, 10, 0));
        statBar.setOpaque(false);
        statBar.add(statCard("Total Items",       "—"));
        totalItems = getLastLabel(statBar);
        statBar.add(statCard("Out of Stock",      "—"));
        outOfStock = getLastLabel(statBar);
        statBar.add(statCard("Low Stock",         "—"));
        lowStock   = getLastLabel(statBar);
        statBar.add(statCard("Total Stock Value", "—"));
        totalValue = getLastLabel(statBar);

        JPanel statsWrapper = new JPanel(new BorderLayout());
        statsWrapper.setOpaque(false);
        statsWrapper.add(statBar);
        statsWrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        // ── Filters ───────────────────────────────────────────────────────────
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        filters.setOpaque(false);

        searchField = new JTextField(20);
        searchField.putClientProperty("JTextField.placeholderText", "Search items…");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) {}
        });
        filters.add(searchField);

        catFilter = new JComboBox<>(new String[]{"All Categories"});
        filters.add(catFilter);

        statusFilter = new JComboBox<>(new String[]{"All Status", "OK", "LOW", "OUT"});
        statusFilter.addActionListener(e -> applyFilter());
        filters.add(statusFilter);

        catFilter.addActionListener(e -> applyFilter());

        // ── Table ─────────────────────────────────────────────────────────────
        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel);
        table.setRowHeight(40);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.getTableHeader().setFont(table.getFont().deriveFont(Font.BOLD, 12f));
        table.getTableHeader().setBackground(new Color(0xF7, 0xF8, 0xFA));
        table.getTableHeader().setForeground(TEXT2);
        table.setAutoCreateRowSorter(true);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && table.getSelectedRow() >= 0) {
                int modelRow = table.convertRowIndexToModel(table.getSelectedRow());
                if (allItems != null && modelRow < allItems.size()) {
                    Home.navigateToEditItem(allItems.get(modelRow).itemId());
                }
            }
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(null);

        // ── Main layout ───────────────────────────────────────────────────────
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setOpaque(false);
        content.add(statsWrapper, BorderLayout.NORTH);

        CardPanel tableCard = new CardPanel(new BorderLayout(0, 0));
        tableCard.add(filters, BorderLayout.NORTH);
        tableCard.add(scroll,  BorderLayout.CENTER);
        ((JPanel)tableCard).setBorder(new EmptyBorder(12, 12, 12, 12));
        content.add(tableCard, BorderLayout.CENTER);

        root.add(content, BorderLayout.CENTER);
        add(root);

        loadCategoriesAsync();
        loadDataAsync();
    }

    private void loadCategoriesAsync() {
        new SwingWorker<List<String>, Void>() {
            @Override protected List<String> doInBackground() {
                return categoryService.listCategories().stream()
                        .map(c -> c.getCategoryName()).collect(Collectors.toList());
            }
            @Override protected void done() {
                try {
                    catFilter.removeAllItems();
                    catFilter.addItem("All Categories");
                    get().forEach(catFilter::addItem);
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void loadDataAsync() {
        new SwingWorker<List<StockLevelDto>, Void>() {
            @Override protected List<StockLevelDto> doInBackground() {
                return itemService.listAllStockLevels();
            }
            @Override protected void done() {
                try {
                    allItems = get();
                    updateStats();
                    populateTable(allItems);
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void applyFilter() {
        if (allItems == null) return;
        String q      = searchField.getText().trim().toLowerCase();
        String cat    = (String) catFilter.getSelectedItem();
        String status = (String) statusFilter.getSelectedItem();

        List<StockLevelDto> filtered = allItems.stream().filter(d -> {
            boolean matchQ = q.isEmpty()
                    || d.itemName().toLowerCase().contains(q)
                    || (d.sku() != null && d.sku().toLowerCase().contains(q));
            boolean matchC = "All Categories".equals(cat) || cat.equals(d.categoryName());
            boolean matchS = "All Status".equals(status) || status.equals(d.stockStatus());
            return matchQ && matchC && matchS;
        }).collect(Collectors.toList());
        populateTable(filtered);
    }

    private void populateTable(List<StockLevelDto> items) {
        tableModel.setRowCount(0);
        for (StockLevelDto d : items) {
            tableModel.addRow(new Object[]{
                    d.itemName(), d.sku(), d.categoryName(), d.brandName(),
                    d.totalQty(), d.stockStatus(),
                    String.format("%.2f", d.sellingPrice()), "Edit"
            });
        }
    }

    private void updateStats() {
        if (allItems == null) return;
        totalItems.setText(String.valueOf(allItems.size()));
        outOfStock.setText(String.valueOf(allItems.stream().filter(d -> "OUT".equals(d.stockStatus())).count()));
        lowStock.setText(String.valueOf(allItems.stream().filter(d -> "LOW".equals(d.stockStatus())).count()));
        double total = allItems.stream().mapToDouble(StockLevelDto::stockValue).sum();
        totalValue.setText(String.format("Rs. %.2f", total));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CardPanel statCard(String label, String value) {
        CardPanel c = new CardPanel(new BorderLayout(0, 4));
        c.setBorder(new EmptyBorder(12, 14, 12, 14));
        JLabel l = new JLabel(label);
        l.setFont(l.getFont().deriveFont(12f));
        l.setForeground(TEXT2);
        JLabel v = new JLabel(value);
        v.setFont(v.getFont().deriveFont(Font.BOLD, 20f));
        c.add(l, BorderLayout.NORTH);
        c.add(v, BorderLayout.CENTER);
        return c;
    }

    private JLabel getLastLabel(JPanel p) {
        CardPanel card = (CardPanel) p.getComponent(p.getComponentCount() - 1);
        return (JLabel) card.getComponent(1);
    }
}
