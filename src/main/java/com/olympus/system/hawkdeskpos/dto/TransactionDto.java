package com.olympus.system.hawkdeskpos.dto;

import java.util.Date;

public record TransactionDto(
        Long   id,
        String type,
        String reference,
        Date   date,
        String description,
        double amount,
        String accountName,
        String category,
        Long   accountId      // null when no account assigned
) {}
