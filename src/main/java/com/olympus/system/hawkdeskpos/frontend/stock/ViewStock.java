/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.olympus.system.hawkdeskpos.frontend.stock;

import com.olympus.system.hawkdeskpos.db.dao.Category;
import com.olympus.system.hawkdeskpos.db.dao.Item;
import com.olympus.system.hawkdeskpos.db.dao.Stock;
import com.olympus.system.hawkdeskpos.db.util.Controller;
import com.olympus.system.hawkdeskpos.frontend.Home;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.KeyEvent;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import javax.swing.ImageIcon;
import javax.swing.table.DefaultTableModel;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.MatchMode;
import org.hibernate.criterion.Restrictions;

/**
 *
 * @author Thusitha
 */
public class ViewStock extends javax.swing.JInternalFrame {

    /**
     * Creates new form ViewStock
     */
    SessionFactory sf = null;
    Session ses = null;
    NumberFormat f = null;

    public ViewStock() {
        super("View Stock", true, true, true);
        initComponents();
        sf = Controller.getSessionFactory();
        ses = sf.openSession();
        f = NumberFormat.getNumberInstance();
        f.setMinimumFractionDigits(2);
        setTableValue("load");
        setCategory();
        jRadioButton1.setVisible(false);
        jLabel7.setVisible(false);
    }

