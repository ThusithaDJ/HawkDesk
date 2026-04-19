package com.olympus.system.hawkdeskpos;

import com.formdev.flatlaf.FlatLightLaf;
import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.FontManager;
import com.olympus.system.hawkdeskpos.service.SettingsService;

import javax.swing.*;
import java.awt.*;

/**
 * HawkDesk POS — application entry point.
 * Trial check runs before the database is initialised.
 */
public class HawkDeskPOS {

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
            FontManager.applyFromConfig();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // ── Trial check (no DB needed — reads config.cnf only) ────────────────
        SettingsService trial = new SettingsService(null);
        trial.ensureTrialStartDate();           // stamp start date on very first run

//        if (trial.isTrialExpired()) {
//            try {
//                SwingUtilities.invokeAndWait(() -> showExpiredDialog(trial));
//            } catch (Exception ignored) {
//                showExpiredDialog(trial);
//            }
//            System.exit(0);
//            return;
//        }

        // ── Launch main window ────────────────────────────────────────────────
        SwingUtilities.invokeLater(() -> new Home().setVisible(true));
    }

    private static void showExpiredDialog(SettingsService trial) {
        String html = "<html><body style='width:320px;font-family:sans-serif'>"
                + "<h2 style='color:#C62828;margin-bottom:4px'>Trial Period Expired</h2>"
                + "<p>Your <b>" + trial.trialTotalDays() + "-day</b> trial of <b>HawkPOS</b> has ended.</p>"
                + "<p>To continue using the software please contact us to activate a full licence.</p>"
                + "<p style='color:#5A6070;margin-top:12px'>"
                + "Olympus Systems &nbsp;·&nbsp; support@olympussystems.lk"
                + "</p>"
                + "</body></html>";

        JLabel msg = new JLabel(html);
        msg.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));

        JOptionPane.showMessageDialog(
                null, msg,
                "HawkPOS — Trial Expired",
                JOptionPane.ERROR_MESSAGE);
    }
}
