package com.olympus.system.hawkdeskpos.service;

import com.olympus.system.hawkdeskpos.db.dao.*;
import com.olympus.system.hawkdeskpos.dto.ItemDto;
import com.olympus.system.hawkdeskpos.dto.StockLevelDto;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ItemService {

    private final SessionFactory sf;
    private final AuditService   audit;

    public ItemService(SessionFactory sf, AuditService audit) {
        this.sf    = sf;
        this.audit = audit;
    }

    /** Live search — used by SearchDropdown. Returns up to 8 matches. */
    public List<ItemDto> search(String query) {
        if (query == null || query.trim().isEmpty()) return Collections.emptyList();
        try (var session = sf.openSession()) {
            String q = "%" + query.trim().toLowerCase() + "%";
            return session.createQuery(
                    "FROM Item i WHERE (lower(i.itemName) LIKE :q OR lower(i.sku) LIKE :q) " +
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

    /** All items as StockLevelDto (for ViewStockPanel). */
    public List<StockLevelDto> listAllStockLevels() {
        try (var session = sf.openSession()) {
            return session.createQuery("FROM Item i ORDER BY i.itemName", Item.class)
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
            // Update selling price on all active stock records
            for (Stock s : item.getStocks()) {
                if ("Active".equals(s.getStat())) s.setPrice(price);
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
        double cost  = item.getStocks().stream().filter(s -> s.getCost() != null).mapToDouble(Stock::getCost).max().orElse(0);
        double price = item.getStocks().stream().filter(s -> s.getPrice() != null).mapToDouble(Stock::getPrice).max().orElse(0);
        String cat   = item.getCategory() != null ? item.getCategory().getCategoryName() : "";
        String brand = item.getBrands()   != null ? item.getBrands().getBrandName()      : "";
        return new ItemDto(item.getItemId(), item.getItemName(), item.getSku() != null ? item.getSku() : "",
                cat, brand, item.getUnit() != null ? item.getUnit() : "pcs",
                item.getStat() != null ? item.getStat() : "Active",
                qty, item.getMinLevel() != null ? item.getMinLevel() : 5,
                item.getMaxLevel() != null ? item.getMaxLevel() : 100, cost, price);
    }

    private StockLevelDto toStockLevelDto(Item item) {
        ItemDto d = toDto(item);
        return new StockLevelDto(d.itemId(), d.itemName(), d.sku(), d.categoryName(),
                d.brandName(), d.currentQty(), d.minLevel(), d.maxLevel(),
                d.costPrice(), d.sellingPrice(), d.stat());
    }
}
