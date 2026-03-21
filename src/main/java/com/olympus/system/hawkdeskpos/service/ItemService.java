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
            return results.stream().limit(10).collect(Collectors.toList());
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
                    int qty = vStocks.stream().mapToInt(s -> s.getQty() != null ? s.getQty() : 0).sum();
                    Stock sellFrom = vStocks.stream()
                            .filter(s -> s.getQty() != null && s.getQty() > 0)
                            .min(Comparator.comparingInt(Stock::getStockId))
                            .orElse(vStocks.isEmpty() ? null : vStocks.get(0));
                    if (sellFrom != null) {
                        results.add(toVariantDto(sellFrom, item, v, qty));
                    } else {
                        // variant exists but no stock yet — show as OUT
                        results.add(new ItemDto(item.getItemId(), item.getItemName(), v.getSku(),
                                item.getCategory() != null ? item.getCategory().getCategoryName() : "",
                                item.getBrands()   != null ? item.getBrands().getBrandName()      : "",
                                item.getUnit()     != null ? item.getUnit()   : "pcs",
                                item.getStat()     != null ? item.getStat()   : "Active",
                                0,
                                item.getMinLevel() != null ? item.getMinLevel() : 5,
                                item.getMaxLevel() != null ? item.getMaxLevel() : 100,
                                0, 0, 0));
                    }
                });

        // Unnamed stocks (no variant) → aggregate, sell FIFO
        List<Stock> unnamed = activeStocks.stream()
                .filter(s -> s.getVariant() == null)
                .collect(Collectors.toList());
        if (!unnamed.isEmpty()) {
            int totalQty = unnamed.stream().mapToInt(s -> s.getQty() != null ? s.getQty() : 0).sum();
            Stock sellFrom = unnamed.stream()
                    .filter(s -> s.getQty() != null && s.getQty() > 0)
                    .min(Comparator.comparingInt(Stock::getStockId))
                    .orElse(unnamed.get(0));
            results.add(toStockDtoAggregated(sellFrom, item, totalQty));
        }

        if (results.isEmpty()) {
            results.add(toDto(item)); // no stock, no variants — show as OUT
        }
        return results;
    }

    private ItemDto toVariantDto(Stock sellFrom, Item item, ItemVariant variant, int totalQty) {
        return new ItemDto(
                item.getItemId(), item.getItemName(), variant.getSku(),
                item.getCategory() != null ? item.getCategory().getCategoryName() : "",
                item.getBrands()   != null ? item.getBrands().getBrandName()      : "",
                item.getUnit()     != null ? item.getUnit()     : "pcs",
                item.getStat()     != null ? item.getStat()     : "Active",
                totalQty,
                item.getMinLevel() != null ? item.getMinLevel() : 5,
                item.getMaxLevel() != null ? item.getMaxLevel() : 100,
                sellFrom.getCost()  != null ? sellFrom.getCost()  : 0,
                sellFrom.getPrice() != null ? sellFrom.getPrice() : 0,
                sellFrom.getStockId());
    }

    private ItemDto toStockDtoAggregated(Stock sellFrom, Item item, int totalQty) {
        return new ItemDto(
                item.getItemId(), item.getItemName(),
                item.getSku() != null ? item.getSku() : "",
                item.getCategory() != null ? item.getCategory().getCategoryName() : "",
                item.getBrands()   != null ? item.getBrands().getBrandName()      : "",
                item.getUnit()     != null ? item.getUnit()     : "pcs",
                item.getStat()     != null ? item.getStat()     : "Active",
                totalQty,
                item.getMinLevel() != null ? item.getMinLevel() : 5,
                item.getMaxLevel() != null ? item.getMaxLevel() : 100,
                sellFrom.getCost()  != null ? sellFrom.getCost()  : 0,
                sellFrom.getPrice() != null ? sellFrom.getPrice() : 0,
                sellFrom.getStockId());
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

    /** Creates a new item. Returns the new item ID. */
    public int createItem(String name, String sku, Integer catId, Integer brandId,
                          String unit, double cost, double price,
                          int openingQty, int minLevel, int maxLevel, Long employeeId) {
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            Category cat   = catId   != null ? session.get(Category.class, catId)   : null;
            Brands   brand = brandId != null ? session.get(Brands.class, brandId)   : null;
            Item item = new Item(cat, brand);
            item.setItemName(name);
            item.setSku(sku);
            item.setUnit(unit);
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
                    stock.setQty(openingQty);
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
                           String unit, double price, int minLevel, int maxLevel,
                           String stat, Long employeeId) {
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            Item item = session.get(Item.class, itemId);
            if (item == null) { tx.rollback(); return; }
            String old = "{\"name\":\"" + item.getItemName() + "\",\"price\":" + price + "}";
            item.setItemName(name);
            item.setUnit(unit);
            item.setMinLevel(minLevel);
            item.setMaxLevel(maxLevel);
            item.setStat(stat);
            if (catId   != null) item.setCategory(session.get(Category.class, catId));
            if (brandId != null) item.setBrands(session.get(Brands.class, brandId));
            // Update selling price on all active unnamed stock records
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

    public ItemDto findById(int itemId) {
        try (var session = sf.openSession()) {
            Item item = session.get(Item.class, itemId);
            return item != null ? toDto(item) : null;
        }
    }

    // ── Converters ─────────────────────────────────────────────────────────────

    private ItemDto toDto(Item item) {
        int qty = item.getStocks().stream()
                .filter(s -> "Active".equals(s.getStat()))
                .mapToInt(s -> s.getQty() == null ? 0 : s.getQty())
                .sum();
        double cost  = item.getStocks().stream().filter(s -> s.getCost()  != null).mapToDouble(Stock::getCost).max().orElse(0);
        double price = item.getStocks().stream().filter(s -> s.getPrice() != null).mapToDouble(Stock::getPrice).max().orElse(0);
        String cat   = item.getCategory() != null ? item.getCategory().getCategoryName() : "";
        String brand = item.getBrands()   != null ? item.getBrands().getBrandName()      : "";
        return new ItemDto(item.getItemId(), item.getItemName(), item.getSku() != null ? item.getSku() : "",
                cat, brand, item.getUnit() != null ? item.getUnit() : "pcs",
                item.getStat() != null ? item.getStat() : "Active",
                qty, item.getMinLevel() != null ? item.getMinLevel() : 5,
                item.getMaxLevel() != null ? item.getMaxLevel() : 100, cost, price, 0);
    }

    private StockLevelDto toStockLevelDto(Item item) {
        ItemDto d = toDto(item);
        return new StockLevelDto(d.itemId(), d.itemName(), d.sku(), d.categoryName(),
                d.brandName(), d.currentQty(), d.minLevel(), d.maxLevel(),
                d.costPrice(), d.sellingPrice(), d.stat());
    }
}
