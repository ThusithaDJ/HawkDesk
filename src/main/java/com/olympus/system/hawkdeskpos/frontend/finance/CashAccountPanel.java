package com.olympus.system.hawkdeskpos.frontend.finance;

import com.olympus.system.hawkdeskpos.db.dao.CashAccount;
import com.olympus.system.hawkdeskpos.db.dao.Expense;
import com.olympus.system.hawkdeskpos.db.dao.Income;
import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.service.CashAccountService;
import com.olympus.system.hawkdeskpos.session.SessionContext;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.List;

/**
 * Cash Account Management screen.
 *
 * Left panel  — account cards (Cash Drawer + bank accounts).
 *               Each shows balance and Add / Withdraw / Adjust buttons.
 * Right panel — transaction ledger for the selected account (income + expense merged).
 */
public class CashAccountPanel extends JPanel
        implements com.olympus.system.hawkdeskpos.frontend.components.Refreshable {

    private static final Color BG    = new Color(0xF0, 0xF2, 0xF5);
    private static final Color NAVY  = new Color(0x1E, 0x3A, 0x5F);
    private static final Color TEXT2 = new Color(0x5A, 0x60, 0x70);
    private static final Color GREEN = new Color(0x2E, 0x7D, 0x32);
    private static final Color RED   = new Color(0xC6, 0x28, 0x28);

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    private final CashAccountService cashAccountService;

    private static final String[] PERIODS = {"Today", "This Week", "This Month", "This Year", "All Time", "Custom"};

    // Account list panel (rebuilt on refresh)
    private JPanel accountsColumn;

    // Detail panel (right)
    private JLabel   detailAccountName;
    private JLabel   detailBalanceLabel;
    private DefaultTableModel ledgerModel;
    private JLabel   noSelectionHint;
    private JPanel   detailContent;
    private CashAccount selectedAccount;

    // Ledger period filter
    private String   ledgerPeriod = "This Month";
    private JSpinner ledgerFromSpinner, ledgerToSpinner;
    private JPanel   ledgerCustomPanel;

    public CashAccountPanel(CashAccountService cashAccountService) {
        this.cashAccountService = cashAccountService;
        setBackground(BG);
        setLayout(new BorderLayout());
        buildUI();
    }

    @Override
    public void refresh() { loadAccounts(); }

    // ── UI construction ───────────────────────────────────────────────────────

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Cash & Accounts");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        header.add(title, BorderLayout.WEST);

        JPanel hBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        hBtns.setOpaque(false);
        JButton transferBtn = new JButton("⇄ Transfer");
        transferBtn.addActionListener(e -> showTransferDialog());
        JButton addAccBtn = new JButton("+ Add Account");
        addAccBtn.addActionListener(e -> showAddAccountDialog());
        JButton refreshBtn = new JButton("↺ Refresh");
        refreshBtn.addActionListener(e -> loadAccounts());
        JButton back = new JButton("← Back");
        back.addActionListener(e -> Home.navigate(Home.CARD_CASHFLOW));
        hBtns.add(transferBtn);
        hBtns.add(addAccBtn);
        hBtns.add(refreshBtn);
        hBtns.add(back);
        header.add(hBtns, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        // ── Left: account cards ───────────────────────────────────────────────
        accountsColumn = new JPanel();
        accountsColumn.setOpaque(false);
        accountsColumn.setLayout(new BoxLayout(accountsColumn, BoxLayout.Y_AXIS));

        JScrollPane accountsScroll = new JScrollPane(accountsColumn);
        accountsScroll.setBorder(null);
        accountsScroll.setOpaque(false);
        accountsScroll.getViewport().setOpaque(false);
        accountsScroll.setPreferredSize(new Dimension(340, 0));
        accountsScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        // ── Right: transaction ledger ─────────────────────────────────────────
        JPanel rightPanel = buildDetailPanel();

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, accountsScroll, rightPanel);
        split.setResizeWeight(0.35);
        split.setBorder(null);
        split.setDividerSize(5);
        split.setOpaque(false);
        split.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0
                    && split.isShowing()) {
                SwingUtilities.invokeLater(() -> split.setDividerLocation(0.35));
            }
        });

        root.add(split, BorderLayout.CENTER);
        add(root);
        loadAccounts();
    }

    private JPanel buildDetailPanel() {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setOpaque(false);

        // Detail header
        JPanel detailHeader = new JPanel(new BorderLayout(0, 4));
        detailHeader.setOpaque(false);
        detailHeader.setBorder(new EmptyBorder(0, 12, 6, 0));

        detailAccountName = new JLabel("Select an account");
        detailAccountName.setFont(detailAccountName.getFont().deriveFont(Font.BOLD, 16f));
        detailBalanceLabel = new JLabel("");
        detailBalanceLabel.setFont(detailBalanceLabel.getFont().deriveFont(Font.BOLD, 24f));
        detailBalanceLabel.setForeground(NAVY);
        detailHeader.add(detailAccountName,  BorderLayout.NORTH);
        detailHeader.add(detailBalanceLabel, BorderLayout.CENTER);

        // Period filter bar
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        filterBar.setOpaque(false);
        filterBar.setBorder(new EmptyBorder(0, 12, 6, 0));

        JComboBox<String> periodCombo = new JComboBox<>(PERIODS);
        periodCombo.setSelectedItem(ledgerPeriod);
        periodCombo.setFont(periodCombo.getFont().deriveFont(11f));
        periodCombo.setPreferredSize(new Dimension(110, 24));

        ledgerFromSpinner = makeDateSpinner(toStartOfDay(LocalDate.now().withDayOfMonth(1)));
        ledgerToSpinner   = makeDateSpinner(toEndOfDay(LocalDate.now()));

        JButton applyBtn = new JButton("Apply");
        applyBtn.setFont(applyBtn.getFont().deriveFont(11f));
        applyBtn.addActionListener(e -> { if (selectedAccount != null) loadLedger(selectedAccount.getId()); });

        ledgerCustomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        ledgerCustomPanel.setOpaque(false);
        ledgerCustomPanel.setVisible("Custom".equals(ledgerPeriod));
        ledgerCustomPanel.add(new JLabel("From:"));
        ledgerCustomPanel.add(ledgerFromSpinner);
        ledgerCustomPanel.add(new JLabel("To:"));
        ledgerCustomPanel.add(ledgerToSpinner);
        ledgerCustomPanel.add(applyBtn);

        periodCombo.addActionListener(e -> {
            ledgerPeriod = (String) periodCombo.getSelectedItem();
            ledgerCustomPanel.setVisible("Custom".equals(ledgerPeriod));
            if (!"Custom".equals(ledgerPeriod) && selectedAccount != null) loadLedger(selectedAccount.getId());
        });

        filterBar.add(new JLabel("Period:"));
        filterBar.add(periodCombo);
        filterBar.add(ledgerCustomPanel);

        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.add(detailHeader, BorderLayout.NORTH);
        north.add(filterBar,    BorderLayout.CENTER);
        outer.add(north, BorderLayout.NORTH);

        ledgerModel = new DefaultTableModel(
                new String[]{"Date / Time", "Type", "Description", "Reference", "Amount (Rs.)"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        noSelectionHint = new JLabel("← Select an account to view transactions");
        noSelectionHint.setForeground(TEXT2);
        noSelectionHint.setFont(noSelectionHint.getFont().deriveFont(14f));
        noSelectionHint.setHorizontalAlignment(SwingConstants.CENTER);

        detailContent = new JPanel(new BorderLayout());
        detailContent.setOpaque(false);
        detailContent.add(noSelectionHint, BorderLayout.CENTER);

        outer.add(detailContent, BorderLayout.CENTER);
        return outer;
    }

    private JSpinner makeDateSpinner(Date initial) {
        JSpinner s = new JSpinner(new SpinnerDateModel());
        s.setEditor(new JSpinner.DateEditor(s, "yyyy-MM-dd"));
        s.setPreferredSize(new Dimension(110, 24));
        s.setValue(initial);
        return s;
    }

    private Date[] getLedgerDateRange() {
        LocalDate today = LocalDate.now();
        return switch (ledgerPeriod != null ? ledgerPeriod : "This Month") {
            case "Today"      -> new Date[]{ toStartOfDay(today),                          toEndOfDay(today) };
            case "This Week"  -> new Date[]{ toStartOfDay(today.with(DayOfWeek.MONDAY)),   toEndOfDay(today) };
            case "This Month" -> new Date[]{ toStartOfDay(today.withDayOfMonth(1)),         toEndOfDay(today) };
            case "This Year"  -> new Date[]{ toStartOfDay(today.withDayOfYear(1)),          toEndOfDay(today) };
            case "Custom"     -> ledgerFromSpinner != null
                    ? new Date[]{ (Date) ledgerFromSpinner.getValue(), (Date) ledgerToSpinner.getValue() }
                    : new Date[]{ null, null };
            default           -> new Date[]{ null, null }; // All Time
        };
    }

    private static Date toStartOfDay(LocalDate d) {
        return Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static Date toEndOfDay(LocalDate d) {
        return Date.from(d.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant());
    }

    // ── Account cards ─────────────────────────────────────────────────────────

    private void loadAccounts() {
        new SwingWorker<List<CashAccount>, Void>() {
            @Override protected List<CashAccount> doInBackground() {
                return cashAccountService.listAccounts();
            }
            @Override protected void done() {
                try {
                    List<CashAccount> accounts = get();
                    accountsColumn.removeAll();
                    for (CashAccount acc : accounts) {
                        accountsColumn.add(buildAccountCard(acc));
                        accountsColumn.add(Box.createVerticalStrut(10));
                    }
                    if (accounts.isEmpty()) {
                        JLabel none = new JLabel("No accounts found.");
                        none.setForeground(TEXT2);
                        none.setAlignmentX(Component.CENTER_ALIGNMENT);
                        accountsColumn.add(none);
                    }
                    accountsColumn.revalidate();
                    accountsColumn.repaint();
                    // Re-select previously selected account
                    if (selectedAccount != null) loadLedger(selectedAccount.getId());
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private JPanel buildAccountCard(CashAccount acc) {
        CardPanel card = new CardPanel(new BorderLayout(0, 8));
        ((JPanel) card).setBorder(new EmptyBorder(14, 14, 14, 14));
        ((JPanel) card).setAlignmentX(Component.LEFT_ALIGNMENT);
        ((JPanel) card).setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
        ((JPanel) card).setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Highlight if selected
        boolean isSelected = selectedAccount != null && selectedAccount.getId().equals(acc.getId());
        if (isSelected) {
            ((JPanel) card).setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(NAVY, 2),
                    new EmptyBorder(12, 12, 12, 12)));
        }

        // Top row: name + type badge
        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        JLabel nameLbl = new JLabel(acc.getName());
        nameLbl.setFont(nameLbl.getFont().deriveFont(Font.BOLD, 14f));
        JLabel typeBadge = new JLabel(acc.typeLabel());
        typeBadge.setFont(typeBadge.getFont().deriveFont(10f));
        typeBadge.setForeground(TEXT2);
        typeBadge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xC8, 0xCD, 0xD6)),
                new EmptyBorder(2, 6, 2, 6)));
        topRow.add(nameLbl,   BorderLayout.WEST);
        topRow.add(typeBadge, BorderLayout.EAST);

        // Balance
        JLabel balLbl = new JLabel(String.format("Rs. %,.2f", acc.getBalance()));
        balLbl.setFont(balLbl.getFont().deriveFont(Font.BOLD, 20f));
        balLbl.setForeground(acc.getBalance() >= 0 ? NAVY : RED);

        // Action buttons
        JPanel btnRow = new JPanel(new GridLayout(1, 3, 6, 0));
        btnRow.setOpaque(false);
        JButton addBtn  = actionButton("+ Add",     new Color(0x2E, 0x7D, 0x32));
        JButton wdBtn   = actionButton("− Withdraw",RED);
        JButton adjBtn  = actionButton("⚖ Adjust",  NAVY);
        addBtn .addActionListener(e -> showAddFundsDialog(acc));
        wdBtn  .addActionListener(e -> showWithdrawDialog(acc));
        adjBtn .addActionListener(e -> showAdjustDialog(acc));
        btnRow.add(addBtn);
        btnRow.add(wdBtn);
        btnRow.add(adjBtn);

        ((JPanel) card).add(topRow, BorderLayout.NORTH);
        ((JPanel) card).add(balLbl, BorderLayout.CENTER);
        ((JPanel) card).add(btnRow, BorderLayout.SOUTH);

        // Click card → load ledger
        java.awt.event.MouseAdapter selectCard = new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                selectedAccount = acc;
                loadLedger(acc.getId());
                loadAccounts(); // refresh card highlight
            }
        };
        ((JPanel) card).addMouseListener(selectCard);
        topRow.addMouseListener(selectCard);
        nameLbl.addMouseListener(selectCard);
        balLbl.addMouseListener(selectCard);

        return (JPanel) card;
    }

    private JButton actionButton(String text, Color fg) {
        JButton btn = new JButton(text);
        btn.setFont(btn.getFont().deriveFont(11f));
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        return btn;
    }

    // ── Ledger loading ────────────────────────────────────────────────────────

    private void loadLedger(Long accountId) {
        detailContent.removeAll();
        detailContent.add(new JLabel("Loading…"), BorderLayout.CENTER);
        detailContent.revalidate();
        detailContent.repaint();

        Date[] range = getLedgerDateRange();
        final Date from = range[0];
        final Date to   = range[1];

        new SwingWorker<Object[], Void>() {
            @Override protected Object[] doInBackground() {
                CashAccount acc      = cashAccountService.getAccount(accountId);
                List<Income>  incomes  = cashAccountService.getIncomes(accountId, from, to);
                List<Expense> expenses = cashAccountService.getExpenses(accountId, from, to);
                return new Object[]{acc, incomes, expenses};
            }
            @Override protected void done() {
                try {
                    Object[]      result   = get();
                    CashAccount   acc      = (CashAccount)   result[0];
                    @SuppressWarnings("unchecked")
                    List<Income>  incomes  = (List<Income>)  result[1];
                    @SuppressWarnings("unchecked")
                    List<Expense> expenses = (List<Expense>) result[2];

                    if (acc == null) return;

                    detailAccountName.setText(acc.getName() + " — " + acc.typeLabel());
                    detailBalanceLabel.setText(String.format("Rs. %,.2f", acc.getBalance()));
                    detailBalanceLabel.setForeground(acc.getBalance() >= 0 ? NAVY : RED);

                    // Merge income + expense into a single list sorted by date DESC
                    record TxRow(Date date, String type, String desc, String ref, double amount) {}
                    List<TxRow> rows = new ArrayList<>();
                    for (Income i : incomes) {
                        rows.add(new TxRow(i.getTransactionDate(), "INCOME",
                                i.getDescription() != null ? i.getDescription() : "",
                                i.getReference()   != null ? i.getReference()   : "",
                                i.getAmount()));
                    }
                    for (Expense e : expenses) {
                        rows.add(new TxRow(e.getTransactionDate(), "EXPENSE",
                                e.getDescription() != null ? e.getDescription() : "",
                                e.getReference()   != null ? e.getReference()   : "",
                                e.getAmount()));
                    }
                    rows.sort((a, b) -> b.date().compareTo(a.date()));

                    ledgerModel.setRowCount(0);
                    for (TxRow r : rows) {
                        ledgerModel.addRow(new Object[]{
                                DATE_FMT.format(r.date()),
                                r.type(),
                                r.desc(),
                                r.ref(),
                                String.format("%,.2f", r.amount())
                        });
                    }

                    // Show the ledger table
                    detailContent.removeAll();
                    // Find the scroll pane from the table — we need to get it from the model's parent
                    // Rebuild a fresh scroll pane
                    JTable tbl = new JTable(ledgerModel);
                    tbl.setRowHeight(30);
                    tbl.setShowGrid(false);
                    tbl.setIntercellSpacing(new Dimension(0, 0));
                    tbl.getTableHeader().setFont(tbl.getFont().deriveFont(Font.BOLD, 12f));
                    tbl.getTableHeader().setBackground(new Color(0xF7, 0xF8, 0xFA));
                    tbl.getTableHeader().setForeground(TEXT2);
                    tbl.setAutoCreateRowSorter(true);
                    tbl.getColumnModel().getColumn(1).setCellRenderer((table, value, isSel, hasFocus, row, col) -> {
                        JLabel lbl = new JLabel(value != null ? value.toString() : "");
                        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 11f));
                        lbl.setOpaque(true);
                        lbl.setBackground(isSel ? table.getSelectionBackground() : table.getBackground());
                        lbl.setForeground("INCOME".equals(value) ? GREEN : RED);
                        return lbl;
                    });
                    tbl.getColumnModel().getColumn(0).setPreferredWidth(140);
                    tbl.getColumnModel().getColumn(1).setPreferredWidth(70);
                    tbl.getColumnModel().getColumn(4).setPreferredWidth(120);

                    JScrollPane scroll = new JScrollPane(tbl);
                    scroll.setBorder(BorderFactory.createLineBorder(new Color(0xE2, 0xE5, 0xEA)));
                    detailContent.add(scroll, BorderLayout.CENTER);
                    detailContent.revalidate();
                    detailContent.repaint();

                } catch (Exception ignored) {}
            }
        }.execute();
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    private void showAddFundsDialog(CashAccount acc) {
        JTextField amtField  = new JTextField();
        JTextField descField = new JTextField("Manual deposit");
        JPanel form = new JPanel(new GridLayout(2, 2, 8, 8));
        form.add(new JLabel("Amount (Rs.):")); form.add(amtField);
        form.add(new JLabel("Description:"));  form.add(descField);

        if (JOptionPane.showConfirmDialog(this, form, "Add Funds to " + acc.getName(),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;

        double amount;
        try { amount = Double.parseDouble(amtField.getText().trim()); }
        catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid amount.", "Error", JOptionPane.ERROR_MESSAGE); return;
        }
        if (amount <= 0) { JOptionPane.showMessageDialog(this, "Amount must be positive."); return; }

        Long empId = empId();
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                cashAccountService.addFunds(acc.getId(), amount, descField.getText().trim(), empId);
                return null;
            }
            @Override protected void done() { loadAccounts(); loadLedger(acc.getId()); }
        }.execute();
    }

    private void showWithdrawDialog(CashAccount acc) {
        JTextField amtField  = new JTextField();
        JTextField descField = new JTextField("Manual withdrawal");
        JPanel form = new JPanel(new GridLayout(2, 2, 8, 8));
        form.add(new JLabel("Amount (Rs.):")); form.add(amtField);
        form.add(new JLabel("Description:"));  form.add(descField);

        if (JOptionPane.showConfirmDialog(this, form, "Withdraw from " + acc.getName(),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;

        double amount;
        try { amount = Double.parseDouble(amtField.getText().trim()); }
        catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid amount.", "Error", JOptionPane.ERROR_MESSAGE); return;
        }
        if (amount <= 0) { JOptionPane.showMessageDialog(this, "Amount must be positive."); return; }

        Long empId = empId();
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                cashAccountService.withdrawFunds(acc.getId(), amount, descField.getText().trim(), empId);
                return null;
            }
            @Override protected void done() { loadAccounts(); loadLedger(acc.getId()); }
        }.execute();
    }

    private void showAdjustDialog(CashAccount acc) {
        JTextField newBalField = new JTextField(String.format("%.2f", acc.getBalance()));
        JTextField reasonField = new JTextField("Balance correction");
        JPanel form = new JPanel(new GridLayout(2, 2, 8, 8));
        form.add(new JLabel("New Balance (Rs.):")); form.add(newBalField);
        form.add(new JLabel("Reason:"));            form.add(reasonField);

        if (JOptionPane.showConfirmDialog(this, form, "Adjust Balance for " + acc.getName(),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;

        double newBalance;
        try { newBalance = Double.parseDouble(newBalField.getText().trim()); }
        catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid amount.", "Error", JOptionPane.ERROR_MESSAGE); return;
        }
        String reason = reasonField.getText().trim();
        if (reason.isEmpty()) reason = "Manual adjustment";

        Long empId = empId();
        final String finalReason = reason;
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                cashAccountService.adjustBalance(acc.getId(), newBalance, finalReason, empId);
                return null;
            }
            @Override protected void done() { loadAccounts(); loadLedger(acc.getId()); }
        }.execute();
    }

    private void showAddAccountDialog() {
        JTextField nameField = new JTextField();
        JComboBox<CashAccount.AccountType> typeCombo =
                new JComboBox<>(CashAccount.AccountType.values());
        typeCombo.setSelectedItem(CashAccount.AccountType.SAVINGS);
        JTextField notesField = new JTextField();

        JPanel form = new JPanel(new GridLayout(3, 2, 8, 8));
        form.add(new JLabel("Account Name:"));  form.add(nameField);
        form.add(new JLabel("Account Type:"));  form.add(typeCombo);
        form.add(new JLabel("Notes:"));         form.add(notesField);

        if (JOptionPane.showConfirmDialog(this, form, "Add New Account",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;

        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Account name is required."); return;
        }
        CashAccount.AccountType type = (CashAccount.AccountType) typeCombo.getSelectedItem();
        String notes = notesField.getText().trim();

        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                cashAccountService.addAccount(name, type, notes.isEmpty() ? null : notes);
                return null;
            }
            @Override protected void done() { loadAccounts(); }
        }.execute();
    }

    private void showTransferDialog() {
        // Load accounts fresh for the combo boxes
        java.util.List<CashAccount> accounts = cashAccountService.listAccounts();
        if (accounts.size() < 2) {
            JOptionPane.showMessageDialog(this,
                    "At least two accounts are required to transfer funds.", "Transfer", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JComboBox<CashAccount> fromCombo = new JComboBox<>(accounts.toArray(new CashAccount[0]));
        JComboBox<CashAccount> toCombo   = new JComboBox<>(accounts.toArray(new CashAccount[0]));
        toCombo.setSelectedIndex(accounts.size() > 1 ? 1 : 0);
        fromCombo.setRenderer((list, value, index, isSel, hasFocus) -> new JLabel(
                value == null ? "" : value.getName() + " (Rs. " + String.format("%,.2f", value.getBalance()) + ")"));
        toCombo.setRenderer(fromCombo.getRenderer());
        JTextField amtField  = new JTextField();
        JTextField descField = new JTextField("Fund transfer");

        JPanel form = new JPanel(new GridLayout(4, 2, 8, 8));
        form.add(new JLabel("From Account:"));  form.add(fromCombo);
        form.add(new JLabel("To Account:"));    form.add(toCombo);
        form.add(new JLabel("Amount (Rs.):"));  form.add(amtField);
        form.add(new JLabel("Description:"));   form.add(descField);

        if (JOptionPane.showConfirmDialog(this, form, "Transfer Funds",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;

        CashAccount from = (CashAccount) fromCombo.getSelectedItem();
        CashAccount to   = (CashAccount) toCombo.getSelectedItem();
        if (from == null || to == null || from.getId().equals(to.getId())) {
            JOptionPane.showMessageDialog(this, "Please select two different accounts.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        double amount;
        try { amount = Double.parseDouble(amtField.getText().trim()); }
        catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid amount.", "Error", JOptionPane.ERROR_MESSAGE); return;
        }
        if (amount <= 0) { JOptionPane.showMessageDialog(this, "Amount must be positive."); return; }

        Long empId = empId();
        final double finalAmount = amount;
        final String desc = descField.getText().trim();
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                cashAccountService.transferFunds(from.getId(), to.getId(), finalAmount, desc, empId);
                return null;
            }
            @Override protected void done() {
                loadAccounts();
                if (selectedAccount != null) loadLedger(selectedAccount.getId());
            }
        }.execute();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Long empId() {
        return SessionContext.current() != null ? SessionContext.current().getEmployee().id() : null;
    }
}
