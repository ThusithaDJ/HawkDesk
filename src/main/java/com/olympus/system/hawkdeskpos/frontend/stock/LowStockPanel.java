package com.olympus.system.hawkdeskpos.frontend.stock;

import com.olympus.system.hawkdeskpos.dto.StockLevelDto;
import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.frontend.components.StatusPill;
import com.olympus.system.hawkdeskpos.service.ItemService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Low Stock Alerts screen.
 * Shows out/low counts; checkable item list; suggests order quantities.
 */
public class LowStockPanel extends JPanel implements com.olympus.system.hawkdeskpos.frontend.components.Refreshable {

    private static final Color BG    = new Color(0xF0, 0xF2, 0xF5);
    private static final Color TEXT2 = new Color(0x5A, 0x60, 0x70);
    private static final Color RED   = new Color(0xC6, 0x28, 0x28);
    private static final Color AMBER = new Color(0xE6, 0x51, 0x00);
    private static final Color NAVY  = new Color(0x1E, 0x3A, 0x5F);

    private final ItemService itemService;

    private JLabel outCount, lowCount, selectedCount;
    private JPanel listPanel;
    private List<StockLevelDto> items = new ArrayList<>();
    private List<JCheckBox>     checkBoxes = new ArrayList<>();
    private List<JSpinner>      orderSpinners = new ArrayList<>();

    public LowStockPanel(ItemService itemService, com.olympus.system.hawkdeskpos.service.ReportService reportService) {
        this.itemService = itemService;
        setBackground(BG);
        setLayout(new BorderLayout());
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
        JLabel title = new JLabel("Low Stock Alerts");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        topBar.add(title, BorderLayout.WEST);
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> loadDataAsync());
        JButton back = new JButton("← Back");
        back.addActionListener(e -> Home.navigate(Home.CARD_DASH));
        btns.add(refresh);
        btns.add(back);
        topBar.add(btns, BorderLayout.EAST);
        root.add(topBar, BorderLayout.NORTH);

        // ── Stat bar ──────────────────────────────────────────────────────────
        JPanel statBar = new JPanel(new GridLayout(1, 3, 10, 0));
        statBar.setOpaque(false);
        statBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        statBar.add(statCard("Out of Stock", "—", RED));
        outCount = valueLabel(statBar);
        statBar.add(statCard("Low Stock", "—", AMBER));
        lowCount = valueLabel(statBar);
        statBar.add(statCard("Selected to Order", "0", NAVY));
        selectedCount = valueLabel(statBar);

        JPanel statsWrapper = new JPanel(new BorderLayout());
        statsWrapper.setOpaque(false);
        statsWrapper.add(statBar);

