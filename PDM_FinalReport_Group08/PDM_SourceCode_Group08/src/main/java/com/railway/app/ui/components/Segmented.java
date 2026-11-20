package com.railway.app.ui.components;

import com.formdev.flatlaf.FlatClientProperties;
import com.railway.app.ui.theme.AppTheme;

import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.border.EmptyBorder;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A row of options where exactly one is chosen (e.g. All / Upcoming).
 */
public class Segmented extends JPanel {

    private final List<JToggleButton> buttons = new ArrayList<>();
    private final List<String> values = new ArrayList<>();
    private final ButtonGroup group = new ButtonGroup();
    private Consumer<String> listener;

    public Segmented() {
        super(new FlowLayout(FlowLayout.LEFT, 2, 0));
        setOpaque(false);
        setBorder(new EmptyBorder(3, 3, 3, 3));
    }

    public Segmented add(String label, String value) {
        JToggleButton button = new JToggleButton(label);
        button.setFont(AppTheme.semibold(12.5f));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setFocusable(false);
        button.putClientProperty(FlatClientProperties.STYLE,
                "arc:8; borderWidth:0; focusWidth:0; margin:5,14,5,14;"
                        + "background:#EEF1F6; foreground:#5B6478; hoverBackground:#E3E8F1;"
                        + "selectedBackground:#FFFFFF; selectedForeground:#D5570C");
        button.addActionListener(e -> {
            if (listener != null) {
                listener.accept(value);
            }
        });
        group.add(button);
        buttons.add(button);
        values.add(value);
        super.add(button);
        if (buttons.size() == 1) {
            button.setSelected(true);
        }
        return this;
    }

    public void onChange(Consumer<String> listener) {
        this.listener = listener;
    }

    public String value() {
        for (int i = 0; i < buttons.size(); i++) {
            if (buttons.get(i).isSelected()) {
                return values.get(i);
            }
        }
        return null;
    }

    public void select(String value) {
        int index = values.indexOf(value);
        if (index >= 0) {
            buttons.get(index).setSelected(true);
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(AppTheme.GRAY_SOFT);
        g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
        g.dispose();
        super.paintComponent(graphics);
    }
}
