package com.olympus.system.hawkdeskpos.dto;

import java.util.Date;

/** Lightweight DTO for a goods-return record. */
public record ReturnDto(
        int    returnId,
        String invoiceNo,
        Date   returnDate,
        String itemName,
        int    stockId,
        int    qty,
        String reason,
        String refundMethod,
        String cashierName
) {}
