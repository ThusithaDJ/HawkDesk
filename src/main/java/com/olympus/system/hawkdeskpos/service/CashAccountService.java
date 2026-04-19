package com.olympus.system.hawkdeskpos.service;

import com.olympus.system.hawkdeskpos.db.dao.*;
import com.olympus.system.hawkdeskpos.dto.TransactionDto;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages cash drawer and bank accounts.
 *
 * Every financial event writes to both the income/expense ledger tables
 * (for per-account balance tracking) and the unified cash_transaction table
 * (for the Cash Flow all-transactions view).
 */
public class CashAccountService {

    private final SessionFactory sf;
    private final SettingsService settingsService;

    public CashAccountService(SessionFactory sf) {
        this(sf, null);
    }

    public CashAccountService(SessionFactory sf, SettingsService settingsService) {
        this.sf              = sf;
        this.settingsService = settingsService;
    }

    // ── Account queries ───────────────────────────────────────────────────────

    public List<CashAccount> listAccounts() {
        try (var session = sf.openSession()) {
            return session.createQuery(
                    "FROM CashAccount a WHERE a.active = true ORDER BY a.accountType, a.name",
                    CashAccount.class).list();
        } catch (Exception e) {
            System.err.println("CashAccountService.listAccounts: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public CashAccount getAccount(Long id) {
        try (var session = sf.openSession()) {
            return session.get(CashAccount.class, id);
        } catch (Exception e) { return null; }
    }

    /** Returns the first active CASH_DRAWER account, or null if none exists. */
    public CashAccount getCashDrawer() {
        try (var session = sf.openSession()) {
            return session.createQuery(
                    "FROM CashAccount a WHERE a.accountType = 'CASH_DRAWER' AND a.active = true ORDER BY a.id",
                    CashAccount.class).setMaxResults(1).uniqueResult();
        } catch (Exception e) { return null; }
    }

    // ── Account management ────────────────────────────────────────────────────

    public void addAccount(String name, CashAccount.AccountType type, String notes) {
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            CashAccount account = new CashAccount(name, type);
            account.setNotes(notes);
            account.setCreatedAt(new Date());
            session.persist(account);
            tx.commit();
        } catch (Exception e) {
            System.err.println("CashAccountService.addAccount: " + e.getMessage());
        }
    }

    // ── Fund operations ───────────────────────────────────────────────────────

    public void addFunds(Long accountId, double amount, String description, Long employeeId) {
        if (amount <= 0) return;
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            CashAccount account = session.get(CashAccount.class, accountId);
            if (account == null) { tx.rollback(); return; }
            Employee emp = resolve(session, employeeId);

            account.setBalance(account.getBalance() + amount);
            session.merge(account);
            session.persist(new Income(account, amount, description, null, new Date(), emp));
            persistTx(session, CashTransaction.TxType.INCOME, null, description,
                      amount, new Date(), account, emp, null);
            tx.commit();
        } catch (Exception e) {
            System.err.println("CashAccountService.addFunds: " + e.getMessage());
        }
    }

