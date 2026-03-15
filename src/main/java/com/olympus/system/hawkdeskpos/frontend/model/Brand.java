/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.olympus.system.hawkdeskpos.frontend.model;

import com.olympus.system.hawkdeskpos.db.dao.Brands;
import com.olympus.system.hawkdeskpos.db.util.Controller;
import java.util.ArrayList;
import java.util.Vector;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import static org.hibernate.annotations.SourceType.DB;

/**
 *
 * @author Thusitha
 */
public class Brand {

    SessionFactory sf = null;
    Session ses = null;

    public Brand() {
        sf = Controller.getSessionFactory();
        ses = sf.openSession();
    }

    public Vector setBrand(JComboBox comboBox) {

        Criteria c = ses.createCriteria(Brands.class);
        ArrayList<Brands> arr = (ArrayList<Brands>) c.list();
        Vector v = new Vector();
        v.add("-- Please select brand");
        for (int i = 0; i < arr.size(); i++) {
            Brands brands = arr.get(i);
            v.add(brands.getBrandId() + " | " + brands.getBrandName());
        }
        return v;
    }

    public void addBrand() {
        String name = JOptionPane.showInputDialog("Please enter the brand name");
        try {
            if (!name.equals("")) {
                Brands brand = new Brands();
                Transaction trans = ses.beginTransaction();

                brand.setBrandName(name);
                ses.save(brand);
                trans.commit();
                JOptionPane.showMessageDialog(null, "Brand name saved");
            }
        } catch (Exception e) {
        }
    }
}
