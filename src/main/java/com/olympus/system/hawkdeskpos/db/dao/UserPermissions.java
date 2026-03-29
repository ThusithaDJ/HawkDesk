package com.olympus.system.hawkdeskpos.db.dao;

import jakarta.persistence.*;

@Entity
@Table(name = "user_permissions")
public class UserPermissions {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false, unique = true)
    private Employee employee;

    @Column(name = "can_make_sale")         private boolean canMakeSale         = true;
    @Column(name = "can_view_sales")        private boolean canViewSales        = true;
    @Column(name = "can_process_returns")   private boolean canProcessReturns   = false;
    @Column(name = "can_find_invoice")      private boolean canFindInvoice      = true;
    @Column(name = "can_view_stock")        private boolean canViewStock        = true;
    @Column(name = "can_add_item")          private boolean canAddItem          = false;
    @Column(name = "can_edit_item")         private boolean canEditItem         = false;
    @Column(name = "can_receive_stock")     private boolean canReceiveStock     = false;
    @Column(name = "can_adjust_stock")      private boolean canAdjustStock      = false;
    @Column(name = "can_manage_categories") private boolean canManageCategories = false;
    @Column(name = "can_delete_categories") private boolean canDeleteCategories = false;
    @Column(name = "can_view_reports")      private boolean canViewReports      = true;
    @Column(name = "can_export_reports")    private boolean canExportReports    = false;
    @Column(name = "can_view_grn")          private boolean canViewGrn          = false;
    @Column(name = "can_access_settings")   private boolean canAccessSettings   = false;
    @Column(name = "can_manage_users")      private boolean canManageUsers      = false;
    @Column(name = "can_access_backup")     private boolean canAccessBackup     = false;
    @Column(name = "can_view_batch_cost")   private boolean canViewBatchCost    = false;
    @Column(name = "can_override_fifo")     private boolean canOverrideFifo     = false;

    public UserPermissions() {}

    public Long getId() { return id; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }

    public boolean isCanMakeSale()         { return canMakeSale; }
    public boolean isCanViewSales()        { return canViewSales; }
    public boolean isCanProcessReturns()   { return canProcessReturns; }
    public boolean isCanFindInvoice()      { return canFindInvoice; }
    public boolean isCanViewStock()        { return canViewStock; }
    public boolean isCanAddItem()          { return canAddItem; }
    public boolean isCanEditItem()         { return canEditItem; }
    public boolean isCanReceiveStock()     { return canReceiveStock; }
    public boolean isCanAdjustStock()      { return canAdjustStock; }
    public boolean isCanManageCategories() { return canManageCategories; }
    public boolean isCanDeleteCategories() { return canDeleteCategories; }
    public boolean isCanViewReports()      { return canViewReports; }
    public boolean isCanExportReports()    { return canExportReports; }
    public boolean isCanViewGrn()          { return canViewGrn; }
    public boolean isCanAccessSettings()   { return canAccessSettings; }
    public boolean isCanManageUsers()      { return canManageUsers; }
    public boolean isCanAccessBackup()     { return canAccessBackup; }
    public boolean isCanViewBatchCost()    { return canViewBatchCost; }
    public boolean isCanOverrideFifo()     { return canOverrideFifo; }

    public void setCanMakeSale(boolean v)         { this.canMakeSale = v; }
    public void setCanViewSales(boolean v)        { this.canViewSales = v; }
    public void setCanProcessReturns(boolean v)   { this.canProcessReturns = v; }
    public void setCanFindInvoice(boolean v)      { this.canFindInvoice = v; }
    public void setCanViewStock(boolean v)        { this.canViewStock = v; }
    public void setCanAddItem(boolean v)          { this.canAddItem = v; }
    public void setCanEditItem(boolean v)         { this.canEditItem = v; }
    public void setCanReceiveStock(boolean v)     { this.canReceiveStock = v; }
    public void setCanAdjustStock(boolean v)      { this.canAdjustStock = v; }
    public void setCanManageCategories(boolean v) { this.canManageCategories = v; }
    public void setCanDeleteCategories(boolean v) { this.canDeleteCategories = v; }
    public void setCanViewReports(boolean v)      { this.canViewReports = v; }
    public void setCanExportReports(boolean v)    { this.canExportReports = v; }
    public void setCanViewGrn(boolean v)          { this.canViewGrn = v; }
    public void setCanAccessSettings(boolean v)   { this.canAccessSettings = v; }
    public void setCanManageUsers(boolean v)      { this.canManageUsers = v; }
    public void setCanAccessBackup(boolean v)     { this.canAccessBackup = v; }
    public void setCanViewBatchCost(boolean v)    { this.canViewBatchCost = v; }
    public void setCanOverrideFifo(boolean v)     { this.canOverrideFifo = v; }
}
