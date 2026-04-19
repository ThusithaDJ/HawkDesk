package com.olympus.system.hawkdeskpos.service;

import net.sf.jasperreports.engine.*;
import org.hibernate.SessionFactory;

import java.io.InputStream;
import java.sql.Connection;
import java.util.*;

public class ReportService {

    private final SessionFactory sf;

    public ReportService(SessionFactory sf) {
        this.sf = sf;
    }

    // ── Period sales stats ────────────────────────────────────────────────────

    public record PeriodStats(double revenue, int transactions, int itemsSold,
                               double avgSale, double profit, int returns) {}

    /**
     * Computes sales analytics for a date range.
     * Each metric is a separate query to avoid duplicate-parameter binding issues.
     * Expects from = start-of-day, to = end-of-day (23:59:59) for correct BETWEEN coverage.
     */
    public PeriodStats getStats(Date from, Date to) {
        try (var session = sf.openSession()) {
            // 1. Revenue, transaction count, average sale
            Object[] main = (Object[]) session.createNativeQuery(
                    "SELECT COALESCE(SUM(net_total), 0), COUNT(*), COALESCE(AVG(net_total), 0) " +
                    "FROM invoiceinfo " +
                    "WHERE date BETWEEN :from AND :to AND stat != 'Void'",
                    Object[].class)
                    .setParameter("from", from).setParameter("to", to)
                    .uniqueResult();

            // 2. Total items sold
            Object itemsObj = session.createNativeQuery(
                    "SELECT COALESCE(SUM(inv.qty), 0) " +
                    "FROM invoice inv " +
                    "JOIN invoiceinfo ii ON inv.invoice_no = ii.invoice_no " +
                    "WHERE ii.date BETWEEN :from AND :to AND ii.stat != 'Void'",
                    Object.class)
                    .setParameter("from", from).setParameter("to", to)
                    .uniqueResult();

            // 3. Cost of goods sold — uses cost captured at sale time (cost_price column),
            //    falling back to the current stock cost for pre-migration rows that have no cost_price.
            Object cogsObj = session.createNativeQuery(
                    "SELECT COALESCE(SUM(inv.qty * COALESCE(inv.cost_price, s.cost, 0)), 0) " +
                    "FROM invoice inv " +
                    "JOIN invoiceinfo ii ON inv.invoice_no = ii.invoice_no " +
                    "LEFT JOIN stock s ON inv.stock_id = s.stock_id " +
                    "WHERE ii.date BETWEEN :from AND :to AND ii.stat != 'Void'",
                    Object.class)
                    .setParameter("from", from).setParameter("to", to)
                    .uniqueResult();

            // 4. Returns in the same date window (matched by the original invoice date)
            Object retObj = session.createNativeQuery(
                    "SELECT COUNT(*) " +
                    "FROM `return` r " +
                    "JOIN invoiceinfo ii ON r.invoice_no = ii.invoice_no " +
                    "WHERE ii.date BETWEEN :from AND :to",
                    Object.class)
                    .setParameter("from", from).setParameter("to", to)
                    .uniqueResult();

            if (main == null) return new PeriodStats(0, 0, 0, 0, 0, 0);
            double revenue = main[0] instanceof Number n ? n.doubleValue() : 0;
            int    tx      = main[1] instanceof Number n ? n.intValue()    : 0;
            double avg     = main[2] instanceof Number n ? n.doubleValue() : 0;
            int    items   = itemsObj instanceof Number n ? n.intValue()   : 0;
            double cogs    = cogsObj  instanceof Number n ? n.doubleValue(): 0;
            int    returns = retObj   instanceof Number n ? n.intValue()   : 0;
            return new PeriodStats(revenue, tx, items, avg, revenue - cogs, returns);
        } catch (Exception e) {
            System.err.println("ReportService.getStats: " + e.getMessage());
            return new PeriodStats(0, 0, 0, 0, 0, 0);
        }
    }

    // ── Top-selling items ─────────────────────────────────────────────────────

    public record TopItem(String name, int qtySold, double revenue) {}

