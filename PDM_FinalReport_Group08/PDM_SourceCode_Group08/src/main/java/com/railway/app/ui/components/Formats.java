package com.railway.app.ui.components;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Turns values into the text shown on screen.
 */
public final class Formats {

    public static final String DASH = "—";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);

    private Formats() {
    }

    public static String money(BigDecimal amount) {
        if (amount == null) {
            return DASH;
        }
        return number(amount) + " ₫";
    }

    public static String number(BigDecimal amount) {
        if (amount == null) {
            return DASH;
        }
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMaximumFractionDigits(amount.stripTrailingZeros().scale() > 0 ? 2 : 0);
        format.setMinimumFractionDigits(0);
        return format.format(amount);
    }

    public static String count(Integer value) {
        return value == null ? DASH : NumberFormat.getIntegerInstance(Locale.US).format(value);
    }

    /** Compact money for chart axes and tiles: 4.1M, 550K. */
    public static String compactMoney(BigDecimal amount) {
        if (amount == null) {
            return DASH;
        }
        BigDecimal million = new BigDecimal(1_000_000);
        BigDecimal thousand = new BigDecimal(1_000);
        if (amount.abs().compareTo(million) >= 0) {
            return amount.divide(million, 1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "M ₫";
        }
        if (amount.abs().compareTo(thousand) >= 0) {
            return amount.divide(thousand, 0, RoundingMode.HALF_UP).toPlainString() + "K ₫";
        }
        return money(amount);
    }

    public static String date(LocalDate date) {
        return date == null ? DASH : DATE.format(date);
    }

    public static String shortDate(LocalDate date) {
        return date == null ? DASH : SHORT_DATE.format(date);
    }

    public static String time(LocalTime time) {
        return time == null ? DASH : TIME.format(time);
    }

    public static String dateTime(LocalDateTime dateTime) {
        return dateTime == null ? DASH : DATE_TIME.format(dateTime);
    }

    public static String dayOffset(Integer days) {
        if (days == null || days <= 0) {
            return "";
        }
        return days == 1 ? "+1 day" : "+" + days + " days";
    }

    public static String duration(Integer minutes) {
        if (minutes == null) {
            return DASH;
        }
        int hours = minutes / 60;
        int rest = minutes % 60;
        if (hours == 0) {
            return rest + "m";
        }
        return String.format("%dh %02dm", hours, rest);
    }

    public static String km(Integer km) {
        return km == null ? DASH : NumberFormat.getIntegerInstance(Locale.US).format(km) + " km";
    }

    public static String html(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Parses what staff typed into a money field ("550,000" -> 550000). */
    public static BigDecimal parseMoney(String text) {
        if (text == null) {
            return null;
        }
        String digits = text.replaceAll("[^0-9.]", "");
        if (digits.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Integer parseInteger(String text) {
        if (text == null) {
            return null;
        }
        String digits = text.replaceAll("[^0-9-]", "");
        if (digits.isEmpty() || "-".equals(digits)) {
            return null;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
