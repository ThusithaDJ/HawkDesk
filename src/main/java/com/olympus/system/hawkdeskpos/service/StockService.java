package com.olympus.system.hawkdeskpos.service;

import com.olympus.system.hawkdeskpos.db.dao.*;
import com.olympus.system.hawkdeskpos.dto.AdjustmentDto;
import com.olympus.system.hawkdeskpos.dto.GrnDto;
import com.olympus.system.hawkdeskpos.dto.StockLevelDto;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class StockService {

    private final SessionFactory sf;
    private final AuditService   audit;

    public StockService(SessionFactory sf, AuditService audit) {
        this.sf    = sf;
        this.audit = audit;
    }

    /** Current total quantity across all active stock records for an item. */
    public int currentQty(int itemId) {
        try (var session = sf.openSession()) {
            Long qty = session.createQuery(
                    "SELECT COALESCE(SUM(s.qty), 0) FROM Stock s " +
                    "WHERE s.item.itemId = :id AND s.stat = 'Active'", Long.class)
                    .setParameter("id", itemId).uniqueResult();
            return qty == null ? 0 : qty.intValue();
        }
    }

    /** Persists a GRN and increments stock. Returns the new GRN number string. */
    public String saveGrn(GrnDto dto, Long employeeId) {
        String grnNumber = dto.grnNumber() != null ? dto.grnNumber() : generateGrnNumber();
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            Employee emp = employeeId != null ? session.get(Employee.class, employeeId) : null;

            Grninfo info = new Grninfo();
            info.setDate(dto.date() != null ? dto.date() : new Date());
            info.setSubTotal(dto.totalCost());
            info.setSupplier(dto.supplier());
            info.setReference(dto.reference());
            session.persist(info);

            for (GrnDto.GrnLineDto line : dto.lines()) {
                Item item = session.get(Item.class, line.itemId());
                if (item == null) continue;

                // Update or create stock record
                Stock stock = new Stock();
                stock.setItem(item);
                stock.setGrninfo(info);
                stock.setQty(line.qtyReceived());
                stock.setCost(line.costPrice());
                stock.setPrice(line.sellingPrice());
                stock.setBatch(grnNumber);
                stock.setEmployee(emp);
                session.persist(stock);

                Grn grn = new Grn(item, info);
                grn.setItemQty(line.qtyReceived());
                grn.setItemCost(line.costPrice());
                grn.setItemPrice(line.sellingPrice());
                session.persist(grn);
            }
            tx.commit();
            audit.log(AuditLog.Action.INSERT, "grninfo", (long) info.getGrnNo(),
                    null, "{\"grnNumber\":\"" + grnNumber + "\"}", employeeId, null);
        }
        return grnNumber;
    }

    /** Records a stock adjustment (add/remove/set/writeoff). */
    public void adjustStock(AdjustmentDto dto, Long employeeId) {
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            Item item = session.get(Item.class, dto.itemId());
            if (item == null) { tx.rollback(); return; }
            Employee emp = employeeId != null ? session.get(Employee.class, employeeId) : null;

            StockAdjustment adj = new StockAdjustment();
            adj.setItem(item);
            adj.setAdjustmentType(StockAdjustment.AdjustmentType.valueOf(dto.adjustmentType()));
            adj.setQtyBefore(dto.qtyBefore());
            adj.setQtyChange(dto.qtyChange());
            adj.setQtyAfter(dto.qtyAfter());
            adj.setReason(dto.reason());
            adj.setNotes(dto.notes());
            adj.setLossValue(dto.lossValue() > 0 ? BigDecimal.valueOf(dto.lossValue()) : null);
            adj.setEmployee(emp);
            session.persist(adj);

            // Apply the quantity delta to the first active stock record
            List<Stock> stocks = session.createQuery(
                    "FROM Stock s WHERE s.item.itemId = :id AND s.stat = 'Active' ORDER BY s.stockId",
                    Stock.class).setParameter("id", dto.itemId()).setMaxResults(1).list();
            if (!stocks.isEmpty()) {
                stocks.get(0).setQty(dto.qtyAfter());
                session.merge(stocks.get(0));
            }
            tx.commit();
            audit.log(AuditLog.Action.UPDATE, "stock", null,
                    "{\"qty\":" + dto.qtyBefore() + "}", "{\"qty\":" + dto.qtyAfter() + "}",
                    employeeId, dto.adjustmentType() + ": " + dto.reason());
        }
    }

    /** Public alias for UI use. */
    public String generateGrnNo() { return generateGrnNumber(); }

    private String generateGrnNumber() {
        String prefix = "GRN-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd")) + "-";
        try (var session = sf.openSession()) {
            Long c = session.createQuery("SELECT COUNT(g) FROM Grninfo g", Long.class).uniqueResult();
            return prefix + String.format("%03d", (c == null ? 0 : c) + 1);
        }
    }

    /** Lists all GRNs for GrnHistoryPanel — returns lightweight summary DTOs. */
    public List<GrnDto> listGrnHistory() {
        try (var session = sf.openSession()) {
            return session.createQuery("FROM Grninfo g ORDER BY g.grnNo DESC", Grninfo.class)
                    .setMaxResults(500).list().stream().map(g -> {
                        // Load GRN lines
                        List<com.olympus.system.hawkdeskpos.dto.GrnDto.GrnLineDto> lines =
                            session.createQuery("FROM Grn grn WHERE grn.grninfo.grnNo = :no", Grn.class)
                                .setParameter("no", g.getGrnNo()).list().stream()
                                .map(ln -> new com.olympus.system.hawkdeskpos.dto.GrnDto.GrnLineDto(
                                        ln.getItem() != null ? ln.getItem().getItemId() : 0,
                                        ln.getItem() != null ? ln.getItem().getItemName() : "",
                                        ln.getItemQty() != null ? ln.getItemQty() : 0,
                                        ln.getItemCost() != null ? ln.getItemCost() : 0,
                                        ln.getItemPrice() != null ? ln.getItemPrice() : 0, 0))
                                .toList();
                        return new GrnDto(
                                g.getSupplier() != null ? g.getSupplier() : ("GRN-" + g.getGrnNo()),
                                g.getDate(), g.getSupplier(), g.getReference(),
                                g.getSubTotal() != null ? g.getSubTotal() : 0, lines);
                    }).toList();
        } catch (Exception e) {
            System.err.println("StockService.listGrnHistory: " + e.getMessage());
            return Collections.emptyList();
        }
    }
}
