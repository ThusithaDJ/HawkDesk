package com.olympus.system.hawkdeskpos.frontend.sale;

import com.olympus.system.hawkdeskpos.dto.CustomerDto;
import com.olympus.system.hawkdeskpos.dto.InvoiceDto;
import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.frontend.components.Refreshable;
import com.olympus.system.hawkdeskpos.service.CustomerService;
import com.olympus.system.hawkdeskpos.service.SaleService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Shows all invoices (credit and cash/card) for a specific customer.
 * Allows resolving outstanding credit debts.
 */
public class CustomerDetailsPanel extends JPanel implements Refreshable {

    private static final Color BG    = new Color(0xF0, 0xF2, 0xF5);
    private static final Color NAVY  = new Color(0x1E, 0x3A, 0x5F);
    private static final Color TEXT2 = new Color(0x5A, 0x60, 0x70);
    private static final Color GREEN = new Color(0x2E, 0x7D, 0x32);
    private static final Color AMBER = new Color(0xE6, 0x51, 0x00);
    private static final Color RED   = new Color(0xC6, 0x28, 0x28);

    private static final SimpleDateFormat SDF = new SimpleDateFormat("dd/MM/yyyy");

    private final SaleService     saleService;
    private final CustomerService customerService;

    private CustomerDto customer;

    private JLabel titleLabel;
    private JLabel subtitleLabel;
    private JLabel totalPurchasesLabel;
    private JLabel outstandingLabel;

    private DefaultTableModel tableModel;
    private JTable            table;
    private List<InvoiceDto>  invoices;

    private JButton resolveBtn;

    public CustomerDetailsPanel(SaleService saleService, CustomerService customerService) {
        this.saleService     = saleService;
        this.customerService = customerService;
        setBackground(BG);
        setLayout(new BorderLayout());
        buildUI();
    }

    /** Call this before navigating to this panel. */
    public void setCustomer(CustomerDto customer) {
        this.customer = customer;
    }

    @Override
    public void refresh() {
        if (customer == null) return;
        titleLabel.setText(customer.name());
        String phone   = customer.phone() != null && !customer.phone().isEmpty() ? customer.phone() : "—";
        String address = customer.address() != null && !customer.address().isEmpty() ? customer.address() : "—";
        subtitleLabel.setText("Phone: " + phone + "   |   Address: " + address);
        loadData();
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        // ── Header ────────────────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setOpaque(false);

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);

