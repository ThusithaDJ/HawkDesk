package com.olympus.system.hawkdeskpos.service;

import com.olympus.system.hawkdeskpos.db.dao.*;
import com.olympus.system.hawkdeskpos.dto.ItemDto;
import com.olympus.system.hawkdeskpos.dto.StockLevelDto;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class ItemService {

    private final SessionFactory sf;
    private final AuditService   audit;

    public ItemService(SessionFactory sf, AuditService audit) {
        this.sf    = sf;
        this.audit = audit;
    }

    /**
     * Live search by item name or item SKU — used by GRN SearchDropdown.
     * Returns one result per matching Item (aggregated across all stocks).
     */
    public List<ItemDto> search(String query) {
        if (query == null || query.trim().isEmpty()) return Collections.emptyList();
        try (var session = sf.openSession()) {
            String q = "%" + query.trim().toLowerCase() + "%";
            return session.createQuery(
                    "FROM Item i LEFT JOIN FETCH i.category LEFT JOIN FETCH i.brands " +
                    "WHERE (lower(i.itemName) LIKE :q OR lower(i.sku) LIKE :q) " +
                    "AND i.stat = 'Active' ORDER BY i.itemName",
                    Item.class)
                    .setParameter("q", q)
                    .setMaxResults(8)
                    .list()
                    .stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("ItemService.search: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Live search for the New Sale screen.
     * Returns one result per active variant (item_variant) plus one aggregated result
     * for unnamed stocks. Also searches by variant SKU directly.
     */
    public List<ItemDto> searchForSale(String query) {
        if (query == null || query.trim().isEmpty()) return Collections.emptyList();
        try (var session = sf.openSession()) {
            String q = "%" + query.trim().toLowerCase() + "%";

            // Items matching by name or item-level SKU
            List<Item> byItem = session.createQuery(
                    "SELECT DISTINCT i FROM Item i " +
                    "LEFT JOIN FETCH i.category LEFT JOIN FETCH i.brands " +
                    "WHERE i.stat = 'Active' " +
                    "AND (lower(i.itemName) LIKE :q OR lower(i.sku) LIKE :q) " +
                    "ORDER BY i.itemName",
                    Item.class)
                    .setParameter("q", q).setMaxResults(12).list();

            // item_variants whose SKU matches (may belong to items not matched above)
            List<ItemVariant> byVariantSku = session.createQuery(
                    "SELECT v FROM ItemVariant v " +
                    "JOIN FETCH v.item i LEFT JOIN FETCH i.category LEFT JOIN FETCH i.brands " +
                    "WHERE v.stat = 'Active' AND lower(v.sku) LIKE :q",
                    ItemVariant.class)
                    .setParameter("q", q).setMaxResults(12).list();

            List<ItemDto> results = new ArrayList<>();
            Set<Integer> processedItemIds = new HashSet<>();

            for (Item item : byItem) {
                processedItemIds.add(item.getItemId());
                results.addAll(expandToVariants(item));
            }
            for (ItemVariant v : byVariantSku) {
                if (processedItemIds.add(v.getItem().getItemId())) {
                    results.addAll(expandToVariants(v.getItem()));
                }
            }
            return results.stream().limit(20).collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("ItemService.searchForSale: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Expands an item into per-variant ItemDtos for the sale search results:
     * - Active item_variants → one result each (aggregates all stock records for that variant)
     * - Unnamed stocks (variant_id = null) → one aggregated result, sold FIFO
     */
    private List<ItemDto> expandToVariants(Item item) {
        List<Stock> activeStocks = item.getStocks().stream()
                .filter(s -> "Active".equals(s.getStat()))
                .collect(Collectors.toList());

        List<ItemDto> results = new ArrayList<>();

        // Named variants — one result per active item_variant
        item.getVariants().stream()
                .filter(v -> "Active".equals(v.getStat()))
                .forEach(v -> {
                    List<Stock> vStocks = activeStocks.stream()
                            .filter(s -> s.getVariant() != null
                                    && s.getVariant().getVariantId().equals(v.getVariantId()))
                            .collect(Collectors.toList());
                    double qty = vStocks.stream().mapToDouble(s -> s.getQty() != null ? s.getQty() : 0.0).sum();
                    Stock sellFrom = vStocks.stream()
                            .filter(s -> s.getQty() != null && s.getQty() > 0)
                            .min(Comparator.comparingInt(Stock::getStockId))
                            .orElse(vStocks.isEmpty() ? null : vStocks.get(0));
                    if (sellFrom != null) {
                        results.add(toVariantDto(sellFrom, item, v, qty));
                    } else {
                        // variant exists but no stock yet — show as OUT
                        String eu = item.getSellUnit() != null ? item.getSellUnit() : "pcs";
                        String au = item.getSecUnit();
                        results.add(new ItemDto(item.getItemId(), item.getItemName(), v.getSku(),
                                item.getCategory() != null ? item.getCategory().getCategoryName() : "",
                                item.getBrands()   != null ? item.getBrands().getBrandName()      : "",
                                eu,
                                item.getStat()     != null ? item.getStat()   : "Active",
                                0.0,
                                item.getMinLevel() != null ? item.getMinLevel() : 5,
                                item.getMaxLevel() != null ? item.getMaxLevel() : 100,
                                0, 0, 0, "",
                                au,
                                item.getConversionFactor() != null ? item.getConversionFactor() : 1.0,
                                0));
                    }
                });

        // Unnamed stocks (no variant) → one result per batch so the user can pick
        List<Stock> unnamed = activeStocks.stream()
                .filter(s -> s.getVariant() == null)
                .collect(Collectors.toList());
        if (!unnamed.isEmpty()) {
            List<Stock> withQty = unnamed.stream()
                    .filter(s -> s.getQty() != null && s.getQty() > 0)
                    .sorted(Comparator.comparingInt(Stock::getStockId))
                    .collect(Collectors.toList());
            if (withQty.isEmpty()) {
                // All batches empty — show one OUT entry so the item is still visible
                results.add(toStockDtoAggregated(unnamed.get(0), item, 0));
            } else {
                for (Stock s : withQty) {
                    results.add(toStockDtoAggregated(s, item, s.getQty()));
                }
            }
        }

        if (results.isEmpty()) {
            results.add(toDto(item)); // no stock, no variants — show as OUT
        }
        return results;
    }

    private ItemDto toVariantDto(Stock sellFrom, Item item, ItemVariant variant, double totalQty) {
        String eu = item.getSellUnit() != null ? item.getSellUnit() : "pcs";
        String au = item.getSecUnit();
        int batchCount = (int) item.getStocks().stream()
                .filter(s -> "Active".equals(s.getStat()) && s.getVariant() != null
                        && s.getVariant().getVariantId().equals(variant.getVariantId()))
                .count();
        return new ItemDto(
                item.getItemId(), item.getItemName(), variant.getSku(),
                item.getCategory() != null ? item.getCategory().getCategoryName() : "",
                item.getBrands()   != null ? item.getBrands().getBrandName()      : "",
                eu,
                item.getStat()     != null ? item.getStat()     : "Active",
                totalQty,
                item.getMinLevel() != null ? item.getMinLevel() : 5,
                item.getMaxLevel() != null ? item.getMaxLevel() : 100,
                sellFrom.getCost()  != null ? sellFrom.getCost()  : 0,
                sellFrom.getPrice() != null ? sellFrom.getPrice() : 0,
                sellFrom.getStockId(),
                sellFrom.getBatch() != null ? sellFrom.getBatch() : "",
                au,
                item.getConversionFactor() != null ? item.getConversionFactor() : 1.0,
                batchCount);
    }

    private ItemDto toStockDtoAggregated(Stock sellFrom, Item item, double totalQty) {
        String eu = item.getSellUnit() != null ? item.getSellUnit() : "pcs";
        String au = item.getSecUnit();
        int batchCount = (int) item.getStocks().stream()
                .filter(s -> "Active".equals(s.getStat())).count();
        return new ItemDto(
                item.getItemId(), item.getItemName(),
                item.getSku() != null ? item.getSku() : "",
                item.getCategory() != null ? item.getCategory().getCategoryName() : "",
                item.getBrands()   != null ? item.getBrands().getBrandName()      : "",
                eu,
                item.getStat()     != null ? item.getStat()     : "Active",
                totalQty,
                item.getMinLevel() != null ? item.getMinLevel() : 5,
                item.getMaxLevel() != null ? item.getMaxLevel() : 100,
                sellFrom.getCost()  != null ? sellFrom.getCost()  : 0,
                sellFrom.getPrice() != null ? sellFrom.getPrice() : 0,
                sellFrom.getStockId(),
                sellFrom.getBatch() != null ? sellFrom.getBatch() : "",
                au,
                item.getConversionFactor() != null ? item.getConversionFactor() : 1.0,
                batchCount);
    }

    /** All items as StockLevelDto (for ViewStockPanel). */
    public List<StockLevelDto> listAllStockLevels() {
        try (var session = sf.openSession()) {
            return session.createQuery(
                    "FROM Item i LEFT JOIN FETCH i.category LEFT JOIN FETCH i.brands ORDER BY i.itemName",
                    Item.class)
                    .list()
                    .stream()
                    .map(this::toStockLevelDto)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("ItemService.listAllStockLevels: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /** Items below minimum stock level. */
    public List<StockLevelDto> listLowStock() {
        return listAllStockLevels().stream()
                .filter(s -> "LOW".equals(s.stockStatus()) || "OUT".equals(s.stockStatus()))
                .collect(Collectors.toList());
    }

    public int countLowStock() {
        return listLowStock().size();
    }

    /** Auto-generates a unique SKU: HWK-YYMMDD-NNNN */
    public String generateSku() {
        String prefix = "HWK-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd")) + "-";
        try (var session = sf.openSession()) {
            Long count = session.createQuery(
                    "SELECT COUNT(i) FROM Item i WHERE i.sku LIKE :prefix", Long.class)
                    .setParameter("prefix", prefix + "%")
                    .uniqueResult();
            return prefix + String.format("%04d", (count == null ? 0 : count) + 1);
        }
    }

    /** Checks if a SKU is already taken (for Add Item validation). */
    public boolean isSkuUnique(String sku, Integer excludeItemId) {
        try (var session = sf.openSession()) {
            String hql = excludeItemId == null
                    ? "SELECT COUNT(i) FROM Item i WHERE i.sku = :sku"
                    : "SELECT COUNT(i) FROM Item i WHERE i.sku = :sku AND i.itemId <> :id";
            var q = session.createQuery(hql, Long.class).setParameter("sku", sku);
            if (excludeItemId != null) q.setParameter("id", excludeItemId);
            Long c = q.uniqueResult();
            return c == null || c == 0;
        }
    }

    /**
     * Returns true if the item has zero stock qty, no sale invoices, and no GRN records —
     * meaning it is safe to permanently delete from the database.
     */
    public boolean canDeleteItem(int itemId) {
        try (var session = sf.openSession()) {
            long invCount = session.createQuery(
                    "SELECT COUNT(i) FROM Invoice i WHERE i.item.itemId = :id", Long.class)
                    .setParameter("id", itemId).uniqueResult();
            if (invCount > 0) return false;
            long grnCount = session.createQuery(
                    "SELECT COUNT(g) FROM Grn g WHERE g.item.itemId = :id", Long.class)
                    .setParameter("id", itemId).uniqueResult();
            if (grnCount > 0) return false;
            Double totalQty = session.createQuery(
                    "SELECT COALESCE(SUM(s.qty), 0.0) FROM Stock s " +
                    "WHERE s.item.itemId = :id AND s.stat = 'Active'", Double.class)
                    .setParameter("id", itemId).uniqueResult();
            return totalQty == null || totalQty <= 0.0;
        } catch (Exception e) {
            System.err.println("ItemService.canDeleteItem: " + e.getMessage());
            return false;
        }
    }

    /**
     * Permanently deletes an item and all its dependent records (stock, variants, batches,
     * adjustments). Only call after confirming {@link #canDeleteItem} returns true.
     */
    public void deleteItemPermanently(int itemId, Long employeeId) {
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            session.createMutationQuery(
                    "DELETE FROM StockAdjustment sa WHERE sa.item.itemId = :id")
                    .setParameter("id", itemId).executeUpdate();
            session.createMutationQuery(
                    "DELETE FROM ItemBatch b WHERE b.item.itemId = :id")
                    .setParameter("id", itemId).executeUpdate();
            session.createMutationQuery(
                    "DELETE FROM Stock s WHERE s.item.itemId = :id")
                    .setParameter("id", itemId).executeUpdate();
            session.createMutationQuery(
                    "DELETE FROM ItemVariant v WHERE v.item.itemId = :id")
                    .setParameter("id", itemId).executeUpdate();
            Item item = session.get(Item.class, itemId);
            if (item != null) session.remove(item);
            tx.commit();
            audit.log(com.olympus.system.hawkdeskpos.db.dao.AuditLog.Action.DELETE,
                    "item", (long) itemId, "{\"itemId\":" + itemId + "}", null, employeeId, null);
        } catch (Exception e) {
            System.err.println("ItemService.deleteItemPermanently: " + e.getMessage());
        }
    }

    /** Creates a new item. Returns the new item ID. */
    public int createItem(String name, String sku, Integer catId, Integer brandId,
                          String unit, String sellUnit, double conversionFactor,
                          double cost, double price,
                          int openingQty, int minLevel, int maxLevel, Long employeeId) {
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            Category cat   = catId   != null ? session.get(Category.class, catId)   : null;
            Brands   brand = brandId != null ? session.get(Brands.class, brandId)   : null;
            Item item = new Item(cat, brand);
            item.setItemName(name);
            item.setSku(sku);
            item.setSellUnit(unit != null && !unit.isBlank() ? unit : "pcs");
            item.setSecUnit(sellUnit != null && !sellUnit.isBlank() ? sellUnit : null);
            item.setConversionFactor(conversionFactor > 0 ? conversionFactor : 1.0);
            item.setMinLevel(minLevel);
            item.setMaxLevel(maxLevel);
            session.persist(item);
            tx.commit();

            // Create initial stock record
            if (openingQty > 0) {
                try (var s2 = sf.openSession()) {
                    Transaction tx2 = s2.beginTransaction();
                    Stock stock = new Stock();
                    stock.setItem(s2.get(Item.class, item.getItemId()));
                    stock.setQty((double) openingQty);
                    stock.setCost(cost);
                    stock.setPrice(price);
                    stock.setBatch("OPENING");
                    if (employeeId != null) stock.setEmployee(s2.get(Employee.class, employeeId));
                    s2.persist(stock);
                    tx2.commit();
                }
            }
            audit.log(com.olympus.system.hawkdeskpos.db.dao.AuditLog.Action.INSERT,
                    "item", (long) item.getItemId(), null, "{\"name\":\"" + name + "\"}", employeeId, null);
            return item.getItemId();
        }
    }

    /** Updates an existing item's fields (except SKU and stock qty). */
    public void updateItem(int itemId, String name, Integer catId, Integer brandId,
                           String unit, String sellUnit, double conversionFactor,
                           double price, int minLevel, int maxLevel,
                           String stat, Long employeeId) {
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            Item item = session.get(Item.class, itemId);
            if (item == null) { tx.rollback(); return; }
            String old = "{\"name\":\"" + item.getItemName() + "\",\"price\":" + price + "}";

            String newSecUnit = sellUnit != null && !sellUnit.isBlank() ? sellUnit : null;
            double newFactor  = conversionFactor > 0 ? conversionFactor : 1.0;

            item.setItemName(name);
            item.setSellUnit(unit != null && !unit.isBlank() ? unit : "pcs");
            item.setSecUnit(newSecUnit);
            item.setConversionFactor(newFactor);
            item.setMinLevel(minLevel);
            item.setMaxLevel(maxLevel);
            item.setStat(stat);
            if (catId   != null) item.setCategory(session.get(Category.class, catId));
            if (brandId != null) item.setBrands(session.get(Brands.class, brandId));
            for (Stock s : item.getStocks()) {
                if ("Active".equals(s.getStat()) && (s.getSku() == null || s.getSku().isEmpty()))
                    s.setPrice(price);
            }
            session.merge(item);
            tx.commit();
            audit.log(com.olympus.system.hawkdeskpos.db.dao.AuditLog.Action.UPDATE,
                    "item", (long) itemId, old, "{\"name\":\"" + name + "\"}", employeeId, null);
        }
    }

    /**
     * Adjusts the qty of all active stock records for an item to reach newTotalQty,
     * distributes evenly across batches, and records a StockAdjustment log entry.
     */
    public void adjustItemQty(int itemId, double newTotalQty, String reason, String reference, Long employeeId) {
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            Item item = session.get(Item.class, itemId);
            if (item == null) { tx.rollback(); return; }

            List<Stock> activeStocks = session.createQuery(
                    "FROM Stock s WHERE s.item.itemId = :id AND s.stat = 'Active' ORDER BY s.stockId",
                    Stock.class).setParameter("id", itemId).list();

            double currentTotal = activeStocks.stream()
                    .mapToDouble(s -> s.getQty() != null ? s.getQty() : 0.0).sum();

            if (activeStocks.isEmpty()) {
                // No stock records — create a manual-adjustment one
                Stock s = new Stock();
                s.setItem(item);
                s.setQty(newTotalQty);
                s.setBatch("MANUAL");
                if (employeeId != null) s.setEmployee(session.get(Employee.class, employeeId));
                session.persist(s);
                activeStocks = List.of(s);
            } else if (activeStocks.size() == 1) {
                Stock s = activeStocks.get(0);
                s.setQty(newTotalQty);
                session.merge(s);
            } else {
                // Distribute proportionally; remainder goes to last batch
                double total = activeStocks.stream()
                        .mapToDouble(s -> s.getQty() != null ? s.getQty() : 0.0).sum();
                double distributed = 0;
                for (int i = 0; i < activeStocks.size() - 1; i++) {
                    Stock s = activeStocks.get(i);
                    double share = total > 0
                            ? newTotalQty * ((s.getQty() != null ? s.getQty() : 0.0) / total)
                            : newTotalQty / activeStocks.size();
                    double rounded = Math.round(share * 1000.0) / 1000.0;
                    s.setQty(rounded);
                    distributed += rounded;
                    session.merge(s);
                }
                Stock last = activeStocks.get(activeStocks.size() - 1);
                last.setQty(Math.max(0, newTotalQty - distributed));
                session.merge(last);
            }

            // Log the adjustment
            com.olympus.system.hawkdeskpos.db.dao.StockAdjustment adj =
                    new com.olympus.system.hawkdeskpos.db.dao.StockAdjustment();
            adj.setItem(item);
            adj.setAdjustmentType(com.olympus.system.hawkdeskpos.db.dao.StockAdjustment.AdjustmentType.SET);
            adj.setQtyBefore((int) Math.round(currentTotal));
            adj.setQtyChange((int) Math.round(newTotalQty - currentTotal));
            adj.setQtyAfter((int) Math.round(newTotalQty));
            adj.setReason(reason != null && !reason.isBlank() ? reason : "Manual adjustment");
            adj.setReference(reference != null && !reference.isBlank() ? reference : null);
            if (employeeId != null) adj.setEmployee(session.get(Employee.class, employeeId));
            session.persist(adj);

            tx.commit();
            audit.log(com.olympus.system.hawkdeskpos.db.dao.AuditLog.Action.UPDATE,
                    "stock", (long) itemId, "{\"qty\":" + currentTotal + "}",
                    "{\"qty\":" + newTotalQty + ",\"reason\":\"" + (reason != null ? reason : "") + "\"}", employeeId, null);
        }
    }

    public ItemDto findById(int itemId) {
        try (var session = sf.openSession()) {
            Item item = session.get(Item.class, itemId);
            return item != null ? toDto(item) : null;
        }
    }

    // ── Converters ─────────────────────────────────────────────────────────────

    private ItemDto toDto(Item item) {
        List<Stock> activeStocks = item.getStocks().stream()
                .filter(s -> "Active".equals(s.getStat()))
                .collect(Collectors.toList());
        double qty   = activeStocks.stream().mapToDouble(s -> s.getQty() == null ? 0.0 : s.getQty()).sum();
        double cost  = activeStocks.stream().filter(s -> s.getCost()  != null).mapToDouble(Stock::getCost).max().orElse(0);
        double price = activeStocks.stream().filter(s -> s.getPrice() != null).mapToDouble(Stock::getPrice).max().orElse(0);
        int batchCount = activeStocks.size();
        String cat   = item.getCategory() != null ? item.getCategory().getCategoryName() : "";
        String brand = item.getBrands()   != null ? item.getBrands().getBrandName()      : "";
        String primaryUnit = item.getSellUnit() != null ? item.getSellUnit() : "pcs";
        String sellUnit    = item.getSecUnit();
        return new ItemDto(item.getItemId(), item.getItemName(), item.getSku() != null ? item.getSku() : "",
                cat, brand, primaryUnit,
                item.getStat() != null ? item.getStat() : "Active",
                qty, item.getMinLevel() != null ? item.getMinLevel() : 5,
                item.getMaxLevel() != null ? item.getMaxLevel() : 100, cost, price, 0, "",
                sellUnit,
                item.getConversionFactor() != null ? item.getConversionFactor() : 1.0,
                batchCount);
    }

    /** Current available qty for a specific stock record. Used for cart availability checks. */
    public double getAvailableQtyForStock(int stockId) {
        try (var session = sf.openSession()) {
            Stock s = session.get(Stock.class, stockId);
            return (s != null && s.getQty() != null) ? s.getQty() : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    /** All active stocks for an item as ItemDtos — used by the batch picker dialog. */
    public List<ItemDto> listBatchesForSale(int itemId) {
        try (var session = sf.openSession()) {
            Item item = session.createQuery(
                    "FROM Item i LEFT JOIN FETCH i.category LEFT JOIN FETCH i.brands " +
                    "LEFT JOIN FETCH i.stocks WHERE i.itemId = :id", Item.class)
                    .setParameter("id", itemId).uniqueResult();
            if (item == null) return Collections.emptyList();
            return item.getStocks().stream()
                    .filter(s -> "Active".equals(s.getStat()) && s.getQty() != null && s.getQty() > 0)
                    .sorted(Comparator.comparingInt(Stock::getStockId))
                    .map(s -> toStockDtoAggregated(s, item, s.getQty()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("ItemService.listBatchesForSale: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private StockLevelDto toStockLevelDto(Item item) {
        ItemDto d = toDto(item);
        return new StockLevelDto(d.itemId(), d.itemName(), d.sku(), d.categoryName(),
                d.brandName(), d.currentQty(), d.minLevel(), d.maxLevel(),
                d.costPrice(), d.sellingPrice(), d.stat());
    }
}
