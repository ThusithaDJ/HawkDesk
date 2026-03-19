package com.olympus.system.hawkdeskpos.session;

import com.olympus.system.hawkdeskpos.dto.EmployeeDto;
import com.olympus.system.hawkdeskpos.dto.PermissionsDto;
import java.util.EnumSet;
import java.util.Set;

/**
 * Thread-local session context. Holds the currently logged-in employee and
 * their resolved permission set. Cleared on logout.
 *
 * Owner role always has all permissions — enforced here.
 */
public class SessionContext {

    private static final ThreadLocal<SessionContext> INSTANCE = new ThreadLocal<>();

    private final EmployeeDto employee;
    private final Set<Permission> permissions;

    private SessionContext(EmployeeDto employee, PermissionsDto perms) {
        this.employee = employee;
        this.permissions = resolvePermissions(employee, perms);
    }

    /** Call after successful PIN validation. */
    public static void login(EmployeeDto employee, PermissionsDto perms) {
        INSTANCE.set(new SessionContext(employee, perms));
    }

    /** Returns the current session context, or null if not logged in. */
    public static SessionContext current() {
        return INSTANCE.get();
    }

    /** Clears the session (call on logout). */
    public static void logout() {
        INSTANCE.remove();
    }

    /** Returns true if the current session is active. */
    public static boolean isLoggedIn() {
        return INSTANCE.get() != null;
    }

    /** Checks a single permission for the current session. */
    public static boolean hasPermission(Permission p) {
        SessionContext ctx = INSTANCE.get();
        return ctx != null && ctx.permissions.contains(p);
    }

    public EmployeeDto getEmployee() {
        return employee;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    /** Owners get every permission regardless of the database record. */
    private static Set<Permission> resolvePermissions(EmployeeDto emp, PermissionsDto perms) {
        if ("OWNER".equals(emp.role())) {
            return EnumSet.allOf(Permission.class);
        }
        Set<Permission> set = EnumSet.noneOf(Permission.class);
        if (perms == null) return set;
        if (perms.canMakeSale())         set.add(Permission.MAKE_SALE);
        if (perms.canViewSales())        set.add(Permission.VIEW_SALES);
        if (perms.canProcessReturns())   set.add(Permission.PROCESS_RETURNS);
        if (perms.canFindInvoice())      set.add(Permission.FIND_INVOICE);
        if (perms.canViewStock())        set.add(Permission.VIEW_STOCK);
        if (perms.canAddItem())          set.add(Permission.ADD_ITEM);
        if (perms.canEditItem())         set.add(Permission.EDIT_ITEM);
        if (perms.canReceiveStock())     set.add(Permission.RECEIVE_STOCK);
        if (perms.canAdjustStock())      set.add(Permission.ADJUST_STOCK);
        if (perms.canManageCategories()) set.add(Permission.MANAGE_CATEGORIES);
        if (perms.canDeleteCategories()) set.add(Permission.DELETE_CATEGORIES);
        if (perms.canViewReports())      set.add(Permission.VIEW_REPORTS);
        if (perms.canExportReports())    set.add(Permission.EXPORT_REPORTS);
        if (perms.canViewGrn())          set.add(Permission.VIEW_GRN);
        if (perms.canAccessSettings())   set.add(Permission.ACCESS_SETTINGS);
        if (perms.canManageUsers())      set.add(Permission.MANAGE_USERS);
        if (perms.canAccessBackup())     set.add(Permission.ACCESS_BACKUP);
        return set;
    }
}
