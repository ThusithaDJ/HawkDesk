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
            // Read connection settings from hibernate.cfg.xml
            Configuration config = new Configuration().configure();
            String url  = config.getProperty("hibernate.connection.url");
            String user = config.getProperty("hibernate.connection.username");
            String pass = config.getProperty("hibernate.connection.password");

            // Apply any pending migrations BEFORE Hibernate validates the schema.
            // baselineOnMigrate: if no flyway_schema_history table exists yet (pre-existing DB),
            // create it and mark V1–V6 as already applied, then run V7+ normally.
            Flyway.configure()
                    .dataSource(url, user, pass)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .baselineVersion("6")
                    .load()
                    .migrate();

            // Now build the SessionFactory — schema is guaranteed up-to-date
            sessionFactory = config.buildSessionFactory();
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
