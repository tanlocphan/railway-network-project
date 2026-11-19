package com.railway.setup.ui;

import javax.swing.JLabel;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Small rounded status label: the colour always comes with words. */
public class Pill extends JLabel {

    public enum Tone {
        OK(Theme.GREEN, Theme.GREEN_SOFT),
        WARN(Theme.AMBER, Theme.AMBER_SOFT),
        ERROR(Theme.RED, Theme.RED_SOFT),
        INFO(Theme.BLUE, Theme.BLUE_SOFT),
        NEUTRAL(Theme.GRAY, Theme.GRAY_SOFT),
        ACCENT(Theme.ORANGE_DARK, Theme.ORANGE_SOFT);

        final Color fg;
        final Color bg;

        Tone(Color fg, Color bg) {
            this.fg = fg;
            this.bg = bg;
        }
    }

    private Tone tone = Tone.NEUTRAL;

    public Pill() {
        this("…", Tone.NEUTRAL);
    }

    public Pill(String text, Tone tone) {
        setFont(Theme.semibold(11.5f));
        setBorder(new EmptyBorder(3, 10, 3, 10));
        setOpaque(false);
        set(text, tone);
    }

    public final void set(String text, Tone newTone) {
        tone = newTone;
        setText(text);
        setForeground(tone.fg);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(tone.bg);
        g.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
        g.dispose();
        super.paintComponent(graphics);
    }
}
