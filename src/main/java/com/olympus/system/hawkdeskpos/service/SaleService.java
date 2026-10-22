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

    /** Today's revenue, transaction count, items sold, and gross profit. */
    public record TodaySummary(double revenue, int transactions, int itemsSold, double profit) {}

    public TodaySummary getTodaySummary() {
        try (var session = sf.openSession()) {
            Object[] row = (Object[]) session.createNativeQuery(
                    "SELECT COALESCE(SUM(net_total), 0), COUNT(*) " +
                    "FROM invoiceinfo WHERE DATE(date) = CURDATE() AND stat != 'Void'",
                    Object[].class).uniqueResult();
            Object itemsObj = session.createNativeQuery(
                    "SELECT COALESCE(SUM(i.qty), 0) FROM invoice i " +
                    "JOIN invoiceinfo ii ON i.invoice_no = ii.invoice_no " +
                    "WHERE DATE(ii.date) = CURDATE() AND ii.stat != 'Void'",
                    Object.class).uniqueResult();
            Object costObj = session.createNativeQuery(
                    "SELECT COALESCE(SUM(i.qty * COALESCE(i.cost_price, s.cost, 0)), 0) " +
                    "FROM invoice i " +
                    "JOIN invoiceinfo ii ON i.invoice_no = ii.invoice_no " +
                    "LEFT JOIN stock s ON i.stock_id = s.stock_id " +
                    "WHERE DATE(ii.date) = CURDATE() AND ii.stat != 'Void'",
                    Object.class).uniqueResult();
            if (row == null) return new TodaySummary(0, 0, 0, 0);
            double revenue = row[0] instanceof Number n ? n.doubleValue() : 0;
            double cost    = costObj instanceof Number n ? n.doubleValue() : 0;
            return new TodaySummary(
                    revenue,
                    row[1] instanceof Number n ? n.intValue()    : 0,
                    itemsObj instanceof Number n ? n.intValue()  : 0,
                    revenue - cost);
        } catch (Exception e) {
            System.err.println("SaleService.getTodaySummary: " + e.getMessage());
            return new TodaySummary(0, 0, 0, 0);
        }
    }

    /** Revenue for the current calendar week (Monday–Sunday). */
    public double getWeekRevenue() {
        try (var session = sf.openSession()) {
            Object obj = session.createNativeQuery(
                    "SELECT COALESCE(SUM(net_total), 0) FROM invoiceinfo " +
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
        return createInvoice(lines, discount, paymentMethod, amountPaid, employeeId, null, null);
    }

    /** Creates and persists an invoice with optional customer and credit resolve date. */
    public String createInvoice(List<SaleLineDto> lines, double discount,
                                String paymentMethod, double amountPaid, Long employeeId,
                                Integer customerId, LocalDate creditResolveDate) {
        String invNo = generateInvoiceNumber();
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            Employee emp      = employeeId != null ? session.get(Employee.class, employeeId) : null;
            Customer customer = customerId != null ? session.get(Customer.class, customerId) : null;

            double subTotal   = lines.stream().mapToDouble(SaleLineDto::lineTotal).sum();
            double tax        = 0.0;
            double grossTotal = subTotal + tax;
            double netTotal   = Math.max(0, grossTotal - discount);

            // For credit: stat = "Credit" (unpaid), paid = 0
            String stat = "CREDIT".equalsIgnoreCase(paymentMethod) ? "Credit" : "Paid";

            Invoiceinfo header = new Invoiceinfo();
            header.setInvoiceNo(invNo);
            header.setDate(new Date());
            header.setSubTotal(subTotal);
            header.setTax(tax);
            header.setGrossTotal(grossTotal);
            header.setNetTotal(netTotal);
            header.setPaid("CREDIT".equalsIgnoreCase(paymentMethod) ? 0.0 : amountPaid);
            header.setDiscount(discount);
            header.setPaymentMethod(paymentMethod.toUpperCase());
            header.setStat(stat);
            header.setEmployee(emp);
            header.setCustomer(customer);
            if (creditResolveDate != null) {
                header.setCreditResolveDate(java.sql.Date.valueOf(creditResolveDate));
            }
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
                    double newQty = Math.max(0.0, stock.getQty() - line.qty());
                    // Explicit HQL UPDATE guarantees the SQL is issued regardless of dirty-check
                    session.createMutationQuery(
                            "UPDATE Stock s SET s.qty = :newQty WHERE s.stockId = :id")
                            .setParameter("newQty", newQty)
                            .setParameter("id", stock.getStockId())
                            .executeUpdate();
                    // Keep ItemBatch qty_remaining in sync (batch stays integer — round down)
                    if (stock.getBatchObj() != null) {
                        ItemBatch batch = stock.getBatchObj();
                        int newRemaining = (int) Math.max(0.0, batch.getQtyRemaining() - line.qty());
                        com.olympus.system.hawkdeskpos.db.dao.BatchStatus newStatus =
                                newRemaining == 0 ? com.olympus.system.hawkdeskpos.db.dao.BatchStatus.EMPTY
                                                  : batch.getStatus();
                        session.createMutationQuery(
                                "UPDATE ItemBatch b SET b.qtyRemaining = :qty, b.status = :status WHERE b.batchId = :id")
                                .setParameter("qty", newRemaining)
                                .setParameter("status", newStatus)
                                .setParameter("id", batch.getBatchId())
                                .executeUpdate();
                    }
                }
                Invoice inv = new Invoice();
                inv.setInvoiceinfo(header);
                inv.setItem(item);
                inv.setStock(stock);
                inv.setQty(line.qty());
                inv.setSubTotal(line.lineTotal());
                inv.setDateTime(new Date());
                inv.setEmployee(emp);
                // Capture cost at time of sale — use the line's cost (from ItemDto) if provided,
                // otherwise fall back to the resolved stock's current cost.
                double saleCost = line.costPrice() > 0 ? line.costPrice()
                        : (stock != null && stock.getCost() != null ? stock.getCost() : 0);
                inv.setCostPrice(saleCost);
                if (stock != null && stock.getBatchObj() != null) {
                    inv.setBatch(stock.getBatchObj().getBatchNumber());
                    inv.setBatchObj(stock.getBatchObj());
                }
                session.persist(inv);
            }
            // Log CREATED event in invoice_history
            InvoiceHistory histEntry = new InvoiceHistory(
                    invNo, InvoiceHistory.EventType.CREATED, new Date(),
                    netTotal, paymentMethod.toUpperCase() + " — " + stat, emp);
            session.persist(histEntry);

            tx.commit();
            audit.log(AuditLog.Action.INSERT, "invoiceinfo", null, null,
                    "{\"invoiceNo\":\"" + invNo + "\",\"total\":" + netTotal + "}", employeeId, null);
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
                    "LEFT JOIN FETCH ii.customer " +
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
                    "LEFT JOIN FETCH ii.customer " +
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

    /** List all credit (unpaid / partially paid) invoices. */
    public List<InvoiceDto> listCreditInvoices() {
        try (var session = sf.openSession()) {
            return session.createQuery(
                    "SELECT DISTINCT ii FROM Invoiceinfo ii " +
                    "LEFT JOIN FETCH ii.employee " +
                    "LEFT JOIN FETCH ii.customer " +
                    "LEFT JOIN FETCH ii.invoices inv " +
                    "LEFT JOIN FETCH inv.item " +
                    "LEFT JOIN FETCH inv.stock " +
                    "WHERE ii.paymentMethod = 'CREDIT' AND ii.stat IN ('Credit', 'Partial') " +
                    "ORDER BY ii.date DESC",
                    Invoiceinfo.class)
                    .setMaxResults(500)
                    .list().stream().map(this::toDto).collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("SaleService.listCreditInvoices: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * All credit invoices with optional search (invoice# or customer name/phone)
     * and optional date range + status filter ("Outstanding", "Paid", or null=all).
     */
    public List<InvoiceDto> listAllCreditInvoicesFiltered(String search, Date from, Date to, String statusFilter) {
        try (var session = sf.openSession()) {
            StringBuilder hql = new StringBuilder(
                    "SELECT DISTINCT ii FROM Invoiceinfo ii " +
                    "LEFT JOIN FETCH ii.employee " +
                    "LEFT JOIN FETCH ii.customer " +
                    "LEFT JOIN FETCH ii.invoices inv " +
                    "LEFT JOIN FETCH inv.item " +
                    "LEFT JOIN FETCH inv.stock " +
                    "WHERE ii.paymentMethod = 'CREDIT'");
            if ("Outstanding".equals(statusFilter)) hql.append(" AND ii.stat IN ('Credit','Partial')");
            else if ("Paid".equals(statusFilter))   hql.append(" AND ii.stat = 'Paid'");
            if (from != null) hql.append(" AND ii.date >= :from");
            if (to   != null) hql.append(" AND ii.date <= :to");
            hql.append(" ORDER BY ii.date DESC");

            var q = session.createQuery(hql.toString(), Invoiceinfo.class).setMaxResults(1000);
            if (from != null) q.setParameter("from", from);
            if (to   != null) q.setParameter("to",   to);
            List<Invoiceinfo> list = q.list();

            // Batch-fetch resolved dates for paid invoices
            List<String> paidNos = list.stream()
                    .filter(ii -> "Paid".equals(ii.getStat()))
                    .map(Invoiceinfo::getInvoiceNo)
                    .collect(Collectors.toList());
            Map<String, Date> resolvedDates = batchFetchPaidDates(session, paidNos);

            // Apply search filter (invoice# or customer name/phone)
            return list.stream()
                    .filter(ii -> {
                        if (search == null || search.isEmpty()) return true;
                        String lc = search.toLowerCase();
                        if (ii.getInvoiceNo().toLowerCase().contains(lc)) return true;
                        if (ii.getCustomer() != null) {
                            String name  = ii.getCustomer().getName()  != null ? ii.getCustomer().getName().toLowerCase()  : "";
                            String phone = ii.getCustomer().getPhone() != null ? ii.getCustomer().getPhone().toLowerCase() : "";
                            if (name.contains(lc) || phone.contains(lc)) return true;
                        }
                        return false;
                    })
                    .map(ii -> toDto(ii, resolvedDates))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("SaleService.listAllCreditInvoicesFiltered: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /** List all invoices for a specific customer (all payment types, most recent first). */
    public List<InvoiceDto> listInvoicesByCustomer(int customerId) {
        try (var session = sf.openSession()) {
            List<Invoiceinfo> list = session.createQuery(
                    "SELECT DISTINCT ii FROM Invoiceinfo ii " +
                    "LEFT JOIN FETCH ii.employee " +
                    "LEFT JOIN FETCH ii.customer " +
                    "LEFT JOIN FETCH ii.invoices inv " +
                    "LEFT JOIN FETCH inv.item " +
                    "LEFT JOIN FETCH inv.stock " +
                    "WHERE ii.customer.customerId = :cid " +
                    "ORDER BY ii.date DESC",
                    Invoiceinfo.class)
                    .setParameter("cid", customerId)
                    .setMaxResults(500)
                    .list();
            // Batch-fetch resolved dates for paid credit invoices
            List<String> paidNos = list.stream()
                    .filter(ii -> "Paid".equals(ii.getStat()) && "CREDIT".equals(ii.getPaymentMethod()))
                    .map(Invoiceinfo::getInvoiceNo)
                    .collect(Collectors.toList());
            Map<String, Date> resolvedDates = batchFetchPaidDates(session, paidNos);
            return list.stream().map(ii -> toDto(ii, resolvedDates)).collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("SaleService.listInvoicesByCustomer: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /** List credit invoices with a resolve date on or before today (for reminders). */
    public List<InvoiceDto> listOverdueCreditInvoices() {
        try (var session = sf.openSession()) {
            return session.createNativeQuery(
                    "SELECT invoice_no FROM invoiceinfo " +
                    "WHERE payment_method = 'CREDIT' AND stat IN ('Credit', 'Partial') " +
                    "AND credit_resolve_date IS NOT NULL AND credit_resolve_date <= CURDATE() " +
                    "ORDER BY credit_resolve_date",
                    String.class)
                    .list().stream()
                    .map(this::findByNumber)
                    .filter(d -> d != null)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("SaleService.listOverdueCreditInvoices: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Apply a payment towards a credit invoice.
     * Updates paid amount and stat (Partial / Paid) and logs to invoice_history.
     */
    public void resolveCreditDebt(String invoiceNo, double paymentAmount) {
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            Invoiceinfo ii = session.get(Invoiceinfo.class, invoiceNo);
            if (ii == null) { tx.rollback(); return; }
            double newPaid = (ii.getPaid() != null ? ii.getPaid() : 0) + paymentAmount;
            double total   = ii.getNetTotal() != null ? ii.getNetTotal() : 0;
            ii.setPaid(newPaid);
            String newStat = newPaid >= total - 0.001 ? "Paid" : "Partial";
            ii.setStat(newStat);
            session.merge(ii);
            session.persist(new InvoiceHistory(invoiceNo, InvoiceHistory.EventType.PAID,
                    new Date(), paymentAmount, "Credit payment — " + newStat, null));
            tx.commit();
            audit.log(AuditLog.Action.UPDATE, "invoiceinfo", null, null,
                    "{\"invoiceNo\":\"" + invoiceNo + "\",\"payment\":" + paymentAmount + "}", null, null);
        } catch (Exception e) {
            System.err.println("SaleService.resolveCreditDebt: " + e.getMessage());
        }
    }

    // ── Invoice History ───────────────────────────────────────────────────────

    /** Records a lifecycle event for an invoice. */
    public void logInvoiceEvent(String invoiceNo, InvoiceHistory.EventType eventType,
                                Double amount, String notes, Long employeeId) {
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            Employee emp = employeeId != null ? session.get(Employee.class, employeeId) : null;
            InvoiceHistory entry = new InvoiceHistory(
                    invoiceNo, eventType, new java.util.Date(), amount, notes, emp);
            session.persist(entry);
            tx.commit();
        } catch (Exception e) {
            System.err.println("SaleService.logInvoiceEvent: " + e.getMessage());
        }
    }

    /** Returns all history events for a given invoice, ordered by event_date ascending. */
    public List<InvoiceHistory> getInvoiceHistory(String invoiceNo) {
        try (var session = sf.openSession()) {
            return session.createQuery(
                    "FROM InvoiceHistory h LEFT JOIN FETCH h.employee WHERE h.invoiceNo = :no ORDER BY h.eventDate ASC",
                    InvoiceHistory.class)
                    .setParameter("no", invoiceNo)
                    .getResultList();
        } catch (Exception e) {
            System.err.println("SaleService.getInvoiceHistory: " + e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    private InvoiceDto toDto(Invoiceinfo ii) {
        return toDto(ii, Collections.emptyMap());
    }

    private InvoiceDto toDto(Invoiceinfo ii, Map<String, Date> resolvedDates) {
        String cashier = ii.getEmployee() != null ? ii.getEmployee().getName() : "—";
        String customerName = ii.getCustomer() != null ? ii.getCustomer().getName() : null;
        List<SaleLineDto> lines = ii.getInvoices().stream()
                .map(inv -> {
                    String sku = inv.getItem() != null ? (inv.getItem().getSku() != null ? inv.getItem().getSku() : "") : "";
                    if (inv.getStock() != null && inv.getStock().getSku() != null && !inv.getStock().getSku().isEmpty()) {
                        sku = inv.getStock().getSku();
                    }
                    String batch = inv.getStock() != null && inv.getStock().getBatch() != null
                            ? inv.getStock().getBatch() : "";
                    String unit  = inv.getItem() != null && inv.getItem().getSellUnit() != null
                            ? inv.getItem().getSellUnit() : "";
                    return new SaleLineDto(
                            inv.getItem() != null ? inv.getItem().getItemId() : 0,
                            inv.getStock() != null ? inv.getStock().getStockId() : 0,
                            inv.getItem() != null ? inv.getItem().getItemName() : "?",
                            sku,
                            inv.getQty() != null ? inv.getQty() : 0,
                            inv.getStock() != null && inv.getStock().getPrice() != null ? inv.getStock().getPrice() : 0,
                            inv.getSubTotal() != null ? inv.getSubTotal() : 0,
                            batch, unit,
                            inv.getCostPrice() != null ? inv.getCostPrice() : 0);
                })
                .collect(Collectors.toList());
        Date resolvedDate = resolvedDates.get(ii.getInvoiceNo());
        return new InvoiceDto(ii.getInvoiceNo(), ii.getDate(),
                ii.getNetTotal()   != null ? ii.getNetTotal()   : 0,
                ii.getSubTotal()   != null ? ii.getSubTotal()   : 0,
                ii.getGrossTotal() != null ? ii.getGrossTotal() : 0,
                ii.getTax()        != null ? ii.getTax()        : 0,
                ii.getPaid()       != null ? ii.getPaid()       : 0,
                ii.getDiscount()   != null ? ii.getDiscount()   : 0,
                ii.getPaymentMethod() != null ? ii.getPaymentMethod() : "CASH",
                ii.getStat() != null ? ii.getStat() : "Paid",
                cashier, lines, customerName, ii.getCreditResolveDate(), resolvedDate);
    }

    /** Batch-fetch the earliest PAID event date from invoice_history for a set of invoice numbers. */
    @SuppressWarnings("unchecked")
    private Map<String, Date> batchFetchPaidDates(org.hibernate.Session session, List<String> invoiceNos) {
        if (invoiceNos == null || invoiceNos.isEmpty()) return Collections.emptyMap();
        List<Object[]> rows = session.createNativeQuery(
                "SELECT invoice_no, MIN(event_date) FROM invoice_history " +
                "WHERE invoice_no IN (:nos) AND event_type = 'PAID' " +
                "GROUP BY invoice_no",
                Object[].class)
                .setParameterList("nos", invoiceNos)
                .list();
        Map<String, Date> map = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] != null && row[1] != null) {
                map.put(row[0].toString(),
                        row[1] instanceof Date d ? d : new Date(((java.sql.Timestamp) row[1]).getTime()));
            }
        }
        return map;
    }
}
