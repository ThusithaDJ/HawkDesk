package com.olympus.system.hawkdeskpos.dto;

import java.util.Date;

public record BatchDto(
        int    batchId,
        int    itemId,
        String itemName,
        String batchNumber,
        String batchLabel,
        int    qtyReceived,
        int    qtyRemaining,
        double costPrice,
        Date   expiryDate,
        String status
) {
    /** Display label: custom label if set, otherwise the batch number. */
    public String displayLabel() {
        return (batchLabel != null && !batchLabel.isBlank()) ? batchLabel : batchNumber;
    }

    public boolean isActive() { return "ACTIVE".equals(status); }
}
