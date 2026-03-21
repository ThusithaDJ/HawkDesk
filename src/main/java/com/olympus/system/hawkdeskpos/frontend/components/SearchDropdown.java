package com.olympus.system.hawkdeskpos.frontend.components;

import com.olympus.system.hawkdeskpos.dto.ItemDto;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Live-search text field with a popup showing matching items.
 * Uses a JWindow (not JPopupMenu) so the text field keeps keyboard focus.
 *
 * Usage:
 *   SearchDropdown sd = new SearchDropdown(
 *       query -> itemService.search(query),
 *       item  -> cartPanel.addItem(item));
 */
public class SearchDropdown extends JPanel {

    private static final Color BG        = Color.WHITE;
    private static final Color BORDER_C  = new Color(0xC8, 0xCD, 0xD6);
    private static final Color HOVER_BG  = new Color(0xF7, 0xF8, 0xFA);
    private static final Color TEXT1     = new Color(0x1A, 0x1D, 0x23);
    private static final Color TEXT2     = new Color(0x5A, 0x60, 0x70);
    private static final Color NAVY      = new Color(0x1E, 0x3A, 0x5F);

    private final JTextField                    searchField;
    private final JWindow                       dropWindow;
    private final DefaultListModel<ItemDto>     model;
    private final JList<ItemDto>                resultList;
    private final Function<String, List<ItemDto>> searcher;
    private final Consumer<ItemDto>             onSelect;
    private Timer debounce;

    public SearchDropdown(Function<String, List<ItemDto>> searcher, Consumer<ItemDto> onSelect) {
        super(new BorderLayout());
        this.searcher = searcher;
        this.onSelect = onSelect;

        searchField = new JTextField();
        searchField.setFont(searchField.getFont().deriveFont(14f));
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_C),
                new EmptyBorder(8, 11, 8, 11)));
        searchField.setToolTipText("Search by item name or SKU…");
        add(searchField, BorderLayout.CENTER);

        // Drop-down list
        model      = new DefaultListModel<>();
        resultList = new JList<>(model);
        resultList.setCellRenderer(new ItemCellRenderer());
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.setBackground(BG);

        JScrollPane scroll = new JScrollPane(resultList);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER_C));

        // JWindow — does NOT steal focus from the text field
        dropWindow = new JWindow();
        dropWindow.setLayout(new BorderLayout());
        dropWindow.add(scroll);
        dropWindow.setFocusableWindowState(false);

        // Search-as-you-type with 250 ms debounce
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { scheduleSearch(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { scheduleSearch(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) {}
        });

        // Keyboard navigation
        searchField.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DOWN && dropWindow.isVisible()) {
                    resultList.requestFocusInWindow();
                    resultList.setSelectedIndex(0);
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    dropWindow.setVisible(false);
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER && dropWindow.isVisible()) {
                    selectCurrent();
                }
            }
        });

        resultList.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER)  selectCurrent();
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) { dropWindow.setVisible(false); searchField.requestFocusInWindow(); }
            }
        });

        resultList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() >= 1) selectCurrent();
            }
        });

        // Dismiss when focus leaves the search field (150 ms delay so list clicks register first)
        searchField.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) {
                Timer t = new Timer(150, ev -> dropWindow.setVisible(false));
                t.setRepeats(false);
                t.start();
            }
        });
    }

    /** Returns the underlying text field so callers can set placeholder text, etc. */
    public JTextField getTextField() { return searchField; }

    /** Clears the search field. */
    public void clear() {
        searchField.setText("");
        dropWindow.setVisible(false);
        model.clear();
    }

    private void scheduleSearch() {
        if (debounce != null && debounce.isRunning()) debounce.stop();
        debounce = new Timer(250, e -> performSearch());
        debounce.setRepeats(false);
        debounce.start();
    }

    private void performSearch() {
        String q = searchField.getText().trim();
        if (q.isEmpty()) { dropWindow.setVisible(false); model.clear(); return; }
        new SwingWorker<List<ItemDto>, Void>() {
            @Override protected List<ItemDto> doInBackground() { return searcher.apply(q); }
            @Override protected void done() {
                try {
                    List<ItemDto> items = get();
                    model.clear();
                    if (items.isEmpty()) { dropWindow.setVisible(false); return; }
                    items.forEach(model::addElement);
                    resultList.setVisibleRowCount(Math.min(items.size(), 8));
                    int rowH = 64;
                    int visRows = Math.min(items.size(), 8);
                    // Position directly below the text field using screen coordinates
                    Point p = searchField.getLocationOnScreen();
                    dropWindow.setBounds(p.x, p.y + searchField.getHeight(),
                            searchField.getWidth(), visRows * rowH + 4);
                    dropWindow.setVisible(true);
                    searchField.requestFocusInWindow();
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void selectCurrent() {
        ItemDto selected = resultList.getSelectedValue();
        if (selected == null && model.getSize() > 0) selected = model.getElementAt(0);
        if (selected != null) {
            dropWindow.setVisible(false);
            clear();
            onSelect.accept(selected);
        }
    }

    // ── Custom cell renderer ──────────────────────────────────────────────────

    private static class ItemCellRenderer implements ListCellRenderer<ItemDto> {
        @Override
        public Component getListCellRendererComponent(JList<? extends ItemDto> list,
                ItemDto item, int index, boolean isSelected, boolean cellHasFocus) {
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setBorder(new EmptyBorder(8, 12, 8, 12));
            row.setBackground(isSelected ? HOVER_BG : BG);

            // Left: name + sku
            JPanel left = new JPanel(new GridLayout(2, 1, 0, 2));
            left.setOpaque(false);
            JLabel name = new JLabel(item.itemName());
            name.setFont(name.getFont().deriveFont(Font.BOLD, 13f));
            name.setForeground(TEXT1);
            JLabel sub  = new JLabel(item.sku() + " · " + item.categoryName());
            sub.setFont(sub.getFont().deriveFont(11f));
            sub.setForeground(TEXT2);
            left.add(name);
            left.add(sub);
            row.add(left, BorderLayout.CENTER);

            // Right: status pill + sell price + cost price
            JPanel right = new JPanel(new GridLayout(3, 1, 0, 1));
            right.setOpaque(false);
            StatusPill pill = StatusPill.forStatus(item.stockStatus());
            pill.setHorizontalAlignment(SwingConstants.RIGHT);
            JLabel price = new JLabel(String.format("Rs. %.2f", item.sellingPrice()));
            price.setFont(price.getFont().deriveFont(Font.BOLD, 12f));
            price.setForeground(NAVY);
            price.setHorizontalAlignment(SwingConstants.RIGHT);
            JLabel cost = new JLabel(String.format("Cost: Rs. %.2f", item.costPrice()));
            cost.setFont(cost.getFont().deriveFont(10f));
            cost.setForeground(TEXT2);
            cost.setHorizontalAlignment(SwingConstants.RIGHT);
            right.add(pill);
            right.add(price);
            right.add(cost);
            row.add(right, BorderLayout.EAST);

            return row;
        }
    }
}
