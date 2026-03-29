package com.olympus.system.hawkdeskpos.dto;

/** Lightweight item view for search dropdowns and table display. */
public record ItemDto(
        int itemId,
        String itemName,
        String sku,
        String categoryName,
        String brandName,
        String unit,
        String stat,          // "Active" | "Inactive"
        int currentQty,
        int minLevel,
        int maxLevel,
        double costPrice,
        double sellingPrice,
        int stockId,          // 0 = aggregated across all unnamed stocks; >0 = specific stock record
        String batchLabel     // batch identifier from the sell-from stock; "" if none
) {
    /** Derived stock status for display. */
    public String stockStatus() {
        if (currentQty <= 0) return "OUT";
        if (currentQty <= minLevel) return "LOW";
        return "OK";
    }
}
