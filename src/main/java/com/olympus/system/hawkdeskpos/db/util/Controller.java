/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.olympus.system.hawkdeskpos.db.util;

import org.flywaydb.core.Flyway;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

public class Controller {

    private static final SessionFactory sessionFactory;

    static {
        try {
            Configuration config = new Configuration().configure();

            // Override connection settings from config.cnf if the user has
            // configured a database through the setup dialog.
            com.olympus.system.hawkdeskpos.util.DbConfig dbCfg =
                    new com.olympus.system.hawkdeskpos.util.DbConfig();
            if (dbCfg.isConfigured()) {
                config.setProperty("hibernate.connection.url",      dbCfg.buildJdbcUrl());
                config.setProperty("hibernate.connection.username", dbCfg.getUser());
                config.setProperty("hibernate.connection.password", dbCfg.getPassword());
            }

            String url  = config.getProperty("hibernate.connection.url");
            String user = config.getProperty("hibernate.connection.username");
            String pass = config.getProperty("hibernate.connection.password");

            Flyway.configure()
                    .dataSource(url, user, pass)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .baselineVersion("6")
                    .load()
                    .migrate();

            sessionFactory = config.buildSessionFactory();
        } catch (Throwable ex) {
            System.err.println("SessionFactory creation failed: " + ex);
            throw new ExceptionInInitializerError(ex);
        }
    }

    public static SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    public static void shutdown() {
        getSessionFactory().close();
    }
}
