package com.olympus.system.hawkdeskpos.service;

import com.olympus.system.hawkdeskpos.db.dao.*;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import java.util.Date;
import java.util.List;
import java.util.Map;

public class ReturnService {

    private final SessionFactory sf;
    private final AuditService   audit;

    public ReturnService(SessionFactory sf, AuditService audit) {
        this.sf    = sf;
        this.audit = audit;
    }

    /**
     * Processes a return for given invoice line items.
     *
     * @param invoiceNo    the original invoice number
     * @param returnLines  map of stockId → qty to return
     * @param reason       reason for return
     * @param refundMethod "Cash" | "Store credit" | "Exchange" | "Void"
     * @param employeeId   acting employee
     */
    public void processReturn(String invoiceNo, Map<Integer, Integer> returnLines,
                              String reason, String refundMethod, Long employeeId) {
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            Invoiceinfo ii = session.get(Invoiceinfo.class, invoiceNo);
            if (ii == null) { tx.rollback(); return; }
            Employee emp = employeeId != null ? session.get(Employee.class, employeeId) : null;

            for (Map.Entry<Integer, Integer> entry : returnLines.entrySet()) {
                int stockId = entry.getKey();
                int qty     = entry.getValue();

                // Restore stock
                Stock stock = session.get(Stock.class, stockId);
                if (stock != null) {
                    stock.setQty(stock.getQty() + qty);
                    session.merge(stock);
                }

                Return ret = new Return();
                ret.setInvoiceinfo(ii);
                ret.setQty(qty);
                ret.setReason(reason);
                ret.setReturnTo(refundMethod);
                ret.setStat(refundMethod);
                ret.setEmployee(emp);
                session.persist(ret);
            }

            if ("Void".equals(refundMethod)) {
                ii.setStat("Void");
                session.merge(ii);
            }
            tx.commit();
            audit.log(AuditLog.Action.INSERT, "return", null,
                    null, "{\"invoiceNo\":\"" + invoiceNo + "\",\"reason\":\"" + reason + "\"}",
                    employeeId, null);
        }
    }
}
