package com.railway.app.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One row returned by a routine, addressed by column label.
 */
public final class Row {

    private final Map<String, Object> values;

    public Row(Map<String, Object> values) {
        this.values = new LinkedHashMap<>(values);
    }

    public Map<String, Object> asMap() {
        return Collections.unmodifiableMap(values);
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    public Object get(String key) {
        return values.get(key);
    }

    public String str(String key) {
        Object value = values.get(key);
        return value == null ? null : value.toString();
    }

    public String str(String key, String fallback) {
        String value = str(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    public Integer integer(String key) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof Boolean flag) {
            return flag ? 1 : 0;
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public int integer(String key, int fallback) {
        Integer value = integer(key);
        return value == null ? fallback : value;
    }

    public boolean flag(String key) {
        Integer value = integer(key);
        return value != null && value != 0;
    }

    public BigDecimal decimal(String key) {
        Object value = values.get(key);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return null;
    }

    public LocalDate date(String key) {
        Object value = values.get(key);
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toLocalDate();
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return null;
    }

    public LocalTime time(String key) {
        Object value = values.get(key);
        if (value instanceof LocalTime time) {
            return time;
        }
        if (value instanceof java.sql.Time sqlTime) {
            return sqlTime.toLocalTime();
        }
        return null;
    }

    public LocalDateTime dateTime(String key) {
        Object value = values.get(key);
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
