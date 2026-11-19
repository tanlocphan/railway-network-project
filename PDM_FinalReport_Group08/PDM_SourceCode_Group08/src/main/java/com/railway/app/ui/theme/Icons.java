package com.railway.app.ui.theme;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.Icon;
import java.awt.Color;

/**
 * Line icons drawn for this application (resources/com/railway/app/icons).
 */
public final class Icons {

    private Icons() {
    }

    public static Icon get(String name, int size, Color color) {
        FlatSVGIcon icon = new FlatSVGIcon("com/railway/app/icons/" + name + ".svg", size, size);
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(original -> color));
        return icon;
    }

    public static Icon get(String name, int size) {
        return get(name, size, AppTheme.TEXT_SECONDARY);
    }
}
