package com.railway.setup.ui;

import com.railway.setup.core.Output;
import com.railway.setup.core.SetupLog;
import com.railway.setup.core.Tasks;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The dark terminal at the bottom of every page. Shows each command that runs
 * and everything it prints, in colour.
 */
public class Console extends JPanel implements Output {

    private static final int MAX_LINES = 5000;

    private final JTextPane text = new JTextPane();
    private final Map<Kind, SimpleAttributeSet> styles = new EnumMap<>(Kind.class);
    private final JButton cancel;
    private final List<String[]> pending = new ArrayList<>();
    private boolean flushQueued;

    public Console(String title) {
        super(new BorderLayout());
        setBackground(Theme.CONSOLE_BG);

        text.setEditable(false);
        text.setBackground(Theme.CONSOLE_BG);
        text.setForeground(Theme.CONSOLE_TEXT);
        text.setCaretColor(Theme.CONSOLE_TEXT);
        text.setFont(Theme.mono(12.5f));
        text.setBorder(new EmptyBorder(10, 14, 10, 14));
        text.setSelectionColor(new Color(0x2A3F75));
        text.setSelectedTextColor(Color.WHITE);
        ((DefaultCaret) text.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);

        style(Kind.TEXT, Theme.CONSOLE_TEXT, false);
        style(Kind.DIM, Theme.CONSOLE_DIM, false);
        style(Kind.OK, Theme.CONSOLE_OK, false);
        style(Kind.WARN, Theme.CONSOLE_WARN, false);
        style(Kind.ERROR, Theme.CONSOLE_ERROR, false);
        style(Kind.COMMAND, Theme.CONSOLE_COMMAND, false);
        style(Kind.TITLE, Color.WHITE, true);

        JScrollPane scroll = new JScrollPane(text);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.CONSOLE_BG);
        add(scroll, BorderLayout.CENTER);

        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.CONSOLE_BAR);
        bar.setBorder(new EmptyBorder(5, 14, 5, 8));
        JLabel caption = new JLabel(title.toUpperCase(), Theme.icon("terminal", 15, Theme.NAVY_TEXT), JLabel.LEFT);
        caption.setIconTextGap(8);
        caption.setFont(Theme.semibold(11f));
        caption.setForeground(Theme.NAVY_TEXT);
        bar.add(caption, BorderLayout.WEST);

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        tools.setOpaque(false);
        cancel = barButton("Cancel", "stop");
        cancel.setVisible(false);
        cancel.addActionListener(e -> Tasks.cancel());
        JButton copy = barButton("Copy", "copy");
        copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text.getText()), null));
        JButton clear = barButton("Clear", "trash");
        clear.addActionListener(e -> text.setText(""));
        tools.add(cancel);
        tools.add(copy);
        tools.add(clear);
        bar.add(tools, BorderLayout.EAST);
        add(bar, BorderLayout.NORTH);

        Tasks.onState((title1, output) -> cancel.setVisible(title1 != null && output == this));
    }

    private JButton barButton(String label, String icon) {
        JButton b = new JButton(label, Theme.icon(icon, 14, Theme.NAVY_TEXT));
        b.setFont(Theme.semibold(11.5f));
        b.setForeground(Theme.NAVY_TEXT);
        b.putClientProperty("JButton.buttonType", "borderless");
        b.putClientProperty("FlatLaf.style", "hoverBackground:#223A70; pressedBackground:#182A55; margin:3,8,3,8");
        b.setFocusable(false);
        return b;
    }

    private void style(Kind kind, Color color, boolean bold) {
        SimpleAttributeSet s = new SimpleAttributeSet();
        StyleConstants.setForeground(s, color);
        StyleConstants.setBold(s, bold);
        styles.put(kind, s);
    }

    @Override
    public void line(Kind kind, String value) {
        SetupLog.write(kind, value);
        synchronized (pending) {
            pending.add(new String[]{kind.name(), value});
            if (flushQueued) {
                return;
            }
            flushQueued = true;
        }
        SwingUtilities.invokeLater(this::flush);
    }

    /** Lines arrive in bursts (Maven prints hundreds); add them in one go. */
    private void flush() {
        List<String[]> batch;
        synchronized (pending) {
            batch = new ArrayList<>(pending);
            pending.clear();
            flushQueued = false;
        }
        StyledDocument doc = text.getStyledDocument();
        boolean atBottom = isAtBottom();
        try {
            for (String[] entry : batch) {
                Kind kind = Kind.valueOf(entry[0]);
                if (kind == Kind.TITLE && doc.getLength() > 0) {
                    doc.insertString(doc.getLength(), "\n", styles.get(Kind.TEXT));
                }
                doc.insertString(doc.getLength(), entry[1] + "\n", styles.get(kind));
            }
            Element root = doc.getDefaultRootElement();
            int extra = root.getElementCount() - MAX_LINES;
            if (extra > 0) {
                doc.remove(0, root.getElement(extra - 1).getEndOffset());
            }
        } catch (BadLocationException ignored) {
            // cannot happen with offsets taken from the document
        }
        if (atBottom) {
            text.setCaretPosition(doc.getLength());
            text.scrollRectToVisible(new java.awt.Rectangle(0, text.getHeight() - 1, 1, 1));
        }
    }

    private boolean isAtBottom() {
        if (!(text.getParent() instanceof javax.swing.JViewport viewport)) {
            return true;
        }
        java.awt.Rectangle view = viewport.getViewRect();
        return view.y + view.height >= text.getHeight() - 40;
    }
}
