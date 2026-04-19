package com.olympus.system.hawkdeskpos.dto;

import java.util.Date;
import java.util.List;

/** Full invoice header + line items. */
public record InvoiceDto(
        String invoiceNo,
        Date date,
        double netTotal,
        double subTotal,
        double grossTotal,
        double tax,
        double paid,
        double discount,
        String paymentMethod,
        String stat,
        String cashierName,
        List<SaleLineDto> lines,
        String customerName,
        Date   creditResolveDate,
        Date   resolvedDate        // when a CREDIT invoice was last paid (from invoice_history); null otherwise
) {}
