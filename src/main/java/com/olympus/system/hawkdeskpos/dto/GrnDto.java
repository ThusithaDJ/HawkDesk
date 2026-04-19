package com.olympus.system.hawkdeskpos.dto;

import java.util.Date;
import java.util.List;

/** Goods Received Note (GRN) header + lines. */
public record GrnDto(
        String grnNumber,
        Date date,
        String supplier,
        String reference,
        double totalCost,
        List<GrnLineDto> lines
) {
    public record GrnLineDto(
            int itemId,
            String itemName,
            int qtyReceived,
            double costPrice,
            double sellingPrice,
            int stockBefore,
            String variantSku,        // empty/null = no named variant; non-empty = find or create item_variant
            String batchName,         // batch number / label; auto-generated if blank
            java.util.Date expiryDate, // optional expiry date for the batch
            Integer existingStockId   // null = create new batch; non-null = add qty to this existing stock record
    ) {}
}
