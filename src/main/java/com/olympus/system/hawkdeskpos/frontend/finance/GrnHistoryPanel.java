package com.olympus.system.hawkdeskpos.frontend.finance;

import com.olympus.system.hawkdeskpos.dto.GrnDto;
import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.service.StockService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.List;

/**
 * GRN History screen — searchable table of all goods received notes.
 */
public class GrnHistoryPanel extends JPanel {

    private static final Color BG    = new Color(0xF0, 0xF2, 0xF5);
    private static final Color TEXT2 = new Color(0x5A, 0x60, 0x70);
    private static final Color NAVY  = new Color(0x1E, 0x3A, 0x5F);

    private final StockService stockService;

    private JTextField searchField;
    private DefaultTableModel tableModel;
    private List<GrnDto> allGrns;

    private static final String[] COLS = {
            "GRN Number", "Date", "Supplier", "Reference", "Lines", "Total Cost (Rs.)"
    };

    public GrnHistoryPanel(StockService stockService) {
        this.stockService = stockService;
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
        JLabel title = new JLabel("GRN History");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        header.add(title, BorderLayout.WEST);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> loadDataAsync());
        JButton newGrn = new JButton("+ Receive Stock");
        newGrn.setBackground(NAVY);
        newGrn.setForeground(Color.WHITE);
        newGrn.setOpaque(true);
        newGrn.setBorderPainted(false);
        newGrn.addActionListener(e -> Home.navigate(Home.CARD_RECEIVE));
        JButton back = new JButton("← Back");
        back.addActionListener(e -> Home.navigate(Home.CARD_DASH));
        btns.add(refresh);
        btns.add(newGrn);
        btns.add(back);
        header.add(btns, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        // Filter bar
        CardPanel filterCard = new CardPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        ((JPanel)filterCard).setBorder(new EmptyBorder(0, 10, 0, 10));
        searchField = new JTextField(22);
        searchField.putClientProperty("JTextField.placeholderText", "Search GRN # or supplier…");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) {}
        });
        ((JPanel)filterCard).add(searchField);

        // Table
        tableModel = new DefaultTableModel(COLS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(tableModel);
        table.setRowHeight(38);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.getTableHeader().setFont(table.getFont().deriveFont(Font.BOLD, 12f));
        table.getTableHeader().setBackground(new Color(0xF7, 0xF8, 0xFA));
        table.getTableHeader().setForeground(TEXT2);
        table.setAutoCreateRowSorter(true);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(null);

        CardPanel tableCard = new CardPanel(new BorderLayout());
        ((JPanel)tableCard).add(filterCard, BorderLayout.NORTH);
        ((JPanel)tableCard).add(scroll,     BorderLayout.CENTER);
        ((JPanel)tableCard).setBorder(new EmptyBorder(0, 0, 0, 0));

        root.add(tableCard, BorderLayout.CENTER);
        add(root);
        loadDataAsync();
    }

    private void loadDataAsync() {
        new SwingWorker<List<GrnDto>, Void>() {
            @Override protected List<GrnDto> doInBackground() { return stockService.listGrnHistory(); }
            @Override protected void done() {
                try {
                    allGrns = get();
                    populateTable(allGrns);
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void applyFilter() {
        if (allGrns == null) return;
        String q = searchField.getText().trim().toLowerCase();
        var filtered = allGrns.stream().filter(g ->
                q.isEmpty()
                || g.grnNumber().toLowerCase().contains(q)
                || (g.supplier() != null && g.supplier().toLowerCase().contains(q))
        ).toList();
        populateTable(filtered);
    }

    private void populateTable(List<GrnDto> grns) {
        tableModel.setRowCount(0);
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        for (GrnDto g : grns) {
            tableModel.addRow(new Object[]{
                    g.grnNumber(),
                    g.date() != null ? fmt.format(g.date()) : "—",
                    g.supplier() != null ? g.supplier() : "—",
                    g.reference() != null ? g.reference() : "—",
                    g.lines().size(),
                    String.format("%.2f", g.totalCost())
            });
        }
    }
}
