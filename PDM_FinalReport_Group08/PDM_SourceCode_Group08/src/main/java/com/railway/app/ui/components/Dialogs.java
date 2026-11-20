package com.railway.app.ui.components;

import com.railway.app.data.AppException;
import com.railway.app.ui.theme.AppTheme;
import com.railway.app.ui.theme.Icons;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.KeyEvent;

/**
 * Friendly message and confirmation windows.
 */
public final class Dialogs {

    private Dialogs() {
    }

    public static void error(Component owner, AppException problem) {
        String icon = problem.kind() == AppException.Kind.RULE ? "info" : "alert";
        Color color = problem.kind() == AppException.Kind.RULE ? AppTheme.AMBER : AppTheme.RED;
        Color soft = problem.kind() == AppException.Kind.RULE ? AppTheme.AMBER_SOFT : AppTheme.RED_SOFT;
        show(owner, problem.title(), problem.getMessage(), icon, color, soft, "OK", null, false);
    }

    public static void info(Component owner, String title, String message) {
        show(owner, title, message, "info", AppTheme.BLUE, AppTheme.BLUE_SOFT, "OK", null, false);
    }

    /** Returns true when the user confirms. */
    public static boolean confirm(Component owner, String title, String message, String confirmText, boolean danger) {
        return show(owner, title, message, danger ? "alert" : "info",
                danger ? AppTheme.RED : AppTheme.ORANGE, danger ? AppTheme.RED_SOFT : AppTheme.ORANGE_SOFT,
                confirmText, "Keep it", danger);
    }

    private static boolean show(Component owner, String title, String message, String icon, Color color,
                                Color soft, String okText, String cancelText, boolean danger) {
        Window window = owner == null ? null : (owner instanceof Window w ? w : SwingUtilities.getWindowAncestor(owner));
        JDialog dialog = new JDialog(window, title, java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        boolean[] confirmed = {false};

        JPanel content = new JPanel(new BorderLayout(18, 0));
        content.setBackground(AppTheme.SURFACE);
        content.setBorder(new EmptyBorder(24, 24, 18, 24));

        JLabel badge = new JLabel(Icons.get(icon, 22, color)) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(soft);
                g.fillOval(0, 0, getWidth(), getHeight());
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        badge.setPreferredSize(new Dimension(44, 44));
        JPanel badgeHolder = Ui.transparent(new BorderLayout());
        badgeHolder.add(badge, BorderLayout.NORTH);
        content.add(badgeHolder, BorderLayout.WEST);

        JComponent text = Ui.stack(
                Ui.strong(title, 16),
                Ui.vgap(6),
                widthLimited(Ui.paragraph(message, AppTheme.TEXT_SECONDARY), 340));
        content.add(text, BorderLayout.CENTER);

        JButton ok = danger ? Ui.dangerSolid(okText, null) : Ui.primary(okText, null);
        ok.addActionListener(e -> {
            confirmed[0] = true;
            dialog.dispose();
        });
        JPanel buttons;
        if (cancelText != null) {
            JButton cancel = Ui.secondary(cancelText, null);
            cancel.addActionListener(e -> dialog.dispose());
            buttons = Ui.flowRight(10, cancel, ok);
        } else {
            buttons = Ui.flowRight(10, ok);
        }
        buttons.setBorder(new EmptyBorder(20, 0, 0, 0));
        content.add(buttons, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(ok);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setResizable(false);
        dialog.pack();
        dialog.setLocationRelativeTo(window);
        dialog.setVisible(true);
        return confirmed[0];
    }

    private static JComponent widthLimited(JComponent component, int width) {
        component.setSize(new Dimension(width, Short.MAX_VALUE));
        Dimension preferred = component.getPreferredSize();
        component.setPreferredSize(new Dimension(width, preferred.height));
        return component;
    }
}
