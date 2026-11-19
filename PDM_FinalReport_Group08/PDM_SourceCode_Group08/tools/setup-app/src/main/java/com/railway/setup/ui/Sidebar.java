package com.railway.setup.ui;

import com.railway.setup.core.Project;
import com.railway.setup.core.Settings;
import com.railway.setup.core.Status;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** Deep navy navigation column, like the Railway app, with a live status box. */
public class Sidebar extends JPanel {

    private final Map<String, NavItem> items = new LinkedHashMap<>();
    private final JPanel menu = new JPanel();
    private final Consumer<String> onNavigate;
    private String selected;

    private final Dot mysqlDot = new Dot();
    private final JLabel mysqlText = statusText();
    private final Dot dbDot = new Dot();
    private final JLabel dbText = statusText();
    private final Dot appDot = new Dot();
    private final JLabel appText = statusText();

    public Sidebar(Consumer<String> onNavigate) {
        super(new BorderLayout());
        this.onNavigate = onNavigate;
        setBackground(Theme.NAVY);
        setPreferredSize(new Dimension(240, 0));
        setBorder(new EmptyBorder(22, 14, 18, 14));

        add(brand(), BorderLayout.NORTH);
        menu.setOpaque(false);
        menu.setLayout(new BoxLayout(menu, BoxLayout.Y_AXIS));
        menu.setBorder(new EmptyBorder(26, 0, 0, 0));
        add(menu, BorderLayout.CENTER);
        add(statusBox(), BorderLayout.SOUTH);

        Status.listen(this::show);
    }

    public void addSection(String caption) {
        JLabel label = new JLabel(caption.toUpperCase(Locale.ENGLISH));
        label.setFont(Theme.semibold(10.5f));
        label.setForeground(Theme.NAVY_MUTED);
        label.setBorder(new EmptyBorder(menu.getComponentCount() == 0 ? 0 : 18, 12, 8, 0));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        menu.add(label);
    }

    public void addItem(String key, String text, String icon) {
        NavItem item = new NavItem(key, text, icon);
        item.setAlignmentX(Component.LEFT_ALIGNMENT);
        items.put(key, item);
        menu.add(item);
        menu.add(Box.createRigidArea(new Dimension(0, 4)));
    }

    public void select(String key) {
        selected = key;
        items.values().forEach(Component::repaint);
    }

    private JComponent brand() {
        JLabel logo = new JLabel(Theme.icon("logo", 24, Color.WHITE)) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(Theme.ORANGE);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        logo.setHorizontalAlignment(JLabel.CENTER);
        logo.setPreferredSize(new Dimension(42, 42));

        JLabel name = new JLabel("Railway Setup");
        name.setFont(Theme.semibold(17));
        name.setForeground(Color.WHITE);
        JLabel tagline = new JLabel("Clone, click, test");
        tagline.setFont(Theme.font(Font.PLAIN, 12));
        tagline.setForeground(Theme.NAVY_TEXT);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(name);
        text.add(tagline);

        JPanel brand = new JPanel(new BorderLayout(12, 0));
        brand.setOpaque(false);
        brand.setBorder(new EmptyBorder(0, 8, 0, 0));
        brand.add(logo, BorderLayout.WEST);
        brand.add(text, BorderLayout.CENTER);
        return brand;
    }

