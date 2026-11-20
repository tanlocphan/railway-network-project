package com.railway.app.service;

import com.railway.app.data.AppException;
import com.railway.app.data.Database;
import com.railway.app.data.Routine;
import com.railway.app.data.Row;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;

/**
 * Runs the report routines.
 */
public final class ReportService {

    private ReportService() {
    }

    public static List<Row> run(Routine routine, Object... args) throws AppException {
        return Database.call(routine, args);
    }

    /** Uses the project's existing function fn_get_passenger_fare. */
    public static BigDecimal fareOnDate(String passengerId, LocalDate date) throws AppException {
        Object value = Database.function(Routine.FN_GET_PASSENGER_FARE, Types.DECIMAL, passengerId, date);
        return value instanceof BigDecimal decimal ? decimal : null;
    }

    public static String lineDescription(String from, String to) throws AppException {
        Object value = Database.function(Routine.FN_LINE_DESCRIPTION, Types.VARCHAR, from, to);
        return value == null ? null : value.toString();
    }
}
