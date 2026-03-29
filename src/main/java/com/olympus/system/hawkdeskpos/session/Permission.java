package com.olympus.system.hawkdeskpos.session;

/**
 * All permission flags that can be checked across screens.
 * Each flag maps to a column in the user_permissions table.
 */
public enum Permission {
    MAKE_SALE,
    VIEW_SALES,
    PROCESS_RETURNS,
    FIND_INVOICE,
    VIEW_STOCK,
    ADD_ITEM,
    EDIT_ITEM,
    RECEIVE_STOCK,
    ADJUST_STOCK,
    MANAGE_CATEGORIES,
    DELETE_CATEGORIES,
    VIEW_REPORTS,
    EXPORT_REPORTS,
    VIEW_GRN,
    ACCESS_SETTINGS,
    MANAGE_USERS,
    ACCESS_BACKUP,
    VIEW_BATCH_COST,
    OVERRIDE_FIFO
}
