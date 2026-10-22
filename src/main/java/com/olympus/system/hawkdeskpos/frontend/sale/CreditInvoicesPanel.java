package com.olympus.system.hawkdeskpos.frontend.sale;

import com.olympus.system.hawkdeskpos.dto.InvoiceDto;
import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.frontend.components.Refreshable;
import com.olympus.system.hawkdeskpos.service.CashAccountService;
import com.olympus.system.hawkdeskpos.service.SaleService;
import com.olympus.system.hawkdeskpos.session.SessionContext;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

public class CreditInvoicesPanel extends JPanel implements Refreshable {

    private static final Color BG    = new Color(0xF0, 0xF2, 0xF5);
    private static final Color NAVY  = new Color(0x1E, 0x3A, 0x5F);
    private static final Color TEXT2 = new Color(0x5A, 0x60, 0x70);
    private static final Color GREEN = new Color(0x2E, 0x7D, 0x32);
    private static final Color AMBER = new Color(0xE6, 0x51, 0x00);
    private static final Color RED   = new Color(0xC6, 0x28, 0x28);

    private static final SimpleDateFormat SDF = new SimpleDateFormat("dd/MM/yyyy");

    private final SaleService        saleService;
    private final CashAccountService cashAccountService;

    // Outstanding table (top)
    private DefaultTableModel outModel;
    private JTable            outTable;
    private List<InvoiceDto>  outstandingInvoices;
    private JLabel            totalDebtLabel;

    // All credit invoices table (bottom)
    private DefaultTableModel allModel;
    private JTable            allTable;
    private List<InvoiceDto>  allInvoices;

    // Filter controls for the bottom table
    private JTextField   allSearchField;
    private JComboBox<String> statusCombo;
    private JSpinner     fromSpinner, toSpinner;
    private JPanel       dateFilterPanel;
    private Timer        searchDebounce;

    public CreditInvoicesPanel(SaleService saleService) {
        this(saleService, null);
    }

    public CreditInvoicesPanel(SaleService saleService, CashAccountService cashAccountService) {
        this.saleService        = saleService;
        this.cashAccountService = cashAccountService;
        setBackground(BG);
        setLayout(new BorderLayout());
        buildUI();
    }

    @Override
    public void refresh() { loadOutstanding(); loadAll(); }

    // ── UI ────────────────────────────────────────────────────────────────────

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Credit Invoices");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        header.add(title, BorderLayout.WEST);
        JButton back = new JButton("← Back");
        back.addActionListener(e -> Home.navigate(Home.CARD_DASH));
        JPanel hBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        hBtns.setOpaque(false);
        hBtns.add(back);
        header.add(hBtns, BorderLayout.EAST);

        // Content — two stacked sections in a scroll pane
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        // ── Top section: Outstanding invoices ─────────────────────────────────
        JLabel outLabel = sectionLabel("OUTSTANDING INVOICES");
        content.add(outLabel);
        content.add(Box.createVerticalStrut(6));
        content.add(buildOutstandingSection());
        content.add(Box.createVerticalStrut(14));

        // ── Bottom section: All credit invoices ───────────────────────────────
        content.add(sectionLabel("ALL CREDIT INVOICES"));
        content.add(Box.createVerticalStrut(6));
        content.add(buildAllCreditSection());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        root.add(header,  BorderLayout.NORTH);
        root.add(scroll,  BorderLayout.CENTER);
        add(root);

