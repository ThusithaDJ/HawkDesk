package com.olympus.system.hawkdeskpos.dto;

import java.util.Date;
import java.util.List;

/** Full invoice header + line items. */
public record InvoiceDto(
        String invoiceNo,
        Date date,
        double total,
        double paid,
        double discount,
        String paymentMethod,
        String stat,
        String cashierName,
        List<SaleLineDto> lines
) {}
