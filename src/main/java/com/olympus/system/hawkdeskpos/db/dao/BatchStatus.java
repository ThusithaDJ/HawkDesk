package com.olympus.system.hawkdeskpos.db.dao;

public enum BatchStatus {
    ACTIVE,   // stock remaining > 0 and not expired
    EMPTY,    // qty_remaining == 0
    EXPIRED   // expiry_date < today
}