        loadOutstanding();
        loadAll();
    }

    // ── Outstanding invoices card ─────────────────────────────────────────────

    private JPanel buildOutstandingSection() {
        String[] cols = {"Invoice #", "Date", "Customer", "Total (Rs.)", "Paid (Rs.)", "Balance (Rs.)", "Status", "Resolve By"};
        outModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        outTable = new JTable(outModel);
        styleTable(outTable);
        outTable.getColumnModel().getColumn(0).setPreferredWidth(130);
        outTable.getColumnModel().getColumn(6).setMaxWidth(90);

        outTable.setDefaultRenderer(Object.class, (tbl, val, isSel, hasFocus, row, col) -> {
            JLabel cell = styledCell(val, isSel);
            if (!isSel) {
                if (col == 5) cell.setForeground(RED);
                if (col == 6) {
                    String s = (String) outModel.getValueAt(row, 6);
                    cell.setForeground("Partial".equals(s) ? AMBER : NAVY);
                    cell.setFont(cell.getFont().deriveFont(Font.BOLD, 11f));
                }
            }
            return cell;
        });

        JScrollPane scroll = new JScrollPane(outTable);
        scroll.setBorder(null);
        scroll.setPreferredSize(new Dimension(0, 220));

        // Summary + action row
        JPanel bottomRow = new JPanel(new BorderLayout());
        bottomRow.setOpaque(false);
        bottomRow.setBorder(new EmptyBorder(6, 8, 8, 8));

        totalDebtLabel = new JLabel("Total Outstanding: Rs. 0.00");
        totalDebtLabel.setFont(totalDebtLabel.getFont().deriveFont(Font.BOLD, 13f));
        totalDebtLabel.setForeground(RED);
        bottomRow.add(totalDebtLabel, BorderLayout.WEST);

        JButton resolveBtn = new JButton("Resolve Debt");
        resolveBtn.setBackground(GREEN);
        resolveBtn.setForeground(Color.WHITE);
        resolveBtn.setOpaque(true);
        resolveBtn.setBorderPainted(false);
        resolveBtn.setFocusPainted(false);
        resolveBtn.addActionListener(e -> resolveSelectedOutstanding());
        JPanel btnWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        btnWrap.setOpaque(false);
        btnWrap.add(resolveBtn);
        bottomRow.add(btnWrap, BorderLayout.EAST);

        CardPanel card = new CardPanel(new BorderLayout());
        ((JPanel) card).setMaximumSize(new Dimension(Integer.MAX_VALUE, 310));
        ((JPanel) card).setAlignmentX(Component.LEFT_ALIGNMENT);
        ((JPanel) card).add(scroll,    BorderLayout.CENTER);
        ((JPanel) card).add(bottomRow, BorderLayout.SOUTH);
        return (JPanel) card;
    }

    // ── All credit invoices card ──────────────────────────────────────────────

    private JPanel buildAllCreditSection() {
        // Filter bar
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        filterBar.setOpaque(false);
        filterBar.setAlignmentX(Component.LEFT_ALIGNMENT);

        allSearchField = new JTextField();
        allSearchField.putClientProperty("JTextField.placeholderText", "Search invoice # or customer…");
        allSearchField.setPreferredSize(new Dimension(240, 30));
        allSearchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { scheduleAllSearch(); }
            public void removeUpdate(DocumentEvent e)  { scheduleAllSearch(); }
            public void changedUpdate(DocumentEvent e) { scheduleAllSearch(); }
        });

        statusCombo = new JComboBox<>(new String[]{"All", "Outstanding", "Paid"});
        statusCombo.addActionListener(e -> loadAll());

        fromSpinner = new JSpinner(new SpinnerDateModel());
        fromSpinner.setEditor(new JSpinner.DateEditor(fromSpinner, "yyyy-MM-dd"));
        fromSpinner.setPreferredSize(new Dimension(120, 30));
        fromSpinner.setValue(toStartOfDay(LocalDate.now().with(DayOfWeek.MONDAY).withDayOfMonth(1)));

        toSpinner = new JSpinner(new SpinnerDateModel());
        toSpinner.setEditor(new JSpinner.DateEditor(toSpinner, "yyyy-MM-dd"));
        toSpinner.setPreferredSize(new Dimension(120, 30));
        toSpinner.setValue(toEndOfDay(LocalDate.now()));

        JButton applyBtn = new JButton("Apply");
        applyBtn.addActionListener(e -> loadAll());

        filterBar.add(allSearchField);
        filterBar.add(new JLabel("Status:"));
        filterBar.add(statusCombo);
        filterBar.add(new JLabel("From:"));
        filterBar.add(fromSpinner);
        filterBar.add(new JLabel("To:"));
        filterBar.add(toSpinner);
        filterBar.add(applyBtn);

        // Table
        String[] cols = {"Invoice #", "Date", "Customer", "Total (Rs.)", "Paid (Rs.)", "Balance (Rs.)", "Status", "Resolve By", "Resolved On"};
        allModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        allTable = new JTable(allModel);
        styleTable(allTable);
        allTable.setAutoCreateRowSorter(true);
        allTable.getColumnModel().getColumn(0).setPreferredWidth(130);
        allTable.getColumnModel().getColumn(6).setMaxWidth(90);
        allTable.getColumnModel().getColumn(7).setPreferredWidth(100);
        allTable.getColumnModel().getColumn(8).setPreferredWidth(110);

        allTable.setDefaultRenderer(Object.class, (tbl, val, isSel, hasFocus, row, col) -> {
            JLabel cell = styledCell(val, isSel);
            if (!isSel) {
                if (col == 5) {
                    try { if (Double.parseDouble(val.toString()) > 0.001) cell.setForeground(RED); }
                    catch (Exception ignored) {}
                }
                if (col == 6) {
                    String s = val != null ? val.toString() : "";
                    cell.setForeground(switch (s) {
                        case "Paid"    -> GREEN;
                        case "Partial" -> AMBER;
                        case "Credit"  -> RED;
                        default        -> NAVY;
                    });
                    cell.setFont(cell.getFont().deriveFont(Font.BOLD, 11f));
                }
                if (col == 8 && val != null && !val.toString().equals("—")) {
                    cell.setForeground(GREEN);
                }
            }
            return cell;
        });

        JScrollPane scroll = new JScrollPane(allTable);
        scroll.setBorder(null);
        scroll.setPreferredSize(new Dimension(0, 300));

        CardPanel card = new CardPanel(new BorderLayout(0, 6));
        ((JPanel) card).setMaximumSize(new Dimension(Integer.MAX_VALUE, 380));
        ((JPanel) card).setAlignmentX(Component.LEFT_ALIGNMENT);
        ((JPanel) card).setBorder(new EmptyBorder(0, 0, 0, 0));

        JPanel inner = new JPanel(new BorderLayout(0, 4));
        inner.setOpaque(false);
        inner.setBorder(new EmptyBorder(6, 6, 6, 6));
        inner.add(filterBar, BorderLayout.NORTH);
        inner.add(scroll,    BorderLayout.CENTER);

        ((JPanel) card).add(inner, BorderLayout.CENTER);
        return (JPanel) card;
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private void loadOutstanding() {
        new SwingWorker<List<InvoiceDto>, Void>() {
            @Override protected List<InvoiceDto> doInBackground() {
                return saleService.listCreditInvoices();
            }
            @Override protected void done() {
                try {
                    outstandingInvoices = get();
                    outModel.setRowCount(0);
                    double total = 0;
                    for (InvoiceDto inv : outstandingInvoices) {
                        double bal = inv.netTotal() - inv.paid();
                        total += bal;
                        outModel.addRow(new Object[]{
                                inv.invoiceNo(),
                                inv.date() != null ? SDF.format(inv.date()) : "—",
                                inv.customerName() != null ? inv.customerName() : "—",
                                String.format("%.2f", inv.netTotal()),
                                String.format("%.2f", inv.paid()),
                                String.format("%.2f", bal),
                                inv.stat(),
                                inv.creditResolveDate() != null ? SDF.format(inv.creditResolveDate()) : "—"
                        });
                    }
                    totalDebtLabel.setText(String.format("Total Outstanding: Rs. %.2f", total));
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void loadAll() {
        String search  = allSearchField != null ? allSearchField.getText().trim() : "";
        String status  = statusCombo   != null ? (String) statusCombo.getSelectedItem() : null;
        Date   from    = fromSpinner   != null ? (Date) fromSpinner.getValue() : null;
        Date   to      = toSpinner     != null ? (Date) toSpinner.getValue()   : null;
        String sf      = "All".equals(status) ? null : status;

        new SwingWorker<List<InvoiceDto>, Void>() {
            @Override protected List<InvoiceDto> doInBackground() {
                return saleService.listAllCreditInvoicesFiltered(search, from, to, sf);
            }
            @Override protected void done() {
                try {
                    allInvoices = get();
                    allModel.setRowCount(0);
                    for (InvoiceDto inv : allInvoices) {
                        double bal = inv.netTotal() - inv.paid();
                        allModel.addRow(new Object[]{
                                inv.invoiceNo(),
                                inv.date() != null ? SDF.format(inv.date()) : "—",
                                inv.customerName() != null ? inv.customerName() : "—",
                                String.format("%.2f", inv.netTotal()),
                                String.format("%.2f", inv.paid()),
                                String.format("%.2f", bal),
                                inv.stat(),
                                inv.creditResolveDate() != null ? SDF.format(inv.creditResolveDate()) : "—",
                                inv.resolvedDate() != null ? SDF.format(inv.resolvedDate()) : "—"
                        });
                    }
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void scheduleAllSearch() {
        if (searchDebounce != null && searchDebounce.isRunning()) searchDebounce.stop();
        searchDebounce = new Timer(280, e -> loadAll());
        searchDebounce.setRepeats(false);
        searchDebounce.start();
    }

    // ── Resolve debt dialog ───────────────────────────────────────────────────

    private void resolveSelectedOutstanding() {
        int row = outTable.getSelectedRow();
        if (row < 0 || outstandingInvoices == null || row >= outstandingInvoices.size()) {
            JOptionPane.showMessageDialog(this, "Select an invoice to resolve.", "Resolve", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        InvoiceDto inv = outstandingInvoices.get(row);
        double balance = inv.netTotal() - inv.paid();
        showResolveDialog(inv, balance);
    }

    private void showResolveDialog(InvoiceDto inv, double balance) {
        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Resolve Debt", true);
        dlg.setSize(380, 220);
        dlg.setLocationRelativeTo(this);
        dlg.setLayout(new BorderLayout());

        JPanel form = new JPanel(new GridLayout(4, 2, 8, 10));
        form.setBorder(new EmptyBorder(16, 16, 8, 16));
        form.add(lbl("Invoice"));       form.add(new JLabel(inv.invoiceNo()));
        form.add(lbl("Customer"));      form.add(new JLabel(inv.customerName() != null ? inv.customerName() : "—"));
        form.add(lbl("Balance (Rs.)")); form.add(new JLabel(String.format("%.2f", balance)));

        JFormattedTextField amtField = new JFormattedTextField(java.text.NumberFormat.getNumberInstance());
        amtField.setValue(balance);
        form.add(lbl("Payment (Rs.)")); form.add(amtField);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton cancel  = new JButton("Cancel");
        cancel.addActionListener(e -> dlg.dispose());
        JButton confirm = new JButton("Confirm");
        confirm.setBackground(GREEN);
        confirm.setForeground(Color.WHITE);
        confirm.setOpaque(true);
        confirm.setBorderPainted(false);
        confirm.addActionListener(e -> {
            try {
                double payment = ((Number) amtField.getValue()).doubleValue();
                if (payment <= 0) {
                    JOptionPane.showMessageDialog(dlg, "Payment must be > 0.", "Error", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (payment > balance + 0.001) {
                    JOptionPane.showMessageDialog(dlg,
                            "Payment exceeds outstanding balance of Rs. " + String.format("%.2f", balance),
                            "Error", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                saleService.resolveCreditDebt(inv.invoiceNo(), payment);
                if (cashAccountService != null) {
                    Long empId = SessionContext.current() != null
                            ? SessionContext.current().getEmployee().id() : null;
                    cashAccountService.recordCreditPayment(payment, inv.invoiceNo(), empId);
                }
                dlg.dispose();
                loadOutstanding();
                loadAll();
                JOptionPane.showMessageDialog(this,
                        "Payment of Rs. " + String.format("%.2f", payment) + " recorded.",
                        "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dlg, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        btns.add(cancel);
        btns.add(confirm);
        dlg.add(form, BorderLayout.CENTER);
        dlg.add(btns, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void styleTable(JTable t) {
        t.setRowHeight(34);
        t.setShowGrid(false);
        t.setIntercellSpacing(new Dimension(0, 0));
        t.getTableHeader().setFont(t.getFont().deriveFont(Font.BOLD, 12f));
        t.getTableHeader().setBackground(new Color(0xF7, 0xF8, 0xFA));
        t.getTableHeader().setForeground(TEXT2);
    }

    private JLabel styledCell(Object val, boolean isSel) {
        JLabel cell = new JLabel(val != null ? val.toString() : "");
        cell.setOpaque(true);
        cell.setBorder(new EmptyBorder(0, 8, 0, 8));
        if (isSel) {
            cell.setBackground(new Color(0xBB, 0xDE, 0xFB));
            cell.setForeground(Color.BLACK);
        } else {
            cell.setBackground(Color.WHITE);
            cell.setForeground(Color.BLACK);
        }
        return cell;
    }

    private JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 11f));
        l.setForeground(TEXT2);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JLabel lbl(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT2);
        return l;
    }

    private static Date toStartOfDay(LocalDate d) {
        return Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static Date toEndOfDay(LocalDate d) {
        return Date.from(d.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant());
    }
}
