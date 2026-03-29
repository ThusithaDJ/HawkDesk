package com.olympus.system.hawkdeskpos.dto;

import java.util.List;

public record ItemWithBatchesDto(
        int            itemId,
        String         itemName,
        String         sku,
        int            totalQty,
        List<BatchDto> batches
) {}
