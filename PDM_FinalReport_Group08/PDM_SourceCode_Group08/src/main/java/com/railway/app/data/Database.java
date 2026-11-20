package com.railway.app.data;

import com.railway.app.config.AppConfig;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLTransientConnectionException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The only class that talks to MySQL. It does exactly two things:
 * call a stored procedure and read its result, or call a stored function
 * and read its value. All business rules live in the database routines.
 */
public final class Database {

    private static final Logger LOG = Logger.getLogger(Database.class.getName());

    private static final String CONNECTION_MESSAGE =
            "We can't reach the booking system right now. Please make sure it is running, "
                    + "then try again. If this keeps happening, contact your administrator.";
    private static final String SETUP_MESSAGE =
            "The booking system has not been fully set up yet. Please contact your administrator.";
    private static final String UNEXPECTED_MESSAGE =
            "Something went wrong and nothing was changed. Please try again. "
                    + "If this keeps happening, contact your administrator.";

    private Database() {
    }

    /** Calls a stored procedure and returns the rows of its first result. */
    public static List<Row> call(Routine routine, Object... args) throws AppException {
        if (routine.isFunction()) {
            throw new IllegalArgumentException(routine + " is a function; use function()");
        }
        String sql = "{call " + routine.routineName() + placeholders(args.length) + "}";
        long started = System.nanoTime();
        try (Connection connection = open();
             CallableStatement statement = connection.prepareCall(sql)) {
            bind(statement, 1, args);
            boolean hasResult = statement.execute();
            List<Row> rows = new ArrayList<>();
            boolean captured = false;
            while (true) {
                if (hasResult) {
                    try (ResultSet resultSet = statement.getResultSet()) {
                        if (!captured) {
                            rows = readAll(resultSet);
                            captured = true;
                        }
                    }
                } else if (statement.getUpdateCount() == -1) {
                    break;
                }
                hasResult = statement.getMoreResults();
            }
            int count = rows.size();
            LOG.fine(() -> String.format("CALL %s -> %d row(s) in %d ms",
                    routine.routineName(), count, (System.nanoTime() - started) / 1_000_000));
            return rows;
        } catch (SQLException e) {
            throw translate(routine, e);
        }
    }

    /** Calls a stored function and returns its value. */
    public static Object function(Routine routine, int sqlType, Object... args) throws AppException {
        if (!routine.isFunction()) {
            throw new IllegalArgumentException(routine + " is a procedure; use call()");
        }
        String sql = "{? = call " + routine.routineName() + placeholders(args.length) + "}";
        try (Connection connection = open();
             CallableStatement statement = connection.prepareCall(sql)) {
            statement.registerOutParameter(1, sqlType);
            bind(statement, 2, args);
            statement.execute();
            Object value = statement.getObject(1);
            LOG.fine(() -> "CALL " + routine.routineName() + " (function)");
            return value;
        } catch (SQLException e) {
            throw translate(routine, e);
        }
    }

    /** Opens and closes a connection, used by the start-up check. */
    public static void ping() throws AppException {
        call(Routine.TRAIN_TYPE_LIST);
    }

    private static Connection open() throws SQLException {
        AppConfig config = AppConfig.get();
        return DriverManager.getConnection(config.jdbcUrl(), config.user(), config.password());
    }

    private static String placeholders(int count) {
        StringJoiner joiner = new StringJoiner(", ", "(", ")");
        for (int i = 0; i < count; i++) {
            joiner.add("?");
        }
        return joiner.toString();
    }

    private static void bind(CallableStatement statement, int firstIndex, Object[] args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            int index = firstIndex + i;
            Object value = args[i];
            if (value == null) {
                statement.setNull(index, Types.NULL);
            } else if (value instanceof String text) {
                statement.setString(index, text);
            } else if (value instanceof Integer number) {
                statement.setInt(index, number);
            } else if (value instanceof BigDecimal decimal) {
                statement.setBigDecimal(index, decimal);
            } else if (value instanceof LocalDate date) {
                statement.setObject(index, date);
            } else if (value instanceof LocalTime time) {
                statement.setObject(index, time);
            } else if (value instanceof LocalDateTime dateTime) {
                statement.setObject(index, dateTime);
            } else {
                statement.setObject(index, value);
            }
        }
    }

    private static List<Row> readAll(ResultSet resultSet) throws SQLException {
        ResultSetMetaData meta = resultSet.getMetaData();
        int columns = meta.getColumnCount();
        List<Row> rows = new ArrayList<>();
        while (resultSet.next()) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (int c = 1; c <= columns; c++) {
                values.put(meta.getColumnLabel(c), readValue(resultSet, c, meta.getColumnType(c)));
            }
            rows.add(new Row(values));
        }
        return rows;
    }

    private static Object readValue(ResultSet resultSet, int column, int type) throws SQLException {
        Object value = switch (type) {
            case Types.DATE -> resultSet.getObject(column, LocalDate.class);
            case Types.TIME -> resultSet.getObject(column, LocalTime.class);
            case Types.TIMESTAMP -> resultSet.getObject(column, LocalDateTime.class);
            default -> resultSet.getObject(column);
        };
        return resultSet.wasNull() ? null : value;
    }

    private static AppException translate(Routine routine, SQLException e) {
        String state = e.getSQLState() == null ? "" : e.getSQLState();
        int code = e.getErrorCode();
        if ("45000".equals(state)) {
            LOG.info(() -> "Rule check in " + routine.routineName() + ": " + e.getMessage());
            return new AppException(AppException.Kind.RULE, e.getMessage(), e);
        }
        LOG.log(Level.WARNING, "Call to " + routine.routineName() + " failed (SQLState " + state
                + ", error " + code + ", target " + AppConfig.get().describeForLog() + ")", e);
        if (state.startsWith("08") || state.startsWith("28") || code == 1045
                || e instanceof SQLNonTransientConnectionException
                || e instanceof SQLTransientConnectionException) {
            return new AppException(AppException.Kind.CONNECTION, CONNECTION_MESSAGE, e);
        }
        if (code == 1049 || code == 1305 || code == 1146 || code == 1318) {
            return new AppException(AppException.Kind.SETUP, SETUP_MESSAGE, e);
        }
        return new AppException(AppException.Kind.UNEXPECTED, UNEXPECTED_MESSAGE, e);
    }
}
