package com.olympus.system.hawkdeskpos.service;

import com.olympus.system.hawkdeskpos.db.dao.AuditLog;
import com.olympus.system.hawkdeskpos.db.dao.Employee;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

/**
 * Writes audit_log records after every data mutation.
 * Called by all other services — never called from UI panels.
 */
public class AuditService {

    private final SessionFactory sf;

    public AuditService(SessionFactory sf) {
        this.sf = sf;
    }

    /**
     * Records an audit event.
     *
     * @param action     one of INSERT, UPDATE, DELETE, LOGIN, LOGOUT, PERMISSION_DENIED
     * @param tableName  the affected table
     * @param recordId   the PK of the affected row (may be null)
     * @param oldValue   JSON string of the previous state (may be null)
     * @param newValue   JSON string of the new state (may be null)
     * @param employeeId the employee performing the action (may be null)
     * @param notes      free-text note (may be null)
     */
    public void log(AuditLog.Action action, String tableName, Long recordId,
                    String oldValue, String newValue, Long employeeId, String notes) {
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            AuditLog entry = new AuditLog();
            entry.setAction(action);
            entry.setTableName(tableName);
            entry.setRecordId(recordId);
            entry.setOldValue(oldValue);
            entry.setNewValue(newValue);
            entry.setNotes(notes);
            if (employeeId != null) {
                Employee emp = session.get(Employee.class, employeeId);
                entry.setEmployee(emp);
            }
            session.persist(entry);
            tx.commit();
        } catch (Exception e) {
            // Audit failures must not crash the application
            System.err.println("AuditService: failed to write log entry: " + e.getMessage());
        }
    }
}
