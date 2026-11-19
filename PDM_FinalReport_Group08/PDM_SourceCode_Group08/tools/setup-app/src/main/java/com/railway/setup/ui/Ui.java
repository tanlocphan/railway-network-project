package com.railway.setup.ui;

import com.railway.setup.core.Output;
import com.railway.setup.core.Tasks;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Small helpers so the pages read like a description of the screen. */
public final class Ui {

    private static final String ALLOWED = "setup.allowed";
    private static final List<AbstractButton> GUARDED = new ArrayList<>();
    private static Component dialogParent;

    static {
        Tasks.onState((title, output) -> GUARDED.forEach(Ui::applyEnabled));
    }

    private Ui() {
    }

    static void setDialogParent(Component parent) {
        dialogParent = parent;
    }

    // ----- buttons ---------------------------------------------------------

    public static JButton primary(String text, String icon) {
        JButton b = new JButton(text, icon == null ? null : Theme.icon(icon, 16, Color.WHITE));
        b.putClientProperty("FlatLaf.style", "background:#F26B1D; foreground:#FFFFFF; borderColor:#F26B1D;"
                + "hoverBackground:#D5570C; hoverBorderColor:#D5570C; pressedBackground:#C04D08;"
                + "focusedBorderColor:#D5570C; disabledBackground:#F7B58F; disabledText:#FFFFFF; font:bold");
        return guard(b);
    }

    public static JButton secondary(String text, String icon) {
        JButton b = new JButton(text, icon == null ? null : Theme.icon(icon, 16, Theme.TEXT));
        return guard(b);
    }

    public static JButton danger(String text, String icon) {
        JButton b = new JButton(text, icon == null ? null : Theme.icon(icon, 16, Theme.RED));
        b.setForeground(Theme.RED);
        b.putClientProperty("FlatLaf.style", "hoverBorderColor:#C93C3C; focusedBorderColor:#C93C3C; hoverBackground:#FCEBEB");
        return guard(b);
    }

    /** Buttons that start work are disabled while any task runs. */
    public static <B extends AbstractButton> B guard(B button) {
        GUARDED.add(button);
        button.putClientProperty(ALLOWED, Boolean.TRUE);
        applyEnabled(button);
        return button;
    }

    /** What the page wants (e.g. "Stop" only while running); the busy state comes on top. */
    public static void allow(AbstractButton button, boolean allowed) {
        button.putClientProperty(ALLOWED, allowed);
        applyEnabled(button);
    }

    private static void applyEnabled(AbstractButton button) {
        boolean allowed = !Boolean.FALSE.equals(button.getClientProperty(ALLOWED));
        button.setEnabled(allowed && !Tasks.isBusy());
    }

    // ----- text --------------------------------------------------------------

