/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.olympus.system.hawkdeskpos.frontend;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 *
 * @author Thusitha
 */
public class Conn {
    
    public Connection con() throws Exception{
        
        Class.forName("com.mysql.jdbc.Driver");
        Connection c = DriverManager.getConnection("jdbc:mysql://localhost:3306/pharmacy", "root", "1234");
        return c;
        
    }
    
}
