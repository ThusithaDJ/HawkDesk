package com.olympus.system.hawkdeskpos.frontend.sale;

import com.olympus.system.hawkdeskpos.dto.ReturnDto;
import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.Refreshable;
import com.olympus.system.hawkdeskpos.service.ReturnService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Displays all return records in a searchable, sortable table.
 */
public class ReturnsPanel extends JPanel implements Refreshable {

    private static final Color BG   = new Color(0xF0, 0xF2, 0xF5);
    private static final Color NAVY = new Color(0x1E, 0x3A, 0x5F);
    private static final Color TEXT2 = new Color(0x5A, 0x60, 0x70);

    private static final String[] COLS = {
        "Return ID", "Invoice No", "Date", "Item", "Qty", "Reason", "Refund Method", "Cashier"
    };

    private final ReturnService returnService;
    private DefaultTableModel   tableModel;
    private JTextField          searchField;
    private TableRowSorter<DefaultTableModel> sorter;

    public ReturnsPanel(ReturnService returnService) {
        this.returnService = returnService;
        setBackground(BG);
        setLayout(new BorderLayout());
        buildUI();
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Returns");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        header.add(title, BorderLayout.WEST);

        JPanel hBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        hBtns.setOpaque(false);
        JButton back = new JButton("← Back");
        back.addActionListener(e -> Home.navigate(Home.CARD_DASH));
        hBtns.add(back);
        header.add(hBtns, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        // Search bar
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filterBar.setOpaque(false);
        searchField = new JTextField(28);
        searchField.putClientProperty("JTextField.placeholderText", "Search invoice, item, cashier…");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { applyFilter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { applyFilter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
        });
        JButton refreshBtn = new JButton("↺ Refresh");
        refreshBtn.addActionListener(e -> loadData());
        filterBar.add(new JLabel("Search:"));
        filterBar.add(searchField);
        filterBar.add(refreshBtn);
        root.add(filterBar, BorderLayout.CENTER);

        // Table
        tableModel = new DefaultTableModel(COLS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(tableModel);
        table.setRowHeight(28);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD, 12f));
        table.getTableHeader().setBackground(new Color(0xE2, 0xE5, 0xEA));
        table.getTableHeader().setForeground(TEXT2);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        // Column widths
        int[] widths = {70, 130, 130, 200, 50, 160, 110, 120};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(0xD1, 0xD5, 0xDB)));
        root.add(scroll, BorderLayout.SOUTH);

        // Adjust layout: put filter + table in a CENTER panel
        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setOpaque(false);
        body.add(filterBar, BorderLayout.NORTH);
        body.add(scroll,    BorderLayout.CENTER);
        root.remove(filterBar); // reattach properly
        root.add(body, BorderLayout.CENTER);

        add(root);
    }

    private void applyFilter() {
        String text = searchField.getText().trim();
        if (text.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text));
        }
    }

    private void loadData() {
        new SwingWorker<List<ReturnDto>, Void>() {
            @Override protected List<ReturnDto> doInBackground() {
                return returnService.listAllReturns();
            }
            @Override protected void done() {
                try {
                    List<ReturnDto> list = get();
                    tableModel.setRowCount(0);
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                    for (ReturnDto r : list) {
                        tableModel.addRow(new Object[]{
                            r.returnId(),
                            r.invoiceNo(),
                            r.returnDate() != null ? sdf.format(r.returnDate()) : "—",
                            r.itemName(),
                            r.qty(),
                            r.reason(),
                            r.refundMethod(),
                            r.cashierName()
                        });
                    }
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    @Override
    public void refresh() {
        loadData();
    }
}