    public void setTableValue(String stake) {
        DefaultTableModel dtm = (DefaultTableModel) jTable1.getModel();

        int r = jTable1.getRowCount();
        for (int i = 0; i < r; i++) {
            dtm.removeRow(0);
        }

        Criteria cr = ses.createCriteria(Stock.class);

//        if (stake.equals("search")) {
//         cr.add(Restrictions.eq(stake, stake));
//        }
//        
        cr.add(Restrictions.eq("stat", "available"));
        ArrayList<Stock> stock = (ArrayList<Stock>) cr.list();
        for (int i = 0; i < stock.size(); i++) {
            Vector v = new Vector();
            Stock stk = stock.get(i);
            Item itm = stk.getItem();
            Category ct = itm.getCategory();

            Date d2 = new Date(System.currentTimeMillis());
            int ig = stk.getExpireDate().compareTo(d2);

            if (stk.getQty() <= stk.getItem().getMinLevel()) {
                v.add("<html><font color='#f00000' size='4'>" + itm.getItemId() + "</font></html>");
                v.add("<html><font color='#f00000' size='4'>" + itm.getItemName() + "</font></html>");
                v.add("<html><font color='#f00000' size='4'>" + ct.getCategoryName() + "</font></html>");
                v.add("<html><font color='#f00000' size='4'>" + stk.getBatch() + "</font></html>");
                v.add("<html><font color='#f00000' size='4'>" + stk.getExpireDate() + "</font></html>");
                v.add("<html><font color='#f00000' size='4'>" + stk.getQty() + "</font></html>");
                v.add("<html><font color='#f00000' size='4'>" + f.format(stk.getCost()) + "</font></html>");
                v.add("<html><font color='#f00000' size='4'>" + f.format(stk.getPrice()) + "</font></html>");

            } else if (ig < 0) {
                v.add("<html><font color='#ce00ff' size='4'>" + itm.getItemId() + "</font></html>");
                v.add("<html><font color='#ce00ff' size='4'>" + itm.getItemName() + "</font></html>");
                v.add("<html><font color='#ce00ff' size='4'>" + ct.getCategoryName() + "</font></html>");
                v.add("<html><font color='#ce00ff' size='4'>" + stk.getBatch() + "</font></html>");
                v.add("<html><font color='#ce00ff' size='4'>" + stk.getExpireDate() + "</font></html>");
                v.add("<html><font color='#ce00ff' size='4'>" + stk.getQty() + "</font></html>");
                v.add("<html><font color='#ce00ff' size='4'>" + f.format(stk.getCost()) + "</font></html>");
                v.add("<html><font color='#ce00ff' size='4'>" + f.format(stk.getPrice()) + "</font></html>");
            } else {
                v.add("<html><font color='#00b300' size='4'>" + itm.getItemId() + "</font></html>");
                v.add("<html><font color='#00b300' size='4'>" + itm.getItemName() + "</font></html>");
                v.add("<html><font color='#00b300' size='4'>" + ct.getCategoryName() + "</font></html>");
                v.add("<html><font color='#00b300' size='4'>" + stk.getBatch() + "</font></html>");
                v.add("<html><font color='#00b300' size='4'>" + stk.getExpireDate() + "</font></html>");
                v.add("<html><font color='#00b300' size='4'>" + stk.getQty() + "</font></html>");
                v.add("<html><font color='#00b300' size='4'>" + f.format(stk.getCost()) + "</font></html>");
                v.add("<html><font color='#00b300' size='4'>" + f.format(stk.getPrice()) + "</font></html>");

            }

            dtm.addRow(v);
            jTable1.setModel(dtm);

        }
        System.gc();

    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonGroup1 = new javax.swing.ButtonGroup();
        buttonGroup2 = new javax.swing.ButtonGroup();
        jPanel1 = new javax.swing.JPanel(){

            private Image image;{
                try{
                    ImageIcon ii = new ImageIcon(getClass().getResource("/images/back.png"));
                    image = ii.getImage();
                }catch(Exception e){

                }
            }
            @Override
            protected void paintComponent(Graphics graphcs){
                super.paintComponent(graphcs);
                graphcs.drawImage(image,0,0,getWidth(), getHeight(), this);
            }
        };
        jLabel2 = new javax.swing.JLabel();
        jPanel5 = new javax.swing.JPanel(){

            private Image image;{
                try{
                    ImageIcon ii = new ImageIcon(getClass().getResource("/images/back2.png"));
                    image = ii.getImage();
                }catch(Exception e){

                }
            }
            @Override
            protected void paintComponent(Graphics graphcs){
                super.paintComponent(graphcs);
                graphcs.drawImage(image,0,0,getWidth(), getHeight(), this);
            }
        };
        jScrollPane1 = new javax.swing.JScrollPane();
        jTable1 = new javax.swing.JTable();
        jLabel1 = new javax.swing.JLabel();
        txtSearch = new javax.swing.JTextField();
        jButton4 = new javax.swing.JButton();
        jLabel5 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        jLabel9 = new javax.swing.JLabel();
        jScrollPane3 = new javax.swing.JScrollPane();
        jPanel2 = new javax.swing.JPanel(){

            private Image image;{
                try{
                    ImageIcon ii = new ImageIcon(getClass().getResource("/images/back2.png"));
                    image = ii.getImage();
                }catch(Exception e){

                }
            }
            @Override
            protected void paintComponent(Graphics graphcs){
                super.paintComponent(graphcs);
                graphcs.drawImage(image,0,0,getWidth(), getHeight(), this);
            }
        };
        jPanel4 = new javax.swing.JPanel(){

            private Image image;{
                try{
                    ImageIcon ii = new ImageIcon(getClass().getResource("/images/back2.png"));
                    image = ii.getImage();
                }catch(Exception e){

                }
            }
            @Override
            protected void paintComponent(Graphics graphcs){
                super.paintComponent(graphcs);
                graphcs.drawImage(image,0,0,getWidth(), getHeight(), this);
            }
        };
        jRadioButton1 = new javax.swing.JRadioButton();
        jRadioButton2 = new javax.swing.JRadioButton();
        jScrollPane2 = new javax.swing.JScrollPane();
        lstCategory = new javax.swing.JList();
        jLabel3 = new javax.swing.JLabel();
        jRadioButton3 = new javax.swing.JRadioButton();
        btnAddStock = new javax.swing.JButton();
        jButton2 = new javax.swing.JButton();

        setTitle("View Stock");
        setFrameIcon(new javax.swing.ImageIcon(getClass().getResource("/images/icons/Inventory-maintenance.png"))); // NOI18N

        jPanel1.setBackground(new java.awt.Color(255, 255, 255));

        jLabel2.setFont(new java.awt.Font("Tahoma", 0, 24)); // NOI18N
        jLabel2.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/stock/stock.png"))); // NOI18N
        jLabel2.setText("View Stock");

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jLabel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jLabel2, javax.swing.GroupLayout.DEFAULT_SIZE, 43, Short.MAX_VALUE)
        );

        jPanel5.setBackground(new java.awt.Color(255, 255, 255));

        jTable1.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Item #", "Name", "Category", "Batch", "Expire Date", "Qty", "Cost", "Price"
            }
        ) {
            boolean[] canEdit = new boolean [] {
                false, false, false, false, false, false, false, false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        jTable1.setSelectionForeground(new java.awt.Color(0, 0, 0));
        jTable1.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jScrollPane1.setViewportView(jTable1);
        if (jTable1.getColumnModel().getColumnCount() > 0) {
            jTable1.getColumnModel().getColumn(0).setMinWidth(70);
            jTable1.getColumnModel().getColumn(0).setPreferredWidth(70);
            jTable1.getColumnModel().getColumn(0).setMaxWidth(70);
            jTable1.getColumnModel().getColumn(1).setMinWidth(200);
            jTable1.getColumnModel().getColumn(2).setPreferredWidth(150);
            jTable1.getColumnModel().getColumn(2).setMaxWidth(150);
            jTable1.getColumnModel().getColumn(3).setPreferredWidth(150);
            jTable1.getColumnModel().getColumn(3).setMaxWidth(150);
            jTable1.getColumnModel().getColumn(4).setMinWidth(110);
            jTable1.getColumnModel().getColumn(4).setPreferredWidth(110);
            jTable1.getColumnModel().getColumn(4).setMaxWidth(110);
            jTable1.getColumnModel().getColumn(5).setMinWidth(50);
            jTable1.getColumnModel().getColumn(5).setPreferredWidth(50);
            jTable1.getColumnModel().getColumn(5).setMaxWidth(50);
            jTable1.getColumnModel().getColumn(6).setMinWidth(70);
            jTable1.getColumnModel().getColumn(6).setPreferredWidth(70);
            jTable1.getColumnModel().getColumn(6).setMaxWidth(70);
            jTable1.getColumnModel().getColumn(7).setMinWidth(70);
            jTable1.getColumnModel().getColumn(7).setPreferredWidth(70);
            jTable1.getColumnModel().getColumn(7).setMaxWidth(70);
        }

        jLabel1.setFont(new java.awt.Font("Tahoma", 0, 18)); // NOI18N
        jLabel1.setText("Search");

        txtSearch.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        txtSearch.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                txtSearchKeyPressed(evt);
            }
            public void keyReleased(java.awt.event.KeyEvent evt) {
                txtSearchKeyReleased(evt);
            }
        });

        jButton4.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        jButton4.setText("Search");

        jLabel5.setBackground(new java.awt.Color(255, 0, 0));
        jLabel5.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        jLabel5.setForeground(new java.awt.Color(255, 0, 0));
        jLabel5.setText("Minimum Stock");

        jLabel7.setBackground(new java.awt.Color(0, 255, 0));
        jLabel7.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        jLabel7.setForeground(new java.awt.Color(203, 0, 255));
        jLabel7.setText("Expired stock");

        jLabel9.setBackground(new java.awt.Color(203, 0, 255));
        jLabel9.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        jLabel9.setForeground(new java.awt.Color(0, 177, 0));
        jLabel9.setText("Good Stock");

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 83, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(txtSearch, javax.swing.GroupLayout.PREFERRED_SIZE, 424, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(jButton4))
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addGap(62, 62, 62)
                        .addComponent(jLabel5)
                        .addGap(85, 85, 85)
                        .addComponent(jLabel7)
                        .addGap(99, 99, 99)
                        .addComponent(jLabel9)))
                .addContainerGap(46, Short.MAX_VALUE))
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel5Layout.createSequentialGroup()
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(txtSearch, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton4))
                .addGap(13, 13, 13)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel5)
                    .addComponent(jLabel7)
                    .addComponent(jLabel9))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1))
        );

        jPanel2.setBackground(new java.awt.Color(204, 204, 204));

        jPanel4.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Filter Options", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 0, 24))); // NOI18N

        buttonGroup1.add(jRadioButton1);
        jRadioButton1.setFont(new java.awt.Font("Tahoma", 0, 16)); // NOI18N
        jRadioButton1.setText("Minimum stock");
        jRadioButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jRadioButton1ActionPerformed(evt);
            }
        });

        buttonGroup1.add(jRadioButton2);
        jRadioButton2.setFont(new java.awt.Font("Tahoma", 0, 16)); // NOI18N
        jRadioButton2.setText("Expired Stock");
        jRadioButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jRadioButton2ActionPerformed(evt);
            }
        });

        lstCategory.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        lstCategory.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                lstCategoryMouseClicked(evt);
            }
        });
        jScrollPane2.setViewportView(lstCategory);

        jLabel3.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        jLabel3.setText("Filter By Category");

        buttonGroup1.add(jRadioButton3);
        jRadioButton3.setFont(new java.awt.Font("Tahoma", 0, 16)); // NOI18N
        jRadioButton3.setSelected(true);
        jRadioButton3.setText("View All");
        jRadioButton3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jRadioButton3ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addComponent(jRadioButton1, javax.swing.GroupLayout.DEFAULT_SIZE, 211, Short.MAX_VALUE)
                        .addContainerGap())
                    .addComponent(jRadioButton2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jRadioButton3)
                            .addComponent(jLabel3))
                        .addGap(0, 0, Short.MAX_VALUE))))
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addGap(19, 19, 19)
                .addComponent(jRadioButton3)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jRadioButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 38, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jRadioButton2, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(10, 10, 10)
                .addComponent(jLabel3)
                .addGap(8, 8, 8)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 201, Short.MAX_VALUE)
                .addContainerGap())
        );

        btnAddStock.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        btnAddStock.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/toolbar/addStock.png"))); // NOI18N
        btnAddStock.setText("Add Stock");
        btnAddStock.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAddStockActionPerformed(evt);
            }
        });

        jButton2.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        jButton2.setText("Add item");
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btnAddStock, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jButton2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(btnAddStock, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(jButton2, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(13, 13, 13))
        );

        jScrollPane3.setViewportView(jPanel2);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 278, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jScrollPane3)))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void jRadioButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jRadioButton2ActionPerformed

        DefaultTableModel dtm = (DefaultTableModel) jTable1.getModel();
        int r = jTable1.getRowCount();
        for (int i = 0; i < r; i++) {
            dtm.removeRow(0);
        }
        Criteria cr = ses.createCriteria(Stock.class);
        List lst = cr.list();

        for (int i = 0; i < lst.size(); i++) {
            Stock stock = (Stock) lst.get(i);
            Item itm = (Item) ses.load(Item.class, stock.getItem().getItemId());

            Date d2 = new Date(System.currentTimeMillis());
            int ig = stock.getExpireDate().compareTo(d2);

            Vector v = new Vector();
            if (ig < 0) {

                v.add("<html><font color='#ce00ff' size='4'>" + itm.getItemId() + "</font></html>");
                v.add("<html><font color='#ce00ff' size='4'>" + itm.getItemName() + "</font></html>");
                v.add("<html><font color='#ce00ff' size='4'>" + itm.getCategory().getCategoryName() + "</font></html>");
                v.add("<html><font color='#ce00ff' size='4'>" + stock.getBatch() + "</font></html>");
                v.add("<html><font color='#ce00ff' size='4'>" + stock.getExpireDate() + "</font></html>");
                v.add("<html><font color='#ce00ff' size='4'>" + stock.getQty() + "</font></html>");
                v.add("<html><font color='#ce00ff' size='4'>" + f.format(stock.getCost()) + "</font></html>");
                v.add("<html><font color='#ce00ff' size='4'>" + f.format(stock.getPrice()) + "</font></html>");

                dtm.addRow(v);
            }

        }
        jTable1.setModel(dtm);
    }//GEN-LAST:event_jRadioButton2ActionPerformed

    private void txtSearchKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_txtSearchKeyPressed

    }//GEN-LAST:event_txtSearchKeyPressed

    private void btnAddStockActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAddStockActionPerformed

        int r = jTable1.getSelectedRowCount();
        if (r == 0) {
            AddStock stock = new AddStock();
            Home.HomeDeskpane.add(stock);
            stock.setVisible(true);
        } else {
            String s = jTable1.getValueAt(jTable1.getSelectedRow(), 0).toString();
            String ss = s.substring(37);
            int i = ss.indexOf('<');
            String sss = ss.substring(0, i);

            int id = Integer.parseInt(sss);
            AddStock stock = new AddStock((Item) ses.load(Item.class, id));
            Home.HomeDeskpane.add(stock);
            stock.setVisible(true);
        }
    }//GEN-LAST:event_btnAddStockActionPerformed

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed

        AddNewItem item = new AddNewItem();
        Home.HomeDeskpane.add(item);
        item.setVisible(true);
    }//GEN-LAST:event_jButton2ActionPerformed

    private void lstCategoryMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_lstCategoryMouseClicked

        DefaultTableModel dtm = (DefaultTableModel) jTable1.getModel();
        int r = jTable1.getRowCount();
        for (int i = 0; i < r; i++) {
            dtm.removeRow(0);
        }

        String cat = lstCategory.getSelectedValue().toString();
        Criteria cr = ses.createCriteria(Category.class);

        cr.add(Restrictions.eq("categoryName", cat));

        Category c = (Category) cr.uniqueResult();
        Criteria cr2 = ses.createCriteria(Item.class);

        cr2.add(Restrictions.eq("category", c));
        List lst = cr2.list();

        for (int i = 0; i < lst.size(); i++) {
            Item item = (Item) lst.get(i);

            Criteria c3 = ses.createCriteria(Stock.class);
            c3.add(Restrictions.eq("item", item));
            ArrayList<Stock> stk = (ArrayList<Stock>) c3.list();

            for (int j = 0; j < stk.size(); j++) {
                Stock stock = stk.get(j);
                Vector v = new Vector();

                v.add(stock.getItem().getItemId());
                v.add(stock.getItem().getItemName());
                v.add(item.getCategory().getCategoryName());
                v.add(stock.getBatch());
                v.add(stock.getExpireDate());
                v.add(stock.getQty());
                v.add(stock.getCost());
                v.add(stock.getPrice());

                dtm.addRow(v);
            }
        }
        jTable1.setModel(dtm);

    }//GEN-LAST:event_lstCategoryMouseClicked

    private void jRadioButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jRadioButton1ActionPerformed

        DefaultTableModel dtm = (DefaultTableModel) jTable1.getModel();
        int r = jTable1.getRowCount();
        for (int i = 0; i < r; i++) {
            dtm.removeRow(0);
        }

        Criteria cr = ses.createCriteria(Stock.class);
        List ls = cr.list();

        for (int i = 0; i < ls.size(); i++) {
            Stock stock = (Stock) ls.get(i);

            Item itm = (Item) ses.load(Item.class, stock.getItem().getItemId());

            int sq = stock.getQty();
            int iq = itm.getMinLevel();

            Vector v = new Vector();
            if (sq <= iq) {

                v.add("<html><font color='#f00000' size='4'>" + itm.getItemId() + "</font></html>");
                v.add("<html><font color='#f00000' size='4'>" + itm.getItemName() + "</font></html>");
                v.add("<html><font color='#f00000' size='4'>" + itm.getCategory().getCategoryName() + "</font></html>");
                v.add("<html><font color='#f00000' size='4'>" + stock.getBatch() + "</font></html>");
                v.add("<html><font color='#f00000' size='4'>" + stock.getExpireDate() + "</font></html>");
                v.add("<html><font color='#f00000' size='4'>" + stock.getQty() + "</font></html>");
                v.add("<html><font color='#f00000' size='4'>" + f.format(stock.getCost()) + "</font></html>");
                v.add("<html><font color='#f00000' size='4'>" + f.format(stock.getPrice()) + "</font></html>");

                dtm.addRow(v);

            }
        }
        jTable1.setModel(dtm);
    }//GEN-LAST:event_jRadioButton1ActionPerformed

    private void jRadioButton3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jRadioButton3ActionPerformed

        setTableValue("load");
    }//GEN-LAST:event_jRadioButton3ActionPerformed

    private void txtSearchKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_txtSearchKeyReleased

        DefaultTableModel dtm = (DefaultTableModel) jTable1.getModel();
        int i = jTable1.getRowCount();
        for (int j = 0; j < i; j++) {
            dtm.removeRow(0);
        }

        if (!txtSearch.getText().equals("")) {
            Criteria cr = ses.createCriteria(Stock.class, "S");
            cr.createAlias("S.item", "i");
            cr.add(Restrictions.like("i.itemName", txtSearch.getText(), MatchMode.ANYWHERE));
            List<Object> lst = cr.list();
            for (Object object : lst) {
                Stock stk = (Stock) object;
                Vector v = new Vector();

                Criteria crt = ses.createCriteria(Item.class);
                crt.add(Restrictions.eq("itemId", stk.getItem().getItemId()));
                Item it = (Item) crt.uniqueResult();

                Date d2 = new Date(System.currentTimeMillis());
                int ig = stk.getExpireDate().compareTo(d2);

                if (stk.getQty() <= stk.getItem().getMinLevel()) {
                    v.add("<html><font color='#f00000' size='4'>" + it.getItemId() + "</font></html>");
                    v.add("<html><font color='#f00000' size='4'>" + it.getItemName() + "</font></html>");
                    v.add("<html><font color='#f00000' size='4'>" + stk.getItem().getCategory().getCategoryName() + "</font></html>");
                    v.add("<html><font color='#f00000' size='4'>" + stk.getBatch() + "</font></html>");
                    v.add("<html><font color='#f00000' size='4'>" + stk.getExpireDate() + "</font></html>");
                    v.add("<html><font color='#f00000' size='4'>" + stk.getQty() + "</font></html>");
                    v.add("<html><font color='#f00000' size='4'>" + f.format(stk.getCost()) + "</font></html>");
                    v.add("<html><font color='#f00000' size='4'>" + f.format(stk.getPrice()) + "</font></html>");

                } else if (ig < 0) {
                    v.add("<html><font color='#ce00ff' size='4'>" + it.getItemId() + "</font></html>");
                    v.add("<html><font color='#ce00ff' size='4'>" + it.getItemName() + "</font></html>");
                    v.add("<html><font color='#ce00ff' size='4'>" + stk.getItem().getCategory().getCategoryName() + "</font></html>");
                    v.add("<html><font color='#ce00ff' size='4'>" + stk.getBatch() + "</font></html>");
                    v.add("<html><font color='#ce00ff' size='4'>" + stk.getExpireDate() + "</font></html>");
                    v.add("<html><font color='#ce00ff' size='4'>" + stk.getQty() + "</font></html>");
                    v.add("<html><font color='#ce00ff' size='4'>" + f.format(stk.getCost()) + "</font></html>");
                    v.add("<html><font color='#ce00ff' size='4'>" + f.format(stk.getPrice()) + "</font></html>");
                } else {
                    v.add("<html><font color='#00b300' size='4'>" + it.getItemId() + "</font></html>");
                    v.add("<html><font color='#00b300' size='4'>" + it.getItemName() + "</font></html>");
                    v.add("<html><font color='#00b300' size='4'>" + stk.getItem().getCategory().getCategoryName() + "</font></html>");
                    v.add("<html><font color='#00b300' size='4'>" + stk.getBatch() + "</font></html>");
                    v.add("<html><font color='#00b300' size='4'>" + stk.getExpireDate() + "</font></html>");
                    v.add("<html><font color='#00b300' size='4'>" + stk.getQty() + "</font></html>");
                    v.add("<html><font color='#00b300' size='4'>" + f.format(stk.getCost()) + "</font></html>");
                    v.add("<html><font color='#00b300' size='4'>" + f.format(stk.getPrice()) + "</font></html>");

                }

                dtm.addRow(v);
                jTable1.setModel(dtm);
            }
        } else {
            setTableValue("load");
        }
        System.gc();
    }//GEN-LAST:event_txtSearchKeyReleased


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnAddStock;
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.ButtonGroup buttonGroup2;
    private javax.swing.JButton jButton2;
    private javax.swing.JButton jButton4;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JRadioButton jRadioButton1;
    private javax.swing.JRadioButton jRadioButton2;
    private javax.swing.JRadioButton jRadioButton3;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JTable jTable1;
    private javax.swing.JList lstCategory;
    private javax.swing.JTextField txtSearch;
    // End of variables declaration//GEN-END:variables

    private void setCategory() {

        Vector v = new Vector();
        Criteria cr = ses.createCriteria(Category.class);
        List lst = cr.list();
        for (int i = 0; i < lst.size(); i++) {
            Category category = (Category) lst.get(i);
            v.add(category.getCategoryName());

        }
        lstCategory.setListData(v);
    }
}