    public static JLabel label(String text, float size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(Font.PLAIN, size));
        l.setForeground(color);
        return l;
    }

    public static JLabel strong(String text, float size) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.semibold(size));
        l.setForeground(Theme.TEXT);
        return l;
    }

    public static JLabel caption(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(Theme.semibold(10.5f));
        l.setForeground(Theme.TEXT_MUTED);
        return l;
    }

    /** Wrapping paragraph. */
    public static JLabel paragraph(String text, int width) {
        JLabel l = new JLabel("<html><div style='width:" + width + "px'>" + text + "</div></html>");
        l.setFont(Theme.font(Font.PLAIN, 12.5f));
        l.setForeground(Theme.TEXT_SECONDARY);
        return l;
    }

    // ----- layout ------------------------------------------------------------

    public static JPanel row(int gap, Component... items) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, gap, 0));
        p.setOpaque(false);
        for (Component c : items) {
            p.add(c);
        }
        return p;
    }

    public static JPanel column(int gap, Component... items) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        boolean first = true;
        for (Component c : items) {
            if (!first && gap > 0) {
                p.add(Box.createRigidArea(new Dimension(0, gap)));
            }
            if (c instanceof JComponent jc) {
                jc.setAlignmentX(Component.LEFT_ALIGNMENT);
            }
            p.add(c);
            first = false;
        }
        return p;
    }

    /** Title + description stacked, as used at the top of cards. */
    public static JPanel heading(String icon, String title, String description) {
        JPanel text = column(3, strong(title, 14.5f), label(description, 12.5f, Theme.TEXT_SECONDARY));
        if (icon == null) {
            return text;
        }
        JLabel badge = iconBadge(icon, Theme.ORANGE, Theme.ORANGE_SOFT);
        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setOpaque(false);
        p.add(badge, BorderLayout.WEST);
        p.add(text, BorderLayout.CENTER);
        return p;
    }

    public static JLabel iconBadge(String icon, Color fg, Color bg) {
        JLabel badge = new JLabel(Theme.icon(icon, 18, fg)) {
            @Override
            protected void paintComponent(java.awt.Graphics graphics) {
                java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(bg);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        badge.setHorizontalAlignment(JLabel.CENTER);
        badge.setPreferredSize(new Dimension(36, 36));
        badge.setMinimumSize(new Dimension(36, 36));
        badge.setMaximumSize(new Dimension(36, 36));
        return badge;
    }

    /**
     * The command a button runs, shown in a dark chip so the team learns the
     * real CLI. Click to copy.
     */
    public static JLabel commandChip(String command) {
        JLabel chip = new JLabel() {
            @Override
            protected void paintComponent(java.awt.Graphics graphics) {
                java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(Theme.CONSOLE_BG);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        chip.setFont(Theme.mono(12f));
        chip.setForeground(Theme.CONSOLE_TEXT);
        chip.setBorder(new EmptyBorder(4, 10, 4, 10));
        chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        chip.setToolTipText("Click to copy this command");
        setChip(chip, command);
        chip.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Object raw = chip.getClientProperty("command");
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(String.valueOf(raw)), null);
                chip.setToolTipText("Copied!");
            }
        });
        return chip;
    }

    public static void setChip(JLabel chip, String command) {
        chip.putClientProperty("command", command);
        chip.setText("<html><span style='color:#7F8BAA'>$</span> " + escape(command) + "</html>");
    }

    public static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static JComponent separator() {
        JPanel line = new JPanel();
        line.setBackground(Theme.BORDER);
        line.setPreferredSize(new Dimension(1, 1));
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        line.setBorder(BorderFactory.createEmptyBorder());
        return line;
    }

    // ----- dialogs & actions ---------------------------------------------------

    public static boolean confirm(String title, String message, String yes) {
        Object[] options = {yes, "Cancel"};
        int choice = JOptionPane.showOptionDialog(dialogParent, "<html><div style='width:360px'>" + message + "</div></html>",
                title, JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
        return choice == 0;
    }

    public static void info(String title, String message) {
        JOptionPane.showMessageDialog(dialogParent, "<html><div style='width:360px'>" + message + "</div></html>",
                title, JOptionPane.INFORMATION_MESSAGE);
    }

    /** Starts a task, or explains that another one is still running. */
    public static void run(String title, Output output, com.railway.setup.core.Tasks.Body body) {
        if (!Tasks.run(title, output, body)) {
            info("Please wait", "\"" + Tasks.runningTitle() + "\" is still running.");
        }
    }

    public static void open(Path path) {
        try {
            Desktop.getDesktop().open(path.toFile());
        } catch (Exception e) {
            info("Cannot open", "Windows could not open " + path);
        }
    }

    public static void browse(String url) {
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception e) {
            info("Cannot open", "Open this address in your browser:<br>" + url);
        }
    }

    /** Work off the Swing thread, then show the result on it. */
    public static <T> void async(Supplier<T> work, Consumer<T> done) {
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() {
                return work.get();
            }

            @Override
            protected void done() {
                try {
                    done.accept(get());
                } catch (Exception ignored) {
                    // the next refresh will try again
                }
            }
        }.execute();
    }

    public static void later(Runnable r) {
        SwingUtilities.invokeLater(r);
    }
}
