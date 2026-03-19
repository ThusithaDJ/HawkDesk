package com.olympus.system.hawkdeskpos.service;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.util.JRLoader;
import org.hibernate.SessionFactory;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;

public class ReportService {

    private final SessionFactory sf;

    public ReportService(SessionFactory sf) {
        this.sf = sf;
    }

    public record PeriodStats(double revenue, int transactions, double avgSale, int returns) {}

    public PeriodStats getStats(Date from, Date to) {
        try (var session = sf.openSession()) {
            Object[] row = (Object[]) session.createNativeQuery(
                    "SELECT COALESCE(SUM(total),0), COUNT(*), COALESCE(AVG(total),0), " +
                    "(SELECT COUNT(*) FROM `return` r JOIN invoiceinfo ii2 ON r.invoice_no = ii2.invoice_no " +
                    "WHERE ii2.date BETWEEN :from AND :to) " +
                    "FROM invoiceinfo WHERE date BETWEEN :from AND :to AND stat != 'Void'",
                    Object[].class)
                    .setParameter("from", from)
                    .setParameter("to",   to)
                    .uniqueResult();
            if (row == null) return new PeriodStats(0, 0, 0, 0);
            return new PeriodStats(
                    row[0] instanceof Number n ? n.doubleValue() : 0,
                    row[1] instanceof Number n ? n.intValue()    : 0,
                    row[2] instanceof Number n ? n.doubleValue() : 0,
                    row[3] instanceof Number n ? n.intValue()    : 0);
        } catch (Exception e) {
            System.err.println("ReportService.getStats: " + e.getMessage());
            return new PeriodStats(0, 0, 0, 0);
        }
    }

    public record TopItem(String name, int qtySold, double revenue) {}

    public List<TopItem> getTopItems(Date from, Date to, int limit) {
        try (var session = sf.openSession()) {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = session.createNativeQuery(
                    "SELECT i.item_name, SUM(inv.qty), SUM(inv.sub_total) " +
                    "FROM invoice inv JOIN item i ON inv.item_id = i.item_id " +
                    "JOIN invoiceinfo ii ON inv.invoice_no = ii.invoice_no " +
                    "WHERE ii.date BETWEEN :from AND :to AND ii.stat != 'Void' " +
                    "GROUP BY i.item_id ORDER BY SUM(inv.qty) DESC LIMIT :lim", Object[].class)
                    .setParameter("from", from)
                    .setParameter("to",   to)
                    .setParameter("lim",  limit)
                    .list();
            List<TopItem> result = new ArrayList<>();
            for (Object[] r : rows) {
                result.add(new TopItem(
                        (String) r[0],
                        r[1] instanceof Number n ? n.intValue()    : 0,
                        r[2] instanceof Number n ? n.doubleValue() : 0));
            }
            return result;
        } catch (Exception e) {
            System.err.println("ReportService.getTopItems: " + e.getMessage());
            return Collections.emptyList();
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
