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
            Object[] row = (Object[]) session.createNativeQuery(
                    "SELECT COALESCE(SUM(total), 0), COUNT(*) " +
                    "FROM invoiceinfo WHERE DATE(date) = CURDATE() AND stat != 'Void'",
                    Object[].class).uniqueResult();
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

    /** Revenue for the current calendar week (Monday–Sunday). */
    public double getWeekRevenue() {
        try (var session = sf.openSession()) {
            Object obj = session.createNativeQuery(
                    "SELECT COALESCE(SUM(total), 0) FROM invoiceinfo " +
                    "WHERE YEARWEEK(date, 1) = YEARWEEK(CURDATE(), 1) AND stat != 'Void'",
                    Object.class).uniqueResult();
            return obj instanceof Number n ? n.doubleValue() : 0;
        } catch (Exception e) {
            System.err.println("SaleService.getWeekRevenue: " + e.getMessage());
            return 0;
        }
    }

    /** Count of invoices with stat = 'Return'. */
    public long getReturnCount() {
        try (var session = sf.openSession()) {
            Object obj = session.createNativeQuery(
                    "SELECT COUNT(*) FROM invoiceinfo WHERE stat = 'Return'",
                    Object.class).uniqueResult();
            return obj instanceof Number n ? n.longValue() : 0;
        } catch (Exception e) {
            return 0;
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
                Item item = session.get(Item.class, line.itemId());

                // Resolve stock: use specified stockId if valid, else pick first active (FIFO)
                Stock stock = null;
                if (line.stockId() > 0) {
                    stock = session.get(Stock.class, line.stockId());
                }
                if (stock == null) {
                    List<Stock> stocks = session.createQuery(
                            "FROM Stock s WHERE s.item.itemId = :id AND s.stat = 'Active' " +
                            "AND s.qty > 0 ORDER BY s.stockId",
                            Stock.class).setParameter("id", line.itemId()).setMaxResults(1).list();
                    if (!stocks.isEmpty()) stock = stocks.get(0);
                }

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

    /**
     * Lists invoices with optional date filtering pushed to DB.
     * search and paymentMethod are filtered in memory after loading.
     */
    public List<InvoiceDto> listInvoices(String search, Date from, Date to, String paymentMethod) {
        try (var session = sf.openSession()) {
            StringBuilder hql = new StringBuilder(
                    "SELECT DISTINCT ii FROM Invoiceinfo ii " +
                    "LEFT JOIN FETCH ii.employee " +
                    "LEFT JOIN FETCH ii.invoices inv " +
                    "LEFT JOIN FETCH inv.item " +
                    "LEFT JOIN FETCH inv.stock " +
                    "WHERE 1=1");
            if (from != null) hql.append(" AND ii.date >= :from");
            if (to   != null) hql.append(" AND ii.date <= :to");
            hql.append(" ORDER BY ii.date DESC");

            var q = session.createQuery(hql.toString(), Invoiceinfo.class);
            if (from != null) q.setParameter("from", from);
            if (to   != null) q.setParameter("to",   to);
            q.setMaxResults(1000);

            return q.list().stream()
                    .filter(ii -> matchesFilter(ii, search, paymentMethod))
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
                    "LEFT JOIN FETCH inv.stock " +
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

    private boolean matchesFilter(Invoiceinfo ii, String search, String method) {
        if (search != null && !search.isEmpty() &&
                !ii.getInvoiceNo().toLowerCase().contains(search.toLowerCase())) return false;
        if (method != null && !method.isEmpty() && !"All".equals(method) &&
                !method.equals(ii.getPaymentMethod())) return false;
        return true;
    }

    private InvoiceDto toDto(Invoiceinfo ii) {
        String cashier = ii.getEmployee() != null ? ii.getEmployee().getName() : "—";
        List<SaleLineDto> lines = ii.getInvoices().stream()
                .map(inv -> {
                    // Use variant SKU from stock if available, otherwise item-level SKU
                    String sku = inv.getItem() != null ? (inv.getItem().getSku() != null ? inv.getItem().getSku() : "") : "";
                    if (inv.getStock() != null && inv.getStock().getSku() != null && !inv.getStock().getSku().isEmpty()) {
                        sku = inv.getStock().getSku();
                    }
                    String batch = inv.getStock() != null && inv.getStock().getBatch() != null
                            ? inv.getStock().getBatch() : "";
                    return new SaleLineDto(
                            inv.getItem() != null ? inv.getItem().getItemId() : 0,
                            inv.getStock() != null ? inv.getStock().getStockId() : 0,
                            inv.getItem() != null ? inv.getItem().getItemName() : "?",
                            sku,
                            inv.getQty() != null ? inv.getQty() : 0,
                            inv.getStock() != null && inv.getStock().getPrice() != null ? inv.getStock().getPrice() : 0,
                            inv.getSubTotal() != null ? inv.getSubTotal() : 0,
                            batch);
                })
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
