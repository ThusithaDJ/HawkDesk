package com.olympus.system.hawkdeskpos.dto;

/** One active stock (batch) record — used for the batch-level stock view. */
public record StockBatchDto(
        int stockId,
        int itemId,
        String itemName,
        String itemSku,      // item-level SKU
        String displaySku,   // variant SKU if set, otherwise item SKU
        String batch,        // batch number / label
        double qty,
        int minLevel,
        double costPrice,
        double sellingPrice,
        String stat,
        java.util.Date expiryDate   // null if no expiry set
) {
    public String stockStatus() {
        if (qty <= 0.0) return "OUT";
        if (qty <= minLevel) return "LOW";
        return "OK";
    }
}
