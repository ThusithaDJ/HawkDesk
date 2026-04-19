package com.olympus.system.hawkdeskpos.service;

import com.olympus.system.hawkdeskpos.db.dao.Customer;
import com.olympus.system.hawkdeskpos.dto.CustomerDto;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class CustomerService {

    private final SessionFactory sf;

    public CustomerService(SessionFactory sf) {
        this.sf = sf;
    }

    public List<CustomerDto> listAll() {
        try (var session = sf.openSession()) {
            return session.createQuery("FROM Customer c ORDER BY c.name", Customer.class)
                    .list().stream().map(this::toDto).collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("CustomerService.listAll: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<CustomerDto> search(String query) {
        try (var session = sf.openSession()) {
            String like = "%" + query.toLowerCase() + "%";
            return session.createQuery(
                    "FROM Customer c WHERE LOWER(c.name) LIKE :q OR c.phone LIKE :q ORDER BY c.name",
                    Customer.class)
                    .setParameter("q", like)
                    .list().stream().map(this::toDto).collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("CustomerService.search: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public CustomerDto findById(int id) {
        try (var session = sf.openSession()) {
            Customer c = session.get(Customer.class, id);
            return c != null ? toDto(c) : null;
        } catch (Exception e) {
            return null;
        }
    }

    public int create(String name, String phone, String address, double maxDebtAmount) {
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            Customer c = new Customer();
            c.setName(name.trim());
            c.setPhone(phone != null ? phone.trim() : null);
            c.setAddress(address != null ? address.trim() : null);
            c.setMaxDebtAmount(maxDebtAmount);
            session.persist(c);
            tx.commit();
            return c.getCustomerId();
        } catch (Exception e) {
            System.err.println("CustomerService.create: " + e.getMessage());
            return -1;
        }
    }

    public void update(int id, String name, String phone, String address, double maxDebtAmount) {
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            Customer c = session.get(Customer.class, id);
            if (c != null) {
                c.setName(name.trim());
                c.setPhone(phone != null ? phone.trim() : null);
                c.setAddress(address != null ? address.trim() : null);
                c.setMaxDebtAmount(maxDebtAmount);
                session.merge(c);
            }
            tx.commit();
        } catch (Exception e) {
            System.err.println("CustomerService.update: " + e.getMessage());
        }
    }

    /** Total outstanding (unpaid) credit balance for a customer. */
    public double totalOutstandingDebt(int customerId) {
        try (var session = sf.openSession()) {
            Object obj = session.createNativeQuery(
                    "SELECT COALESCE(SUM(net_total - paid), 0) FROM invoiceinfo " +
                    "WHERE customer_id = :cid AND payment_method = 'CREDIT' AND stat IN ('Credit', 'Partial')",
                    Object.class)
                    .setParameter("cid", customerId)
                    .uniqueResult();
            return obj instanceof Number n ? n.doubleValue() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /** Total purchase amount for a customer (all non-void invoices). */
    public double totalPurchases(int customerId) {
        try (var session = sf.openSession()) {
            Object obj = session.createNativeQuery(
                    "SELECT COALESCE(SUM(net_total), 0) FROM invoiceinfo " +
                    "WHERE customer_id = :cid AND stat != 'Void'",
                    Object.class)
                    .setParameter("cid", customerId)
                    .uniqueResult();
            return obj instanceof Number n ? n.doubleValue() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private CustomerDto toDto(Customer c) {
        return new CustomerDto(
                c.getCustomerId(),
                c.getName(),
                c.getPhone() != null ? c.getPhone() : "",
                c.getAddress() != null ? c.getAddress() : "",
                c.getMaxDebtAmount() != null ? c.getMaxDebtAmount() : 0.0
        );
    }
}
