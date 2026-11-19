package com.railway.app.ui.components;

import com.formdev.flatlaf.FlatClientProperties;
import com.railway.app.ui.theme.AppTheme;
import com.railway.app.ui.theme.Icons;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;

/**
 * Small factory for consistently styled labels, buttons and layouts.
 */
public final class Ui {

    private Ui() {
    }

    // ---- text -------------------------------------------------------------

    public static JLabel label(String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        // A little spare room so the last glyph of bold text is never clipped.
        label.setBorder(new EmptyBorder(0, 0, 0, Math.max(2, Math.round(font.getSize2D() * 0.2f))));
        return label;
    }

    public static JLabel pageTitle(String text) {
        return label(text, AppTheme.semibold(22), AppTheme.TEXT);
    }

    public static JLabel heading(String text) {
        return label(text, AppTheme.semibold(15), AppTheme.TEXT);
    }

    public static JLabel text(String text) {
        return label(text, AppTheme.font(Font.PLAIN, 13), AppTheme.TEXT);
    }

    public static JLabel strong(String text, float size) {
        return label(text, AppTheme.semibold(size), AppTheme.TEXT);
    }

    public static JLabel muted(String text) {
        return label(text, AppTheme.font(Font.PLAIN, 12.5f), AppTheme.TEXT_SECONDARY);
    }

    public static JLabel caption(String text) {
        return label(text.toUpperCase(), AppTheme.semibold(10.5f), AppTheme.TEXT_MUTED);
    }

    /** Multi-line text that wraps to the available width. */
    public static JTextArea paragraph(String text, Color color) {
        JTextArea area = new JTextArea(text);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setBorder(null);
        area.setFont(AppTheme.font(Font.PLAIN, 13));
        area.setForeground(color);
        return area;
    }

    // ---- buttons ----------------------------------------------------------

    public static JButton primary(String text, String icon) {
        JButton button = base(text, icon, Color.WHITE);
        button.putClientProperty(FlatClientProperties.STYLE,
                "background:#F26B1D; foreground:#FFFFFF; borderWidth:0; focusWidth:0; innerFocusWidth:0;"
                        + "hoverBackground:#D5570C; pressedBackground:#BD4C09; disabledBackground:#F8C6A6;"
                        + "disabledText:#FFFFFF; arc:10");
        return button;
    }

    public static JButton secondary(String text, String icon) {
        JButton button = base(text, icon, AppTheme.TEXT);
        button.putClientProperty(FlatClientProperties.STYLE,
                "background:#FFFFFF; foreground:#18213A; borderColor:#D7DEEA; hoverBorderColor:#F26B1D;"
                        + "hoverBackground:#FFF8F3; pressedBackground:#FFEFE4; focusWidth:0; arc:10");
        return button;
    }

    public static JButton danger(String text, String icon) {
        JButton button = base(text, icon, AppTheme.RED);
        button.putClientProperty(FlatClientProperties.STYLE,
                "background:#FFFFFF; foreground:#C93C3C; borderColor:#F0C9C9; hoverBorderColor:#C93C3C;"
                        + "hoverBackground:#FDF1F1; pressedBackground:#FBE3E3; focusWidth:0; arc:10");
        return button;
    }

    /** Solid red button for confirming something that cannot be undone. */
    public static JButton dangerSolid(String text, String icon) {
        JButton button = base(text, icon, Color.WHITE);
        button.putClientProperty(FlatClientProperties.STYLE,
                "background:#C93C3C; foreground:#FFFFFF; borderWidth:0; focusWidth:0; innerFocusWidth:0;"
                        + "hoverBackground:#B03232; pressedBackground:#982A2A; arc:10");
        return button;
    }

    private static JButton base(String text, String icon, Color iconColor) {
        JButton button = new JButton(text);
        button.setFont(AppTheme.semibold(13));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setFocusPainted(false);
        if (icon != null) {
            button.setIcon(Icons.get(icon, 16, iconColor));
            button.setIconTextGap(8);
        }
        return button;
    }

    // ---- layout -----------------------------------------------------------

    public static JPanel flow(int gap, Component... components) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, gap, 0));
        panel.setOpaque(false);
        for (Component component : components) {
            panel.add(component);
        }
        return panel;
    }

    public static JPanel flowRight(int gap, Component... components) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, gap, 0));
        panel.setOpaque(false);
        for (Component component : components) {
            panel.add(component);
        }
        return panel;
    }

    public static JPanel stack(Component... components) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        for (Component component : components) {
            if (component instanceof JComponent jc) {
                jc.setAlignmentX(Component.LEFT_ALIGNMENT);
            }
            panel.add(component);
        }
        return panel;
    }

    public static Component vgap(int height) {
        return Box.createRigidArea(new Dimension(0, height));
    }

    public static Component hgap(int width) {
        return Box.createRigidArea(new Dimension(width, 0));
    }

    public static <T extends JComponent> T pad(T component, int top, int left, int bottom, int right) {
        component.setBorder(new EmptyBorder(top, left, bottom, right));
        return component;
    }

    public static JPanel transparent(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }
}
