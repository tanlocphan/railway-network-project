package com.railway.app.ui.components;

import com.railway.app.ui.theme.AppTheme;

import javax.swing.JLabel;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * A small pill with a coloured background, used for statuses and types.
 */
public class Badge extends JLabel {

    private Color fill;

    public Badge(String text, Color foreground, Color fill) {
        super(text);
        this.fill = fill;
        setForeground(foreground);
        setFont(AppTheme.semibold(11.5f));
        setBorder(new EmptyBorder(3, 10, 3, 10));
        setOpaque(false);
    }

    public static Badge of(String text) {
        Badge badge = new Badge(text, AppTheme.GRAY, AppTheme.GRAY_SOFT);
        badge.applyStatus(text);
        return badge;
    }

    public void applyStatus(String status) {
        setText(status == null ? "" : status);
        StatusStyle style = StatusStyle.of(status);
        setForeground(style.foreground());
        this.fill = style.background();
        repaint();
    }

    public void setColors(Color foreground, Color background) {
        setForeground(foreground);
        this.fill = background;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        if (getText() != null && !getText().isEmpty()) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(fill);
            int h = getHeight();
            g.fillRoundRect(0, 0, getWidth(), h, h, h);
            g.dispose();
        }
        super.paintComponent(graphics);
    }
}
