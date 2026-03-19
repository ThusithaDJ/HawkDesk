package com.olympus.system.hawkdeskpos.frontend.sale;

import com.olympus.system.hawkdeskpos.dto.InvoiceDto;
import com.olympus.system.hawkdeskpos.dto.SaleLineDto;
import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.frontend.components.ConfirmDialog;
import com.olympus.system.hawkdeskpos.service.ReturnService;
import com.olympus.system.hawkdeskpos.service.SaleService;
import com.olympus.system.hawkdeskpos.session.SessionContext;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Goods Return screen.
 * Search by invoice → select items + qty + reason → refund method → confirm.
 */
public class GoodsReturnPanel extends JPanel {

    private static final Color BG    = new Color(0xF0, 0xF2, 0xF5);
    private static final Color TEXT2 = new Color(0x5A, 0x60, 0x70);
    private static final Color NAVY  = new Color(0x1E, 0x3A, 0x5F);
    private static final Color RED   = new Color(0xC6, 0x28, 0x28);

    private final SaleService   saleService;
    private final ReturnService returnService;

    private JTextField invoiceField;
    private InvoiceDto currentInvoice;
    private JPanel itemsPanel;
    private final List<JCheckBox>          checkBoxes  = new ArrayList<>();
    private final List<JSpinner>           qtySpinners = new ArrayList<>();
    private final List<JComboBox<String>>  reasonCombos= new ArrayList<>();
    private JComboBox<String> refundMethod;
    private JLabel totalRefundLabel;

