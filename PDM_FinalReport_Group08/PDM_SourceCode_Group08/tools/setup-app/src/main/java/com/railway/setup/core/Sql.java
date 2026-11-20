package com.railway.setup.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.function.UnaryOperator;

/**
 * JDBC for the setup tool. Unlike the Railway app (which may only call
 * routines), the setup tool is an administration tool: it creates the
 * database from the .sql files and inspects information_schema.
 */
public final class Sql {

    private static final int MAX_ROWS_PRINTED = 200;

    private Sql() {
    }

    public static Connection connect(Settings s, String database) throws SQLException {
        return connect(s.host(), s.port(), s.user(), s.password(), database);
    }

    public static Connection connect(String host, int port, String user, String password, String database)
            throws SQLException {
        String url = "jdbc:mysql://" + host + ":" + port + "/" + (database == null ? "" : database)
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8"
                + "&connectTimeout=4000&socketTimeout=600000&serverTimezone=UTC";
        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", password == null ? "" : password);
        DriverManager.setLoginTimeout(5);
        return DriverManager.getConnection(url, props);
    }

    /** Turns driver messages into something a classmate understands. */
    public static String friendly(SQLException e, Settings s) {
        int code = e.getErrorCode();
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (code == 1045) {
            return "MySQL refused the user name or password for " + s.describe()
                    + ". Fix the password in Environment > Connection.";
        }
        String privilege = privilegeHint(e, s);
        if (privilege != null) {
            return privilege;
        }
        if (code == 1049) {
            return "The database " + Project.DATABASE + " does not exist yet. Create it on the Database page.";
        }
        if (message.contains("Communications link failure") || message.contains("Connection refused")) {
            return "No MySQL server is answering at " + s.host() + ":" + s.port() + ". Start MySQL on the Environment page.";
        }
        return message + (code != 0 ? " (MySQL error " + code + ")" : "");
    }

    /**
     * The sign-in works but the account is not allowed to create databases,
     * routines or users (e.g. a classmate typed a normal account, not root).
     */
    public static String privilegeHint(SQLException e, Settings s) {
        int code = e.getErrorCode();
        // 1044 access denied to database, 1142/1143 command denied, 1227 needs a privilege,
        // 1370/1410 not allowed to create routines / users, 1419 SUPER needed for routines
        if (code == 1044 || code == 1142 || code == 1143 || code == 1227 || code == 1370
                || code == 1410 || code == 1419) {
            return "The account " + s.user() + " signs in but may not do this (" + e.getMessage() + "). "
                    + "Setup needs an administrator account such as root: change the user in Environment > Connection, "
                    + "or use the portable MySQL (root / " + PortableMySql.ROOT_PASSWORD + ").";
        }
        return null;
    }

    /**
     * Runs every statement of a .sql file, printing one line per statement.
     *
     * @param transform  optional change to the file text (e.g. fill in a password)
     * @param printRows  print result sets as tables (for query files)
     */
    public static void runFile(TaskContext ctx, Connection connection, Path file,
                               UnaryOperator<String> transform, boolean printRows) throws IOException, SQLException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        if (transform != null) {
            text = transform.apply(text);
        }
        List<SqlScript.Statement> statements = SqlScript.parse(text);
        String name = file.getFileName().toString();
        ctx.step("Running " + name + " (" + statements.size() + " statements)");
        int index = 0;
        try (Statement st = connection.createStatement()) {
            for (SqlScript.Statement statement : statements) {
                ctx.checkCancelled();
                index++;
                ctx.progress(name, index, statements.size());
                String label = statement.label();
                try {
                    boolean hasRows = st.execute(statement.sql());
                    if (printRows) {
                        ctx.text("");
                        ctx.command(oneLine(statement.sql()));
                        printResults(ctx, st, hasRows);
                    } else {
                        int count = hasRows ? -1 : st.getUpdateCount();
                        drainResults(st, hasRows);
                        String rows = count > 0 && label.startsWith("INSERT") ? "  · " + count + " row" + (count == 1 ? "" : "s") : "";
                        ctx.dim(String.format("  [%3d/%d] %s%s", index, statements.size(), label, rows));
                    }
                } catch (SQLException e) {
                    ctx.error("  " + name + " line " + statement.line() + ": " + label);
                    ctx.error("  MySQL says: " + e.getMessage());
                    String hint = privilegeHint(e, Settings.get());
                    throw new TaskContext.Failure(name + " stopped at line " + statement.line()
                            + " — " + (hint != null ? hint : e.getMessage()));
                }
            }
        }
        ctx.ok("  " + name + " finished.");
    }

    private static void drainResults(Statement st, boolean hasRows) throws SQLException {
        if (hasRows) {
            st.getResultSet().close();
        }
        while (st.getMoreResults() || st.getUpdateCount() != -1) {
            ResultSet rs = st.getResultSet();
            if (rs != null) {
                rs.close();
            }
        }
    }

    private static void printResults(TaskContext ctx, Statement st, boolean hasRows) throws SQLException {
        boolean rows = hasRows;
        while (true) {
            if (rows) {
                try (ResultSet rs = st.getResultSet()) {
                    printTable(ctx, rs);
                }
            } else {
                int count = st.getUpdateCount();
                if (count == -1) {
                    break;
                }
                ctx.dim("  " + count + " row(s) affected");
            }
            rows = st.getMoreResults();
        }
    }

    /** Prints a result set as a text table, like the mysql client does. */
    public static void printTable(TaskContext ctx, ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int cols = meta.getColumnCount();
        List<String[]> data = new ArrayList<>();
        String[] header = new String[cols];
        int[] width = new int[cols];
        for (int c = 0; c < cols; c++) {
            header[c] = meta.getColumnLabel(c + 1);
            width[c] = header[c].length();
        }
        int total = 0;
        while (rs.next()) {
            total++;
            if (data.size() >= MAX_ROWS_PRINTED) {
                continue;
            }
            String[] row = new String[cols];
            for (int c = 0; c < cols; c++) {
                String v = rs.getString(c + 1);
                row[c] = v == null ? "NULL" : v.replace('\n', ' ');
                width[c] = Math.min(60, Math.max(width[c], row[c].length()));
            }
            data.add(row);
        }
        StringBuilder sep = new StringBuilder("+");
        for (int w : width) {
            sep.append("-".repeat(w + 2)).append('+');
        }
        ctx.text(sep.toString());
        ctx.text(row(header, width));
        ctx.text(sep.toString());
        for (String[] r : data) {
            ctx.text(row(r, width));
        }
        ctx.text(sep.toString());
        String more = total > data.size() ? " (showing the first " + data.size() + ")" : "";
        ctx.dim(total + " row" + (total == 1 ? "" : "s") + more);
    }

    private static String row(String[] values, int[] width) {
        StringBuilder sb = new StringBuilder("|");
        for (int c = 0; c < values.length; c++) {
            String v = values[c].length() > width[c] ? values[c].substring(0, width[c] - 1) + "…" : values[c];
            sb.append(' ').append(v).append(" ".repeat(width[c] - v.length())).append(" |");
        }
        return sb.toString();
    }

    private static String oneLine(String sql) {
        String flat = SqlScript.stripComments(sql).replaceAll("\\s+", " ").trim();
        return flat.length() > 140 ? flat.substring(0, 137) + "..." : flat;
    }

    public static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
