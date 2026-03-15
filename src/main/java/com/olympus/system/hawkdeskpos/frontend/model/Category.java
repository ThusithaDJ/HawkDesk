package com.olympus.system.hawkdeskpos.frontend.model;

import com.olympus.system.hawkdeskpos.db.util.Controller;
import java.util.List;
import java.util.Vector;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

/**
 * @author Thusitha Updated to Hibernate 6 / Jakarta Persistence
 */
public class Category {

    private static final SessionFactory sf = Controller.getSessionFactory();

    public Category() {
    }

    public Vector<String> setComboBox(JComboBox comboBox) {
        Vector<String> v = new Vector<>();
        v.add("-- Please select category --");

        try (Session session = sf.openSession()) {
            List<com.olympus.system.hawkdeskpos.db.dao.Category> list = session.createQuery(
                    "FROM Category c WHERE c.stat = :stat",
                    com.olympus.system.hawkdeskpos.db.dao.Category.class)
                    .setParameter("stat", "active")
                    .getResultList();

            for (com.olympus.system.hawkdeskpos.db.dao.Category category : list) {
                v.add(category.getCatId() + " | " + category.getCategoryName());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return v;
    }

    public void addCategory() {
        String name = JOptionPane.showInputDialog("Please enter the category name");
        if (name == null || name.trim().isEmpty()) {
            return;
        }

        try (Session session = sf.openSession()) {
            Transaction trans = session.beginTransaction();
            com.olympus.system.hawkdeskpos.db.dao.Category cat
                    = new com.olympus.system.hawkdeskpos.db.dao.Category();
            cat.setCategoryName(name.trim());
            cat.setStat("active");
            session.persist(cat);
            trans.commit();
            JOptionPane.showMessageDialog(null, "Category Saved");

        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Failed to save category: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