    public void withdrawFunds(Long accountId, double amount, String description, Long employeeId) {
        if (amount <= 0) return;
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            CashAccount account = session.get(CashAccount.class, accountId);
            if (account == null) { tx.rollback(); return; }
            Employee emp = resolve(session, employeeId);

            account.setBalance(account.getBalance() - amount);
            session.merge(account);
            session.persist(new Expense(account, amount, description, null, new Date(), emp));
            persistTx(session, CashTransaction.TxType.EXPENSE, null, description,
                      amount, new Date(), account, emp, null);
            tx.commit();
        } catch (Exception e) {
            System.err.println("CashAccountService.withdrawFunds: " + e.getMessage());
        }
    }

    public void adjustBalance(Long accountId, double newBalance, String reason, Long employeeId) {
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            CashAccount account = session.get(CashAccount.class, accountId);
            if (account == null) { tx.rollback(); return; }
            Employee emp = resolve(session, employeeId);

            double diff = newBalance - account.getBalance();
            account.setBalance(newBalance);
            session.merge(account);

            if (diff > 0) {
                String desc = "Adjustment: " + reason;
                session.persist(new Income(account, diff, desc, null, new Date(), emp));
                persistTx(session, CashTransaction.TxType.INCOME, null, desc,
                          diff, new Date(), account, emp, "Adjustment");
            } else if (diff < 0) {
                String desc = "Adjustment: " + reason;
                session.persist(new Expense(account, Math.abs(diff), desc, null, new Date(), emp));
                persistTx(session, CashTransaction.TxType.EXPENSE, null, desc,
                          Math.abs(diff), new Date(), account, emp, "Adjustment");
            }
            tx.commit();
        } catch (Exception e) {
            System.err.println("CashAccountService.adjustBalance: " + e.getMessage());
        }
    }

    // ── Auto-recording ────────────────────────────────────────────────────────

    public void recordSaleIncome(double amount, String invoiceNo, Long employeeId) {
        if (amount <= 0) return;
        try (var session = sf.openSession()) {
            CashAccount account = resolveDefaultAccount(session, "DefaultSaleAccountId", "CASH_DRAWER");
            if (account == null) return;
            Transaction tx = session.beginTransaction();
            Employee emp = resolve(session, employeeId);
            account.setBalance(account.getBalance() + amount);
            session.merge(account);
            Income inc = new Income(account, amount, "Sale", invoiceNo, new Date(), emp);
            session.persist(inc);
            CashTransaction saleTx = new CashTransaction(CashTransaction.TxType.SALE, invoiceNo, "Sale",
                    amount, new Date(), account, emp, null);
            saleTx.setInvoiceNoRef(invoiceNo);
            saleTx.setIncomeIdRef(inc.getId());
            session.persist(saleTx);
            tx.commit();
        } catch (Exception e) {
            System.err.println("CashAccountService.recordSaleIncome: " + e.getMessage());
        }
    }

    public void recordGrnExpense(double amount, String grnNo, Long employeeId) {
        if (amount <= 0) return;
        try (var session = sf.openSession()) {
            CashAccount account = resolveDefaultAccount(session, "DefaultGrnAccountId", "CASH_DRAWER");
            if (account == null) return;
            Transaction tx = session.beginTransaction();
            Employee emp = resolve(session, employeeId);
            account.setBalance(account.getBalance() - amount);
            session.merge(account);
            Expense grnExp = new Expense(account, amount, "Stock Purchase (GRN)", grnNo, new Date(), emp);
            session.persist(grnExp);
            try {
                CashTransaction grnTx = new CashTransaction(CashTransaction.TxType.GRN, grnNo,
                        "Stock Purchase (GRN)", amount, new Date(), account, emp, null);
                grnTx.setGrnNoRef(Integer.parseInt(grnNo));
                grnTx.setExpenseIdRef(grnExp.getId());
                session.persist(grnTx);
            } catch (NumberFormatException ignored) {
                persistTx(session, CashTransaction.TxType.GRN, grnNo, "Stock Purchase (GRN)",
                          amount, new Date(), account, emp, null);
            }
            tx.commit();
        } catch (Exception e) {
            System.err.println("CashAccountService.recordGrnExpense: " + e.getMessage());
        }
    }

    public void recordReturnRefund(double amount, String invoiceNo, Long employeeId) {
        recordReturnRefund(amount, "Return Refund", invoiceNo, employeeId);
    }

    public void recordReturnRefund(double amount, String description, String invoiceNo, Long employeeId) {
        if (amount <= 0) return;
        try (var session = sf.openSession()) {
            CashAccount drawer = session.createQuery(
                    "FROM CashAccount a WHERE a.accountType = 'CASH_DRAWER' AND a.active = true ORDER BY a.id",
                    CashAccount.class).setMaxResults(1).uniqueResult();
            if (drawer == null) return;
            Transaction tx = session.beginTransaction();
            Employee emp = resolve(session, employeeId);
            drawer.setBalance(drawer.getBalance() - amount);
            session.merge(drawer);
            session.persist(new Expense(drawer, amount, description, invoiceNo, new Date(), emp));
            persistTx(session, CashTransaction.TxType.RETURN_REFUND, invoiceNo, description,
                      amount, new Date(), drawer, emp, null);
            tx.commit();
        } catch (Exception e) {
            System.err.println("CashAccountService.recordReturnRefund: " + e.getMessage());
        }
    }

    /** Records a manual income entry — written to income table + cash_transaction. */
    public void recordManualIncome(double amount, String description, String category,
                                   Long accountId, Date date, Long employeeId) {
        if (amount <= 0) return;
        Date txDate = date != null ? date : new Date();
        try (var session = sf.openSession()) {
            CashAccount account = session.get(CashAccount.class, accountId);
            if (account == null) return;
            Transaction tx = session.beginTransaction();
            Employee emp = resolve(session, employeeId);
            account.setBalance(account.getBalance() + amount);
            session.merge(account);
            session.persist(new Income(account, amount, description, null, txDate, emp));
            persistTx(session, CashTransaction.TxType.INCOME, null, description,
                      amount, txDate, account, emp, category);
            tx.commit();
        } catch (Exception e) {
            System.err.println("CashAccountService.recordManualIncome: " + e.getMessage());
        }
    }

    /** Records a manual expense entry — written to expense table + cash_transaction. */
    public void recordManualExpense(double amount, String description, String category,
                                    Long accountId, Date date, Long employeeId) {
        if (amount <= 0) return;
        Date txDate = date != null ? date : new Date();
        try (var session = sf.openSession()) {
            CashAccount account = session.get(CashAccount.class, accountId);
            if (account == null) return;
            Transaction tx = session.beginTransaction();
            Employee emp = resolve(session, employeeId);
            account.setBalance(account.getBalance() - amount);
            session.merge(account);
            session.persist(new Expense(account, amount, description, null, txDate, emp));
            persistTx(session, CashTransaction.TxType.EXPENSE, null, description,
                      amount, txDate, account, emp, category);
            tx.commit();
        } catch (Exception e) {
            System.err.println("CashAccountService.recordManualExpense: " + e.getMessage());
        }
    }

    public void transferFunds(Long fromAccountId, Long toAccountId, double amount,
                              String description, Long employeeId) {
        if (amount <= 0) return;
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            CashAccount from = session.get(CashAccount.class, fromAccountId);
            CashAccount to   = session.get(CashAccount.class, toAccountId);
            if (from == null || to == null) { tx.rollback(); return; }
            Employee emp = resolve(session, employeeId);

            from.setBalance(from.getBalance() - amount);
            to.setBalance(to.getBalance()   + amount);
            session.merge(from);
            session.merge(to);

            String desc = (description != null && !description.isEmpty()) ? description : "Transfer";
            String descOut = "Transfer to "   + to.getName()   + ": " + desc;
            String descIn  = "Transfer from " + from.getName() + ": " + desc;
            session.persist(new Expense(from, amount, descOut, null, new Date(), emp));
            session.persist(new Income(to,   amount, descIn,  null, new Date(), emp));
            persistTx(session, CashTransaction.TxType.EXPENSE, null, descOut, amount, new Date(), from, emp, "Transfer");
            persistTx(session, CashTransaction.TxType.INCOME,  null, descIn,  amount, new Date(), to,   emp, "Transfer");
            tx.commit();
        } catch (Exception e) {
            System.err.println("CashAccountService.transferFunds: " + e.getMessage());
        }
    }

    // ── Transaction queries ───────────────────────────────────────────────────

    /** Lists all cash_transaction rows in [from, to], newest first. */
    public List<TransactionDto> listTransactions(Date from, Date to) {
        try (var session = sf.openSession()) {
            List<CashTransaction> rows = session.createQuery(
                    "FROM CashTransaction t LEFT JOIN FETCH t.account LEFT JOIN FETCH t.employee " +
                    "WHERE t.transactionDate BETWEEN :from AND :to " +
                    "ORDER BY t.transactionDate DESC",
                    CashTransaction.class)
                    .setParameter("from", from).setParameter("to", to).list();
            return rows.stream().map(t -> new TransactionDto(
                    t.getId(),
                    t.getType().name(),
                    t.getReference(),
                    t.getTransactionDate(),
                    t.getDescription(),
                    t.getAmount() != null ? t.getAmount() : 0,
                    t.getAccount() != null ? t.getAccount().getName() : "—",
                    t.getCategory(),
                    t.getAccount() != null ? t.getAccount().getId() : null
            )).collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("CashAccountService.listTransactions: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Records a credit invoice payment as income.
     * Writes to both the Income ledger table and cash_transaction.
     * Works even when no default account exists — transaction is still recorded.
     */
    public void recordCreditPayment(double amount, String invoiceNo, Long employeeId) {
        if (amount <= 0) return;
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            CashAccount account = resolveDefaultAccount(session, "DefaultSaleAccountId", "CASH_DRAWER");
            Employee emp = resolve(session, employeeId);
            if (account != null) {
                account.setBalance(account.getBalance() + amount);
                session.merge(account);
                Income income = new Income(account, amount, "Credit Payment", invoiceNo, new Date(), emp);
                session.persist(income);
                CashTransaction ctxn = new CashTransaction(CashTransaction.TxType.CREDIT_PAYMENT,
                        invoiceNo, "Credit Payment", amount, new Date(), account, emp, null);
                ctxn.setInvoiceNoRef(invoiceNo);
                ctxn.setIncomeIdRef(income.getId());
                session.persist(ctxn);
            } else {
                // No account — still record the transaction
                CashTransaction ctxn = new CashTransaction(CashTransaction.TxType.CREDIT_PAYMENT,
                        invoiceNo, "Credit Payment", amount, new Date(), null, emp, null);
                ctxn.setInvoiceNoRef(invoiceNo);
                session.persist(ctxn);
            }
            tx.commit();
        } catch (Exception e) {
            System.err.println("CashAccountService.recordCreditPayment: " + e.getMessage());
        }
    }

    /**
     * Moves a cash_transaction to a different account.
     * Adjusts balances on both old and new accounts.
     * @return "MOVED", "ASSIGNED" (no previous account), "SAME_ACCOUNT", or "ERROR"
     */
    public String moveCashTransaction(Long txId, Long newAccountId) {
        try (var session = sf.openSession()) {
            CashTransaction ctxn = session.get(CashTransaction.class, txId);
            if (ctxn == null) return "ERROR";
            CashAccount newAccount = session.get(CashAccount.class, newAccountId);
            if (newAccount == null) return "ERROR";
            if (ctxn.getAccount() != null && ctxn.getAccount().getId().equals(newAccountId))
                return "SAME_ACCOUNT";

            Transaction tx = session.beginTransaction();
            double amount = ctxn.getAmount() != null ? ctxn.getAmount() : 0;
            boolean isIncome = isIncomeType(ctxn.getType());
            String result;

            if (ctxn.getAccount() != null) {
                // Reverse from old account
                CashAccount old = ctxn.getAccount();
                old.setBalance(old.getBalance() + (isIncome ? -amount : amount));
                session.merge(old);
                result = "MOVED";
            } else {
                result = "ASSIGNED";
            }

            // Apply to new account
            newAccount.setBalance(newAccount.getBalance() + (isIncome ? amount : -amount));
            session.merge(newAccount);
            ctxn.setAccount(newAccount);
            session.merge(ctxn);
            tx.commit();
            return result;
        } catch (Exception e) {
            System.err.println("CashAccountService.moveCashTransaction: " + e.getMessage());
            return "ERROR";
        }
    }

    private static boolean isIncomeType(CashTransaction.TxType type) {
        return type == CashTransaction.TxType.SALE
            || type == CashTransaction.TxType.INCOME
            || type == CashTransaction.TxType.CREDIT_PAYMENT;
    }

    /** Income rows for an account, newest first, optionally date-filtered. */
    public List<Income> getIncomes(Long accountId, Date from, Date to) {
        try (var session = sf.openSession()) {
            StringBuilder hql = new StringBuilder(
                    "FROM Income i LEFT JOIN FETCH i.employee " +
                    "WHERE i.account.id = :aid");
            if (from != null) hql.append(" AND i.transactionDate >= :from");
            if (to   != null) hql.append(" AND i.transactionDate <= :to");
            hql.append(" ORDER BY i.transactionDate DESC");
            var q = session.createQuery(hql.toString(), Income.class).setParameter("aid", accountId);
            if (from != null) q.setParameter("from", from);
            if (to   != null) q.setParameter("to",   to);
            return q.list();
        } catch (Exception e) {
            System.err.println("CashAccountService.getIncomes: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /** Expense rows for an account, newest first, optionally date-filtered. */
    public List<Expense> getExpenses(Long accountId, Date from, Date to) {
        try (var session = sf.openSession()) {
            StringBuilder hql = new StringBuilder(
                    "FROM Expense e LEFT JOIN FETCH e.employee " +
                    "WHERE e.account.id = :aid");
            if (from != null) hql.append(" AND e.transactionDate >= :from");
            if (to   != null) hql.append(" AND e.transactionDate <= :to");
            hql.append(" ORDER BY e.transactionDate DESC");
            var q = session.createQuery(hql.toString(), Expense.class).setParameter("aid", accountId);
            if (from != null) q.setParameter("from", from);
            if (to   != null) q.setParameter("to",   to);
            return q.list();
        } catch (Exception e) {
            System.err.println("CashAccountService.getExpenses: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // ── Assignment helpers ────────────────────────────────────────────────────

    public Map<String, String> getAssignedReferenceMap() {
        Map<String, String> map = new LinkedHashMap<>();
        try (var session = sf.openSession()) {
            session.createNativeQuery(
                    "SELECT i.reference, a.name FROM income i " +
                    "JOIN cash_account a ON a.id = i.account_id " +
                    "WHERE i.reference IS NOT NULL AND i.reference != ''",
                    Object[].class).list()
                    .forEach(row -> map.put(row[0].toString(), row[1].toString()));
            session.createNativeQuery(
                    "SELECT e.reference, a.name FROM expense e " +
                    "JOIN cash_account a ON a.id = e.account_id " +
                    "WHERE e.reference IS NOT NULL AND e.reference != ''",
                    Object[].class).list()
                    .forEach(row -> map.put(row[0].toString(), row[1].toString()));
        } catch (Exception e) {
            System.err.println("CashAccountService.getAssignedReferenceMap: " + e.getMessage());
        }
        return map;
    }

    public String assignOrMoveSaleIncome(String invoiceNo, double amount, Long targetAccountId, Long empId) {
        try (var session = sf.openSession()) {
            Income existing = session.createQuery(
                    "FROM Income i LEFT JOIN FETCH i.account WHERE i.reference = :ref",
                    Income.class).setParameter("ref", invoiceNo).setMaxResults(1).uniqueResult();
            CashAccount target = session.get(CashAccount.class, targetAccountId);
            if (target == null) return "ERROR";
            Employee emp = resolve(session, empId);
            Transaction tx = session.beginTransaction();
            if (existing == null) {
                target.setBalance(target.getBalance() + amount);
                session.merge(target);
                session.persist(new Income(target, amount, "Sale", invoiceNo, new Date(), emp));
                persistTx(session, CashTransaction.TxType.SALE, invoiceNo, "Sale",
                          amount, new Date(), target, emp, null);
                tx.commit();
                return "ASSIGNED";
            } else if (existing.getAccount().getId().equals(targetAccountId)) {
                tx.rollback();
                return "SAME_ACCOUNT";
            } else {
                CashAccount old = existing.getAccount();
                old.setBalance(old.getBalance() - existing.getAmount());
                session.merge(old);
                target.setBalance(target.getBalance() + existing.getAmount());
                session.merge(target);
                existing.setAccount(target);
                session.merge(existing);
                // Update cash_transaction row if present
                updateTxAccount(session, invoiceNo, CashTransaction.TxType.SALE, target);
                tx.commit();
                return "MOVED";
            }
        } catch (Exception e) {
            System.err.println("CashAccountService.assignOrMoveSaleIncome: " + e.getMessage());
            return "ERROR";
        }
    }

    public String assignOrMoveGrnExpense(String grnNo, double amount, Long targetAccountId, Long empId) {
        try (var session = sf.openSession()) {
            Expense existing = session.createQuery(
                    "FROM Expense e LEFT JOIN FETCH e.account WHERE e.reference = :ref",
                    Expense.class).setParameter("ref", grnNo).setMaxResults(1).uniqueResult();
            CashAccount target = session.get(CashAccount.class, targetAccountId);
            if (target == null) return "ERROR";
            Employee emp = resolve(session, empId);
            Transaction tx = session.beginTransaction();
            if (existing == null) {
                target.setBalance(target.getBalance() - amount);
                session.merge(target);
                session.persist(new Expense(target, amount, "Stock Purchase (GRN)", grnNo, new Date(), emp));
                persistTx(session, CashTransaction.TxType.GRN, grnNo, "Stock Purchase (GRN)",
                          amount, new Date(), target, emp, null);
                tx.commit();
                return "ASSIGNED";
            } else if (existing.getAccount().getId().equals(targetAccountId)) {
                tx.rollback();
                return "SAME_ACCOUNT";
            } else {
                CashAccount old = existing.getAccount();
                old.setBalance(old.getBalance() + existing.getAmount());
                session.merge(old);
                target.setBalance(target.getBalance() - existing.getAmount());
                session.merge(target);
                existing.setAccount(target);
                session.merge(existing);
                updateTxAccount(session, grnNo, CashTransaction.TxType.GRN, target);
                tx.commit();
                return "MOVED";
            }
        } catch (Exception e) {
            System.err.println("CashAccountService.assignOrMoveGrnExpense: " + e.getMessage());
            return "ERROR";
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void persistTx(org.hibernate.Session session, CashTransaction.TxType type,
                            String reference, String description, double amount,
                            Date date, CashAccount account, Employee emp, String category) {
        session.persist(new CashTransaction(type, reference, description, amount, date, account, emp, category));
    }

    private void updateTxAccount(org.hibernate.Session session, String reference,
                                 CashTransaction.TxType type, CashAccount target) {
        try {
            CashTransaction tx = session.createQuery(
                    "FROM CashTransaction t WHERE t.reference = :ref AND t.type = :type",
                    CashTransaction.class)
                    .setParameter("ref", reference)
                    .setParameter("type", type)
                    .setMaxResults(1).uniqueResult();
            if (tx != null) { tx.setAccount(target); session.merge(tx); }
        } catch (Exception ignored) {}
    }

    private Employee resolve(org.hibernate.Session session, Long empId) {
        return empId != null ? session.get(Employee.class, empId) : null;
    }

    private CashAccount resolveDefaultAccount(org.hibernate.Session session, String settingKey,
                                              @SuppressWarnings("unused") String ignored) {
        if (settingsService != null) {
            String idStr = settingsService.get(settingKey, null);
            if (idStr != null && !idStr.isBlank()) {
                try {
                    Long id = Long.parseLong(idStr.trim());
                    CashAccount acc = session.get(CashAccount.class, id);
                    if (acc != null && acc.isActive()) return acc;
                } catch (NumberFormatException e2) { /* fall through */ }
            }
        }
        return session.createQuery(
                "FROM CashAccount a WHERE a.accountType = :type AND a.active = true ORDER BY a.id",
                CashAccount.class)
                .setParameter("type", CashAccount.AccountType.CASH_DRAWER)
                .setMaxResults(1).uniqueResult();
    }
}
