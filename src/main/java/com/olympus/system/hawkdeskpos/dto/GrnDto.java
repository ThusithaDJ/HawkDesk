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
            int stockBefore
    ) {}
}
