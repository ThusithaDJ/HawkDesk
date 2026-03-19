package com.olympus.system.hawkdeskpos.frontend.components;

import javax.swing.*;
import java.awt.*;

/**
 * Two-step confirmation modal with a danger-red confirm button.
 * Usage:
 *   boolean confirmed = ConfirmDialog.show(parent, "Delete Item",
 *       "This cannot be undone. Are you sure?", "Delete");
 */
public class ConfirmDialog extends JDialog {

    private static final Color DANGER_FG = new Color(0xC6, 0x28, 0x28);
    private static final Color DANGER_BG = new Color(0xFF, 0xEB, 0xEE);

    private boolean confirmed = false;

    private ConfirmDialog(Frame parent, String title, String message, String confirmLabel) {
        super(parent, title, true);
        buildUI(message, confirmLabel);
    }

    private ConfirmDialog(Dialog parent, String title, String message, String confirmLabel) {
        super(parent, title, true);
        buildUI(message, confirmLabel);
    }

    private void buildUI(String message, String confirmLabel) {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setBorder(BorderFactory.createEmptyBorder(24, 24, 20, 24));
        content.setBackground(Color.WHITE);

        // Message
        JLabel lbl = new JLabel("<html><body style='width:280px'>" + message + "</body></html>");
        lbl.setFont(UIManager.getFont("Label.font"));
        lbl.setForeground(new Color(0x1A, 0x1D, 0x23));
        content.add(lbl, BorderLayout.CENTER);

        // Buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);

        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        buttons.add(cancel);

        JButton confirm = new JButton(confirmLabel);
        confirm.setForeground(DANGER_FG);
        confirm.setBackground(DANGER_BG);
        confirm.setOpaque(true);
        confirm.setBorderPainted(true);
        confirm.setBorder(BorderFactory.createLineBorder(new Color(0xFF, 0xCD, 0xD2)));
        confirm.addActionListener(e -> { confirmed = true; dispose(); });
        buttons.add(confirm);

        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        pack();
        setLocationRelativeTo(getOwner());
    }

    /** Shows the dialog and returns true if the user confirmed. */
    public static boolean show(Component parent, String title, String message, String confirmLabel) {
        Window w = SwingUtilities.getWindowAncestor(parent);
        ConfirmDialog dlg;
        if (w instanceof Dialog d)      dlg = new ConfirmDialog(d, title, message, confirmLabel);
        else if (w instanceof Frame f)  dlg = new ConfirmDialog(f, title, message, confirmLabel);
        else                            dlg = new ConfirmDialog((Frame) null, title, message, confirmLabel);
        dlg.setVisible(true);
        return dlg.confirmed;
    }
}
