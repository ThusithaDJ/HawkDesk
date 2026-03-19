package com.olympus.system.hawkdeskpos.service;

import com.olympus.system.hawkdeskpos.db.dao.AuditLog;
import com.olympus.system.hawkdeskpos.db.dao.Employee;
import com.olympus.system.hawkdeskpos.db.dao.UserPermissions;
import com.olympus.system.hawkdeskpos.dto.EmployeeDto;
import com.olympus.system.hawkdeskpos.dto.PermissionsDto;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.mindrot.jbcrypt.BCrypt;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles login, PIN validation (BCrypt), account lockout.
 * No Swing imports — safe to call from any context.
 */
public class AuthService {

    private static final int MAX_FAILURES   = 3;
    private static final int LOCKOUT_SECONDS = 30;

    private final SessionFactory sf;
    private final AuditService   audit;

    public AuthService(SessionFactory sf, AuditService audit) {
        this.sf    = sf;
        this.audit = audit;
    }

    /** Result returned from login attempt. */
    public enum LoginResult { SUCCESS, WRONG_PIN, LOCKED, INACTIVE, NOT_FOUND }

    public record LoginOutcome(LoginResult result, EmployeeDto employee, PermissionsDto permissions) {}

    /**
     * Validates the PIN for the given employee ID.
     * Increments failure count, locks after 3 failures.
     */
    public LoginOutcome login(long employeeId, String rawPin) {
        try (var session = sf.openSession()) {
            Employee emp = session.get(Employee.class, employeeId);
            if (emp == null) return new LoginOutcome(LoginResult.NOT_FOUND, null, null);
            if (!emp.isActive()) return new LoginOutcome(LoginResult.INACTIVE, null, null);

            // Check lockout
            if (emp.getLockedUntil() != null && LocalDateTime.now().isBefore(emp.getLockedUntil())) {
                return new LoginOutcome(LoginResult.LOCKED, null, null);
            }

            boolean pinOk = BCrypt.checkpw(rawPin, emp.getPinHash());
            Transaction tx = session.beginTransaction();
            if (pinOk) {
                emp.setFailedAttempts(0);
                emp.setLockedUntil(null);
                emp.setLastLogin(LocalDateTime.now());
                session.merge(emp);
                tx.commit();

                EmployeeDto dto = toDto(emp);
                PermissionsDto perms = loadPermissions(employeeId);
                audit.log(AuditLog.Action.LOGIN, "employees", employeeId, null, null, employeeId, null);
                return new LoginOutcome(LoginResult.SUCCESS, dto, perms);
            } else {
                int failures = emp.getFailedAttempts() + 1;
                emp.setFailedAttempts(failures);
                if (failures >= MAX_FAILURES) {
                    emp.setLockedUntil(LocalDateTime.now().plusSeconds(LOCKOUT_SECONDS));
                    emp.setFailedAttempts(0);
                }
                session.merge(emp);
                tx.commit();
                audit.log(AuditLog.Action.PERMISSION_DENIED, "employees", employeeId,
                        null, null, employeeId, "Wrong PIN attempt " + failures);
                return new LoginOutcome(LoginResult.WRONG_PIN, null, null);
            }
        }
    }

    /** Returns remaining lockout seconds, or 0 if not locked. */
    public long lockoutSecondsRemaining(long employeeId) {
        try (var session = sf.openSession()) {
            Employee emp = session.get(Employee.class, employeeId);
            if (emp == null || emp.getLockedUntil() == null) return 0;
            long remaining = java.time.Duration.between(LocalDateTime.now(), emp.getLockedUntil()).getSeconds();
            return Math.max(0, remaining);
        }
    }

    /** Lists all active employees for the login screen staff-card list. */
    public List<EmployeeDto> listActiveEmployees() {
        try (var session = sf.openSession()) {
            return session.createQuery(
                    "FROM Employee e WHERE e.active = true ORDER BY e.name", Employee.class)
                    .list()
                    .stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("AuthService.listActiveEmployees: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private EmployeeDto toDto(Employee emp) {
        return new EmployeeDto(emp.getId(), emp.getName(),
                emp.getRole().name(), emp.isActive(), emp.getLastLogin());
    }

    private PermissionsDto loadPermissions(long employeeId) {
        try (var session = sf.openSession()) {
            UserPermissions p = session.createQuery(
                    "FROM UserPermissions up WHERE up.employee.id = :id", UserPermissions.class)
                    .setParameter("id", employeeId)
                    .uniqueResult();
            if (p == null) return PermissionsDto.fullAccess(employeeId); // Owner fallback
            return new PermissionsDto(employeeId,
                    p.isCanMakeSale(), p.isCanViewSales(), p.isCanProcessReturns(),
                    p.isCanFindInvoice(), p.isCanViewStock(), p.isCanAddItem(),
                    p.isCanEditItem(), p.isCanReceiveStock(), p.isCanAdjustStock(),
                    p.isCanManageCategories(), p.isCanDeleteCategories(),
                    p.isCanViewReports(), p.isCanExportReports(), p.isCanViewGrn(),
                    p.isCanAccessSettings(), p.isCanManageUsers(), p.isCanAccessBackup());
        } catch (Exception e) {
            return PermissionsDto.fullAccess(employeeId);
        }
    }
}
