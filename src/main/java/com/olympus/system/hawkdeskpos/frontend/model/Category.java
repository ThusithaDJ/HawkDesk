/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.olympus.system.hawkdeskpos.frontend.model;

import com.olympus.system.hawkdeskpos.db.util.Controller;
import java.util.ArrayList;
import java.util.Vector;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.criterion.Restrictions;

/**
 *
 * @author Thusitha
 */
public class Category {
    
    SessionFactory sf =null;
    Session ses = null;
    public Category(){
        sf = Controller.getSessionFactory();
        ses = sf.openSession();
    }
    
    public Vector setComboBox(JComboBox comboBox){
        
        Criteria cr = ses.createCriteria(com.olympus.system.hawkdeskpos.db.dao.Category.class);
        cr.add(Restrictions.eq("stat", "active"));
        ArrayList<com.olympus.system.hawkdeskpos.db.dao.Category> list = (ArrayList<com.olympus.system.hawkdeskpos.db.dao.Category>) cr.list();
        Vector v = new Vector();
        v.add("-- Please select category --");
        for (int i = 0; i < list.size(); i++) {
            com.olympus.system.hawkdeskpos.db.dao.Category category = list.get(i);
            v.add(category.getCatId()+" | "+category.getCategoryName());
        }
        
        return v;
    }
    
    public void addCategory(){
        String name = JOptionPane.showInputDialog("Please enter the category name");
        try {
            if (!name.equals("")) {
                com.olympus.system.hawkdeskpos.db.dao.Category cat = new com.olympus.system.hawkdeskpos.db.dao.Category();
                Transaction trans = ses.beginTransaction();
                cat.setCategoryName(name);
                cat.setStat("active");
                ses.save(cat);
                trans.commit();
                JOptionPane.showMessageDialog(null, "Category Saved");
            }
        } catch (Exception e) {}
    }
    
}
