package com.railway.app.ui.components;

import com.railway.app.ui.theme.AppTheme;

import java.awt.Color;

/**
 * Colours for the status words produced by the booking system.
 * The word itself is always shown, so colour is never the only signal.
 */
public record StatusStyle(Color foreground, Color background) {

    private static final StatusStyle GOOD = new StatusStyle(AppTheme.GREEN, AppTheme.GREEN_SOFT);
    private static final StatusStyle INFO = new StatusStyle(AppTheme.BLUE, AppTheme.BLUE_SOFT);
    private static final StatusStyle WARN = new StatusStyle(AppTheme.AMBER, AppTheme.AMBER_SOFT);
    private static final StatusStyle BAD = new StatusStyle(AppTheme.RED, AppTheme.RED_SOFT);
    private static final StatusStyle ACTIVE = new StatusStyle(AppTheme.ORANGE_DARK, AppTheme.ORANGE_SOFT);
    private static final StatusStyle NEUTRAL = new StatusStyle(AppTheme.GRAY, AppTheme.GRAY_SOFT);
    private static final StatusStyle INDIGO = new StatusStyle(new Color(0x4B3FB0), new Color(0xEEECFB));

    public static StatusStyle of(String status) {
        if (status == null) {
            return NEUTRAL;
        }
        return switch (status) {
            case "On sale", "Available", "Paid" -> GOOD;
            case "Upcoming", "Scheduled", "Local", "Origin", "Destination" -> INFO;
            case "No seats set up", "Timetable incomplete" -> WARN;
            case "Timetable needs attention", "Sold out", "No payment record" -> BAD;
            case "On the way", "Express" -> ACTIVE;
            case "Night" -> INDIGO;
            default -> NEUTRAL;
        };
    }
}
