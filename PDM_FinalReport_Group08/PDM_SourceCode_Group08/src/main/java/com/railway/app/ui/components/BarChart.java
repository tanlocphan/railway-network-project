package com.railway.app.ui.components;

import com.railway.app.ui.theme.AppTheme;

import javax.swing.JComponent;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Horizontal bar chart for one series (e.g. revenue per train).
 * Thin bars, rounded at the data end and square at the baseline, a quiet
 * value axis, and a tooltip with the exact figure on hover.
 */
public class BarChart extends JComponent {

    public record Bar(String label, BigDecimal value, String tooltip) {
    }

    private static final int LABEL_WIDTH = 56;
    private static final int AXIS_HEIGHT = 24;
    private static final int MAX_BAR = 18;

    private List<Bar> bars = new ArrayList<>();
    private final List<Rectangle> hitAreas = new ArrayList<>();
    private int hoverIndex = -1;
    private String emptyMessage = "No tickets have been sold yet";

    public BarChart() {
        setPreferredSize(new Dimension(360, 260));
        setToolTipText("");
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int index = indexAt(e.getX(), e.getY());
                if (index != hoverIndex) {
                    hoverIndex = index;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverIndex = -1;
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    public void setBars(List<Bar> bars) {
        this.bars = new ArrayList<>(bars);
        hoverIndex = -1;
        repaint();
    }

    public void setEmptyMessage(String message) {
        this.emptyMessage = message;
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        int index = indexAt(event.getX(), event.getY());
        return index < 0 ? null : bars.get(index).tooltip();
    }

    private int indexAt(int x, int y) {
        for (int i = 0; i < hitAreas.size(); i++) {
            if (hitAreas.get(i).contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        hitAreas.clear();

        int width = getWidth();
        int height = getHeight();
        Font small = AppTheme.font(Font.PLAIN, 11.5f);
        g.setFont(small);
        FontMetrics metrics = g.getFontMetrics();

        if (bars.isEmpty()) {
            g.setColor(AppTheme.TEXT_MUTED);
            g.drawString(emptyMessage, (width - metrics.stringWidth(emptyMessage)) / 2, height / 2);
            g.dispose();
            return;
        }

        double max = 0;
        for (Bar bar : bars) {
            max = Math.max(max, bar.value() == null ? 0 : bar.value().doubleValue());
        }
        double step = niceStep(max / 4.0);
        double axisMax = Math.max(step, Math.ceil(max / step) * step);

        int plotLeft = LABEL_WIDTH;
        int plotRight = width - 16;
        int plotTop = 4;
        int plotBottom = height - AXIS_HEIGHT;
        int plotWidth = Math.max(10, plotRight - plotLeft);

        // Recessive grid and tick labels
        for (double tick = 0; tick <= axisMax + 0.5; tick += step) {
            int x = plotLeft + (int) Math.round(tick / axisMax * plotWidth);
            g.setColor(AppTheme.CHART_GRID);
            g.drawLine(x, plotTop, x, plotBottom);
            String label = tick == 0 ? "0" : Formats.compactMoney(BigDecimal.valueOf(tick)).replace(" ₫", "");
            g.setColor(AppTheme.TEXT_MUTED);
            int labelX = Math.min(x - metrics.stringWidth(label) / 2, width - metrics.stringWidth(label) - 2);
            g.drawString(label, Math.max(plotLeft - 4, labelX), height - 6);
        }

        int slot = Math.max(1, (plotBottom - plotTop) / bars.size());
        int barHeight = Math.max(6, Math.min(MAX_BAR, slot - 10));
        for (int i = 0; i < bars.size(); i++) {
            Bar bar = bars.get(i);
            int centerY = plotTop + slot * i + slot / 2;
            int y = centerY - barHeight / 2;
            double value = bar.value() == null ? 0 : bar.value().doubleValue();
            int length = (int) Math.round(value / axisMax * plotWidth);

            // Category label in text ink, never the series colour
            g.setFont(i == hoverIndex ? AppTheme.semibold(12) : AppTheme.font(Font.PLAIN, 12));
            g.setColor(i == hoverIndex ? AppTheme.TEXT : AppTheme.TEXT_SECONDARY);
            FontMetrics labelMetrics = g.getFontMetrics();
            g.drawString(bar.label(), plotLeft - 10 - labelMetrics.stringWidth(bar.label()),
                    centerY + labelMetrics.getAscent() / 2 - 2);

            if (length > 0) {
                Area shape = new Area(new RoundRectangle2D.Double(plotLeft, y, length, barHeight, 8, 8));
                shape.add(new Area(new Rectangle2D.Double(plotLeft, y, Math.min(4, length), barHeight)));
                g.setColor(i == hoverIndex ? AppTheme.CHART_BAR_HOVER : AppTheme.CHART_BAR);
                g.fill(shape);
            }
            hitAreas.add(new Rectangle(0, plotTop + slot * i, width, slot));
        }

        // Baseline
        g.setColor(AppTheme.BORDER);
        g.drawLine(plotLeft, plotTop, plotLeft, plotBottom);
        g.dispose();
    }

    private static double niceStep(double raw) {
        if (raw <= 0) {
            return 1;
        }
        double exponent = Math.pow(10, Math.floor(Math.log10(raw)));
        double fraction = raw / exponent;
        double nice = fraction <= 1 ? 1 : fraction <= 2 ? 2 : fraction <= 5 ? 5 : 10;
        return nice * exponent;
    }
}
