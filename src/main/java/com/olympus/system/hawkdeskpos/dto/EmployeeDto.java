package com.olympus.system.hawkdeskpos.dto;

import java.time.LocalDateTime;

/** Immutable view of an employee — safe to pass to the UI layer. */
public record EmployeeDto(
        Long id,
        String name,
        String role,         // "OWNER" | "MANAGER" | "CASHIER" | "STOCK_KEEPER"
        boolean active,
        LocalDateTime lastLogin
) {}
