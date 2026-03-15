/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */
package com.olympus.system.hawkdeskpos;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.olympus.system.hawkdeskpos.frontend.Home;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;
import javax.swing.UIManager;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.view.JasperViewer;

/**
 *
 * @author Thusitha
 */
public class HawkDeskPOS {

    public static void main(String[] args) {
        System.out.println("Hello World!");
        try {
//            JasperReport jasperReport = JasperCompileManager.compileReport("D:\\Dev\\Workspaces\\NetBeans\\Products\\Pharmacy\\src\\reports\\invoice703.jrxml");
//
//            // 2. Open a JDBC connection to your database
//            Connection connection = DriverManager.getConnection(
//                    "jdbc:mysql://localhost:3306/pharmacy",
//                    "root",
//                    "1234"
//            );
//
//            // 3. Pass parameters — matches $P{invoiceId} in your report
//            Map<String, Object> params = new HashMap<>();
//            params.put("invoiceId", 4);  // Replace with actual invoice ID
//
//            // 4. Fill the report with data from the DB
//            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, params, connection);
//
//            // 5. Show in a Swing viewer window
//            JasperViewer.viewReport(jasperPrint, true);

//            connection.close();

            UIManager.setLookAndFeel(new FlatLightLaf());
            new Home().setVisible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
