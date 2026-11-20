package com.railway.app.ui.components;

import com.railway.app.ui.theme.AppTheme;

import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;

/**
 * White rounded surface with a hairline border.
 */
public class Card extends JPanel {

    private Color fill = AppTheme.SURFACE;
    private Color borderColor = AppTheme.BORDER;
    private int radius = 14;

    public Card() {
        this(new BorderLayout(), 18);
    }

    public Card(LayoutManager layout) {
        this(layout, 18);
    }

    public Card(LayoutManager layout, int padding) {
        super(layout);
        setOpaque(false);
        setBorder(new EmptyBorder(padding, padding, padding, padding));
    }

    public Card fill(Color color) {
        this.fill = color;
        repaint();
        return this;
    }

    public Card outline(Color color) {
        this.borderColor = color;
        repaint();
        return this;
    }

    public Card radius(int value) {
        this.radius = value;
        repaint();
        return this;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(fill);
        g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
        if (borderColor != null) {
            g.setColor(borderColor);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
        }
        g.dispose();
        super.paintComponent(graphics);
    }
}
