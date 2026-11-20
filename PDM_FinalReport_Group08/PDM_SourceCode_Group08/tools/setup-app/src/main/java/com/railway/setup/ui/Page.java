package com.railway.setup.ui;

import com.railway.setup.core.Status;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.Scrollable;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Rectangle;

/**
 * One screen: a header, a scrolling column of cards, and the console docked
 * at the bottom (drag the divider to give it more room).
 */
public abstract class Page extends JPanel {

    protected final Console console;
    protected final JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    private final JLabel subtitle;
    private final ColumnPanel content = new ColumnPanel();

    protected Page(String title, String subtitleText, String consoleTitle) {
        super(new BorderLayout());
        setBackground(Theme.BACKGROUND);

        JLabel heading = new JLabel(title);
        heading.setFont(Theme.semibold(24));
        heading.setForeground(Theme.TEXT);
        subtitle = Ui.label(subtitleText, 13.5f, Theme.TEXT_SECONDARY);
        JPanel titles = Ui.column(4, heading, subtitle);

        actions.setOpaque(false);
        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(26, 30, 18, 30));
        header.add(titles, BorderLayout.CENTER);
        header.add(actions, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(0, 30, 24, 30));
        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(18);

        if (consoleTitle == null) {
            // a page without console: the content fills the space (e.g. the log viewer)
            console = null;
            content.setLayout(new BorderLayout());
            add(content, BorderLayout.CENTER);
            return;
        }
        console = new Console(consoleTitle);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scroll, console);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setResizeWeight(1.0);
        split.setDividerSize(6);
        split.setContinuousLayout(true);
        console.setPreferredSize(new Dimension(100, 230));
        console.setMinimumSize(new Dimension(100, 60));
        scroll.setMinimumSize(new Dimension(100, 120));
        // small preferred height: the split then gives the console its 230 px
        scroll.setPreferredSize(new Dimension(100, 300));
        add(split, BorderLayout.CENTER);
    }

    /** For pages without console: the one component that fills the page. */
    protected void fill(JComponent body) {
        content.removeAll();
        content.add(body, BorderLayout.CENTER);
    }

    protected void setSubtitle(String text) {
        subtitle.setText(text);
    }

    /** Adds a block to the column, with a gap above it. */
    protected void block(JComponent block) {
        if (content.getComponentCount() > 0) {
            content.add(Box.createRigidArea(new Dimension(0, 16)));
        }
        block.setAlignmentX(Component.LEFT_ALIGNMENT);
        block.setMaximumSize(new Dimension(Integer.MAX_VALUE, block.getMaximumSize().height));
        content.add(block);
    }

    protected void addSection(String caption) {
        JLabel l = Ui.caption(caption);
        l.setBorder(new EmptyBorder(content.getComponentCount() == 0 ? 0 : 10, 2, 0, 0));
        block(l);
    }

    /** Cards side by side, sharing the width equally. */
    protected static JPanel grid(int columns, JComponent... cards) {
        JPanel row = new JPanel(new GridLayout(0, columns, 16, 16));
        row.setOpaque(false);
        for (JComponent c : cards) {
            row.add(c);
        }
        return row;
    }

    protected static JLabel small(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(Font.PLAIN, 12));
        l.setForeground(Theme.TEXT_MUTED);
        return l;
    }

    /** Called with every fresh status snapshot (on the Swing thread). */
    protected abstract void showStatus(Status status);

    /** Starts receiving status snapshots (called once the page is fully built). */
    public final void listen() {
        Status.listen(this::showStatus);
    }

    /** Called when the page becomes visible. */
    public void shown() {
    }

    /** A vertical panel that always takes the viewport width (no sideways scrolling). */
    private static final class ColumnPanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 18;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return visibleRect.height - 40;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
