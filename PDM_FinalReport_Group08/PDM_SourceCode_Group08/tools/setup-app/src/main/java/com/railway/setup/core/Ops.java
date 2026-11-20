package com.railway.setup.core;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/** The jobs behind the buttons. Each one runs inside a task. */
public final class Ops {

    private Ops() {
    }

    private static Path sql(String file) {
        return Project.databaseDir().resolve(file);
    }

    // ----- MySQL server ---------------------------------------------------

    public static void startMySql(TaskContext ctx, Settings s) throws Exception {
        if (s.mode == Settings.Mode.PORTABLE) {
            if (!PortableMySql.isInstalled()) {
                PortableMySql.install(ctx);
            } else {
                PortableMySql.start(ctx);
            }
            return;
        }
        if (Net.isOpen(s.host(), s.port())) {
            ctx.ok("MySQL is already answering on " + s.host() + ":" + s.port() + ".");
            return;
        }
        List<MySqlService.Service> services = MySqlService.list();
        MySqlService.Service service = services.stream().filter(x -> !x.running()).findFirst().orElse(null);
        if (service == null) {
            throw new TaskContext.Failure("No MySQL is answering on " + s.host() + ":" + s.port()
                    + " and no stopped MySQL service was found. Install the portable MySQL instead.");
        }
        MySqlService.control(ctx, service.name(), true);
        waitForPort(ctx, s.host(), s.port());
    }

    public static void stopMySql(TaskContext ctx, Settings s) throws Exception {
        if (s.mode == Settings.Mode.PORTABLE) {
            PortableMySql.stop(ctx);
            return;
        }
        MySqlService.Service service = MySqlService.list().stream()
                .filter(MySqlService.Service::running).findFirst().orElse(null);
        if (service == null) {
            ctx.ok("No running MySQL service was found.");
            return;
        }
        MySqlService.control(ctx, service.name(), false);
        ctx.ok("The service " + service.name() + " was asked to stop.");
    }

    private static void waitForPort(TaskContext ctx, String host, int port) throws InterruptedException {
        for (int i = 0; i < 60; i++) {
            ctx.checkCancelled();
            ctx.progress("Waiting for MySQL", i, 60);
            if (Net.isOpen(host, port)) {
                ctx.ok("MySQL is answering on " + host + ":" + port + ".");
                return;
            }
            Thread.sleep(500);
        }
        throw new TaskContext.Failure("MySQL did not start answering on " + host + ":" + port + " within 30 seconds.");
    }

    public static void testSignIn(TaskContext ctx, Settings s) {
        ctx.text("Signing in to " + s.describe() + " ...");
        try (Connection c = Sql.connect(s, null); Statement st = c.createStatement();
             var rs = st.executeQuery("SELECT VERSION()")) {
            rs.next();
            ctx.ok("Signed in. MySQL server version " + rs.getString(1) + ".");
            if (!canCreateDatabase(st)) {
                ctx.warn(s.user() + " signs in but may not create databases or routines. Use root (or another "
                        + "administrator) to create the database, or the portable MySQL.");
            }
        } catch (SQLException e) {
            throw new TaskContext.Failure(Sql.friendly(e, s));
        }
    }

    /** Reads SHOW GRANTS: ALL PRIVILEGES or CREATE + CREATE ROUTINE on *.* (or the project database). */
    static boolean canCreateDatabase(Statement st) {
        try (var rs = st.executeQuery("SHOW GRANTS")) {
            boolean create = false;
            boolean routine = false;
            while (rs.next()) {
                String g = rs.getString(1).toUpperCase(java.util.Locale.ROOT);
                boolean scope = g.contains(" ON *.* ") || g.contains(" ON `" + Project.DATABASE.toUpperCase(java.util.Locale.ROOT) + "`.*");
                if (!scope) {
                    continue;
                }
                if (g.contains("ALL PRIVILEGES")) {
                    return true;
                }
                create |= g.matches(".*\\bCREATE\\b(?! (ROUTINE|USER|VIEW|TEMPORARY|ROLE|TABLESPACE)).*");
                routine |= g.contains("CREATE ROUTINE");
            }
            return create && routine;
        } catch (SQLException e) {
            return true; // cannot tell: do not warn
        }
    }

    // ----- database -------------------------------------------------------

    /** 01 + 02 + 03: drop and recreate the database with the sample data. */
    public static void resetDatabase(TaskContext ctx, Settings s) throws Exception {
        try (Connection c = connect(s, null)) {
            Sql.runFile(ctx, c, sql("01_schema_and_seed.sql"), null, false);
            Sql.runFile(ctx, c, sql("02_payment_table.sql"), null, false);
            Sql.runFile(ctx, c, sql("03_business_routines.sql"), null, false);
        }
        ctx.ok("Database " + Project.DATABASE + " is ready with the sample data.");
    }

