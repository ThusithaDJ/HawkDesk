package com.olympus.system.hawkdeskpos.service;

import com.olympus.system.hawkdeskpos.db.dao.*;
import com.olympus.system.hawkdeskpos.dto.EmployeeDto;
import com.olympus.system.hawkdeskpos.dto.PermissionsDto;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.mindrot.jbcrypt.BCrypt;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class UserService {

    private final SessionFactory sf;
    private final AuditService   audit;

    public UserService(SessionFactory sf, AuditService audit) {
        this.sf    = sf;
        this.audit = audit;
    }

    public List<EmployeeDto> listAll() {
        try (var session = sf.openSession()) {
            return session.createQuery("FROM Employee ORDER BY name", Employee.class).list()
                    .stream().map(this::toDto).collect(Collectors.toList());
        } catch (Exception e) { return Collections.emptyList(); }
    }

    public EmployeeDto findById(long id) {
        try (var session = sf.openSession()) {
            Employee e = session.get(Employee.class, id);
            return e != null ? toDto(e) : null;
        }
    }

    public PermissionsDto loadPermissions(long employeeId) {
        try (var session = sf.openSession()) {
            UserPermissions p = session.createQuery(
                    "FROM UserPermissions up WHERE up.employee.id = :id", UserPermissions.class)
                    .setParameter("id", employeeId).uniqueResult();
            if (p == null) return PermissionsDto.fullAccess(employeeId);
            return new PermissionsDto(employeeId,
                    p.isCanMakeSale(), p.isCanViewSales(), p.isCanProcessReturns(),
                    p.isCanFindInvoice(), p.isCanViewStock(), p.isCanAddItem(),
                    p.isCanEditItem(), p.isCanReceiveStock(), p.isCanAdjustStock(),
                    p.isCanManageCategories(), p.isCanDeleteCategories(),
                    p.isCanViewReports(), p.isCanExportReports(), p.isCanViewGrn(),
                    p.isCanAccessSettings(), p.isCanManageUsers(), p.isCanAccessBackup(),
                    p.isCanViewBatchCost(), p.isCanOverrideFifo());
        }
    }

    public long createEmployee(String name, String role, String rawPin, Long actorId) {
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            Employee emp = new Employee();
            emp.setName(name);
            emp.setRole(Employee.Role.valueOf(role));
            emp.setPinHash(BCrypt.hashpw(rawPin, BCrypt.gensalt()));
            emp.setActive(true);
            session.persist(emp);

            UserPermissions perms = new UserPermissions();
            perms.setEmployee(emp);
            session.persist(perms);
            tx.commit();
            audit.log(AuditLog.Action.INSERT, "employees", emp.getId(),
                    null, "{\"name\":\"" + name + "\"}", actorId, null);
            return emp.getId();
        }
    }

    public void updateEmployee(long id, String name, String role, boolean active, Long actorId) {
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            Employee emp = session.get(Employee.class, id);
            if (emp == null) { tx.rollback(); return; }
            emp.setName(name);
            emp.setRole(Employee.Role.valueOf(role));
            emp.setActive(active);
            session.merge(emp);
            tx.commit();
            audit.log(AuditLog.Action.UPDATE, "employees", id, null,
                    "{\"name\":\"" + name + "\",\"active\":" + active + "}", actorId, null);
        }
    }

    public void resetPin(long id, String newRawPin, Long actorId) {
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            Employee emp = session.get(Employee.class, id);
            if (emp == null) { tx.rollback(); return; }
            emp.setPinHash(BCrypt.hashpw(newRawPin, BCrypt.gensalt()));
            session.merge(emp);
            tx.commit();
            audit.log(AuditLog.Action.UPDATE, "employees", id, null, "{\"pinReset\":true}", actorId, null);
        }
    }

    public void savePermissions(PermissionsDto dto, Long actorId) {
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            UserPermissions p = session.createQuery(
                    "FROM UserPermissions up WHERE up.employee.id = :id", UserPermissions.class)
                    .setParameter("id", dto.employeeId()).uniqueResult();
            if (p == null) {
                p = new UserPermissions();
                p.setEmployee(session.get(Employee.class, dto.employeeId()));
            }
            p.setCanMakeSale(dto.canMakeSale());
            p.setCanViewSales(dto.canViewSales());
            p.setCanProcessReturns(dto.canProcessReturns());
            p.setCanFindInvoice(dto.canFindInvoice());
            p.setCanViewStock(dto.canViewStock());
            p.setCanAddItem(dto.canAddItem());
            p.setCanEditItem(dto.canEditItem());
            p.setCanReceiveStock(dto.canReceiveStock());
            p.setCanAdjustStock(dto.canAdjustStock());
            p.setCanManageCategories(dto.canManageCategories());
            p.setCanDeleteCategories(dto.canDeleteCategories());
            p.setCanViewReports(dto.canViewReports());
            p.setCanExportReports(dto.canExportReports());
            p.setCanViewGrn(dto.canViewGrn());
            p.setCanAccessSettings(dto.canAccessSettings());
            p.setCanManageUsers(dto.canManageUsers());
            p.setCanAccessBackup(dto.canAccessBackup());
            p.setCanViewBatchCost(dto.canViewBatchCost());
            p.setCanOverrideFifo(dto.canOverrideFifo());
            session.merge(p);
            tx.commit();
            audit.log(AuditLog.Action.UPDATE, "user_permissions", dto.employeeId(),
                    null, null, actorId, "Permissions updated");
        }
    }

    private EmployeeDto toDto(Employee e) {
        return new EmployeeDto(e.getId(), e.getName(), e.getRole().name(), e.isActive(), e.getLastLogin());
    }
}
