package com.olympus.system.hawkdeskpos.dto;

public record BatchDeductionResult(
        int    batchId,
        String batchNumber,
        int    qtyDeducted,
        double costPrice
) {}
