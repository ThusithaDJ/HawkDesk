package com.olympus.system.hawkdeskpos.frontend.sale;

import com.olympus.system.hawkdeskpos.dto.InvoiceDto;
import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.service.SaleService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.SimpleDateFormat;

/**
 * Find Invoice screen — split: left search, right full detail.
 */
public class FindInvoicePanel extends JPanel {

    private static final Color BG    = new Color(0xF0, 0xF2, 0xF5);
    private static final Color TEXT2 = new Color(0x5A, 0x60, 0x70);
    private static final Color NAVY  = new Color(0x1E, 0x3A, 0x5F);

    private final SaleService saleService;

    private JTextField invoiceNoField;
    private JPanel detailArea;
    private InvoiceDto currentInvoice;

    public FindInvoicePanel(SaleService saleService) {
        this.saleService = saleService;
        setBackground(BG);
        setLayout(new BorderLayout());
        buildUI();
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(14, 0));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Find Invoice");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        header.add(title, BorderLayout.WEST);
        JButton back = new JButton("← Back");
        back.addActionListener(e -> Home.navigate(Home.CARD_DASH));
        JPanel hBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        hBtns.setOpaque(false);
        hBtns.add(back);
        header.add(hBtns, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        // Split content
        JPanel content = new JPanel(new GridLayout(1, 2, 14, 0));
        content.setOpaque(false);
        content.add(buildSearchPanel());
        content.add(buildDetailPanel());

        root.add(content, BorderLayout.CENTER);
        add(root);
    }

    private CardPanel buildSearchPanel() {
        CardPanel c = new CardPanel(new BorderLayout(0, 12));
        ((JPanel)c).setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel title = new JLabel("SEARCH");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 11f));
        title.setForeground(TEXT2);

        JPanel form = new JPanel(new GridLayout(0, 1, 0, 12));
        form.setOpaque(false);

        invoiceNoField = new JTextField();
        invoiceNoField.putClientProperty("JTextField.placeholderText", "INV-YYMMDD-NNNN");

        JButton search = new JButton("Search");
        search.setBackground(NAVY);
        search.setForeground(Color.WHITE);
        search.setOpaque(true);
        search.setBorderPainted(false);
        search.addActionListener(e -> searchInvoice());

        // Allow Enter key
        invoiceNoField.addActionListener(e -> searchInvoice());

        form.add(labeled("Invoice Number", invoiceNoField));
        form.add(search);

        ((JPanel)c).add(title, BorderLayout.NORTH);
        ((JPanel)c).add(form,  BorderLayout.CENTER);
        return c;
    }

    private CardPanel buildDetailPanel() {
        CardPanel c = new CardPanel(new BorderLayout(0, 10));
        ((JPanel)c).setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel title = new JLabel("INVOICE DETAIL");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 11f));
        title.setForeground(TEXT2);
        ((JPanel)c).add(title, BorderLayout.NORTH);

        detailArea = new JPanel();
        detailArea.setOpaque(false);
        detailArea.setLayout(new BoxLayout(detailArea, BoxLayout.Y_AXIS));

        JLabel placeholder = new JLabel("<html><center>Enter an invoice number<br>and click Search</center></html>");
        placeholder.setForeground(TEXT2);
        placeholder.setHorizontalAlignment(SwingConstants.CENTER);
        placeholder.setAlignmentX(Component.CENTER_ALIGNMENT);
        detailArea.add(Box.createVerticalGlue());
        detailArea.add(placeholder);
        detailArea.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(detailArea);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);

        ((JPanel)c).add(scroll, BorderLayout.CENTER);
        return c;
    }

    private void searchInvoice() {
        String invNo = invoiceNoField.getText().trim();
        if (invNo.isEmpty()) return;

        new SwingWorker<InvoiceDto, Void>() {
            @Override protected InvoiceDto doInBackground() { return saleService.findByNumber(invNo); }
            @Override protected void done() {
                try {
                    currentInvoice = get();
                    renderDetail(currentInvoice);
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void renderDetail(InvoiceDto inv) {
        detailArea.removeAll();
        if (inv == null) {
            JLabel notFound = new JLabel("Invoice not found.");
            notFound.setForeground(new Color(0xC6, 0x28, 0x28));
            notFound.setAlignmentX(Component.LEFT_ALIGNMENT);
            detailArea.add(notFound);
            detailArea.revalidate();
            return;
        }

        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        // Invoice header
        JPanel hdr = new JPanel(new GridLayout(0, 2, 8, 6));
        hdr.setOpaque(false);
        hdr.setAlignmentX(Component.LEFT_ALIGNMENT);
        addDetailPair(hdr, "Invoice #:", inv.invoiceNo());
        addDetailPair(hdr, "Date/Time:", inv.date() != null ? fmt.format(inv.date()) : "—");
        addDetailPair(hdr, "Cashier:",   inv.cashierName() != null ? inv.cashierName() : "—");
        addDetailPair(hdr, "Payment:",   inv.paymentMethod());
        addDetailPair(hdr, "Status:",    inv.stat());
        detailArea.add(hdr);
        detailArea.add(Box.createVerticalStrut(12));

        // Line items
        JLabel itemsTitle = new JLabel("Items:");
        itemsTitle.setFont(itemsTitle.getFont().deriveFont(Font.BOLD, 12f));
        itemsTitle.setForeground(TEXT2);
        itemsTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailArea.add(itemsTitle);
        detailArea.add(Box.createVerticalStrut(4));

        for (var line : inv.lines()) {
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setOpaque(false);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            JLabel name = new JLabel(line.itemName() + "  ×" + line.qty());
            JLabel price = new JLabel(String.format("Rs. %.2f", line.lineTotal()));
            price.setForeground(NAVY);
            row.add(name, BorderLayout.WEST);
            row.add(price, BorderLayout.EAST);
            detailArea.add(row);
        }

        detailArea.add(Box.createVerticalStrut(8));
        JSeparator sep = new JSeparator();
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        detailArea.add(sep);
        detailArea.add(Box.createVerticalStrut(8));

        // Totals
        JPanel totals = new JPanel(new GridLayout(0, 2, 8, 4));
        totals.setOpaque(false);
        totals.setAlignmentX(Component.LEFT_ALIGNMENT);
        addDetailPair(totals, "Discount:", String.format("Rs. %.2f", inv.discount()));
        addDetailPair(totals, "TOTAL:",    String.format("Rs. %.2f", inv.total()));
        detailArea.add(totals);
        detailArea.add(Box.createVerticalStrut(16));

        // Actions
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton reprint = new JButton("Reprint Receipt");
        reprint.addActionListener(e -> JOptionPane.showMessageDialog(this, "Print coming soon.", "Print", JOptionPane.INFORMATION_MESSAGE));
        JButton processRet = new JButton("Process Return");
        processRet.addActionListener(e -> Home.navigate(Home.CARD_RETURNS));
        actions.add(reprint);
        if (!"RETURNED".equals(inv.stat()) && !"VOIDED".equals(inv.stat())) {
            actions.add(processRet);
        }
        detailArea.add(actions);

        detailArea.revalidate();
        detailArea.repaint();
    }

    private void addDetailPair(JPanel panel, String label, String value) {
        JLabel l = new JLabel(label);
        l.setForeground(TEXT2);
        l.setFont(l.getFont().deriveFont(12f));
        JLabel v = new JLabel(value);
        v.setFont(v.getFont().deriveFont(Font.BOLD, 12f));
        panel.add(l);
        panel.add(v);
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