    /** Only 03: replaces every function and procedure, keeps the data. */
    public static void reloadRoutines(TaskContext ctx, Settings s) throws Exception {
        try (Connection c = connect(s, Project.DATABASE)) {
            Sql.runFile(ctx, c, sql("03_business_routines.sql"), null, false);
        }
    }

    public static void runReferenceQueries(TaskContext ctx, Settings s) throws Exception {
        try (Connection c = connect(s, Project.DATABASE)) {
            Sql.runFile(ctx, c, sql("queries_reference.sql"), null, true);
        }
    }

    public static void dropDatabase(TaskContext ctx, Settings s) throws Exception {
        try (Connection c = connect(s, null); Statement st = c.createStatement()) {
            ctx.command("DROP DATABASE IF EXISTS " + Project.DATABASE);
            st.execute("DROP DATABASE IF EXISTS " + Project.DATABASE);
        }
        ctx.ok("Database " + Project.DATABASE + " was deleted.");
    }

    /** 04: the EXECUTE-only account for the app, with the password typed on screen. */
    public static void createAppAccount(TaskContext ctx, Settings s, String password, boolean useForApp) throws Exception {
        String host = s.host().toLowerCase(java.util.Locale.ROOT);
        if (!host.equals("localhost") && !host.equals("127.0.0.1") && !host.equals("::1")) {
            throw new TaskContext.Failure("04_app_account.sql creates " + Settings.APP_ACCOUNT
                    + "@'localhost', which can only sign in on the computer that runs MySQL. MySQL here is on "
                    + s.host() + ", so keep the app on " + s.user() + " (or create the account on that server by hand).");
        }
        String literal = password.replace("\\", "\\\\").replace("'", "''");
        try (Connection c = connect(s, null)) {
            Sql.runFile(ctx, c, sql("04_app_account.sql"), text -> text.replace("CHANGE_ME_PASSWORD", literal), false);
            // CREATE USER IF NOT EXISTS keeps an old password, so set it explicitly
            try (PreparedStatement ps = c.prepareStatement("ALTER USER '" + Settings.APP_ACCOUNT + "'@'localhost' IDENTIFIED BY ?")) {
                ps.setString(1, password);
                ps.execute();
            }
        }
        ctx.text("Checking that " + Settings.APP_ACCOUNT + " can sign in ...");
        try (Connection c = Sql.connect(s.host(), s.port(), Settings.APP_ACCOUNT, password, Project.DATABASE)) {
            ctx.ok(Settings.APP_ACCOUNT + " signs in and may only run the routines.");
        }
        s.appPassword = password;
        s.appUsesRestrictedAccount = useForApp;
        s.save();
        ctx.ok(useForApp ? "The Railway app now signs in as " + Settings.APP_ACCOUNT + " (config/connection.properties)."
                : "The Railway app keeps using " + s.user() + ".");
    }

    private static Connection connect(Settings s, String database) {
        try {
            return Sql.connect(s, database);
        } catch (SQLException e) {
            throw new TaskContext.Failure(Sql.friendly(e, s));
        }
    }

    // ----- everything -----------------------------------------------------

    /**
     * The one-click path for a fresh clone: MySQL running, sign-in works,
     * database loaded, app built, app open. Steps that are already fine are skipped.
     */
    public static void setUpEverything(TaskContext ctx, Settings s, Output output, Runnable onAppExit) throws Exception {
        ctx.step("1/5  MySQL server");
        if (Net.isOpen(s.host(), s.port())) {
            ctx.ok("MySQL is answering on " + s.host() + ":" + s.port() + ".");
        } else {
            startMySql(ctx, s);
        }

        ctx.step("2/5  Sign-in");
        testSignIn(ctx, s);
        s.writeAppConnection();
        ctx.dim("config/connection.properties points the app to " + s.host() + ":" + s.port() + ".");

        ctx.step("3/5  Database");
        Status now = Status.collect(s);
        if (!now.dbExists || now.rowCounts.isEmpty()) {
            resetDatabase(ctx, s);
        } else if (!now.missingRoutines().isEmpty()) {
            ctx.text(now.missingRoutines().size() + " routine(s) are missing; reloading 03_business_routines.sql (data is kept).");
            reloadRoutines(ctx, s);
        } else {
            ctx.ok(Project.DATABASE + " is complete: " + now.rowCounts.size() + " tables, "
                    + now.routinesFound.size() + " routines. Data was left as it is.");
        }
        if (s.appUsesRestrictedAccount && !now.appAccountExists) {
            ctx.warn("The app is set to sign in as " + Settings.APP_ACCOUNT + ", which does not exist here; using "
                    + s.user() + " instead.");
            s.appUsesRestrictedAccount = false;
            s.save();
        }

        ctx.step("4/5  Build");
        if (RailwayApp.isBuilt() && !RailwayApp.isOutdated()) {
            ctx.ok("The app is already built and up to date.");
        } else {
            RailwayApp.build(ctx, false);
        }

        ctx.step("5/5  Open the Railway app");
        RailwayApp.start(ctx, output, onAppExit);
    }
}
