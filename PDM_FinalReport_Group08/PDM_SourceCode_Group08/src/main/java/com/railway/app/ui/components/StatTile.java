package com.railway.app.ui.components;

import com.railway.app.ui.theme.AppTheme;
import com.railway.app.ui.theme.Icons;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * A headline number with a caption, e.g. "Tickets sold  27".
 */
public class StatTile extends Card {

    private final JLabel value = Ui.label("—", AppTheme.semibold(26), AppTheme.TEXT);
    private final JLabel note = Ui.muted(" ");

    public StatTile(String caption, String icon) {
        super(new BorderLayout(14, 0), 18);
        JLabel badge = new JLabel(Icons.get(icon, 20, AppTheme.ORANGE), SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(AppTheme.ORANGE_SOFT);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        badge.setPreferredSize(new Dimension(42, 42));
        JPanel badgeHolder = Ui.transparent(new BorderLayout());
        badgeHolder.add(badge, BorderLayout.NORTH);
        add(badgeHolder, BorderLayout.WEST);
        add(Ui.stack(Ui.caption(caption), Ui.vgap(6), value, Ui.vgap(2), note), BorderLayout.CENTER);
    }

    public void set(String valueText, String noteText) {
        value.setText(valueText);
        note.setText(noteText == null || noteText.isEmpty() ? " " : noteText);
    }
}