    private JComponent statusBox() {
        JPanel box = new JPanel() {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(Theme.NAVY_HOVER);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g.dispose();
            }
        };
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBorder(new EmptyBorder(12, 14, 12, 14));
        JLabel caption = new JLabel("THIS COMPUTER");
        caption.setFont(Theme.semibold(10));
        caption.setForeground(Theme.NAVY_MUTED);
        box.add(caption);
        box.add(Box.createRigidArea(new Dimension(0, 8)));
        box.add(statusLine(mysqlDot, mysqlText));
        box.add(Box.createRigidArea(new Dimension(0, 6)));
        box.add(statusLine(dbDot, dbText));
        box.add(Box.createRigidArea(new Dimension(0, 6)));
        box.add(statusLine(appDot, appText));
        box.add(Box.createRigidArea(new Dimension(0, 10)));
        JLabel folder = new JLabel(shorten(Project.root().toString()));
        folder.setToolTipText(Project.root().toString());
        folder.setFont(Theme.font(Font.PLAIN, 11));
        folder.setForeground(Theme.NAVY_MUTED);
        folder.setIcon(Theme.icon("folder", 13, Theme.NAVY_MUTED));
        folder.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(folder);
        mysqlText.setText("Checking…");
        dbText.setText("Checking…");
        appText.setText("Checking…");
        return box;
    }

    private void show(Status s) {
        String where = s.mode == Settings.Mode.PORTABLE ? "Portable MySQL" : "MySQL";
        if (!s.serverOpen) {
            mysqlDot.set(Theme.RED);
            mysqlText.setText(where + " stopped");
        } else if (!s.loginOk) {
            mysqlDot.set(Theme.CONSOLE_WARN);
            mysqlText.setText(where + ": sign-in fails");
        } else {
            mysqlDot.set(Theme.CONSOLE_OK);
            mysqlText.setText(where + " running");
        }
        if (s.databaseReady()) {
            dbDot.set(Theme.CONSOLE_OK);
            dbText.setText("Database ready");
        } else if (s.dbExists) {
            dbDot.set(Theme.CONSOLE_WARN);
            dbText.setText("Database incomplete");
        } else {
            dbDot.set(s.loginOk ? Theme.RED : Theme.NAVY_MUTED);
            dbText.setText(s.loginOk ? "No database" : "Database unknown");
        }
        if (s.appRunning) {
            appDot.set(Theme.CONSOLE_OK);
            appText.setText("Railway app open");
        } else if (s.appBuilt) {
            appDot.set(s.appOutdated ? Theme.CONSOLE_WARN : Theme.NAVY_TEXT);
            appText.setText(s.appOutdated ? "Built (code changed)" : "Built, not open");
        } else {
            appDot.set(Theme.NAVY_MUTED);
            appText.setText("Not built yet");
        }
    }

    private static String shorten(String path) {
        return path.length() <= 30 ? path : "…" + path.substring(path.length() - 29);
    }

    private static JLabel statusText() {
        JLabel l = new JLabel();
        l.setFont(Theme.font(Font.PLAIN, 12.5f));
        l.setForeground(Color.WHITE);
        return l;
    }

    private static JComponent statusLine(Dot dot, JLabel text) {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setOpaque(false);
        p.add(dot, BorderLayout.WEST);
        p.add(text, BorderLayout.CENTER);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        return p;
    }

    private static final class Dot extends JComponent {
        private Color color = Theme.NAVY_MUTED;

        Dot() {
            setPreferredSize(new Dimension(10, 18));
        }

        void set(Color c) {
            color = c;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color);
            g.fillOval(0, (getHeight() - 9) / 2, 9, 9);
            g.dispose();
        }
    }

    private final class NavItem extends JComponent {
        private final String key;
        private final String text;
        private final Icon normalIcon;
        private final Icon activeIcon;
        private boolean hover;

        NavItem(String key, String text, String icon) {
            this.key = key;
            this.text = text;
            this.normalIcon = Theme.icon(icon, 19, Theme.NAVY_TEXT);
            this.activeIcon = Theme.icon(icon, 19, Theme.ORANGE);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(212, 42));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            setFont(Theme.semibold(13.5f));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    onNavigate.accept(NavItem.this.key);
                }
            });
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean active = key.equals(selected);
            if (active) {
                g.setColor(Theme.NAVY_SELECTED);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g.setColor(Theme.ORANGE);
                g.fillRoundRect(0, 10, 4, getHeight() - 20, 4, 4);
            } else if (hover) {
                g.setColor(Theme.NAVY_HOVER);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            }
            Icon icon = active ? activeIcon : normalIcon;
            icon.paintIcon(this, g, 16, (getHeight() - icon.getIconHeight()) / 2);
            g.setFont(getFont());
            g.setColor(active || hover ? Color.WHITE : Theme.NAVY_TEXT);
            int textY = (getHeight() + g.getFontMetrics().getAscent() - g.getFontMetrics().getDescent()) / 2;
            g.drawString(text, 48, textY);
            g.dispose();
        }
    }
}
