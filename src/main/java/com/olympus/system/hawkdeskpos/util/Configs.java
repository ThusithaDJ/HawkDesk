package com.olympus.system.hawkdeskpos.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

public class    Configs {

    private static final String CONFIG_PATH =
            System.getProperty("user.home") + File.separator + "HawkDeskPOS" + File.separator + "config.cnf";

    public static Properties prop = new Properties();

    private static File ensureConfigFile() {
        File f = new File(CONFIG_PATH);
        if (!f.getParentFile().exists()) {
            f.getParentFile().mkdirs();
        }
        return f;
    }

    public void SaveProp(String title, String value) {
        try {
            File f = ensureConfigFile();
            if (f.exists()) {
                prop.load(new FileInputStream(f));
            }
            prop.setProperty(title, value);
            prop.store(new FileOutputStream(f), "HawkDeskPOS Configuration");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getProp(String title) {
        String value = "";
        try {
            File f = ensureConfigFile();
            if (f.exists()) {
                prop.load(new FileInputStream(f));
                value = prop.getProperty(title, "");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return value;
    }
}
