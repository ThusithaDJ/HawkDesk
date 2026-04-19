package com.olympus.system.hawkdeskpos.frontend.finance;

import com.olympus.system.hawkdeskpos.dto.GrnDto;
import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.service.StockService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * GRN Details screen — searchable/filterable list of GRNs with master-detail view.
 */
public class GrnHistoryPanel extends JPanel implements com.olympus.system.hawkdeskpos.frontend.components.Refreshable {

    private static final Color BG    = new Color(0xF0, 0xF2, 0xF5);
    private static final Color TEXT2 = new Color(0x5A, 0x60, 0x70);
    private static final Color NAVY  = new Color(0x1E, 0x3A, 0x5F);

    private final StockService stockService;

    // Filter controls
    private JTextField searchField;
    private JTextField dateFromField;
    private JTextField dateToField;

    // Master table
    private DefaultTableModel tableModel;
    private JTable table;
    private List<GrnDto> allGrns       = new ArrayList<>();
    private List<GrnDto> displayedGrns = new ArrayList<>();

    // Detail panel
    private JPanel detailPanel;
    private JLabel detailHeader;
    private DefaultTableModel linesModel;

    private static final String[] MASTER_COLS = {
            "GRN Number", "Date", "Supplier", "Reference", "Lines", "Total Cost (Rs.)"
    };
    private static final String[] LINES_COLS = {
            "Item Name", "Qty", "Cost (Rs.)", "Sell (Rs.)", "Batch #", "Expiry"
    };

    private final SimpleDateFormat FMT_DISPLAY = new SimpleDateFormat("yyyy-MM-dd");
    private final SimpleDateFormat FMT_FILTER  = new SimpleDateFormat("yyyy-MM-dd");

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

        // ── Header ────────────────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("GRN Details");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        header.add(title, BorderLayout.WEST);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        JButton refreshBtn = new JButton("↺ Refresh");
        refreshBtn.addActionListener(e -> loadDataAsync());
        JButton newGrn = new JButton("+ Receive Stock");
        newGrn.setBackground(NAVY);
        newGrn.setForeground(Color.WHITE);
        newGrn.setOpaque(true);
        newGrn.setBorderPainted(false);
        newGrn.addActionListener(e -> Home.navigate(Home.CARD_RECEIVE));
        JButton back = new JButton("← Back");
        back.addActionListener(e -> Home.navigate(Home.CARD_DASH));
        btns.add(refreshBtn);
        btns.add(newGrn);
        btns.add(back);
        header.add(btns, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        // ── Filter bar ────────────────────────────────────────────────────────
        CardPanel filterCard = new CardPanel(new BorderLayout(0, 4));
        ((JPanel)filterCard).setBorder(new EmptyBorder(6, 10, 6, 10));

        searchField = new JTextField(22);
        searchField.putClientProperty("JTextField.placeholderText", "Search GRN # or supplier…");

        dateFromField = new JTextField(10);
        dateFromField.putClientProperty("JTextField.placeholderText", "From yyyy-MM-dd");

        dateToField = new JTextField(10);
        dateToField.putClientProperty("JTextField.placeholderText", "To yyyy-MM-dd");

        DocumentListener filterListener = new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { applyFilter(); }
            public void removeUpdate(DocumentEvent e)  { applyFilter(); }
            public void changedUpdate(DocumentEvent e) {}
        };
        searchField.getDocument().addDocumentListener(filterListener);
        dateFromField.getDocument().addDocumentListener(filterListener);
        dateToField.getDocument().addDocumentListener(filterListener);

