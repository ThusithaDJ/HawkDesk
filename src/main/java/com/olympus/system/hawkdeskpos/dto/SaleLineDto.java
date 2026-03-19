package com.olympus.system.hawkdeskpos.dto;

/** One line item in the cart / invoice. */
public record SaleLineDto(
        int itemId,
        int stockId,
        String itemName,
        String sku,
        int qty,
        double unitPrice,
        double lineTotal
) {}