    public GoodsReturnPanel(SaleService saleService, ReturnService returnService) {
        this.saleService   = saleService;
        this.returnService = returnService;
        setBackground(BG);
        setLayout(new BorderLayout());
        buildUI();
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Process Return");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        header.add(title, BorderLayout.WEST);
        JButton back = new JButton("← Back");
        back.addActionListener(e -> Home.navigate(Home.CARD_HIST));
        JPanel hBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        hBtns.setOpaque(false);
        hBtns.add(back);
        header.add(hBtns, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(buildInvoiceSearch());
        content.add(Box.createVerticalStrut(12));
        content.add(buildItemsSection());
        content.add(Box.createVerticalStrut(12));
        content.add(buildRefundSection());
        content.add(Box.createVerticalStrut(16));
        content.add(buildActionRow());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        root.add(scroll, BorderLayout.CENTER);
        add(root);
    }

    private CardPanel buildInvoiceSearch() {
        CardPanel c = sectionCard("ORIGINAL INVOICE");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        invoiceField = new JTextField(22);
        invoiceField.putClientProperty("JTextField.placeholderText", "e.g. INV-250101-0001");
        invoiceField.addActionListener(e -> lookupInvoice());
        JButton btn = new JButton("Load Invoice");
        btn.setBackground(NAVY);
        btn.setForeground(Color.WHITE);
        btn.setOpaque(true);
        btn.setBorderPainted(false);
        btn.addActionListener(e -> lookupInvoice());
        row.add(invoiceField);
        row.add(btn);
        ((JPanel)c).add(row, BorderLayout.CENTER);
        return c;
    }

    private CardPanel buildItemsSection() {
        CardPanel c = sectionCard("SELECT ITEMS TO RETURN");
        itemsPanel = new JPanel();
        itemsPanel.setOpaque(false);
        itemsPanel.setLayout(new BoxLayout(itemsPanel, BoxLayout.Y_AXIS));
        itemsPanel.add(new JLabel("  Load an invoice to see items."));
        JScrollPane scroll = new JScrollPane(itemsPanel);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setPreferredSize(new Dimension(0, 180));
        ((JPanel)c).add(scroll, BorderLayout.CENTER);
        return c;
    }

    private CardPanel buildRefundSection() {
        CardPanel c = sectionCard("REFUND METHOD");
        JPanel grid = new JPanel(new GridLayout(1, 2, 12, 0));
        grid.setOpaque(false);
        refundMethod = new JComboBox<>(new String[]{"Cash", "Store Credit", "Exchange", "Void"});
        totalRefundLabel = new JLabel("Rs. 0.00");
        totalRefundLabel.setFont(totalRefundLabel.getFont().deriveFont(Font.BOLD, 18f));
        totalRefundLabel.setForeground(NAVY);
        grid.add(labeled("Refund Method", refundMethod));
        grid.add(labeled("Total Refund",  totalRefundLabel));
        ((JPanel)c).add(grid, BorderLayout.CENTER);
        return c;
    }

    private JPanel buildActionRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> Home.navigate(Home.CARD_HIST));
        JButton process = new JButton("Process Return");
        process.setBackground(RED);
        process.setForeground(Color.WHITE);
        process.setOpaque(true);
        process.setBorderPainted(false);
        process.addActionListener(e -> processReturn());
        row.add(cancel);
        row.add(process);
        return row;
    }

    private void lookupInvoice() {
        String invNo = invoiceField.getText().trim();
        if (invNo.isEmpty()) return;
        new SwingWorker<InvoiceDto, Void>() {
            @Override protected InvoiceDto doInBackground() { return saleService.findByNumber(invNo); }
            @Override protected void done() {
                try { currentInvoice = get(); renderItems(currentInvoice); } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void renderItems(InvoiceDto inv) {
        itemsPanel.removeAll();
        checkBoxes.clear();
        qtySpinners.clear();
        reasonCombos.clear();

        if (inv == null) {
            JLabel nf = new JLabel("  Invoice not found.");
            nf.setForeground(RED);
            itemsPanel.add(nf);
            itemsPanel.revalidate();
            return;
        }

        JPanel hdr = new JPanel(new GridLayout(1, 5, 8, 0));
        hdr.setOpaque(false);
        hdr.setBorder(new EmptyBorder(0, 4, 6, 4));
        for (String col : new String[]{"", "Item", "Orig. Qty", "Return Qty", "Reason"}) {
            JLabel l = new JLabel(col);
            l.setFont(l.getFont().deriveFont(Font.BOLD, 11f));
            l.setForeground(TEXT2);
            hdr.add(l);
        }
        itemsPanel.add(hdr);

        for (SaleLineDto line : inv.lines()) {
            itemsPanel.add(buildLineRow(line));
        }
        itemsPanel.revalidate();
        itemsPanel.repaint();
    }

    private JPanel buildLineRow(SaleLineDto line) {
        JPanel row = new JPanel(new GridLayout(1, 5, 8, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(6, 4, 6, 4));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JCheckBox cb = new JCheckBox();
        cb.setOpaque(false);
        cb.addActionListener(e -> recalcTotal());
        checkBoxes.add(cb);

        JLabel name = new JLabel("<html><b>" + line.itemName() + "</b></html>");
        JLabel origQty = new JLabel(String.valueOf(line.qty()));
        origQty.setForeground(TEXT2);

        JSpinner spinner = new JSpinner(new SpinnerNumberModel(1, 1, Math.max(1, line.qty()), 1));
        spinner.addChangeListener(e -> recalcTotal());
        qtySpinners.add(spinner);

        JComboBox<String> reason = new JComboBox<>(
                new String[]{"Damaged", "Wrong item", "Customer change of mind", "Other"});
        reasonCombos.add(reason);

        row.add(cb); row.add(name); row.add(origQty); row.add(spinner); row.add(reason);
        return row;
    }

    private void recalcTotal() {
        if (currentInvoice == null) return;
        List<SaleLineDto> lines = currentInvoice.lines();
        double total = 0;
        for (int i = 0; i < checkBoxes.size(); i++) {
            if (checkBoxes.get(i).isSelected() && i < lines.size()) {
                int qty = ((Number) qtySpinners.get(i).getValue()).intValue();
                total += lines.get(i).unitPrice() * qty;
            }
        }
        totalRefundLabel.setText(String.format("Rs. %.2f", total));
    }

    private void processReturn() {
        if (currentInvoice == null) {
            JOptionPane.showMessageDialog(this, "Load an invoice first.", "Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<Integer> selectedIdxs = new ArrayList<>();
        for (int i = 0; i < checkBoxes.size(); i++) {
            if (checkBoxes.get(i).isSelected()) selectedIdxs.add(i);
        }
        if (selectedIdxs.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select at least one item.", "Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String confirmMsg = "Process return of " + selectedIdxs.size() + " item(s)?\nTotal: " + totalRefundLabel.getText();
        if (!ConfirmDialog.show(this, "Confirm Return", confirmMsg, "Process Return")) return;

        Long empId = SessionContext.current() != null ? SessionContext.current().getEmployee().id() : null;
        List<SaleLineDto> origLines = currentInvoice.lines();

        // Build stockId → qty map; use the first selected reason
        Map<Integer, Integer> returnLines = new LinkedHashMap<>();
        String commonReason = "Return";
        for (int idx : selectedIdxs) {
            if (idx < origLines.size()) {
                int stockId = origLines.get(idx).stockId();
                int qty     = ((Number) qtySpinners.get(idx).getValue()).intValue();
                returnLines.put(stockId, qty);
                commonReason = (String) reasonCombos.get(idx).getSelectedItem();
            }
        }

        final String method = (String) refundMethod.getSelectedItem();
        final String reason = commonReason;

        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                returnService.processReturn(currentInvoice.invoiceNo(), returnLines, reason, method, empId);
                return null;
            }
            @Override protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(GoodsReturnPanel.this,
                            "Return processed.", "Return Complete", JOptionPane.INFORMATION_MESSAGE);
                    currentInvoice = null;
                    invoiceField.setText("");
                    itemsPanel.removeAll();
                    itemsPanel.add(new JLabel("  Load an invoice to see items."));
                    itemsPanel.revalidate();
                    totalRefundLabel.setText("Rs. 0.00");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(GoodsReturnPanel.this,
                            "Failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private CardPanel sectionCard(String title) {
        CardPanel c = new CardPanel(new BorderLayout(0, 10));
        ((JPanel)c).setBorder(new EmptyBorder(14, 14, 14, 14));
        ((JPanel)c).setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));
        ((JPanel)c).setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel t = new JLabel(title);
        t.setFont(t.getFont().deriveFont(Font.BOLD, 11f));
        t.setForeground(TEXT2);
        ((JPanel)c).add(t, BorderLayout.NORTH);
        return c;
    }

    private JPanel labeled(String lbl, JComponent comp) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        JLabel l = new JLabel(lbl);
        l.setFont(l.getFont().deriveFont(12f));
        l.setForeground(TEXT2);
        p.add(l, BorderLayout.NORTH);
        p.add(comp, BorderLayout.CENTER);
        return p;
    }
}
