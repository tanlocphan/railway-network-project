package com.railway.app.ui.components;

import com.railway.app.ui.theme.AppTheme;
import com.railway.app.ui.theme.Icons;

import javax.swing.Icon;
import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * "1 Trip — 2 Seat — 3 Passenger — 4 Done" progress header.
 */
public class StepIndicator extends JComponent {

    private final String[] steps;
    private int current;

    public StepIndicator(String... steps) {
        this.steps = steps;
        setPreferredSize(new Dimension(600, 58));
    }

    public void setCurrent(int index) {
        this.current = index;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int n = steps.length;
        int diameter = 28;
        int margin = 70;
        int usable = getWidth() - margin * 2;
        int gap = n > 1 ? usable / (n - 1) : 0;
        int cy = 16;

        for (int i = 0; i < n - 1; i++) {
            int x1 = margin + gap * i + diameter / 2 + 6;
            int x2 = margin + gap * (i + 1) - diameter / 2 - 6;
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(i < current ? AppTheme.ORANGE : AppTheme.BORDER);
            g.drawLine(x1, cy, x2, cy);
        }

        Font numberFont = AppTheme.semibold(13);
        Font labelFont = AppTheme.semibold(12);
        for (int i = 0; i < n; i++) {
            int cx = margin + gap * i;
            int x = cx - diameter / 2;
            int y = cy - diameter / 2;
            boolean done = i < current;
            boolean active = i == current;
            if (done) {
                g.setColor(AppTheme.ORANGE);
                g.fillOval(x, y, diameter, diameter);
                Icon check = Icons.get("check", 16, Color.WHITE);
                check.paintIcon(this, g, cx - 8, cy - 8);
            } else if (active) {
                g.setColor(AppTheme.ORANGE_SOFT);
                g.fillOval(x - 4, y - 4, diameter + 8, diameter + 8);
                g.setColor(AppTheme.ORANGE);
                g.fillOval(x, y, diameter, diameter);
                drawCentered(g, String.valueOf(i + 1), numberFont, Color.WHITE, cx, cy);
            } else {
                g.setColor(AppTheme.SURFACE);
                g.fillOval(x, y, diameter, diameter);
                g.setColor(AppTheme.BORDER);
                g.setStroke(new BasicStroke(1.5f));
                g.drawOval(x, y, diameter, diameter);
                drawCentered(g, String.valueOf(i + 1), numberFont, AppTheme.TEXT_MUTED, cx, cy);
            }
            g.setFont(labelFont);
            g.setColor(active ? AppTheme.TEXT : (done ? AppTheme.TEXT_SECONDARY : AppTheme.TEXT_MUTED));
            FontMetrics metrics = g.getFontMetrics();
            g.drawString(steps[i], cx - metrics.stringWidth(steps[i]) / 2, cy + diameter / 2 + 20);
        }
        g.dispose();
    }

    private static void drawCentered(Graphics2D g, String text, Font font, Color color, int cx, int cy) {
        g.setFont(font);
        g.setColor(color);
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(text, cx - metrics.stringWidth(text) / 2, cy + (metrics.getAscent() - metrics.getDescent()) / 2);
    }
}
