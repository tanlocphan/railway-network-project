package com.railway.setup.ui;

import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;

/** White rounded surface with a hairline border (same as the Railway app). */
public class Card extends JPanel {

    private Color fill = Theme.SURFACE;
    private Color outline = Theme.BORDER;
    private float outlineWidth = 1f;

    public Card() {
        this(new BorderLayout(), 18);
    }

    public Card(LayoutManager layout, int padding) {
        super(layout);
        setOpaque(false);
        setBorder(new EmptyBorder(padding, padding, padding, padding));
    }

    public Card fill(Color color) {
        fill = color;
        repaint();
        return this;
    }

    public Card outline(Color color, float width) {
        outline = color;
        outlineWidth = width;
        repaint();
        return this;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(fill);
        g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
        if (outline != null) {
            g.setColor(outline);
            g.setStroke(new java.awt.BasicStroke(outlineWidth));
            int inset = Math.round(outlineWidth / 2);
            g.drawRoundRect(inset, inset, getWidth() - 1 - 2 * inset, getHeight() - 1 - 2 * inset, 14, 14);
        }
        g.dispose();
        super.paintComponent(graphics);
    }
}
