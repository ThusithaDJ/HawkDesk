package com.olympus.system.hawkdeskpos.service;

import com.olympus.system.hawkdeskpos.db.dao.*;
import com.olympus.system.hawkdeskpos.dto.InvoiceDto;
import com.olympus.system.hawkdeskpos.dto.SaleLineDto;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class SaleService {

    private final SessionFactory sf;
    private final AuditService   audit;

    public SaleService(SessionFactory sf, AuditService audit) {
        this.sf    = sf;
        this.audit = audit;
    }

    /** Today's revenue, transaction count, and items sold. */
    public record TodaySummary(double revenue, int transactions, int itemsSold) {}

    public TodaySummary getTodaySummary() {
        try (var session = sf.openSession()) {
            // Revenue + transaction count
            Object[] row = (Object[]) session.createNativeQuery(
                    "SELECT COALESCE(SUM(total), 0), COUNT(*) " +
                    "FROM invoiceinfo WHERE DATE(date) = CURDATE() AND stat != 'Void'",
                    Object[].class).uniqueResult();
            // Items sold (separate query avoids LEFT JOIN row multiplication)
            Object itemsObj = session.createNativeQuery(
                    "SELECT COALESCE(SUM(i.qty), 0) FROM invoice i " +
                    "JOIN invoiceinfo ii ON i.invoice_no = ii.invoice_no " +
                    "WHERE DATE(ii.date) = CURDATE() AND ii.stat != 'Void'",
                    Object.class).uniqueResult();
            if (row == null) return new TodaySummary(0, 0, 0);
            return new TodaySummary(
                    row[0] instanceof Number n ? n.doubleValue() : 0,
                    row[1] instanceof Number n ? n.intValue()    : 0,
                    itemsObj instanceof Number n ? n.intValue()  : 0);
        } catch (Exception e) {
            System.err.println("SaleService.getTodaySummary: " + e.getMessage());
            return new TodaySummary(0, 0, 0);
        }
    }

    /** Creates and persists an invoice. Decrements stock. Returns the invoice number. */
    public String createInvoice(List<SaleLineDto> lines, double discount,
                                String paymentMethod, double amountPaid, Long employeeId) {
        String invNo = generateInvoiceNumber();
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            Employee emp = employeeId != null ? session.get(Employee.class, employeeId) : null;

            double total = lines.stream().mapToDouble(SaleLineDto::lineTotal).sum() - discount;

            Invoiceinfo header = new Invoiceinfo();
            header.setInvoiceNo(invNo);
            header.setDate(new Date());
            header.setTotal(total);
            header.setPaid(amountPaid);
            header.setDiscount(discount);
            header.setPaymentMethod(paymentMethod);
            header.setStat("Paid");
            header.setEmployee(emp);
            session.persist(header);

            for (SaleLineDto line : lines) {
                Stock stock = session.get(Stock.class, line.stockId());
                Item  item  = session.get(Item.class,  line.itemId());
                if (stock != null) {
                    stock.setQty(Math.max(0, stock.getQty() - line.qty()));
                    session.merge(stock);
                }
                Invoice inv = new Invoice();
                inv.setInvoiceinfo(header);
                inv.setItem(item);
                inv.setStock(stock);
                inv.setQty(line.qty());
                inv.setSubTotal(line.lineTotal());
                inv.setDateTime(new Date());
                inv.setEmployee(emp);
                session.persist(inv);
            }
            tx.commit();
            audit.log(AuditLog.Action.INSERT, "invoiceinfo", null, null,
                    "{\"invoiceNo\":\"" + invNo + "\",\"total\":" + total + "}", employeeId, null);
        }
        return invNo;
    }

    /** Lists invoices for SalesHistoryPanel and FindInvoicePanel. */
    public List<InvoiceDto> listInvoices(String search, Date from, Date to, String paymentMethod) {
        try (var session = sf.openSession()) {
            var q = session.createQuery(
                    "SELECT DISTINCT ii FROM Invoiceinfo ii " +
                    "LEFT JOIN FETCH ii.employee " +
                    "LEFT JOIN FETCH ii.invoices inv " +
                    "LEFT JOIN FETCH inv.item " +
                    "ORDER BY ii.date DESC",
                    Invoiceinfo.class)
                    .setMaxResults(500).list();
            return q.stream()
                    .filter(ii -> matches(ii, search, from, to, paymentMethod))
                    .map(this::toDto)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("SaleService.listInvoices: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public InvoiceDto findByNumber(String invoiceNo) {
        try (var session = sf.openSession()) {
            List<Invoiceinfo> list = session.createQuery(
                    "SELECT DISTINCT ii FROM Invoiceinfo ii " +
                    "LEFT JOIN FETCH ii.employee " +
                    "LEFT JOIN FETCH ii.invoices inv " +
                    "LEFT JOIN FETCH inv.item " +
                    "WHERE ii.invoiceNo = :no",
                    Invoiceinfo.class)
                    .setParameter("no", invoiceNo)
                    .list();
            return list.isEmpty() ? null : toDto(list.get(0));
        } catch (Exception e) {
            System.err.println("SaleService.findByNumber: " + e.getMessage());
            return null;
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** INV-YYMMDD-NNNN — sequential per day */
    private String generateInvoiceNumber() {
        String prefix = "INV-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd")) + "-";
        try (var session = sf.openSession()) {
            Long count = session.createQuery(
                    "SELECT COUNT(ii) FROM Invoiceinfo ii WHERE ii.invoiceNo LIKE :p", Long.class)
                    .setParameter("p", prefix + "%").uniqueResult();
            return prefix + String.format("%04d", (count == null ? 0 : count) + 1);
        }
    }

    private boolean matches(Invoiceinfo ii, String search, Date from, Date to, String method) {
        if (search != null && !search.isEmpty() &&
                !ii.getInvoiceNo().toLowerCase().contains(search.toLowerCase())) return false;
        if (from != null && ii.getDate() != null && ii.getDate().before(from)) return false;
        if (to   != null && ii.getDate() != null && ii.getDate().after(to))    return false;
        if (method != null && !method.isEmpty() && !"All".equals(method) &&
                !method.equals(ii.getPaymentMethod())) return false;
        return true;
    }

    private InvoiceDto toDto(Invoiceinfo ii) {
        String cashier = ii.getEmployee() != null ? ii.getEmployee().getName() : "—";
        List<SaleLineDto> lines = ii.getInvoices().stream()
                .map(inv -> new SaleLineDto(
                        inv.getItem() != null ? inv.getItem().getItemId() : 0,
                        inv.getStock() != null ? inv.getStock().getStockId() : 0,
                        inv.getItem() != null ? inv.getItem().getItemName() : "?",
                        inv.getItem() != null ? inv.getItem().getSku() : "",
                        inv.getQty() != null ? inv.getQty() : 0,
                        inv.getStock() != null && inv.getStock().getPrice() != null ? inv.getStock().getPrice() : 0,
                        inv.getSubTotal() != null ? inv.getSubTotal() : 0))
                .collect(Collectors.toList());
        return new InvoiceDto(ii.getInvoiceNo(), ii.getDate(),
                ii.getTotal() != null ? ii.getTotal() : 0,
                ii.getPaid()  != null ? ii.getPaid()  : 0,
                ii.getDiscount() != null ? ii.getDiscount() : 0,
                ii.getPaymentMethod() != null ? ii.getPaymentMethod() : "Cash",
                ii.getStat() != null ? ii.getStat() : "Paid",
                cashier, lines);
    }
}
