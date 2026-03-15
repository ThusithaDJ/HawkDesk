/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.olympus.system.hawkdeskpos.frontend.model;

import com.olympus.system.hawkdeskpos.db.dao.Brands;
import com.olympus.system.hawkdeskpos.db.util.Controller;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import static org.hibernate.annotations.SourceType.DB;

/**
 *
 * @author Thusitha
 */
public class Brand {

    private static final SessionFactory sf = Controller.getSessionFactory();

    public Brand() {
    }

    public Vector<String> setBrand(JComboBox comboBox) {
        Vector<String> v = new Vector<>();
        v.add("-- Please select brand");

        try (Session session = sf.openSession()) {
            List<Brands> brands = session.createQuery("FROM Brands", Brands.class)
                    .getResultList();

            for (Brands brand : brands) {
                v.add(brand.getBrandId() + " | " + brand.getBrandName());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return v;
    }

    public void addBrand() {
        String name = JOptionPane.showInputDialog("Please enter the brand name");
        if (name == null || name.trim().isEmpty()) {
            return;
        }

        try (Session session = sf.openSession()) {
            Transaction trans = session.beginTransaction();
            Brands brand = new Brands();
            brand.setBrandName(name.trim());
            session.persist(brand);
            trans.commit();
            JOptionPane.showMessageDialog(null, "Brand name saved");

        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Failed to save brand: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
