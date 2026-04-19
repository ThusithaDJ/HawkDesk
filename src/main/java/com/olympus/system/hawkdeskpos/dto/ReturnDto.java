package com.olympus.system.hawkdeskpos.dto;

import java.util.Date;

/** Lightweight DTO for a goods-return record. */
public record ReturnDto(
        int    returnId,
        String invoiceNo,
        Date   returnDate,
        String itemName,
        int    itemId,
        int    stockId,
        double qty,
        String reason,
        String refundMethod,
        String stat,
        String resolveAction,
        Integer linkedGrnNo,
        String cashierName,
        double originalSaleCost,   // cost_price * qty from invoice line; 0 if not recorded
        String customerRef,        // customer name for exchange returns assigned to a customer
        String resolvedInvoiceNo   // invoice number when exchange credit was applied to a sale
) {}
