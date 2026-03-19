/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */
package com.olympus.system.hawkdeskpos;

import com.formdev.flatlaf.FlatLightLaf;
import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.FontManager;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * HawkDesk POS — application entry point.
 */
public class HawkDeskPOS {

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
            FontManager.applyFromConfig();
        } catch (Exception e) {
            e.printStackTrace();
        }
        SwingUtilities.invokeLater(() -> new Home().setVisible(true));
    }
}
