package com.olympus.system.hawkdeskpos.service;

import com.olympus.system.hawkdeskpos.db.dao.*;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import java.util.Collections;
import java.util.List;

public class CategoryService {

    private final SessionFactory sf;
    private final AuditService   audit;

    public CategoryService(SessionFactory sf, AuditService audit) {
        this.sf    = sf;
        this.audit = audit;
    }

    public List<Category> listCategories() {
        try (var session = sf.openSession()) {
            return session.createQuery("FROM Category ORDER BY categoryName", Category.class).list();
        } catch (Exception e) { return Collections.emptyList(); }
    }

    public List<Brands> listBrands() {
        try (var session = sf.openSession()) {
            return session.createQuery("FROM Brands ORDER BY brandName", Brands.class).list();
        } catch (Exception e) { return Collections.emptyList(); }
    }

    public int itemCountForCategory(int catId) {
        try (var session = sf.openSession()) {
            Long c = session.createQuery("SELECT COUNT(i) FROM Item i WHERE i.category.catId = :id", Long.class)
                    .setParameter("id", catId).uniqueResult();
            return c == null ? 0 : c.intValue();
        }
    }

    public int itemCountForBrand(int brandId) {
        try (var session = sf.openSession()) {
            Long c = session.createQuery("SELECT COUNT(i) FROM Item i WHERE i.brands.brandId = :id", Long.class)
                    .setParameter("id", brandId).uniqueResult();
            return c == null ? 0 : c.intValue();
        }
    }

    public void addCategory(String name, String colour, Long employeeId) {
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            Category cat = new Category(name, "Active");
            cat.setColour(colour);
            session.persist(cat);
            tx.commit();
            audit.log(AuditLog.Action.INSERT, "category", (long) cat.getCatId(),
                    null, "{\"name\":\"" + name + "\"}", employeeId, null);
        }
    }

    public void renameCategory(int catId, String newName, Long employeeId) {
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            Category cat = session.get(Category.class, catId);
            if (cat == null) { tx.rollback(); return; }
            String old = cat.getCategoryName();
            cat.setCategoryName(newName);
            session.merge(cat);
            tx.commit();
            audit.log(AuditLog.Action.UPDATE, "category", (long) catId,
                    "{\"name\":\"" + old + "\"}", "{\"name\":\"" + newName + "\"}", employeeId, null);
        }
    }

    public boolean deleteCategory(int catId, Long employeeId) {
        if (itemCountForCategory(catId) > 0) return false;
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            Category cat = session.get(Category.class, catId);
            if (cat != null) session.remove(cat);
            tx.commit();
            audit.log(AuditLog.Action.DELETE, "category", (long) catId, null, null, employeeId, null);
            return true;
        }
    }

    public void addBrand(String name, Long employeeId) {
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            Brands b = new Brands(name);
            session.persist(b);
            tx.commit();
            audit.log(AuditLog.Action.INSERT, "brands", (long) b.getBrandId(),
                    null, "{\"name\":\"" + name + "\"}", employeeId, null);
        }
    }

    public void renameBrand(int brandId, String newName, Long employeeId) {
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            Brands b = session.get(Brands.class, brandId);
            if (b == null) { tx.rollback(); return; }
            String old = b.getBrandName();
            b.setBrandName(newName);
            session.merge(b);
            tx.commit();
            audit.log(AuditLog.Action.UPDATE, "brands", (long) brandId,
                    "{\"name\":\"" + old + "\"}", "{\"name\":\"" + newName + "\"}", employeeId, null);
        }
    }

    public boolean deleteBrand(int brandId, Long employeeId) {
        if (itemCountForBrand(brandId) > 0) return false;
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            Brands b = session.get(Brands.class, brandId);
            if (b != null) session.remove(b);
            tx.commit();
            audit.log(AuditLog.Action.DELETE, "brands", (long) brandId, null, null, employeeId, null);
            return true;
        }
    }
}
