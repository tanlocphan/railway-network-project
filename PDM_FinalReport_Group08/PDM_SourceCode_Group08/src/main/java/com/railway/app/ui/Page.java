package com.railway.app.ui;

import com.railway.app.ui.theme.AppTheme;

import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;

/**
 * One screen reachable from the side menu.
 */
public abstract class Page extends JPanel {

    private final String title;
    private final String subtitle;

    protected Page(String title, String subtitle) {
        super(new BorderLayout(0, 16));
        this.title = title;
        this.subtitle = subtitle;
        setBackground(AppTheme.BACKGROUND);
        setBorder(new EmptyBorder(4, 28, 24, 28));
    }

    public String title() {
        return title;
    }

    public String subtitle() {
        return subtitle;
    }

    /** Called every time the page becomes visible; reloads its data. */
    public abstract void refresh();
}
