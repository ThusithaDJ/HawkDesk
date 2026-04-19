package com.olympus.system.hawkdeskpos.dto;

/** One line item in the cart / invoice. */
public record SaleLineDto(
        int itemId,
        int stockId,
        String itemName,
        String sku,
        double qty,
        double unitPrice,
        double lineTotal,
        String batch,      // batch / GRN number from the stock record; empty string when unknown
        String unit,       // unit of measure (e.g. "pcs", "kg"); empty string when unknown
        double costPrice   // unit cost at time of sale; 0 when not yet recorded (historical data)
) {}
