package com.railway.setup.ui;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.fonts.inter.FlatInterFont;

import javax.swing.Icon;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Same palette as the Railway app: navy navigation, orange accent, light surfaces. */
public final class Theme {

    public static final Color NAVY = new Color(0x0E1B3D);
    public static final Color NAVY_HOVER = new Color(0x182A55);
    public static final Color NAVY_SELECTED = new Color(0x223A70);
    public static final Color NAVY_TEXT = new Color(0xB7C2DC);
    public static final Color NAVY_MUTED = new Color(0x6F7DA0);
    public static final Color ORANGE = new Color(0xF26B1D);
    public static final Color ORANGE_DARK = new Color(0xD5570C);
    public static final Color ORANGE_SOFT = new Color(0xFFF1E7);

    public static final Color BACKGROUND = new Color(0xF4F6FB);
    public static final Color SURFACE = Color.WHITE;
    public static final Color SURFACE_ALT = new Color(0xF8FAFD);
    public static final Color BORDER = new Color(0xE3E8F1);
    public static final Color TEXT = new Color(0x18213A);
    public static final Color TEXT_SECONDARY = new Color(0x5B6478);
    public static final Color TEXT_MUTED = new Color(0x8A93A6);

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

    // Console (dark terminal)
    public static final Color CONSOLE_BG = new Color(0x0B1430);
    public static final Color CONSOLE_BAR = new Color(0x111D40);
    public static final Color CONSOLE_TEXT = new Color(0xD3DCF0);
    public static final Color CONSOLE_DIM = new Color(0x7F8BAA);
    public static final Color CONSOLE_OK = new Color(0x5BD68F);
    public static final Color CONSOLE_WARN = new Color(0xF7C35C);
    public static final Color CONSOLE_ERROR = new Color(0xFF7A7A);
    public static final Color CONSOLE_COMMAND = new Color(0xFFA36B);

    private static String monoFamily;

    private Theme() {
    }

    public static void install() {
        // only the faces packed into SetupApp.jar (see the shade filter in pom.xml)
        FlatInterFont.installStyle(FlatInterFont.STYLE_REGULAR);
        FlatInterFont.installStyle(FlatInterFont.STYLE_BOLD);
        FlatInterFont.installStyle(FlatInterFont.STYLE_SEMIBOLD);
        FlatLaf.setPreferredFontFamily(FlatInterFont.FAMILY);
        FlatLaf.registerCustomDefaultsSource("com.railway.setup.theme");
        FlatLightLaf.setup();
        UIManager.put("defaultFont", font(Font.PLAIN, 13));
    }

    public static Font font(int style, float size) {
        return new Font(FlatInterFont.FAMILY, style, Math.round(size));
    }

    public static Font semibold(float size) {
        Font f = new Font(FlatInterFont.FAMILY_SEMIBOLD, Font.PLAIN, Math.round(size));
        return FlatInterFont.FAMILY_SEMIBOLD.equals(f.getFamily()) ? f : font(Font.BOLD, size);
    }

    public static Font mono(float size) {
        if (monoFamily == null) {
            Set<String> names = Set.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
            monoFamily = Font.MONOSPACED;
            for (String candidate : new String[]{"Cascadia Mono", "Consolas", "JetBrains Mono", "Menlo"}) {
                if (names.contains(candidate)) {
                    monoFamily = candidate;
                    break;
                }
            }
        }
        return new Font(monoFamily, Font.PLAIN, Math.round(size));
    }

    public static Icon icon(String name, int size, Color color) {
        FlatSVGIcon icon = new FlatSVGIcon("com/railway/setup/icons/" + name + ".svg", size, size);
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> color));
        return icon;
    }

    public static Icon icon(String name, int size) {
        return icon(name, size, TEXT_SECONDARY);
    }

    /** The logo on an orange rounded square, as used for the window and Setup.exe icon. */
    public static BufferedImage appIcon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setColor(ORANGE);
        int arc = Math.max(4, Math.round(size * 0.28f));
        g.fillRoundRect(0, 0, size, size, arc, arc);
        int inner = Math.round(size * 0.72f);
        FlatSVGIcon logo = new FlatSVGIcon("com/railway/setup/icons/logo.svg", inner, inner);
        logo.setColorFilter(new FlatSVGIcon.ColorFilter(c -> Color.WHITE));
        logo.paintIcon(null, g, (size - inner) / 2, (size - inner) / 2);
        // small gear badge: this is the *setup* app
        if (size >= 32) {
            int d = Math.round(size * 0.40f);
            int x = size - d - Math.max(1, size / 32);
            int y = size - d - Math.max(1, size / 32);
            g.setColor(NAVY);
            g.fillOval(x, y, d, d);
            FlatSVGIcon gear = new FlatSVGIcon("com/railway/setup/icons/wrench.svg", Math.round(d * 0.58f), Math.round(d * 0.58f));
            gear.setColorFilter(new FlatSVGIcon.ColorFilter(c -> Color.WHITE));
            gear.paintIcon(null, g, x + (d - gear.getIconWidth()) / 2, y + (d - gear.getIconHeight()) / 2);
        }
        g.dispose();
        return image;
    }

    public static List<Image> windowIcons() {
        List<Image> list = new ArrayList<>();
        for (int s : new int[]{16, 20, 24, 32, 40, 48, 64, 128, 256}) {
            list.add(appIcon(s));
        }
        return list;
    }
}
