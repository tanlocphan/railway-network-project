package com.railway.app.ui;

import com.railway.app.ui.theme.AppTheme;
import com.railway.app.ui.theme.Icons;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Deep navy navigation column on the left of the window.
 */
public class Sidebar extends JPanel {

    private final Map<String, NavItem> items = new LinkedHashMap<>();
    private final JPanel menu = new JPanel();
    private final Consumer<String> onNavigate;
    private String selectedKey;

    public Sidebar(Consumer<String> onNavigate) {
        super(new BorderLayout());
        this.onNavigate = onNavigate;
        setBackground(AppTheme.NAVY);
        setPreferredSize(new Dimension(248, 0));
        setBorder(new EmptyBorder(22, 14, 18, 14));

        add(brand(), BorderLayout.NORTH);

        menu.setOpaque(false);
        menu.setLayout(new BoxLayout(menu, BoxLayout.Y_AXIS));
        menu.setBorder(new EmptyBorder(26, 0, 0, 0));
        add(menu, BorderLayout.CENTER);

        add(clock(), BorderLayout.SOUTH);
    }

    public void addSection(String caption) {
        JLabel label = new JLabel(caption.toUpperCase(Locale.ENGLISH));
        label.setFont(AppTheme.semibold(10.5f));
        label.setForeground(new Color(0x6F7DA0));
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
        selectedKey = key;
        items.values().forEach(Component::repaint);
    }

    private JComponent brand() {
        JLabel logo = new JLabel(Icons.get("logo", 24, Color.WHITE)) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(AppTheme.ORANGE);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        logo.setHorizontalAlignment(JLabel.CENTER);
        logo.setPreferredSize(new Dimension(42, 42));

        JLabel name = new JLabel("Railway");
        name.setFont(AppTheme.semibold(17));
        name.setForeground(Color.WHITE);
        JLabel tagline = new JLabel("Network & Timetable");
        tagline.setFont(AppTheme.font(Font.PLAIN, 12));
        tagline.setForeground(AppTheme.NAVY_TEXT);

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

    private JComponent clock() {
        JLabel day = new JLabel();
        day.setFont(AppTheme.font(Font.PLAIN, 12));
        day.setForeground(AppTheme.NAVY_TEXT);
        JLabel time = new JLabel();
        time.setFont(AppTheme.semibold(22));
        time.setForeground(Color.WHITE);

        DateTimeFormatter dayFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);
        DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
        Runnable update = () -> {
            LocalDateTime now = LocalDateTime.now();
            day.setText(dayFormat.format(now));
            time.setText(timeFormat.format(now));
        };
        update.run();
        Timer timer = new Timer(10_000, e -> update.run());
        timer.start();

        JPanel box = new JPanel() {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(AppTheme.NAVY_HOVER);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g.dispose();
            }
        };
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBorder(new EmptyBorder(12, 14, 12, 14));
        JLabel caption = new JLabel("LOCAL TIME");
        caption.setFont(AppTheme.semibold(10));
        caption.setForeground(new Color(0x6F7DA0));
        box.add(caption);
        box.add(Box.createRigidArea(new Dimension(0, 4)));
        box.add(time);
        box.add(day);
        return box;
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
            this.normalIcon = Icons.get(icon, 19, AppTheme.NAVY_TEXT);
            this.activeIcon = Icons.get(icon, 19, AppTheme.ORANGE);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(220, 42));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            setFont(AppTheme.semibold(13.5f));
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
            boolean active = key.equals(selectedKey);
            if (active) {
                g.setColor(AppTheme.NAVY_SELECTED);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g.setColor(AppTheme.ORANGE);
                g.fillRoundRect(0, 10, 4, getHeight() - 20, 4, 4);
            } else if (hover) {
                g.setColor(AppTheme.NAVY_HOVER);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            }
            Icon icon = active ? activeIcon : normalIcon;
            int iconY = (getHeight() - icon.getIconHeight()) / 2;
            icon.paintIcon(this, g, 16, iconY);
            g.setFont(getFont());
            g.setColor(active ? Color.WHITE : (hover ? Color.WHITE : AppTheme.NAVY_TEXT));
            int textY = (getHeight() + g.getFontMetrics().getAscent() - g.getFontMetrics().getDescent()) / 2;
            g.drawString(text, 48, textY);
            g.dispose();
        }
    }
}
