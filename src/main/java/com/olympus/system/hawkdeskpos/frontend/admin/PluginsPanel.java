package com.olympus.system.hawkdeskpos.frontend.admin;

import com.olympus.system.hawkdeskpos.plugin.PluginEntry;
import com.olympus.system.hawkdeskpos.plugin.PluginLoader;
import com.olympus.system.hawkdeskpos.plugin.PluginRegistry;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Settings > Plugins panel.
 * Shows installed plugins and lets admins install new ones from a JAR file.
 * Plugin changes take effect after restarting the app.
 */
public class PluginsPanel extends JPanel {

    private static final Color TEXT2 = new Color(0x5A, 0x60, 0x70);
    private static final Color BORDER_COLOR = new Color(0xE0, 0xE4, 0xEB);
    private static final Color RED   = new Color(0xC6, 0x28, 0x28);

    private final JPanel listArea;

    public PluginsPanel() {
        setOpaque(false);
        setLayout(new BorderLayout(0, 12));
        setBorder(new EmptyBorder(0, 0, 0, 0));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Installed Plugins");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        header.add(title, BorderLayout.WEST);

        JButton installBtn = new JButton("+ Install Plugin");
        installBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        installBtn.addActionListener(e -> installPlugin());
        header.add(installBtn, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // Scrollable plugin list
        listArea = new JPanel();
        listArea.setOpaque(false);
        listArea.setLayout(new BoxLayout(listArea, BoxLayout.Y_AXIS));

        JScrollPane scroll = new JScrollPane(listArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);

        JLabel hint = new JLabel("Plugin changes take effect after restarting the app.");
        hint.setFont(hint.getFont().deriveFont(11f));
        hint.setForeground(TEXT2);
        add(hint, BorderLayout.SOUTH);

        refresh();
    }

    private void refresh() {
        listArea.removeAll();
        List<PluginEntry> plugins = PluginRegistry.getInstance().getLoadedPlugins();
        if (plugins.isEmpty()) {
            JLabel empty = new JLabel("No plugins installed. Click \"+ Install Plugin\" to add one.");
            empty.setForeground(TEXT2);
            empty.setBorder(new EmptyBorder(16, 0, 0, 0));
            listArea.add(empty);
        } else {
            for (PluginEntry entry : plugins) {
                listArea.add(buildRow(entry));
                listArea.add(Box.createVerticalStrut(8));
            }
        }
        listArea.revalidate();
        listArea.repaint();
    }

    private JPanel buildRow(PluginEntry entry) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(true);
        row.setBackground(Color.WHITE);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                new EmptyBorder(10, 14, 10, 14)
        ));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));

        JLabel name = new JLabel(entry.meta().name() + "   v" + entry.meta().version());
        name.setFont(name.getFont().deriveFont(Font.BOLD, 13f));
        info.add(name);

        String byLine = entry.meta().description().isBlank()
                ? "by " + entry.meta().author()
                : entry.meta().description() + " — " + entry.meta().author();
        JLabel desc = new JLabel(byLine);
        desc.setFont(desc.getFont().deriveFont(11f));
        desc.setForeground(TEXT2);
        info.add(desc);

        row.add(info, BorderLayout.CENTER);

        JButton remove = new JButton("Remove");
        remove.setForeground(RED);
        remove.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        remove.addActionListener(e -> removePlugin(entry));
        row.add(remove, BorderLayout.EAST);

        return row;
    }

    private void installPlugin() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Plugin JAR");
        chooser.setFileFilter(new FileNameExtensionFilter("JAR files (*.jar)", "jar"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File src = chooser.getSelectedFile();
        File dest = new File(PluginLoader.resolvePluginsDir(), src.getName());
        try {
            Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            JOptionPane.showMessageDialog(this,
                    "Plugin installed successfully.\nRestart the app to activate it.",
                    "Plugin Installed", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not install plugin: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void removePlugin(PluginEntry entry) {
        int res = JOptionPane.showConfirmDialog(this,
                "Remove plugin \"" + entry.meta().name() + "\"?\nThe plugin file will be deleted.",
                "Remove Plugin", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (res != JOptionPane.YES_OPTION) return;

        if (entry.jarFile().delete()) {
            JOptionPane.showMessageDialog(this,
                    "Plugin removed. Restart the app to complete uninstallation.",
                    "Plugin Removed", JOptionPane.INFORMATION_MESSAGE);
            refresh();
        } else {
            JOptionPane.showMessageDialog(this,
                    "Could not delete the plugin file. You may need to remove it manually from:\n"
                            + entry.jarFile().getAbsolutePath(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
