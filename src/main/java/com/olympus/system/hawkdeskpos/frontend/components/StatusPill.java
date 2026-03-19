package com.olympus.system.hawkdeskpos.frontend.components;

import javax.swing.*;
import java.awt.*;

/**
 * Rounded-rectangle status badge.
 * Usage: new StatusPill("OK", StatusPill.PillStyle.OK)
 */
public class StatusPill extends JLabel {

    public enum PillStyle {
        OK       ("#2E7D32", "#E8F5E9"),
        LOW      ("#E65100", "#FFF3E0"),
        OUT      ("#C62828", "#FFEBEE"),
        INACTIVE ("#5A6070", "#F0F2F5"),
        INFO     ("#185FA5", "#E6F1FB");

        final String fg, bg;
        PillStyle(String fg, String bg) { this.fg = fg; this.bg = bg; }

        public Color fgColor() { return Color.decode(fg); }
        public Color bgColor() { return Color.decode(bg); }
    }

    private final PillStyle style;

    public StatusPill(String text, PillStyle style) {
        super(text);
        this.style = style;
        setFont(getFont().deriveFont(Font.BOLD, 11f));
        setForeground(style.fgColor());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(2, 9, 2, 9));
    }

    /** Factory: maps "OK"|"LOW"|"OUT" status strings to a pill. */
    public static StatusPill forStatus(String status) {
        PillStyle s = switch (status == null ? "" : status.toUpperCase()) {
            case "LOW"      -> PillStyle.LOW;
            case "OUT"      -> PillStyle.OUT;
            case "INACTIVE" -> PillStyle.INACTIVE;
            case "INFO"     -> PillStyle.INFO;
            default         -> PillStyle.OK;
        };
        String label = switch (s) {
            case LOW      -> "Low";
            case OUT      -> "Out of stock";
            case INACTIVE -> "Inactive";
            case INFO     -> status;
            default       -> "OK";
        };
        return new StatusPill(label, s);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(style.bgColor());
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
        g2.dispose();
        super.paintComponent(g);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        d.height = Math.max(d.height, 20);
        return d;
    }
}
