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
     * Creates Return records and updates invoice status.
     * Does NOT restore stock — that is handled via the manage-return workflow.
     */
    public void processReturn(String invoiceNo, Map<Integer, Double> returnLines,
                              String reason, String refundMethod, Long employeeId) {
        processReturn(invoiceNo, returnLines, reason, refundMethod, employeeId, null);
    }

    /**
     * Processes a return with optional customer assignment (used for Exchange refund method).
     * Creates Return records and updates invoice status.
     * Does NOT restore stock — that is handled via the manage-return workflow.
     */
    public void processReturn(String invoiceNo, Map<Integer, Double> returnLines,
                              String reason, String refundMethod, Long employeeId,
                              Integer customerId) {
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            Invoiceinfo ii = session.get(Invoiceinfo.class, invoiceNo);
            if (ii == null) { tx.rollback(); return; }
            Employee emp      = employeeId != null ? session.get(Employee.class, employeeId) : null;
            Customer customer = customerId != null ? session.get(Customer.class, customerId) : null;
            Date now = new Date();

            for (Map.Entry<Integer, Double> entry : returnLines.entrySet()) {
                int    stockId = entry.getKey();
                double qty     = entry.getValue();

                Stock stock = session.get(Stock.class, stockId);

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
                ret.setQty(qty);  // Double — decimal primary-unit quantity
                ret.setReason(reason);
                ret.setReturnTo(refundMethod);
                ret.setStat(refundMethod);
                ret.setEmployee(emp);
                ret.setItem(item);
                ret.setStock(stock);
                ret.setItemName(itemName);
                ret.setReturnDate(now);
                ret.setCustomer(customer);
                session.persist(ret);
            }

            // Determine new invoice status
            if ("Void".equals(refundMethod)) {
                ii.setStat("Void");
                session.merge(ii);
            } else {
                // Per-item (per stock) comparison — Full Return only when every line is fully returned
                List<Object[]> soldLines = session.createQuery(
                        "SELECT i.stock.stockId, i.qty FROM Invoice i WHERE i.invoiceinfo.invoiceNo = :no",
                        Object[].class)
                        .setParameter("no", invoiceNo).list();

                List<Object[]> prevRetLines = session.createQuery(
                        "SELECT r.stock.stockId, SUM(r.qty) FROM Return r " +
                        "WHERE r.invoiceinfo.invoiceNo = :no GROUP BY r.stock.stockId",
                        Object[].class)
                        .setParameter("no", invoiceNo).list();

                Map<Integer, Double> soldMap     = new HashMap<>();
                Map<Integer, Double> returnedMap = new HashMap<>();
                for (Object[] row : soldLines)    soldMap.put((Integer) row[0], ((Number) row[1]).doubleValue());
                for (Object[] row : prevRetLines) returnedMap.put((Integer) row[0], ((Number) row[1]).doubleValue());
                // Merge current batch of returns
                for (Map.Entry<Integer, Double> e : returnLines.entrySet())
                    returnedMap.merge(e.getKey(), e.getValue(), Double::sum);

                if (!soldMap.isEmpty()) {
                    boolean fullReturn = soldMap.entrySet().stream().allMatch(
                            e -> returnedMap.getOrDefault(e.getKey(), 0.0) >= e.getValue() - 0.001);
                    ii.setStat(fullReturn ? "Full Return" : "Partial Return");
                    session.merge(ii);
                }
            }

            tx.commit();
            audit.log(AuditLog.Action.INSERT, "return", null,
                    null, "{\"invoiceNo\":\"" + invoiceNo + "\",\"reason\":\"" + reason + "\"}",
                    employeeId, null);
        }
    }

    /** Restore item to the same stock/batch it was sold from. Marks return as resolved. */
    public void resolveReturnSameStock(int returnId, Long employeeId) {
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            Return ret = session.get(Return.class, returnId);
            if (ret == null || "Resolved - Same Stock".equals(ret.getResolveAction())) {
                tx.rollback(); return;
            }
            double qty = ret.getQty() != null ? ret.getQty() : 0.0;
            Stock stock = ret.getStock();
            if (stock != null && qty > 0) {
                stock.setQty((stock.getQty() != null ? stock.getQty() : 0.0) + qty);
                session.merge(stock);
                // Also restore ItemBatch qty_remaining (batch stays int — round up)
                if (stock.getBatchObj() != null) {
                    ItemBatch batch = stock.getBatchObj();
                    batch.setQtyRemaining((int) Math.ceil(batch.getQtyRemaining() + qty));
                    if (batch.getStatus() == com.olympus.system.hawkdeskpos.db.dao.BatchStatus.EMPTY) {
                        batch.setStatus(com.olympus.system.hawkdeskpos.db.dao.BatchStatus.ACTIVE);
                    }
                    session.merge(batch);
                }
            }
            ret.setResolveAction("Resolved - Same Stock");
            session.merge(ret);
            tx.commit();
            audit.log(AuditLog.Action.UPDATE, "return", (long) returnId,
                    null, "{\"action\":\"Resolved - Same Stock\"}", employeeId, null);
        } catch (Exception e) {
            System.err.println("ReturnService.resolveReturnSameStock: " + e.getMessage());
        }
    }

    /**
     * Add returned item back to stock as a new batch with given cost/price.
     * Marks return as resolved.
     */
    public void resolveReturnNewBatch(int returnId, double cost, double price, Long employeeId) {
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            Return ret = session.get(Return.class, returnId);
            if (ret == null) { tx.rollback(); return; }
            double qty = ret.getQty() != null ? ret.getQty() : 0.0;
            Item item = ret.getItem();
            if (item != null && qty > 0) {
                Stock newStock = new Stock();
                newStock.setItem(item);
                newStock.setQty(qty);
                newStock.setCost(cost);
                newStock.setPrice(price);
                newStock.setBatch("RTN-" + returnId);
                newStock.setStat("Active");
                session.persist(newStock);
            }
            ret.setResolveAction("Resolved - New Batch");
            session.merge(ret);
            tx.commit();
            audit.log(AuditLog.Action.UPDATE, "return", (long) returnId,
                    null, "{\"action\":\"Resolved - New Batch\",\"cost\":" + cost + ",\"price\":" + price + "}",
                    employeeId, null);
        } catch (Exception e) {
            System.err.println("ReturnService.resolveReturnNewBatch: " + e.getMessage());
        }
    }

    /** Mark return as Return to Seller, optionally linking a GRN number. */
    public void markReturnToSeller(int returnId, Integer linkedGrnNo, Long employeeId) {
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            Return ret = session.get(Return.class, returnId);
            if (ret == null) { tx.rollback(); return; }
            ret.setResolveAction("Return to Seller");
            if (linkedGrnNo != null) ret.setLinkedGrnNo(linkedGrnNo);
            session.merge(ret);
            tx.commit();
            audit.log(AuditLog.Action.UPDATE, "return", (long) returnId,
                    null, "{\"action\":\"Return to Seller\",\"grnNo\":" + linkedGrnNo + "}",
                    employeeId, null);
        } catch (Exception e) {
            System.err.println("ReturnService.markReturnToSeller: " + e.getMessage());
        }
    }

    /** Get original cost (cost_price × returned_qty) from invoice line — for supplier-return deductions. */
    private double getReturnItemCost(int returnId) {
        try (var session = sf.openSession()) {
            Return ret = session.get(Return.class, returnId);
            if (ret == null || ret.getStock() == null) return 0;
            Object result = session.createNativeQuery(
                    "SELECT COALESCE(cost_price, 0) FROM invoice " +
                    "WHERE invoice_no = :no AND stock_id = :sid LIMIT 1")
                    .setParameter("no", ret.getInvoiceinfo() != null ? ret.getInvoiceinfo().getInvoiceNo() : "")
                    .setParameter("sid", ret.getStock().getStockId())
                    .uniqueResult();
            if (result == null) return 0;
            double unitCost = ((Number) result).doubleValue();
            return unitCost * (ret.getQty() != null ? ret.getQty() : 0);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Get original sale value for a return (unit sell price × returned qty, from invoice line). */
    public double getOriginalSaleCost(int returnId) {
        try (var session = sf.openSession()) {
            Return ret = session.get(Return.class, returnId);
            if (ret == null || ret.getStock() == null) return 0;
            Object result = session.createNativeQuery(
                    "SELECT COALESCE(sub_total, 0), COALESCE(qty, 1) FROM invoice " +
                    "WHERE invoice_no = :no AND stock_id = :sid LIMIT 1")
                    .setParameter("no", ret.getInvoiceinfo() != null ? ret.getInvoiceinfo().getInvoiceNo() : "")
                    .setParameter("sid", ret.getStock().getStockId())
                    .uniqueResult();
            if (!(result instanceof Object[] row)) return 0;
            double lineTotal  = ((Number) row[0]).doubleValue();
            double invoiceQty = ((Number) row[1]).doubleValue();
            if (invoiceQty == 0) return 0;
            double unitSell = lineTotal / invoiceQty;
            return unitSell * (ret.getQty() != null ? ret.getQty() : 0);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Returns where resolveAction = 'Return to Seller' that have not yet been linked to a GRN. */
    public List<ReturnDto> listReturnToSellerPending() {
        try (var session = sf.openSession()) {
            List<Return> list = session.createQuery(
                    "SELECT r FROM Return r " +
                    "LEFT JOIN FETCH r.invoiceinfo " +
                    "LEFT JOIN FETCH r.employee " +
                    "LEFT JOIN FETCH r.item " +
                    "LEFT JOIN FETCH r.stock " +
                    "WHERE r.resolveAction = 'Return to Seller' " +
                    "ORDER BY r.returnId DESC",
                    Return.class).list();
            return list.stream().map(r -> {
                ReturnDto base = toDto(r);
                int rid = r.getReturnId() != null ? r.getReturnId() : 0;
                double cost = (r.getInvoiceinfo() != null && r.getStock() != null && r.getQty() != null)
                        ? getReturnItemCost(rid) : 0;
                return new ReturnDto(base.returnId(), base.invoiceNo(), base.returnDate(), base.itemName(),
                        base.itemId(), base.stockId(), base.qty(), base.reason(), base.refundMethod(),
                        base.stat(), base.resolveAction(), base.linkedGrnNo(), base.cashierName(),
                        cost, base.customerRef(), base.resolvedInvoiceNo());
            }).collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("ReturnService.listReturnToSellerPending: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Links a set of return records to a GRN.
     * Sets resolveAction to "GRN" and stores the grninfo integer PK.
     */
    public void linkGrnToReturns(List<Integer> returnIds, int grnNo, Long employeeId) {
        if (returnIds == null || returnIds.isEmpty()) return;
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            for (Integer rid : returnIds) {
                Return ret = session.get(Return.class, rid);
                if (ret == null) continue;
                ret.setResolveAction("GRN");
                ret.setLinkedGrnNo(grnNo);
                session.merge(ret);
            }
            tx.commit();
            audit.log(AuditLog.Action.UPDATE, "return", null,
                    null, "{\"action\":\"GRN\",\"grnNo\":" + grnNo + ",\"returnIds\":" + returnIds + "}",
                    employeeId, null);
        } catch (Exception e) {
            System.err.println("ReturnService.linkGrnToReturns: " + e.getMessage());
        }
    }

    /** All return records, newest first. */
    public List<ReturnDto> listAllReturns() {
        try (var session = sf.openSession()) {
            List<Return> list = session.createQuery(
                    "SELECT r FROM Return r " +
                    "LEFT JOIN FETCH r.invoiceinfo " +
                    "LEFT JOIN FETCH r.employee " +
                    "LEFT JOIN FETCH r.item " +
                    "LEFT JOIN FETCH r.stock " +
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
                    "LEFT JOIN FETCH r.item " +
                    "LEFT JOIN FETCH r.stock " +
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

    /**
     * Returns all pending Exchange-type returns (not yet applied to a sale).
     * Used by the New Sale screen to offer exchange credit.
     */
    public List<ReturnDto> listPendingExchangeReturns() {
        try (var session = sf.openSession()) {
            List<Return> list = session.createQuery(
                    "SELECT r FROM Return r " +
                    "LEFT JOIN FETCH r.invoiceinfo " +
                    "LEFT JOIN FETCH r.employee " +
                    "LEFT JOIN FETCH r.item " +
                    "LEFT JOIN FETCH r.stock " +
                    "LEFT JOIN FETCH r.customer " +
                    "WHERE r.returnTo = 'Exchange' " +
                    "AND (r.resolveAction IS NULL OR r.resolveAction = 'Pending') " +
                    "ORDER BY r.returnId DESC",
                    Return.class).list();
            return list.stream().map(this::toDto).collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("ReturnService.listPendingExchangeReturns: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Pending Exchange returns filtered by customer (returns from invoices belonging to that customer,
     * OR returns explicitly assigned to that customer).
     */
    public List<ReturnDto> listPendingExchangeReturnsByCustomer(int customerId) {
        try (var session = sf.openSession()) {
            List<Return> list = session.createQuery(
                    "SELECT r FROM Return r " +
                    "LEFT JOIN FETCH r.invoiceinfo ii " +
                    "LEFT JOIN FETCH r.employee " +
                    "LEFT JOIN FETCH r.item " +
                    "LEFT JOIN FETCH r.stock " +
                    "LEFT JOIN FETCH r.customer c " +
                    "WHERE r.returnTo = 'Exchange' " +
                    "AND (r.resolveAction IS NULL OR r.resolveAction = 'Pending') " +
                    "AND (c.customerId = :cid OR ii.customer.customerId = :cid) " +
                    "ORDER BY r.returnId DESC",
                    Return.class)
                    .setParameter("cid", customerId)
                    .list();
            return list.stream().map(this::toDto).collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("ReturnService.listPendingExchangeReturnsByCustomer: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Marks exchange returns as resolved by linking them to the sale invoice that consumed their credit.
     * Sets resolveAction = "Exchange" and records the resolvedInvoiceNo.
     */
    public void linkExchangeReturnsToInvoice(List<Integer> returnIds, String invoiceNo, Long empId) {
        if (returnIds == null || returnIds.isEmpty()) return;
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            for (Integer rid : returnIds) {
                Return ret = session.get(Return.class, rid);
                if (ret == null) continue;
                ret.setResolveAction("Exchange");
                ret.setResolvedInvoiceNo(invoiceNo);
                session.merge(ret);
            }
            tx.commit();
            audit.log(AuditLog.Action.UPDATE, "return", null,
                    null, "{\"action\":\"Exchange\",\"invoiceNo\":\"" + invoiceNo + "\",\"returnIds\":" + returnIds + "}",
                    empId, null);
        } catch (Exception e) {
            System.err.println("ReturnService.linkExchangeReturnsToInvoice: " + e.getMessage());
        }
    }

    private ReturnDto toDto(Return r) {
        String cashier   = r.getEmployee() != null ? r.getEmployee().getName() : "—";
        String invoiceNo = r.getInvoiceinfo() != null ? r.getInvoiceinfo().getInvoiceNo() : "";
        int stockId = (r.getStock() != null && r.getStock().getStockId() != null)
                ? r.getStock().getStockId() : 0;
        int itemId  = (r.getItem() != null && r.getItem().getItemId() != null)
                ? r.getItem().getItemId() : 0;
        // Compute original sale cost from invoice line if available
        double saleCost = 0;
        if (r.getInvoiceinfo() != null && stockId > 0 && r.getQty() != null) {
            saleCost = getOriginalSaleCost(r.getReturnId() != null ? r.getReturnId() : 0);
        }
        String customerRef = (r.getCustomer() != null && r.getCustomer().getName() != null)
                ? r.getCustomer().getName() : null;
        return new ReturnDto(
                r.getReturnId() != null ? r.getReturnId() : 0,
                invoiceNo,
                r.getReturnDate(),
                r.getItemName() != null ? r.getItemName() : "",
                itemId,
                stockId,
                r.getQty()      != null ? r.getQty()      : 0,
                r.getReason()   != null ? r.getReason()   : "",
                r.getReturnTo() != null ? r.getReturnTo() : "",
                r.getStat()     != null ? r.getStat()     : "",
                r.getResolveAction(),
                r.getLinkedGrnNo(),
                cashier,
                saleCost,
                customerRef,
                r.getResolvedInvoiceNo()
        );
    }
}
