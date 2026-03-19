package com.olympus.system.hawkdeskpos.frontend.components;

import javax.swing.*;
import java.awt.*;

/**
 * White card with 12 px rounded border in #E2E5EA.
 * Drop-in replacement for JPanel wherever a "card" appearance is needed.
 */
public class CardPanel extends JPanel {

    private static final Color BORDER_COLOR = new Color(0xE2, 0xE5, 0xEA);
    private static final int   RADIUS       = 12;

    public CardPanel() {
        this(new BorderLayout());
    }

    public CardPanel(LayoutManager layout) {
        super(layout);
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // White fill
        g2.setColor(Color.WHITE);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), RADIUS, RADIUS);

        // 1 px border
        g2.setColor(BORDER_COLOR);
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS, RADIUS);

        g2.dispose();
    }
}
