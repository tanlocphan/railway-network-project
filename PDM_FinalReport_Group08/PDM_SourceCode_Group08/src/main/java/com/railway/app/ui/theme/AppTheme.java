package com.railway.app.ui.theme;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.fonts.inter.FlatInterFont;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;

/**
 * Colours and fonts of the application: deep navy navigation, warm orange
 * accent, light surfaces.
 */
public final class AppTheme {

    // Brand
    public static final Color NAVY = new Color(0x0E1B3D);
    public static final Color NAVY_HOVER = new Color(0x182A55);
    public static final Color NAVY_SELECTED = new Color(0x223A70);
    public static final Color NAVY_TEXT = new Color(0xB7C2DC);
    public static final Color ORANGE = new Color(0xF26B1D);
    public static final Color ORANGE_DARK = new Color(0xD5570C);
    public static final Color ORANGE_SOFT = new Color(0xFFF1E7);

    // Surfaces and text
    public static final Color BACKGROUND = new Color(0xF4F6FB);
    public static final Color SURFACE = Color.WHITE;
    public static final Color SURFACE_ALT = new Color(0xF8FAFD);
    public static final Color BORDER = new Color(0xE3E8F1);
    public static final Color TEXT = new Color(0x18213A);
    public static final Color TEXT_SECONDARY = new Color(0x5B6478);
    public static final Color TEXT_MUTED = new Color(0x8A93A6);

    // Status colours (always shown together with a text label)
    public static final Color GREEN = new Color(0x18864B);
    public static final Color GREEN_SOFT = new Color(0xE5F5EC);
    public static final Color RED = new Color(0xC93C3C);
    public static final Color RED_SOFT = new Color(0xFCEBEB);
    public static final Color BLUE = new Color(0x2B63D0);
    public static final Color BLUE_SOFT = new Color(0xE9F0FD);
    public static final Color AMBER = new Color(0xA86A00);
    public static final Color AMBER_SOFT = new Color(0xFFF4D9);
    public static final Color GRAY = new Color(0x5B6478);
    public static final Color GRAY_SOFT = new Color(0xEDF0F5);

    // Chart (single series)
    public static final Color CHART_BAR = new Color(0x2F5BB7);
    public static final Color CHART_BAR_HOVER = new Color(0x1E438F);
    public static final Color CHART_GRID = new Color(0xEDF0F5);

    public static final String FONT_FAMILY = FlatInterFont.FAMILY;

    private AppTheme() {
    }

    public static void install() {
        FlatInterFont.install();
        FlatInterFont.installSemiBold();
        FlatLaf.setPreferredFontFamily(FlatInterFont.FAMILY);
        FlatLaf.registerCustomDefaultsSource("com.railway.app.ui.theme");
        FlatLightLaf.setup();
        UIManager.put("defaultFont", font(Font.PLAIN, 13));
    }

    public static Font font(int style, float size) {
        return new Font(FONT_FAMILY, style, Math.round(size));
    }

    /** Semibold Inter, falling back to bold if the semibold face is missing. */
    public static Font semibold(float size) {
        Font semibold = new Font(FlatInterFont.FAMILY_SEMIBOLD, Font.PLAIN, Math.round(size));
        if (FlatInterFont.FAMILY_SEMIBOLD.equals(semibold.getFamily())) {
            return semibold;
        }
        return font(Font.BOLD, size);
    }

    public static String hex(Color color) {
        return String.format("#%06X", color.getRGB() & 0xFFFFFF);
    }
}
