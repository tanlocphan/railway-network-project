package com.railway.app.ui.components;

import com.railway.app.ui.theme.AppTheme;
import com.railway.app.ui.theme.Icons;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;

/**
 * Centered illustration + message for screens with nothing to show yet.
 */
public final class EmptyState {

    private EmptyState() {
    }

    public static JPanel create(String icon, String title, String message, JComponent action) {
        JLabel circle = new JLabel(Icons.get(icon, 28, AppTheme.ORANGE), SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(AppTheme.ORANGE_SOFT);
                g.fillOval(0, 0, getWidth(), getHeight());
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        circle.setPreferredSize(new Dimension(64, 64));
        circle.setMaximumSize(new Dimension(64, 64));

        JLabel heading = Ui.strong(title, 16);
        JLabel text = Ui.muted(message);
        JPanel stack = Ui.stack(circle, Ui.vgap(14), heading, Ui.vgap(6), text);
        for (Component c : stack.getComponents()) {
            if (c instanceof JComponent jc) {
                jc.setAlignmentX(Component.CENTER_ALIGNMENT);
            }
        }
        if (action != null) {
            stack.add(Ui.vgap(16));
            action.setAlignmentX(Component.CENTER_ALIGNMENT);
            stack.add(action);
        }
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setOpaque(false);
        wrapper.add(stack);
        return wrapper;
    }
}
