package com.railway.app.ui.components;

import com.formdev.flatlaf.FlatClientProperties;
import com.github.lgooddatepicker.components.DatePicker;
import com.github.lgooddatepicker.components.DatePickerSettings;
import com.github.lgooddatepicker.components.TimePicker;
import com.github.lgooddatepicker.components.TimePickerSettings;
import com.railway.app.ui.theme.AppTheme;
import com.railway.app.ui.theme.Icons;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Factory for the styled input controls used in forms.
 */
public final class Inputs {

    private Inputs() {
    }

    public static JTextField text(String placeholder) {
        JTextField field = new JTextField(20);
        field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        field.setPreferredSize(new Dimension(280, 36));
        return field;
    }

    /** Amount in VND, shown with thousands separators and a "₫" suffix. */
    public static JTextField money() {
        JTextField field = new JTextField(12);
        field.setHorizontalAlignment(SwingConstants.RIGHT);
        field.setPreferredSize(new Dimension(200, 36));
        JLabel suffix = new JLabel("₫  ");
        suffix.setForeground(AppTheme.TEXT_MUTED);
        field.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, suffix);
        field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Enter the fare");
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                BigDecimal value = Formats.parseMoney(field.getText());
                if (value != null) {
                    field.setText(Formats.number(value));
                }
            }
        });
        return field;
    }

    public static void setMoney(JTextField field, BigDecimal amount) {
        field.setText(amount == null ? "" : Formats.number(amount));
    }

    public static JSpinner number(int value, int min, int max) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, 1));
        spinner.setPreferredSize(new Dimension(120, 36));
        return spinner;
    }

    public static DatePicker date(LocalDate initial) {
        DatePickerSettings settings = new DatePickerSettings(Locale.ENGLISH);
        settings.setFormatForDatesCommonEra(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH));
        settings.setFontValidDate(AppTheme.font(Font.PLAIN, 13));
        settings.setColor(DatePickerSettings.DateArea.CalendarBackgroundSelectedDate, AppTheme.ORANGE);
        settings.setColor(DatePickerSettings.DateArea.CalendarBorderSelectedDate, AppTheme.ORANGE_DARK);
        settings.setColor(DatePickerSettings.DateArea.BackgroundMonthAndYearMenuLabels, AppTheme.SURFACE);
        settings.setColor(DatePickerSettings.DateArea.BackgroundTodayLabel, AppTheme.SURFACE);
        settings.setColor(DatePickerSettings.DateArea.BackgroundClearLabel, AppTheme.SURFACE);
        settings.setColor(DatePickerSettings.DateArea.CalendarBackgroundNormalDates, AppTheme.SURFACE);
        settings.setColor(DatePickerSettings.DateArea.BackgroundOverallCalendarPanel, AppTheme.SURFACE);
        settings.setColor(DatePickerSettings.DateArea.CalendarTextWeekdays, AppTheme.TEXT_SECONDARY);
        settings.setColor(DatePickerSettings.DateArea.BackgroundCalendarPanelLabelsOnHover, AppTheme.ORANGE_SOFT);
        settings.setColor(DatePickerSettings.DateArea.TextFieldBackgroundValidDate, AppTheme.SURFACE);
        DatePicker picker = new DatePicker(settings);
        settings.setAllowEmptyDates(false);
        picker.setDate(initial == null ? LocalDate.now() : initial);
        JButton toggle = picker.getComponentToggleCalendarButton();
        toggle.setText("");
        toggle.setIcon(Icons.get("calendar", 16, AppTheme.TEXT_SECONDARY));
        toggle.setPreferredSize(new Dimension(38, 36));
        picker.getComponentDateTextField().setPreferredSize(new Dimension(170, 36));
        return picker;
    }

    /** 24-hour time picker; leaving it empty means "no time at this stop". */
    public static TimePicker time() {
        TimePickerSettings settings = new TimePickerSettings(Locale.ENGLISH);
        DateTimeFormatter format = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
        settings.setFormatForDisplayTime(format);
        settings.setFormatForMenuTimes(format);
        settings.generatePotentialMenuTimes(TimePickerSettings.TimeIncrement.FifteenMinutes, null, null);
        settings.setAllowEmptyTimes(true);
        settings.setInitialTimeToNow();
        settings.fontValidTime = AppTheme.font(Font.PLAIN, 13);
        TimePicker picker = new TimePicker(settings);
        picker.setTime(null);
        JButton toggle = picker.getComponentToggleTimeMenuButton();
        toggle.setText("");
        toggle.setIcon(Icons.get("clock", 16, AppTheme.TEXT_SECONDARY));
        toggle.setPreferredSize(new Dimension(38, 36));
        picker.getComponentTimeTextField().setPreferredSize(new Dimension(90, 36));
        picker.getComponentTimeTextField().putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "none");
        return picker;
    }
}
