package com.olympus.system.hawkdeskpos.dto;

/** Permission flags for a single employee. */
public record PermissionsDto(
        long employeeId,
        boolean canMakeSale,
        boolean canViewSales,
        boolean canProcessReturns,
        boolean canFindInvoice,
        boolean canViewStock,
        boolean canAddItem,
        boolean canEditItem,
        boolean canReceiveStock,
        boolean canAdjustStock,
        boolean canManageCategories,
        boolean canDeleteCategories,
        boolean canViewReports,
        boolean canExportReports,
        boolean canViewGrn,
        boolean canAccessSettings,
        boolean canManageUsers,
        boolean canAccessBackup,
        boolean canViewBatchCost,
        boolean canOverrideFifo
) {
    /** Full-access permissions (used for Owner role). */
    public static PermissionsDto fullAccess(long employeeId) {
        return new PermissionsDto(employeeId,
                true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true, true);
    }

    /** Returns all 19 permission flags as a boolean array (in declaration order). */
    public boolean[] toArray() {
        return new boolean[]{
                canMakeSale, canViewSales, canProcessReturns, canFindInvoice,
                canViewStock, canAddItem, canEditItem, canReceiveStock, canAdjustStock,
                canManageCategories, canDeleteCategories, canViewReports, canExportReports,
                canViewGrn, canAccessSettings, canManageUsers, canAccessBackup,
                canViewBatchCost, canOverrideFifo
        };
    }

    /** Creates a PermissionsDto from a boolean array (in declaration order, length 19). */
    public static PermissionsDto fromArray(long employeeId, boolean[] arr) {
        boolean[] a = arr != null && arr.length >= 19 ? arr : new boolean[19];
        return new PermissionsDto(employeeId,
                a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8],
                a[9], a[10], a[11], a[12], a[13], a[14], a[15], a[16],
                a[17], a[18]);
    }
}