    public List<TopItem> getTopItems(Date from, Date to, int limit) {
        try (var session = sf.openSession()) {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = session.createNativeQuery(
                    "SELECT i.item_name, SUM(inv.qty), SUM(inv.sub_total) " +
                    "FROM invoice inv " +
                    "JOIN item i ON inv.item_id = i.item_id " +
                    "JOIN invoiceinfo ii ON inv.invoice_no = ii.invoice_no " +
                    "WHERE ii.date BETWEEN :from AND :to AND ii.stat != 'Void' " +
                    "GROUP BY i.item_id ORDER BY SUM(inv.qty) DESC LIMIT :lim",
                    Object[].class)
                    .setParameter("from", from).setParameter("to", to).setParameter("lim", limit)
                    .list();
            List<TopItem> result = new ArrayList<>();
            for (Object[] r : rows)
                result.add(new TopItem((String) r[0],
                        r[1] instanceof Number n ? n.intValue()    : 0,
                        r[2] instanceof Number n ? n.doubleValue() : 0));
            return result;
        } catch (Exception e) {
            System.err.println("ReportService.getTopItems: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // ── Stock overview ────────────────────────────────────────────────────────

    public record StockSummary(int totalItems, int lowStock, int outOfStock, double totalStockValue) {}

    /** Real-time snapshot of inventory health across all active items. */
    public StockSummary getStockSummary() {
        try (var session = sf.openSession()) {
            Object[] row = (Object[]) session.createNativeQuery(
                    "SELECT COUNT(*), " +
                    "  SUM(CASE WHEN COALESCE(iq.qty,0) > 0 AND COALESCE(iq.qty,0) < i.min_level THEN 1 ELSE 0 END), " +
                    "  SUM(CASE WHEN COALESCE(iq.qty,0) = 0 THEN 1 ELSE 0 END), " +
                    "  COALESCE(SUM(iq.val), 0) " +
                    "FROM item i " +
                    "LEFT JOIN (" +
                    "  SELECT s.item_id, COALESCE(SUM(s.qty),0) AS qty, COALESCE(SUM(s.qty * s.cost),0) AS val " +
                    "  FROM stock s WHERE s.stat = 'Active' GROUP BY s.item_id" +
                    ") iq ON iq.item_id = i.item_id " +
                    "WHERE i.stat = 'Active'",
                    Object[].class).uniqueResult();
            if (row == null) return new StockSummary(0, 0, 0, 0);
            return new StockSummary(
                    row[0] instanceof Number n ? n.intValue()    : 0,
                    row[1] instanceof Number n ? n.intValue()    : 0,
                    row[2] instanceof Number n ? n.intValue()    : 0,
                    row[3] instanceof Number n ? n.doubleValue() : 0);
        } catch (Exception e) {
            System.err.println("ReportService.getStockSummary: " + e.getMessage());
            return new StockSummary(0, 0, 0, 0);
        }
    }

    // ── Customer debt analytics ───────────────────────────────────────────────

    public record CustomerDebt(String name, double outstanding) {}
    public record DebtSummary(double totalOutstanding, int overdueCount, List<CustomerDebt> topDebtors) {}

    /** Real-time snapshot of all outstanding credit balances. */
    public DebtSummary getDebtSummary() {
        try (var session = sf.openSession()) {
            Object[] main = (Object[]) session.createNativeQuery(
                    "SELECT COALESCE(SUM(net_total - paid), 0), " +
                    "  SUM(CASE WHEN credit_resolve_date IS NOT NULL AND credit_resolve_date <= CURDATE() THEN 1 ELSE 0 END) " +
                    "FROM invoiceinfo WHERE payment_method = 'CREDIT' AND stat IN ('Credit', 'Partial')",
                    Object[].class).uniqueResult();

            @SuppressWarnings("unchecked")
            List<Object[]> debtorRows = session.createNativeQuery(
                    "SELECT c.name, SUM(ii.net_total - ii.paid) AS owed " +
                    "FROM invoiceinfo ii " +
                    "JOIN customer c ON ii.customer_id = c.customer_id " +
                    "WHERE ii.payment_method = 'CREDIT' AND ii.stat IN ('Credit', 'Partial') " +
                    "GROUP BY c.customer_id ORDER BY owed DESC LIMIT 6",
                    Object[].class).list();

            double total   = main != null && main[0] instanceof Number n ? n.doubleValue() : 0;
            int    overdue = main != null && main[1] instanceof Number n ? n.intValue()    : 0;
            List<CustomerDebt> debtors = new ArrayList<>();
            for (Object[] r : debtorRows)
                debtors.add(new CustomerDebt(
                        r[0] != null ? (String) r[0] : "Unknown",
                        r[1] instanceof Number n ? n.doubleValue() : 0));
            return new DebtSummary(total, overdue, debtors);
        } catch (Exception e) {
            System.err.println("ReportService.getDebtSummary: " + e.getMessage());
            return new DebtSummary(0, 0, Collections.emptyList());
        }
    }

    // ── Cashflow analytics ────────────────────────────────────────────────────

    public record PayMethodStats(long count, double total) {}

    public record CashflowData(
        PayMethodStats cash, PayMethodStats card, PayMethodStats cheque,
        PayMethodStats creditIssued,
        double creditCollected,   // sum of paid on CREDIT invoices in the period
        double creditOutstanding, // all-time outstanding balance
        long grnCount, double grnCost,
        double discountGiven,     // total discounts given to customers in period
        double cashRefunded       // total cash refunds paid to customers in period
    ) {}

    /**
     * Cashflow breakdown for a date range:
     * sales by payment method, credit status, and GRN stock costs.
     */
    public CashflowData getCashflowData(Date from, Date to) {
        try (var session = sf.openSession()) {
            // Sales grouped by payment method
            @SuppressWarnings("unchecked")
            List<Object[]> pmRows = session.createNativeQuery(
                    "SELECT payment_method, COUNT(*), COALESCE(SUM(net_total), 0) " +
                    "FROM invoiceinfo " +
                    "WHERE date BETWEEN :from AND :to AND stat != 'Void' " +
                    "GROUP BY payment_method",
                    Object[].class)
                    .setParameter("from", from).setParameter("to", to).list();

            PayMethodStats cash = new PayMethodStats(0, 0);
            PayMethodStats card = new PayMethodStats(0, 0);
            PayMethodStats cheque = new PayMethodStats(0, 0);
            PayMethodStats creditIssued = new PayMethodStats(0, 0);
            for (Object[] r : pmRows) {
                String method = r[0] != null ? r[0].toString().toUpperCase() : "";
                long   cnt = r[1] instanceof Number n ? n.longValue()   : 0;
                double tot = r[2] instanceof Number n ? n.doubleValue() : 0;
                switch (method) {
                    case "CASH"   -> cash         = new PayMethodStats(cnt, tot);
                    case "CARD"   -> card         = new PayMethodStats(cnt, tot);
                    case "CHEQUE" -> cheque       = new PayMethodStats(cnt, tot);
                    case "CREDIT" -> creditIssued = new PayMethodStats(cnt, tot);
                }
            }

            // Credit paid amount on invoices created in period
            Object collObj = session.createNativeQuery(
                    "SELECT COALESCE(SUM(paid), 0) FROM invoiceinfo " +
                    "WHERE payment_method = 'CREDIT' AND date BETWEEN :from AND :to",
                    Object.class).setParameter("from", from).setParameter("to", to).uniqueResult();

            // All-time outstanding credit balance
            Object outObj = session.createNativeQuery(
                    "SELECT COALESCE(SUM(net_total - paid), 0) FROM invoiceinfo " +
                    "WHERE payment_method = 'CREDIT' AND stat IN ('Credit', 'Partial')",
                    Object.class).uniqueResult();

            // GRNs received in period
            Object[] grnRow = (Object[]) session.createNativeQuery(
                    "SELECT COUNT(*), COALESCE(SUM(sub_total), 0) FROM grninfo " +
                    "WHERE date BETWEEN :from AND :to",
                    Object[].class).setParameter("from", from).setParameter("to", to).uniqueResult();

            // Discounts given to customers in period
            Object discObj = session.createNativeQuery(
                    "SELECT COALESCE(SUM(discount), 0) FROM invoiceinfo " +
                    "WHERE date BETWEEN :from AND :to AND stat != 'Void'",
                    Object.class).setParameter("from", from).setParameter("to", to).uniqueResult();

            // Cash refunds paid to customers — query unified transaction ledger by type
            Object refundObj = session.createNativeQuery(
                    "SELECT COALESCE(SUM(amount), 0) FROM cash_transaction " +
                    "WHERE transaction_date BETWEEN :from AND :to AND type = 'RETURN_REFUND'",
                    Object.class).setParameter("from", from).setParameter("to", to).uniqueResult();

            return new CashflowData(cash, card, cheque, creditIssued,
                    collObj   instanceof Number n ? n.doubleValue() : 0,
                    outObj    instanceof Number n ? n.doubleValue() : 0,
                    grnRow != null && grnRow[0] instanceof Number n ? n.longValue()   : 0,
                    grnRow != null && grnRow[1] instanceof Number n ? n.doubleValue() : 0,
                    discObj   instanceof Number n ? n.doubleValue() : 0,
                    refundObj instanceof Number n ? n.doubleValue() : 0);
        } catch (Exception e) {
            System.err.println("ReportService.getCashflowData: " + e.getMessage());
            return new CashflowData(
                    new PayMethodStats(0, 0), new PayMethodStats(0, 0),
                    new PayMethodStats(0, 0), new PayMethodStats(0, 0),
                    0, 0, 0, 0, 0, 0);
        }
    }

    /** Generates a JasperReport PDF and returns the filled report. */
    public JasperPrint generateInvoiceReport(String invoiceNo, Connection conn) throws JRException {
        try (InputStream is = getClass().getResourceAsStream("/reports/invoice703.jrxml")) {
            if (is == null) throw new JRException("Report template not found: invoice703.jrxml");
            JasperReport compiled = JasperCompileManager.compileReport(is);
            Map<String, Object> params = new HashMap<>();
            params.put("InvoiceNo", invoiceNo);
            return JasperFillManager.fillReport(compiled, params, conn);
        } catch (Exception e) {
            throw new JRException("Failed to generate report: " + e.getMessage(), e);
        }
    }
}
