package com.olympus.system.hawkdeskpos.frontend.finance;

import com.olympus.system.hawkdeskpos.db.dao.CashAccount;
import com.olympus.system.hawkdeskpos.dto.TransactionDto;
import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.service.CashAccountService;
import com.olympus.system.hawkdeskpos.service.ReportService;
import com.olympus.system.hawkdeskpos.service.SaleService;
import com.olympus.system.hawkdeskpos.service.SettingsService;
import com.olympus.system.hawkdeskpos.service.StockService;
import com.olympus.system.hawkdeskpos.session.SessionContext;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.List;

public class CashflowPanel extends JPanel
        implements com.olympus.system.hawkdeskpos.frontend.components.Refreshable {

    private static final Color BG     = new Color(0xF0, 0xF2, 0xF5);
    private static final Color TEXT2  = new Color(0x5A, 0x60, 0x70);
    private static final Color TEXT1  = new Color(0x1A, 0x1D, 0x23);
    private static final Color NAVY   = new Color(0x1E, 0x3A, 0x5F);
    private static final Color GREEN  = new Color(0x2E, 0x7D, 0x32);
    private static final Color RED    = new Color(0xC6, 0x28, 0x28);
    private static final Color AMBER  = new Color(0xE6, 0x51, 0x00);
    private static final Color PURPLE = new Color(0x4A, 0x14, 0x8C);
    private static final Color TEAL   = new Color(0x00, 0x69, 0x64);

    private static final String[] PERIODS = { "Today", "This Week", "This Month", "This Year", "Custom" };

    private final ReportService       reportService;
    private final SaleService         saleService;
    private final StockService        stockService;
    private final CashAccountService  cashAccountService;

    // Period controls
    private JComboBox<String> periodCombo;
    private JPanel  customDatePanel;
    private JSpinner fromSpinner, toSpinner;

    // Account row
    private JPanel accountsRow;

    // P&L tiles
    private JLabel revenueVal, discountVal, netRevenueVal, grnCostVal, refundedVal, profitVal;

    // Income breakdown
    private JLabel cashCountVal, cashTotalVal;
    private JLabel cardCountVal, cardTotalVal;
    private JLabel chequeCountVal, chequeTotalVal;
    private JLabel creditCountVal, creditTotalVal;
    private JLabel totalInVal, creditCollectedVal, creditOutstandingVal;

    // Transactions table
    private DefaultTableModel       txTableModel;
    private JTable                  txTable;
    private List<TransactionDto>    currentTxRows = new ArrayList<>();

    public CashflowPanel(ReportService reportService, SettingsService settingsService) {
        this(reportService, settingsService, null, null, null);
    }

    public CashflowPanel(ReportService reportService, SettingsService settingsService,
                         SaleService saleService) {
        this(reportService, settingsService, saleService, null, null);
    }

    public CashflowPanel(ReportService reportService, SettingsService settingsService,
                         SaleService saleService, CashAccountService cashAccountService) {
        this(reportService, settingsService, saleService, cashAccountService, null);
    }

    public CashflowPanel(ReportService reportService, SettingsService settingsService,
                         SaleService saleService, CashAccountService cashAccountService,
                         StockService stockService) {
        this.reportService      = reportService;
        this.saleService        = saleService;
        this.stockService       = stockService;
        this.cashAccountService = cashAccountService;
        setBackground(BG);
        setLayout(new BorderLayout());
        buildUI();
    }

    @Override
    public void refresh() { loadDataAsync(); }

    // ── Build UI ───────────────────────────────────────────────────────────────

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(16, 16, 16, 16));
        root.add(buildTopBar(),  BorderLayout.NORTH);
        root.add(buildContent(), BorderLayout.CENTER);
        add(root);
        loadDataAsync();
    }

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);

        JLabel title = new JLabel("Cash Flow");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        bar.add(title, BorderLayout.WEST);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);

        // Income / Expense recording buttons
        if (cashAccountService != null) {
            JButton incomeBtn = new JButton("+ Income");
            incomeBtn.setBackground(new Color(0x2E, 0x7D, 0x32));
            incomeBtn.setForeground(Color.WHITE);
            incomeBtn.setFocusPainted(false);
            incomeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            incomeBtn.addActionListener(e -> showRecordDialog(true));

            JButton expenseBtn = new JButton("− Expense");
            expenseBtn.setBackground(new Color(0xC6, 0x28, 0x28));
            expenseBtn.setForeground(Color.WHITE);
            expenseBtn.setFocusPainted(false);
            expenseBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            expenseBtn.addActionListener(e -> showRecordDialog(false));

            btns.add(incomeBtn);
            btns.add(expenseBtn);
        }

        JButton refreshBtn = new JButton("\u21ba Refresh");
        refreshBtn.addActionListener(e -> loadDataAsync());
        JButton back = new JButton("\u2190 Back");
        back.addActionListener(e -> Home.navigate(Home.CARD_DASH));
        btns.add(refreshBtn);
        btns.add(back);
        bar.add(btns, BorderLayout.EAST);

        bar.add(buildPeriodRow(), BorderLayout.SOUTH);
        return bar;
    }

    private JPanel buildPeriodRow() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setBorder(new EmptyBorder(8, 0, 0, 0));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(new JLabel("Period:"));

        periodCombo = new JComboBox<>(PERIODS);
        periodCombo.setSelectedItem("Today");
        periodCombo.addActionListener(e -> {
            boolean custom = "Custom".equals(periodCombo.getSelectedItem());
            customDatePanel.setVisible(custom);
            if (!custom) loadDataAsync();
        });
        left.add(periodCombo);

        customDatePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        customDatePanel.setOpaque(false);
        customDatePanel.setVisible(false);

        fromSpinner = new JSpinner(new SpinnerDateModel());
        fromSpinner.setEditor(new JSpinner.DateEditor(fromSpinner, "yyyy-MM-dd"));
        fromSpinner.setPreferredSize(new Dimension(130, 28));
        fromSpinner.setValue(toStartOfDay(LocalDate.now()));

        toSpinner = new JSpinner(new SpinnerDateModel());
        toSpinner.setEditor(new JSpinner.DateEditor(toSpinner, "yyyy-MM-dd"));
        toSpinner.setPreferredSize(new Dimension(130, 28));
        toSpinner.setValue(toEndOfDay(LocalDate.now()));

        JButton applyBtn = new JButton("Apply");
        applyBtn.addActionListener(e -> loadDataAsync());

        customDatePanel.add(new JLabel("From:"));
        customDatePanel.add(fromSpinner);
        customDatePanel.add(new JLabel("To:"));
        customDatePanel.add(toSpinner);
        customDatePanel.add(applyBtn);
        left.add(customDatePanel);

        wrapper.add(left, BorderLayout.WEST);
        return wrapper;
    }

    // ── Content ────────────────────────────────────────────────────────────────

    private JScrollPane buildContent() {
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        if (cashAccountService != null) {
            content.add(Box.createVerticalStrut(32));
            content.add(sectionLabel("ACCOUNTS"));
            content.add(Box.createVerticalStrut(6));
            accountsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            accountsRow.setOpaque(false);
            accountsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            accountsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
            content.add(accountsRow);
        }

        content.add(Box.createVerticalStrut(32));
        content.add(sectionLabel("FINANCIAL SUMMARY"));
        content.add(Box.createVerticalStrut(6));
        content.add(buildPnlSection());
        content.add(Box.createVerticalStrut(32));

        content.add(sectionLabel("INCOME BREAKDOWN  (by payment method)"));
        content.add(Box.createVerticalStrut(6));
        content.add(buildIncomeSection());
        content.add(Box.createVerticalStrut(32));

        content.add(sectionLabel("ALL TRANSACTIONS"));
        content.add(Box.createVerticalStrut(6));
        content.add(buildTransactionsSection());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }

    // ── P&L summary ───────────────────────────────────────────────────────────

    private JPanel buildPnlSection() {
        JPanel row = new JPanel(new GridLayout(1, 6, 10, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        revenueVal    = addInfoCard(row, "Sales Revenue",    "Rs. 0.00", new Color(0x18, 0x5F, 0xA5));
        discountVal   = addInfoCard(row, "Discounts Given",  "Rs. 0.00", AMBER);
        netRevenueVal = addInfoCard(row, "Net Revenue",      "Rs. 0.00", GREEN);
        grnCostVal    = addInfoCard(row, "Stock Purchases",  "Rs. 0.00", RED);
        refundedVal   = addInfoCard(row, "Cash Refunds",     "Rs. 0.00", PURPLE);
        profitVal     = addInfoCard(row, "Net Profit",       "Rs. 0.00", NAVY);
        return row;
    }

    // ── Income breakdown ──────────────────────────────────────────────────────

    private JPanel buildIncomeSection() {
        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel methodRow = new JPanel(new GridLayout(1, 5, 10, 0));
        methodRow.setOpaque(false);
//        methodRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        methodRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        cashCountVal   = addMethodCard(methodRow, "CASH",   GREEN);
        cashTotalVal   = lastTotalLabel(methodRow);
        cardCountVal   = addMethodCard(methodRow, "CARD",   NAVY);
        cardTotalVal   = lastTotalLabel(methodRow);
        chequeCountVal = addMethodCard(methodRow, "CHEQUE", PURPLE);
        chequeTotalVal = lastTotalLabel(methodRow);
        creditCountVal = addMethodCard(methodRow, "CREDIT", AMBER);
        creditTotalVal = lastTotalLabel(methodRow);

        CardPanel sumCard = new CardPanel(new BorderLayout(0, 4));
        sumCard.setBorder(new EmptyBorder(10, 12, 10, 12));
        JLabel sumTitle = new JLabel("TOTAL INCOME");
        sumTitle.setFont(sumTitle.getFont().deriveFont(Font.BOLD, 10f));
        sumTitle.setForeground(TEXT2);
        totalInVal = new JLabel("Rs. 0.00");
        totalInVal.setFont(totalInVal.getFont().deriveFont(Font.BOLD, 16f));
        totalInVal.setForeground(GREEN);
        JPanel creditInfo = new JPanel(new GridLayout(2, 1, 0, 2));
        creditInfo.setOpaque(false);
        creditCollectedVal   = new JLabel("Collected: Rs. 0.00");
        creditCollectedVal.setFont(creditCollectedVal.getFont().deriveFont(11f));
        creditCollectedVal.setForeground(GREEN);
        creditOutstandingVal = new JLabel("Outstanding: Rs. 0.00");
        creditOutstandingVal.setFont(creditOutstandingVal.getFont().deriveFont(11f));
        creditOutstandingVal.setForeground(RED);
        creditInfo.add(creditCollectedVal);
        creditInfo.add(creditOutstandingVal);
        JPanel sumInner = new JPanel(new BorderLayout(0, 4));
        sumInner.setOpaque(false);
        sumInner.add(sumTitle,   BorderLayout.NORTH);
        sumInner.add(totalInVal, BorderLayout.CENTER);
        sumInner.add(creditInfo, BorderLayout.SOUTH);
        sumCard.add(sumInner, BorderLayout.CENTER);

        JButton viewCreditBtn = linkButton("View Credit Invoices \u2192");
        viewCreditBtn.addActionListener(e -> Home.navigate(Home.CARD_CREDIT_INVOICES));
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        btnRow.setOpaque(false);
        btnRow.add(viewCreditBtn);
        sumCard.add(btnRow, BorderLayout.SOUTH);
        methodRow.add(sumCard);

        wrapper.add(methodRow);
        return wrapper;
    }

    private JLabel addMethodCard(JPanel parent, String method, Color accent) {
        CardPanel c = new CardPanel(new BorderLayout(0, 2));
        c.setBorder(new EmptyBorder(10, 12, 10, 12));
        JLabel mLbl = new JLabel(method);
        mLbl.setFont(mLbl.getFont().deriveFont(Font.BOLD, 10f));
        mLbl.setForeground(TEXT2);
        JLabel cntLbl = new JLabel("0 invoices");
        cntLbl.setFont(cntLbl.getFont().deriveFont(11f));
        cntLbl.setName("count");
        JLabel totLbl = new JLabel("Rs. 0.00");
        totLbl.setFont(totLbl.getFont().deriveFont(Font.BOLD, 14f));
        totLbl.setForeground(accent);
        totLbl.setName("total");
        JPanel inner = new JPanel(new GridLayout(3, 1, 0, 2));
        inner.setOpaque(false);
        inner.add(mLbl); inner.add(cntLbl); inner.add(totLbl);
        c.add(inner, BorderLayout.CENTER);
        parent.add(c);
        return cntLbl;
    }

    private JLabel lastTotalLabel(JPanel parent) {
        CardPanel card = (CardPanel) parent.getComponent(parent.getComponentCount() - 1);
        JPanel inner = (JPanel) card.getComponent(0);
        return (JLabel) inner.getComponent(2);
    }

    // ── Transactions section ──────────────────────────────────────────────────

    private JPanel buildTransactionsSection() {
        String[] cols = { "Type", "Reference", "Date / Time", "Description", "Category", "Amount (Rs.)", "Account", "Action" };
        txTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        txTable = new JTable(txTableModel);
        txTable.setRowHeight(32);
        txTable.setShowGrid(false);
        txTable.setIntercellSpacing(new Dimension(0, 0));
        txTable.getTableHeader().setFont(txTable.getFont().deriveFont(Font.BOLD, 12f));
        txTable.getTableHeader().setBackground(new Color(0xF7, 0xF8, 0xFA));
        txTable.getTableHeader().setForeground(TEXT2);
        txTable.setAutoCreateRowSorter(true);

        txTable.getColumnModel().getColumn(0).setPreferredWidth(90);
        txTable.getColumnModel().getColumn(0).setMaxWidth(110);
        txTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        txTable.getColumnModel().getColumn(2).setPreferredWidth(130);
        txTable.getColumnModel().getColumn(3).setPreferredWidth(180);
        txTable.getColumnModel().getColumn(4).setPreferredWidth(100);
        txTable.getColumnModel().getColumn(5).setPreferredWidth(110);
        txTable.getColumnModel().getColumn(6).setPreferredWidth(120);
        txTable.getColumnModel().getColumn(7).setPreferredWidth(85);
        txTable.getColumnModel().getColumn(7).setMaxWidth(95);

        // Type column — colored badge
        txTable.getColumnModel().getColumn(0).setCellRenderer((tbl, value, sel, foc, row, col) -> {
            String v = value != null ? value.toString() : "";
            JLabel lbl = new JLabel(v, SwingConstants.CENTER);
            lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 10f));
            lbl.setOpaque(true);
            lbl.setBackground(sel ? tbl.getSelectionBackground() : tbl.getBackground());
            Color fg = switch (v) {
                case "SALE"           -> GREEN;
                case "GRN"            -> RED;
                case "RETURN_REFUND"  -> PURPLE;
                case "INCOME"         -> TEAL;
                case "EXPENSE"        -> AMBER;
                case "CREDIT_PAYMENT" -> new Color(0x00, 0x6E, 0xC9);
                default               -> TEXT2;
            };
            lbl.setForeground(fg);
            return lbl;
        });

        // Amount column — green for inflows, red for outflows
        txTable.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value,
                    boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(tbl, value, sel, foc, row, col);
                setHorizontalAlignment(SwingConstants.RIGHT);
                int modelRow = tbl.convertRowIndexToModel(row);
                String type  = txTableModel.getValueAt(modelRow, 0).toString();
                boolean isOut = "GRN".equals(type) || "RETURN_REFUND".equals(type) || "EXPENSE".equals(type);
                if (!sel) setForeground(isOut ? RED : GREEN);
                return this;
            }
        });

        // Action column — link-style button
        txTable.getColumnModel().getColumn(7).setCellRenderer((tbl, value, sel, foc, row, col) -> {
            if (cashAccountService == null) return new JLabel();
            String v = value != null ? value.toString() : "";
            JLabel lbl = new JLabel(v);
            lbl.setFont(lbl.getFont().deriveFont(11f));
            lbl.setForeground(NAVY);
            lbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            lbl.setOpaque(true);
            lbl.setBackground(sel ? tbl.getSelectionBackground() : tbl.getBackground());
            return lbl;
        });

        // Click on Action column
        txTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int viewRow = txTable.rowAtPoint(e.getPoint());
                int viewCol = txTable.columnAtPoint(e.getPoint());
                if (viewRow < 0 || viewCol != 7 || cashAccountService == null) return;
                int modelRow = txTable.convertRowIndexToModel(viewRow);
                if (modelRow >= 0 && modelRow < currentTxRows.size()) {
                    moveTransaction(currentTxRows.get(modelRow));
                }
            }
        });

        JScrollPane scroll = new JScrollPane(txTable);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(0xE2, 0xE5, 0xEA)));

        CardPanel card = new CardPanel(new BorderLayout());
        ((JPanel) card).setAlignmentX(Component.LEFT_ALIGNMENT);
        ((JPanel) card).add(scroll, BorderLayout.NORTH);
        return (JPanel) card;
    }

    // ── Move transaction to another account ───────────────────────────────────

    private void moveTransaction(TransactionDto tx) {
        if (cashAccountService == null) return;
        List<CashAccount> accounts = cashAccountService.listAccounts();
        if (accounts.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No cash accounts configured.",
                    "Move Transaction", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String[] names = accounts.stream()
                .map(a -> a.getName() + " (" + a.typeLabel() + ")")
                .toArray(String[]::new);
        boolean isAssign = (tx.accountId() == null);
        String title = isAssign ? "Assign Transaction" : "Move Transaction";
        String msg   = "<html>Transaction: <b>" + (tx.reference() != null ? tx.reference() : tx.description()) + "</b><br>"
                + "Amount: Rs. " + String.format("%,.2f", tx.amount())
                + "<br>Current account: <b>" + tx.accountName() + "</b>"
                + "<br><br>Select destination account:</html>";

        String choice = (String) JOptionPane.showInputDialog(this, msg, title,
                JOptionPane.PLAIN_MESSAGE, null, names, names[0]);
        if (choice == null) return;

        int idx = Arrays.asList(names).indexOf(choice);
        if (idx < 0 || idx >= accounts.size()) return;

        String result = cashAccountService.moveCashTransaction(tx.id(), accounts.get(idx).getId());
        String msg2 = switch (result) {
            case "MOVED"        -> "Moved to \"" + accounts.get(idx).getName() + "\".";
            case "ASSIGNED"     -> "Assigned to \"" + accounts.get(idx).getName() + "\".";
            case "SAME_ACCOUNT" -> "Already on this account — no change.";
            default             -> "An error occurred. Please try again.";
        };
        JOptionPane.showMessageDialog(this, msg2, title, JOptionPane.INFORMATION_MESSAGE);
        loadDataAsync();
    }

    // ── Income / Expense dialog ────────────────────────────────────────────────

    private void showRecordDialog(boolean isIncome) {
        if (cashAccountService == null) return;
        List<CashAccount> accounts = cashAccountService.listAccounts();
        if (accounts.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No cash accounts configured.",
                    isIncome ? "Record Income" : "Record Expense", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String title = isIncome ? "Record Income" : "Record Expense";
        Color  accent = isIncome ? GREEN : RED;

        JPanel form = new JPanel(new GridBagLayout());
        form.setPreferredSize(new Dimension(380, 220));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5, 5, 5, 5);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;

        JTextField amountField = new JTextField();
        JTextField descField   = new JTextField();
        JTextField catField    = new JTextField();

        String[] accountNames = accounts.stream()
                .map(a -> a.getName() + " (" + a.typeLabel() + ")")
                .toArray(String[]::new);
        JComboBox<String> accountCombo = new JComboBox<>(accountNames);

        JSpinner dateSpinner = new JSpinner(new SpinnerDateModel());
        dateSpinner.setEditor(new JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd"));
        dateSpinner.setValue(new Date());

        addRow(form, gc, 0, "Amount (Rs.):", amountField);
        addRow(form, gc, 1, "Description:", descField);
        addRow(form, gc, 2, "Category:", catField);
        addRow(form, gc, 3, "Account:", accountCombo);
        addRow(form, gc, 4, "Date:", dateSpinner);

        JLabel header = new JLabel(title);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 14f));
        header.setForeground(accent);
        header.setBorder(new EmptyBorder(0, 0, 8, 0));

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(header, BorderLayout.NORTH);
        wrapper.add(form,   BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(this, wrapper, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        double amount;
        try {
            amount = Double.parseDouble(amountField.getText().trim().replace(",", ""));
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Please enter a valid positive amount.",
                    title, JOptionPane.WARNING_MESSAGE);
            return;
        }

        String desc     = descField.getText().trim();
        String category = catField.getText().trim();
        if (desc.isEmpty()) desc = isIncome ? "Income" : "Expense";

        int selectedIdx = accountCombo.getSelectedIndex();
        Long accountId  = accounts.get(selectedIdx).getId();
        Date txDate     = (Date) dateSpinner.getValue();

        Long empId = SessionContext.current() != null
                ? SessionContext.current().getEmployee().id() : null;

        if (isIncome) {
            cashAccountService.recordManualIncome(amount, desc, category.isEmpty() ? null : category,
                    accountId, txDate, empId);
        } else {
            cashAccountService.recordManualExpense(amount, desc, category.isEmpty() ? null : category,
                    accountId, txDate, empId);
        }

        JOptionPane.showMessageDialog(this,
                (isIncome ? "Income" : "Expense") + " of Rs. " + String.format("%,.2f", amount) + " recorded.",
                title, JOptionPane.INFORMATION_MESSAGE);
        loadDataAsync();
    }

    private static void addRow(JPanel panel, GridBagConstraints gc, int row, String label, JComponent field) {
        gc.gridx = 0; gc.gridy = row; gc.weightx = 0.3;
        panel.add(new JLabel(label), gc);
        gc.gridx = 1; gc.weightx = 0.7;
        panel.add(field, gc);
    }

    // ── Data loading ───────────────────────────────────────────────────────────

    private void loadAccountsAsync() {
        if (cashAccountService == null || accountsRow == null) return;
        new SwingWorker<List<CashAccount>, Void>() {
            @Override protected List<CashAccount> doInBackground() {
                return cashAccountService.listAccounts();
            }
            @Override protected void done() {
                try {
                    List<CashAccount> accs = get();
                    accountsRow.removeAll();
                    for (CashAccount acc : accs) accountsRow.add(buildAccountBadge(acc));
                    CardPanel mc = new CardPanel(new BorderLayout(0, 4));
                    mc.setBorder(new EmptyBorder(10, 12, 10, 12));
                    mc.setPreferredSize(new Dimension(160, 80));
                    JButton mgr = linkButton("Manage Accounts \u2192");
                    mgr.addActionListener(e -> Home.navigate(Home.CARD_CASH_ACCOUNTS));
                    mc.add(mgr, BorderLayout.CENTER);
                    accountsRow.add(mc);
                    accountsRow.revalidate();
                    accountsRow.repaint();
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private CardPanel buildAccountBadge(CashAccount acc) {
        Color typeColor = switch (acc.getAccountType()) {
            case CASH_DRAWER -> GREEN;
            case SAVINGS     -> NAVY;
            case CURRENT     -> PURPLE;
        };
        CardPanel c = new CardPanel(new BorderLayout(0, 3));
        c.setBorder(new EmptyBorder(10, 12, 10, 12));
        c.setPreferredSize(new Dimension(200, 80));
        JLabel typeLbl = new JLabel(acc.typeLabel());
        typeLbl.setFont(typeLbl.getFont().deriveFont(Font.BOLD, 9f));
        typeLbl.setForeground(typeColor);
        JLabel nameLbl = new JLabel(acc.getName());
        nameLbl.setFont(nameLbl.getFont().deriveFont(Font.BOLD, 12f));
        JLabel balLbl = new JLabel(String.format("Rs. %,.2f", acc.getBalance()));
        balLbl.setFont(balLbl.getFont().deriveFont(Font.BOLD, 15f));
        balLbl.setForeground(typeColor);
        JPanel inner = new JPanel(new GridLayout(3, 1, 0, 1));
        inner.setOpaque(false);
        inner.add(typeLbl); inner.add(nameLbl); inner.add(balLbl);
        c.add(inner, BorderLayout.CENTER);
        return c;
    }

    private void loadDataAsync() {
        Date[] range = getDateRange();
        loadAccountsAsync();

        // Financial summary
        new SwingWorker<ReportService.CashflowData, Void>() {
            @Override protected ReportService.CashflowData doInBackground() {
                try { return reportService.getCashflowData(range[0], range[1]); }
                catch (Exception e) { return null; }
            }
            @Override protected void done() {
                try {
                    ReportService.CashflowData d = get();
                    if (d == null) return;

                    cashCountVal  .setText(d.cash().count()   + " invoice" + plural(d.cash().count()));
                    cashTotalVal  .setText("Rs. " + String.format("%.2f", d.cash().total()));
                    cardCountVal  .setText(d.card().count()   + " invoice" + plural(d.card().count()));
                    cardTotalVal  .setText("Rs. " + String.format("%.2f", d.card().total()));
                    chequeCountVal.setText(d.cheque().count() + " invoice" + plural(d.cheque().count()));
                    chequeTotalVal.setText("Rs. " + String.format("%.2f", d.cheque().total()));
                    creditCountVal.setText(d.creditIssued().count() + " invoice" + plural(d.creditIssued().count()));
                    creditTotalVal.setText("Rs. " + String.format("%.2f", d.creditIssued().total()));

                    double totalIn = d.cash().total() + d.card().total()
                            + d.cheque().total() + d.creditIssued().total();
                    totalInVal.setText("Rs. " + String.format("%.2f", totalIn));
                    creditCollectedVal  .setText("Collected: Rs. "   + String.format("%.2f", d.creditCollected()));
                    creditOutstandingVal.setText("Outstanding: Rs. " + String.format("%.2f", d.creditOutstanding()));

                    double netRev = totalIn - d.discountGiven();
                    double profit = netRev - d.grnCost() - d.cashRefunded();
                    revenueVal   .setText("Rs. " + String.format("%,.2f", totalIn));
                    discountVal  .setText("Rs. " + String.format("%,.2f", d.discountGiven()));
                    netRevenueVal.setText("Rs. " + String.format("%,.2f", netRev));
                    grnCostVal   .setText("Rs. " + String.format("%,.2f", d.grnCost()));
                    refundedVal  .setText("Rs. " + String.format("%,.2f", d.cashRefunded()));
                    profitVal    .setText("Rs. " + String.format("%,.2f", profit));
                    profitVal    .setForeground(profit >= 0 ? GREEN : RED);
                } catch (Exception ignored) {}
            }
        }.execute();

        // Unified transaction table
        if (cashAccountService != null && txTableModel != null) {
            new SwingWorker<List<TransactionDto>, Void>() {
                @Override protected List<TransactionDto> doInBackground() {
                    return cashAccountService.listTransactions(range[0], range[1]);
                }
                @Override protected void done() {
                    try {
                        List<TransactionDto> rows = get();
                        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                        currentTxRows.clear();
                        txTableModel.setRowCount(0);
                        for (TransactionDto t : rows) {
                            currentTxRows.add(t);
                            String action = t.accountId() != null ? "Move \u2192" : "Assign \u2192";
                            txTableModel.addRow(new Object[]{
                                    t.type(),
                                    t.reference() != null ? t.reference() : "—",
                                    t.date() != null ? fmt.format(t.date()) : "—",
                                    t.description() != null ? t.description() : "—",
                                    t.category() != null ? t.category() : "—",
                                    String.format("%,.2f", t.amount()),
                                    t.accountName(),
                                    action
                            });
                        }
                    } catch (Exception ignored) {}
                }
            }.execute();
        }
    }

    // ── Period helpers ─────────────────────────────────────────────────────────

    private Date[] getDateRange() {
        String period = (String) periodCombo.getSelectedItem();
        LocalDate today = LocalDate.now();
        return switch (period) {
            case "Today"      -> new Date[]{ toStartOfDay(today),                        toEndOfDay(today) };
            case "This Week"  -> new Date[]{ toStartOfDay(today.with(DayOfWeek.MONDAY)), toEndOfDay(today) };
            case "This Month" -> new Date[]{ toStartOfDay(today.withDayOfMonth(1)),       toEndOfDay(today) };
            case "This Year"  -> new Date[]{ toStartOfDay(today.withDayOfYear(1)),        toEndOfDay(today) };
            case "Custom"     -> new Date[]{ (Date) fromSpinner.getValue(), (Date) toSpinner.getValue() };
            default           -> new Date[]{ toStartOfDay(today),                        toEndOfDay(today) };
        };
    }

    private static Date toStartOfDay(LocalDate d) {
        return Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static Date toEndOfDay(LocalDate d) {
        return Date.from(d.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant());
    }

    private static String plural(long n) { return n == 1 ? "" : "s"; }

    // ── Component helpers ──────────────────────────────────────────────────────

    private JLabel sectionLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 11f));
        lbl.setForeground(TEXT2);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        return lbl;
    }

    private JLabel addInfoCard(JPanel parent, String label, String initial, Color valueColor) {
        CardPanel c = new CardPanel(new BorderLayout(0, 4));
        c.setBorder(new EmptyBorder(12, 14, 12, 14));
        JLabel l = new JLabel(label);
        l.setFont(l.getFont().deriveFont(10f));
        l.setForeground(TEXT2);
        JLabel v = new JLabel(initial);
        v.setFont(v.getFont().deriveFont(Font.BOLD, 15f));
        v.setForeground(valueColor);
        c.add(l, BorderLayout.NORTH);
        c.add(v, BorderLayout.CENTER);
        parent.add(c);
        return v;
    }

    private JButton linkButton(String text) {
        JButton btn = new JButton(text);
        btn.setForeground(NAVY);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }
}
