package com.olympus.system.hawkdeskpos.dto;

/** Aggregated stock level summary for an item. */
public record StockLevelDto(
        int itemId,
        String itemName,
        String sku,
        String categoryName,
        String brandName,
        int totalQty,
        int minLevel,
        int maxLevel,
        double costPrice,
        double sellingPrice,
        String stat
) {
    public String stockStatus() {
        if (totalQty <= 0) return "OUT";
        if (totalQty <= minLevel) return "LOW";
        return "OK";
    }

    public double stockValue() {
        return totalQty * costPrice;
    }
}
