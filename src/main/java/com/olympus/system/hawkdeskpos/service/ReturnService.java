package com.olympus.system.hawkdeskpos.service;

import com.olympus.system.hawkdeskpos.db.dao.*;
import com.olympus.system.hawkdeskpos.dto.ReturnDto;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import java.util.*;
import java.util.stream.Collectors;

public class ReturnService {

    private final SessionFactory sf;
    private final AuditService   audit;

    public ReturnService(SessionFactory sf, AuditService audit) {
        this.sf    = sf;
        this.audit = audit;
    }

    /**
     * Processes a return for given invoice line items.
     * Updates stock, creates Return records, and updates invoice status.
     *
     * @param invoiceNo    the original invoice number
     * @param returnLines  map of stockId → qty to return
     * @param reason       reason for return
     * @param refundMethod "Cash" | "Store Credit" | "Exchange" | "Void"
     * @param employeeId   acting employee
     */
    public void processReturn(String invoiceNo, Map<Integer, Integer> returnLines,
                              String reason, String refundMethod, Long employeeId) {
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            Invoiceinfo ii = session.get(Invoiceinfo.class, invoiceNo);
            if (ii == null) { tx.rollback(); return; }
            Employee emp = employeeId != null ? session.get(Employee.class, employeeId) : null;
            Date now = new Date();

            for (Map.Entry<Integer, Integer> entry : returnLines.entrySet()) {
                int stockId = entry.getKey();
                int qty     = entry.getValue();

                // Restore stock
                Stock stock = session.get(Stock.class, stockId);
                if (stock != null) {
                    stock.setQty(stock.getQty() + qty);
                    session.merge(stock);
                }

                // Find item via original invoice line
                Item item = null;
                String itemName = "";
                List<Invoice> invLines = session.createQuery(
                        "FROM Invoice i WHERE i.invoiceinfo.invoiceNo = :no AND i.stock.stockId = :sid",
                        Invoice.class)
                        .setParameter("no", invoiceNo)
                        .setParameter("sid", stockId)
                        .list();
                if (!invLines.isEmpty() && invLines.get(0).getItem() != null) {
                    item     = invLines.get(0).getItem();
                    itemName = item.getItemName() != null ? item.getItemName() : "";
                }

                Return ret = new Return();
                ret.setInvoiceinfo(ii);
                ret.setQty(qty);
                ret.setReason(reason);
                ret.setReturnTo(refundMethod);
                ret.setStat(refundMethod);
                ret.setEmployee(emp);
                ret.setItem(item);
                ret.setStock(stock);
                ret.setItemName(itemName);
                ret.setReturnDate(now);
                session.persist(ret);
            }

            // Determine new invoice status
            if ("Void".equals(refundMethod)) {
                ii.setStat("Void");
                session.merge(ii);
            } else {
                Long sold = session.createQuery(
                        "SELECT SUM(i.qty) FROM Invoice i WHERE i.invoiceinfo.invoiceNo = :no", Long.class)
                        .setParameter("no", invoiceNo).uniqueResult();
                Long prevReturned = session.createQuery(
                        "SELECT SUM(r.qty) FROM Return r WHERE r.invoiceinfo.invoiceNo = :no", Long.class)
                        .setParameter("no", invoiceNo).uniqueResult();
                long totalSold     = sold         != null ? sold         : 0;
                long alreadyRet    = prevReturned != null ? prevReturned : 0;
                long nowReturning  = returnLines.values().stream().mapToInt(Integer::intValue).sum();
                long totalReturned = alreadyRet + nowReturning;

                if (totalSold > 0) {
                    ii.setStat(totalReturned >= totalSold ? "Full Return" : "Partial Return");
                    session.merge(ii);
                }
            }

            tx.commit();
            audit.log(AuditLog.Action.INSERT, "return", null,
                    null, "{\"invoiceNo\":\"" + invoiceNo + "\",\"reason\":\"" + reason + "\"}",
                    employeeId, null);
        }
    }

    /** All return records, newest first. */
    public List<ReturnDto> listAllReturns() {
        try (var session = sf.openSession()) {
            List<Return> list = session.createQuery(
                    "SELECT r FROM Return r " +
                    "LEFT JOIN FETCH r.invoiceinfo " +
                    "LEFT JOIN FETCH r.employee " +
                    "ORDER BY r.returnId DESC",
                    Return.class).list();
            return list.stream().map(this::toDto).collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("ReturnService.listAllReturns: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /** Return records for a single invoice, ordered by returnId. */
    public List<ReturnDto> getReturnsForInvoice(String invoiceNo) {
        try (var session = sf.openSession()) {
            List<Return> list = session.createQuery(
                    "SELECT r FROM Return r " +
                    "LEFT JOIN FETCH r.invoiceinfo " +
                    "LEFT JOIN FETCH r.employee " +
                    "WHERE r.invoiceinfo.invoiceNo = :no " +
                    "ORDER BY r.returnId",
                    Return.class)
                    .setParameter("no", invoiceNo).list();
            return list.stream().map(this::toDto).collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("ReturnService.getReturnsForInvoice: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private ReturnDto toDto(Return r) {
        String cashier   = r.getEmployee() != null ? r.getEmployee().getName() : "—";
        String invoiceNo = r.getInvoiceinfo() != null ? r.getInvoiceinfo().getInvoiceNo() : "";
        int stockId = (r.getStock() != null && r.getStock().getStockId() != null)
                ? r.getStock().getStockId() : 0;
        return new ReturnDto(
                r.getReturnId() != null ? r.getReturnId() : 0,
                invoiceNo,
                r.getReturnDate(),
                r.getItemName() != null ? r.getItemName() : "",
                stockId,
                r.getQty()      != null ? r.getQty()      : 0,
                r.getReason()   != null ? r.getReason()   : "",
                r.getReturnTo() != null ? r.getReturnTo() : "",
                cashier
        );
    }
}
