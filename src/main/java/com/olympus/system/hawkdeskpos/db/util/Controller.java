/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.olympus.system.hawkdeskpos.db.util;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

public class Controller {

    private static final SessionFactory sessionFactory;

    static {
        try {
            sessionFactory = new Configuration()
                    .configure() // reads hibernate.cfg.xml from classpath
                    .buildSessionFactory();
        } catch (Throwable ex) {
            System.err.println("SessionFactory creation failed: " + ex);
            throw new ExceptionInInitializerError(ex);
        }
    }

    public static SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    // Always close the factory on app shutdown
    public static void shutdown() {
        getSessionFactory().close();
    }
}