        // ── Item list ─────────────────────────────────────────────────────────
        listPanel = new JPanel();
        listPanel.setOpaque(false);
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));

        JScrollPane listScroll = new JScrollPane(listPanel);
        listScroll.setBorder(null);
        listScroll.setOpaque(false);
        listScroll.getViewport().setOpaque(false);

        CardPanel listCard = new CardPanel(new BorderLayout(0, 0));
        ((JPanel)listCard).setBorder(new EmptyBorder(12, 12, 12, 12));

        // List header
        JPanel listHeader = buildListHeader();
        ((JPanel)listCard).add(listHeader, BorderLayout.NORTH);
        ((JPanel)listCard).add(listScroll, BorderLayout.CENTER);

        // ── Action row ────────────────────────────────────────────────────────
        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actionRow.setOpaque(false);
        JButton selectAll = new JButton("Select All");
        selectAll.addActionListener(e -> { checkBoxes.forEach(cb -> cb.setSelected(true)); updateSelectedCount(); });
        JButton clearAll = new JButton("Clear All");
        clearAll.addActionListener(e -> { checkBoxes.forEach(cb -> cb.setSelected(false)); updateSelectedCount(); });
        JButton goReceive = new JButton("Receive Stock for Selected →");
        goReceive.setBackground(NAVY);
        goReceive.setForeground(Color.WHITE);
        goReceive.setOpaque(true);
        goReceive.setBorderPainted(false);
        goReceive.addActionListener(e -> Home.navigate(Home.CARD_RECEIVE));
        actionRow.add(selectAll);
        actionRow.add(clearAll);
        actionRow.add(goReceive);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setOpaque(false);
        content.add(statsWrapper, BorderLayout.NORTH);
        content.add(listCard,     BorderLayout.CENTER);
        content.add(actionRow,    BorderLayout.SOUTH);

        root.add(content, BorderLayout.CENTER);
        add(root);

        loadDataAsync();
    }

    private JPanel buildListHeader() {
        JPanel h = new JPanel(new GridLayout(1, 5, 10, 0));
        h.setOpaque(false);
        h.setBorder(new EmptyBorder(0, 4, 8, 4));
        String[] cols = {"", "Item Name", "In Stock / Min Level", "Status", "Order Qty"};
        for (String col : cols) {
            JLabel l = new JLabel(col);
            l.setFont(l.getFont().deriveFont(Font.BOLD, 12f));
            l.setForeground(TEXT2);
            h.add(l);
        }
        return h;
    }

    private void loadDataAsync() {
        listPanel.removeAll();
        checkBoxes.clear();
        orderSpinners.clear();
        items.clear();
        listPanel.add(new JLabel("  Loading…"));
        listPanel.revalidate();

        new SwingWorker<List<StockLevelDto>, Void>() {
            @Override protected List<StockLevelDto> doInBackground() {
                return itemService.listLowStock();
            }
            @Override protected void done() {
                try {
                    items = get();
                    populateList();
                    updateStats();
                } catch (Exception e) {
                    System.err.println("LowStockPanel.loadDataAsync: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void populateList() {
        listPanel.removeAll();
        checkBoxes.clear();
        orderSpinners.clear();

        if (items.isEmpty()) {
            JLabel ok = new JLabel("  All stock levels are OK ✓");
            ok.setFont(ok.getFont().deriveFont(14f));
            ok.setForeground(new Color(0x2E, 0x7D, 0x32));
            ok.setBorder(new EmptyBorder(20, 0, 20, 0));
            listPanel.add(ok);
        } else {
            for (StockLevelDto d : items) {
                listPanel.add(buildItemRow(d));
                listPanel.add(Box.createVerticalStrut(4));
            }
        }
        listPanel.revalidate();
        listPanel.repaint();
    }

    private JPanel buildItemRow(StockLevelDto d) {
        JPanel row = new JPanel(new GridLayout(1, 5, 10, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xF0, 0xF2, 0xF5)),
                new EmptyBorder(8, 4, 8, 4)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JCheckBox cb = new JCheckBox();
        cb.setOpaque(false);
        cb.addActionListener(e -> updateSelectedCount());
        checkBoxes.add(cb);

        JLabel name = new JLabel(d.itemName());
        name.setFont(name.getFont().deriveFont(Font.BOLD, 13f));

        JLabel stock = new JLabel(d.totalQty() + " / " + d.minLevel() + " min");
        stock.setForeground(TEXT2);

        // Suggested order: 3× min level - current qty, at least 1
        int suggested = Math.max(1, d.minLevel() * 3 - d.totalQty());
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(suggested, 1, 99999, 1));
        orderSpinners.add(spinner);

        row.add(cb);
        row.add(name);
        row.add(stock);
        row.add(StatusPill.forStatus(d.stockStatus()));
        row.add(spinner);
        return row;
    }

    private void updateStats() {
        long out = items.stream().filter(d -> "OUT".equals(d.stockStatus())).count();
        long low = items.stream().filter(d -> "LOW".equals(d.stockStatus())).count();
        outCount.setText(String.valueOf(out));
        lowCount.setText(String.valueOf(low));
        updateSelectedCount();
    }

    private void updateSelectedCount() {
        long sel = checkBoxes.stream().filter(JCheckBox::isSelected).count();
        selectedCount.setText(String.valueOf(sel));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CardPanel statCard(String label, String value, Color valueColor) {
        CardPanel c = new CardPanel(new BorderLayout(0, 4));
        c.setBorder(new EmptyBorder(12, 14, 12, 14));
        JLabel l = new JLabel(label);
        l.setFont(l.getFont().deriveFont(12f));
        l.setForeground(TEXT2);
        JLabel v = new JLabel(value);
        v.setFont(v.getFont().deriveFont(Font.BOLD, 22f));
        v.setForeground(valueColor);
        c.add(l, BorderLayout.NORTH);
        c.add(v, BorderLayout.CENTER);
        return c;
    }

    private JLabel valueLabel(JPanel parent) {
        CardPanel card = (CardPanel) parent.getComponent(parent.getComponentCount() - 1);
        return (JLabel) card.getComponent(1);
    }
}