        // Period preset combo
        String[] periods = {"All Time", "Today", "This Week", "This Month", "This Year", "Custom"};
        JComboBox<String> periodCombo = new JComboBox<>(periods);
        periodCombo.setSelectedItem("All Time");
        periodCombo.setPreferredSize(new Dimension(110, 24));
        periodCombo.addActionListener(e -> {
            String sel = (String) periodCombo.getSelectedItem();
            LocalDate today = LocalDate.now();
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
            switch (sel) {
                case "Today" -> {
                    dateFromField.setText(fmt.format(Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant())));
                    dateToField.setText(fmt.format(Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant())));
                }
                case "This Week" -> {
                    dateFromField.setText(fmt.format(Date.from(today.with(DayOfWeek.MONDAY).atStartOfDay(ZoneId.systemDefault()).toInstant())));
                    dateToField.setText(fmt.format(Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant())));
                }
                case "This Month" -> {
                    dateFromField.setText(fmt.format(Date.from(today.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant())));
                    dateToField.setText(fmt.format(Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant())));
                }
                case "This Year" -> {
                    dateFromField.setText(fmt.format(Date.from(today.withDayOfYear(1).atStartOfDay(ZoneId.systemDefault()).toInstant())));
                    dateToField.setText(fmt.format(Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant())));
                }
                case "All Time" -> {
                    dateFromField.setText("");
                    dateToField.setText("");
                }
                // Custom: leave date fields as-is so user can type manually
            }
        });

        JButton clearBtn = new JButton("Clear");
        clearBtn.addActionListener(e -> {
            searchField.setText("");
            dateFromField.setText("");
            dateToField.setText("");
            periodCombo.setSelectedItem("All Time");
        });

        // Row 1: period preset
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row1.setOpaque(false);
        row1.add(new JLabel("Period:"));
        row1.add(periodCombo);

        // Row 2: search + manual date fields
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row2.setOpaque(false);
        row2.add(new JLabel("Search:"));
        row2.add(searchField);
        row2.add(new JLabel("From:"));
        row2.add(dateFromField);
        row2.add(new JLabel("To:"));
        row2.add(dateToField);
        row2.add(clearBtn);

        JPanel filterRows = new JPanel();
        filterRows.setOpaque(false);
        filterRows.setLayout(new BoxLayout(filterRows, BoxLayout.Y_AXIS));
        filterRows.add(row1);
        filterRows.add(row2);
        ((JPanel)filterCard).add(filterRows, BorderLayout.CENTER);

        // ── Master table ──────────────────────────────────────────────────────
        tableModel = new DefaultTableModel(MASTER_COLS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel);
        table.setRowHeight(38);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.getTableHeader().setFont(table.getFont().deriveFont(Font.BOLD, 12f));
        table.getTableHeader().setBackground(new Color(0xF7, 0xF8, 0xFA));
        table.getTableHeader().setForeground(TEXT2);
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showDetail(table.getSelectedRow());
        });

        JScrollPane masterScroll = new JScrollPane(table);
        masterScroll.setBorder(null);

        CardPanel masterCard = new CardPanel(new BorderLayout());
        ((JPanel)masterCard).add(filterCard, BorderLayout.NORTH);
        ((JPanel)masterCard).add(masterScroll, BorderLayout.CENTER);
        ((JPanel)masterCard).setBorder(new EmptyBorder(0, 0, 0, 0));

        // ── Detail panel ──────────────────────────────────────────────────────
        detailPanel = buildDetailPanel();

        // ── Split pane ────────────────────────────────────────────────────────
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, masterCard, detailPanel);
        split.setResizeWeight(0.55);
        split.setBorder(null);
        split.setDividerSize(6);
        split.setOpaque(false);
        split.addHierarchyListener(he -> {
            if ((he.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0
                    && split.isShowing()) {
                SwingUtilities.invokeLater(() -> split.setDividerLocation(0.55));
            }
        });

        root.add(split, BorderLayout.CENTER);
        add(root);
        loadDataAsync();
    }

    private JPanel buildDetailPanel() {
        CardPanel c = new CardPanel(new BorderLayout(0, 8));
        ((JPanel)c).setBorder(new EmptyBorder(12, 14, 12, 14));

        detailHeader = new JLabel("Select a GRN above to view line items");
        detailHeader.setFont(detailHeader.getFont().deriveFont(Font.BOLD, 13f));
        detailHeader.setForeground(TEXT2);
        ((JPanel)c).add(detailHeader, BorderLayout.NORTH);

        linesModel = new DefaultTableModel(LINES_COLS, 0) {
            @Override public boolean isCellEditable(int r, int col) { return false; }
        };
        JTable linesTable = new JTable(linesModel);
        linesTable.setRowHeight(32);
        linesTable.setShowGrid(false);
        linesTable.setIntercellSpacing(new Dimension(0, 0));
        linesTable.getTableHeader().setFont(linesTable.getFont().deriveFont(Font.BOLD, 11f));
        linesTable.getTableHeader().setBackground(new Color(0xF7, 0xF8, 0xFA));
        linesTable.getTableHeader().setForeground(TEXT2);
        linesTable.getColumnModel().getColumn(0).setPreferredWidth(200);
        linesTable.getColumnModel().getColumn(1).setPreferredWidth(60);
        linesTable.getColumnModel().getColumn(1).setMaxWidth(80);
        linesTable.getColumnModel().getColumn(2).setPreferredWidth(110);
        linesTable.getColumnModel().getColumn(3).setPreferredWidth(110);
        linesTable.getColumnModel().getColumn(4).setPreferredWidth(120);
        linesTable.getColumnModel().getColumn(5).setPreferredWidth(100);

        JScrollPane scroll = new JScrollPane(linesTable);
        scroll.setBorder(null);
        ((JPanel)c).add(scroll, BorderLayout.CENTER);

        return (JPanel) c;
    }

    private void showDetail(int viewRow) {
        linesModel.setRowCount(0);
        if (viewRow < 0) {
            detailHeader.setText("Select a GRN above to view line items");
            detailHeader.setForeground(TEXT2);
            return;
        }
        // Map sorted/view row index back to model row, then to displayedGrns
        int modelRow = table.convertRowIndexToModel(viewRow);
        if (modelRow < 0 || modelRow >= displayedGrns.size()) return;
        GrnDto grn = displayedGrns.get(modelRow);

        String dateStr = grn.date() != null ? FMT_DISPLAY.format(grn.date()) : "—";
        detailHeader.setText(grn.grnNumber()
                + "   |   " + (grn.supplier() != null ? grn.supplier() : "—")
                + "   |   " + dateStr
                + "   |   Total: Rs. " + String.format("%,.2f", grn.totalCost()));
        detailHeader.setForeground(NAVY);

        for (GrnDto.GrnLineDto ln : grn.lines()) {
            String expiry = ln.expiryDate() != null ? FMT_DISPLAY.format(ln.expiryDate()) : "—";
            linesModel.addRow(new Object[]{
                    ln.itemName(),
                    ln.qtyReceived(),
                    String.format("%.2f", ln.costPrice()),
                    String.format("%.2f", ln.sellingPrice()),
                    ln.batchName() != null ? ln.batchName() : "—",
                    expiry
            });
        }
    }

    private void loadDataAsync() {
        new SwingWorker<List<GrnDto>, Void>() {
            @Override protected List<GrnDto> doInBackground() { return stockService.listGrnHistory(); }
            @Override protected void done() {
                try {
                    allGrns = get();
                    applyFilter();
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void applyFilter() {
        if (allGrns == null) return;
        String q       = searchField.getText().trim().toLowerCase();
        String fromStr = dateFromField.getText().trim();
        String toStr   = dateToField.getText().trim();

        Date from = parseDate(fromStr);
        Date to   = parseDate(toStr);
        // Make "to" inclusive by advancing to end of that day
        if (to != null) {
            to = new Date(to.getTime() + 86_399_999L);
        }

        displayedGrns = new ArrayList<>();
        for (GrnDto g : allGrns) {
            if (!q.isEmpty()) {
                boolean matchText = g.grnNumber().toLowerCase().contains(q)
                        || (g.supplier() != null && g.supplier().toLowerCase().contains(q))
                        || (g.reference() != null && g.reference().toLowerCase().contains(q));
                if (!matchText) continue;
            }
            if (from != null && g.date() != null && g.date().before(from)) continue;
            if (to   != null && g.date() != null && g.date().after(to))    continue;
            displayedGrns.add(g);
        }
        populateTable(displayedGrns);
        linesModel.setRowCount(0);
        detailHeader.setText("Select a GRN above to view line items");
        detailHeader.setForeground(TEXT2);
    }

    private void populateTable(List<GrnDto> grns) {
        tableModel.setRowCount(0);
        for (GrnDto g : grns) {
            tableModel.addRow(new Object[]{
                    g.grnNumber(),
                    g.date() != null ? FMT_DISPLAY.format(g.date()) : "—",
                    g.supplier() != null ? g.supplier() : "—",
                    g.reference() != null ? g.reference() : "—",
                    g.lines().size(),
                    String.format("%,.2f", g.totalCost())
            });
        }
    }

    private Date parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return FMT_FILTER.parse(s.trim()); } catch (ParseException e) { return null; }
    }
}
