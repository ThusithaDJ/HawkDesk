package com.olympus.system.hawkdeskpos.dto;

/** Data for a stock adjustment / write-off operation. */
public record AdjustmentDto(
        int itemId,
        String adjustmentType,   // "ADD" | "REMOVE" | "SET" | "WRITEOFF"
        int qtyBefore,
        int qtyChange,
        int qtyAfter,
        String reason,
        String notes,
        double lossValue         // populated for WRITEOFF only
) {}