        titleLabel = new JLabel("Customer Details");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 20f));
        titleRow.add(titleLabel, BorderLayout.WEST);

        JButton back = new JButton("← Back");
        back.addActionListener(e -> Home.navigate(Home.CARD_CUSTOMERS));
        JPanel hBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        hBtns.setOpaque(false);
        hBtns.add(back);
        titleRow.add(hBtns, BorderLayout.EAST);

        subtitleLabel = new JLabel(" ");
        subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(12f));
        subtitleLabel.setForeground(TEXT2);

        header.add(titleRow,    BorderLayout.NORTH);
        header.add(subtitleLabel, BorderLayout.CENTER);

        // ── Summary cards ─────────────────────────────────────────────────────
        JPanel summaryRow = new JPanel(new GridLayout(1, 2, 12, 0));
        summaryRow.setOpaque(false);

        totalPurchasesLabel = makeSummaryLabel("Total Purchases", "Rs. 0.00", NAVY);
        outstandingLabel    = makeSummaryLabel("Outstanding Debt", "Rs. 0.00", RED);
        summaryRow.add(totalPurchasesLabel.getParent());
        summaryRow.add(outstandingLabel.getParent());

        JPanel headerBlock = new JPanel(new BorderLayout(0, 8));
        headerBlock.setOpaque(false);
        headerBlock.add(header,     BorderLayout.NORTH);
        headerBlock.add(summaryRow, BorderLayout.SOUTH);
        root.add(headerBlock, BorderLayout.NORTH);

        // ── Table ─────────────────────────────────────────────────────────────
        String[] cols = {"Invoice #", "Date", "Payment", "Total (Rs.)", "Paid (Rs.)",
                         "Balance (Rs.)", "Status", "Resolve By"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel);
        table.setRowHeight(36);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.getTableHeader().setFont(table.getFont().deriveFont(Font.BOLD, 12f));
        table.getTableHeader().setBackground(new Color(0xF7, 0xF8, 0xFA));
        table.getTableHeader().setForeground(TEXT2);
        table.getColumnModel().getColumn(0).setPreferredWidth(140);
        table.getColumnModel().getColumn(2).setMaxWidth(90);
        table.getColumnModel().getColumn(6).setMaxWidth(90);

        table.setDefaultRenderer(Object.class, (tbl, val, isSel, hasFocus, row, col) -> {
            JLabel cell = new JLabel(val != null ? val.toString() : "");
            cell.setOpaque(true);
            cell.setBorder(new EmptyBorder(0, 8, 0, 8));
            if (isSel) {
                cell.setBackground(tbl.getSelectionBackground());
                cell.setForeground(tbl.getSelectionForeground());
            } else {
                cell.setBackground(row % 2 == 0 ? Color.WHITE : new Color(0xF7, 0xF8, 0xFB));
                cell.setForeground(Color.BLACK);
                if (col == 5) {
                    String balStr = val != null ? val.toString() : "0.00";
                    try {
                        double bal = Double.parseDouble(balStr);
                        if (bal > 0.001) cell.setForeground(RED);
                    } catch (NumberFormatException ignored) {}
                }
                if (col == 6) {
                    String status = val != null ? val.toString() : "";
                    cell.setForeground(switch (status) {
                        case "Partial" -> AMBER;
                        case "Credit"  -> RED;
                        case "Paid"    -> GREEN;
                        default        -> NAVY;
                    });
                    cell.setFont(cell.getFont().deriveFont(Font.BOLD, 11f));
                }
                if (col == 2) {
                    String pm = val != null ? val.toString() : "";
                    cell.setForeground(switch (pm) {
                        case "CREDIT" -> RED;
                        case "CARD"   -> NAVY;
                        default       -> TEXT2;
                    });
                }
            }
            return cell;
        });

        CardPanel tableCard = new CardPanel(new BorderLayout());
        tableCard.add(new JScrollPane(table));

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        actionRow.setOpaque(false);

        resolveBtn = new JButton("Resolve Debt");
        resolveBtn.setBackground(GREEN);
        resolveBtn.setForeground(Color.WHITE);
        resolveBtn.setOpaque(true);
        resolveBtn.setBorderPainted(false);
        resolveBtn.setFocusPainted(false);
        resolveBtn.addActionListener(e -> resolveSelected());
        actionRow.add(resolveBtn);

        tableCard.add(actionRow, BorderLayout.SOUTH);
        root.add(tableCard, BorderLayout.CENTER);
        add(root);
    }

    private void loadData() {
        new SwingWorker<List<InvoiceDto>, Void>() {
            @Override protected List<InvoiceDto> doInBackground() {
                return saleService.listInvoicesByCustomer(customer.customerId());
            }
            @Override protected void done() {
                try {
                    invoices = get();
                    tableModel.setRowCount(0);
                    double totalPurchases  = 0;
                    double totalOutstanding = 0;
                    for (InvoiceDto inv : invoices) {
                        double balance = inv.netTotal() - inv.paid();
                        if (!"Void".equals(inv.stat())) totalPurchases += inv.netTotal();
                        if (balance > 0.001 && "CREDIT".equals(inv.paymentMethod())) {
                            totalOutstanding += balance;
                        }
                        String resolveBy = inv.creditResolveDate() != null
                                ? SDF.format(inv.creditResolveDate()) : "—";
                        String pm = inv.paymentMethod() != null ? inv.paymentMethod() : "CASH";
                        tableModel.addRow(new Object[]{
                                inv.invoiceNo(),
                                inv.date() != null ? SDF.format(inv.date()) : "—",
                                pm,
                                String.format("%.2f", inv.netTotal()),
                                String.format("%.2f", inv.paid()),
                                String.format("%.2f", balance),
                                inv.stat(),
                                resolveBy
                        });
                    }
                    totalPurchasesLabel.setText("Rs. " + String.format("%.2f", totalPurchases));
                    outstandingLabel.setText("Rs. " + String.format("%.2f", totalOutstanding));
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void resolveSelected() {
        int row = table.getSelectedRow();
        if (row < 0 || invoices == null || row >= invoices.size()) {
            JOptionPane.showMessageDialog(this, "Select a credit invoice to resolve.",
                    "Resolve", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        InvoiceDto inv = invoices.get(row);
        if (!"CREDIT".equals(inv.paymentMethod())) {
            JOptionPane.showMessageDialog(this, "Only credit invoices can be resolved.",
                    "Resolve", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        double balance = inv.netTotal() - inv.paid();
        if (balance <= 0.001) {
            JOptionPane.showMessageDialog(this, "This invoice is already fully paid.",
                    "Resolve", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Resolve Debt", true);
        dlg.setSize(380, 220);
        dlg.setLocationRelativeTo(this);
        dlg.setLayout(new BorderLayout());

        JPanel form = new JPanel(new GridLayout(4, 2, 8, 10));
        form.setBorder(new EmptyBorder(16, 16, 8, 16));
        form.add(lbl("Invoice"));       form.add(new JLabel(inv.invoiceNo()));
        form.add(lbl("Customer"));      form.add(new JLabel(customer.name()));
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
                dlg.dispose();
                loadData();
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

    /** Creates a summary card widget and returns the value label. */
    private JLabel makeSummaryLabel(String title, String value, Color valueColor) {
        CardPanel card = new CardPanel(new BorderLayout(0, 4));
        card.setBorder(new EmptyBorder(12, 16, 12, 16));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(titleLbl.getFont().deriveFont(11f));
        titleLbl.setForeground(TEXT2);

        JLabel valueLbl = new JLabel(value);
        valueLbl.setFont(valueLbl.getFont().deriveFont(Font.BOLD, 16f));
        valueLbl.setForeground(valueColor);

        card.add(titleLbl, BorderLayout.NORTH);
        card.add(valueLbl, BorderLayout.CENTER);
        return valueLbl;
    }

    private JLabel lbl(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT2);
        return l;
    }
}
