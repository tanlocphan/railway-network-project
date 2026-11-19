package com.railway.app.ui.components;

import com.railway.app.ui.theme.AppTheme;
import com.railway.app.ui.theme.Icons;

import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Short confirmation shown in the bottom-right corner ("Ticket booked").
 */
public final class Toast {

    private Toast() {
    }

    public static void success(Component anywhere, String message) {
        JRootPane root = anywhere == null ? null : SwingUtilities.getRootPane(anywhere);
        if (root == null) {
            return;
        }
        JLayeredPane layers = root.getLayeredPane();

        JPanel toast = new JPanel(new BorderLayout(10, 0)) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(AppTheme.NAVY);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g.dispose();
            }
        };
        toast.setOpaque(false);
        toast.setBorder(new EmptyBorder(12, 16, 12, 18));
        JLabel icon = new JLabel(Icons.get("check", 18, AppTheme.ORANGE));
        JLabel text = new JLabel(message);
        text.setFont(AppTheme.font(Font.PLAIN, 13));
        text.setForeground(java.awt.Color.WHITE);
        toast.add(icon, BorderLayout.WEST);
        toast.add(text, BorderLayout.CENTER);

        Dimension size = toast.getPreferredSize();
        int x = layers.getWidth() - size.width - 28;
        int y = layers.getHeight() - size.height - 28;
        toast.setBounds(x, y, size.width, size.height);
        layers.add(toast, JLayeredPane.POPUP_LAYER);
        layers.revalidate();
        layers.repaint();

        Timer timer = new Timer(3200, e -> {
            layers.remove(toast);
            layers.repaint();
        });
        timer.setRepeats(false);
        timer.start();
    }
}
