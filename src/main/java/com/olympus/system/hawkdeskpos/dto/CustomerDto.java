package com.olympus.system.hawkdeskpos.dto;

public record CustomerDto(
        int    customerId,
        String name,
        String phone,
        String address,
        double maxDebtAmount
) {}
