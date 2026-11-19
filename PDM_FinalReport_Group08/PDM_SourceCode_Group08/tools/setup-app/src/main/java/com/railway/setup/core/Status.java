package com.railway.setup.core;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * A snapshot of everything the checklist shows: Java, Maven, MySQL, sign-in,
 * database, build, app. Collected in the background and handed to every page.
 */
public final class Status {

    // Java and tools
    public String javaVersion;
    public Path javaHome;
    public boolean jdk;
    public boolean mavenReady;
    public String gitVersion;

    // MySQL
    public Settings.Mode mode;
    public String target;
    public List<MySqlService.Service> services = List.of();
    public Path installedFolder;
    public boolean portableInstalled;
    public boolean portableRunning;
    public boolean serverOpen;
    public boolean loginOk;
    public String loginError;
    public String serverVersion;

    // Database
    public boolean dbExists;
    public Map<String, Long> rowCounts = new LinkedHashMap<>();
    public Set<String> routinesFound = new LinkedHashSet<>();
    public Set<String> routinesExpected = new LinkedHashSet<>();
    public boolean appAccountExists;

    // App
    public boolean appBuilt;
    public boolean appOutdated;
    public Instant appBuiltAt;
    public boolean appRunning;

    public Instant collectedAt = Instant.now();

    public List<String> missingRoutines() {
        List<String> missing = new ArrayList<>();
        for (String name : routinesExpected) {
            if (!routinesFound.contains(name)) {
                missing.add(name);
            }
        }
        return missing;
    }

    public boolean databaseReady() {
        return dbExists && !rowCounts.isEmpty() && missingRoutines().isEmpty();
    }

    public MySqlService.Service stoppedService() {
        return services.stream().filter(s -> !s.running()).findFirst().orElse(null);
    }

    // ----- collecting -----------------------------------------------------

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "status");
        t.setDaemon(true);
        return t;
    });
    private static final List<Consumer<Status>> LISTENERS = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean PENDING = new AtomicBoolean();
    private static volatile Status last;

    public static Status last() {
        return last;
    }

    public static void listen(Consumer<Status> listener) {
        LISTENERS.add(listener);
        if (last != null) {
            listener.accept(last);
        }
    }

    /** Collects a new snapshot; several quick calls collapse into one. */
    public static void refresh() {
        if (!PENDING.compareAndSet(false, true)) {
            return;
        }
        EXECUTOR.execute(() -> {
            PENDING.set(false);
            Status s;
            try {
                s = collect(Settings.get());
            } catch (Exception e) {
                SetupLog.error("Status check", e);
                return;
            }
            last = s;
            SwingUtilities.invokeLater(() -> LISTENERS.forEach(l -> l.accept(s)));
        });
    }

    public static Status collect(Settings settings) {
        Status s = new Status();
        s.javaHome = Project.javaHome();
        s.javaVersion = System.getProperty("java.version");
        s.jdk = Files.exists(s.javaHome.resolve("bin").resolve(Project.isWindows() ? "javac.exe" : "javac"));
        s.mavenReady = mavenDownloaded();
        Proc.Result git = Proc.capture(List.of("git", "--version"), Project.root(), 5);
        s.gitVersion = git.ok() ? git.output().replace("git version", "").trim() : null;

        s.mode = settings.mode;
        s.target = settings.describe();
        s.services = MySqlService.list();
        s.installedFolder = MySqlService.installedServerFolder();
        s.portableInstalled = PortableMySql.isInstalled();
        s.portableRunning = s.portableInstalled && PortableMySql.isRunning();
        s.serverOpen = Net.isOpen(settings.host(), settings.port());

        s.routinesExpected = expectedRoutines();
        if (s.serverOpen) {
            probeDatabase(s, settings);
        }

        s.appBuilt = RailwayApp.isBuilt();
        s.appOutdated = s.appBuilt && RailwayApp.isOutdated();
        try {
            s.appBuiltAt = s.appBuilt ? Files.getLastModifiedTime(Project.appJar()).toInstant() : null;
        } catch (IOException e) {
            s.appBuiltAt = null;
        }
        s.appRunning = RailwayApp.isRunning();
        s.collectedAt = Instant.now();
        return s;
    }

    private static void probeDatabase(Status s, Settings settings) {
        try (Connection c = Sql.connect(settings, null); Statement st = c.createStatement()) {
            s.loginOk = true;
            try (ResultSet rs = st.executeQuery("SELECT VERSION()")) {
                s.serverVersion = rs.next() ? rs.getString(1) : null;
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = ?")) {
                ps.setString(1, Project.DATABASE);
                try (ResultSet rs = ps.executeQuery()) {
                    s.dbExists = rs.next() && rs.getInt(1) > 0;
                }
            }
            if (s.dbExists) {
                List<String> tables = new ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? "
                                + "AND TABLE_TYPE = 'BASE TABLE' ORDER BY CREATE_TIME, TABLE_NAME")) {
                    ps.setString(1, Project.DATABASE);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            tables.add(rs.getString(1));
                        }
                    }
                }
                for (String table : tables) {
                    try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM `" + Project.DATABASE + "`.`"
                            + table.replace("`", "``") + "`")) {
                        s.rowCounts.put(table, rs.next() ? rs.getLong(1) : 0);
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT LOWER(ROUTINE_NAME) FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA = ?")) {
                    ps.setString(1, Project.DATABASE);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            s.routinesFound.add(rs.getString(1));
                        }
                    }
                }
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM mysql.user WHERE user = '" + Settings.APP_ACCOUNT + "'")) {
                s.appAccountExists = rs.next() && rs.getInt(1) > 0;
            } catch (SQLException ignored) {
                // not allowed to read mysql.user: leave false
            }
        } catch (SQLException e) {
            s.loginOk = false;
            s.loginError = Sql.friendly(e, settings);
        }
    }

    private static Set<String> expectedCache;
    private static long expectedStamp;

    /** Routines that 01 + 03 create, so a half-loaded database is noticed. */
    static synchronized Set<String> expectedRoutines() {
        Path a = Project.databaseDir().resolve("01_schema_and_seed.sql");
        Path b = Project.databaseDir().resolve("03_business_routines.sql");
        long stamp = stamp(a) * 31 + stamp(b);
        if (expectedCache == null || stamp != expectedStamp) {
            Set<String> names = new LinkedHashSet<>();
            for (Path p : List.of(a, b)) {
                try {
                    names.addAll(SqlScript.routineNames(Files.readString(p, StandardCharsets.UTF_8)));
                } catch (IOException ignored) {
                    // missing file: nothing expected from it
                }
            }
            expectedCache = names;
            expectedStamp = stamp;
        }
        return expectedCache;
    }

    private static long stamp(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    /** Has the Maven Wrapper already downloaded Maven on this computer? */
    public static boolean mavenDownloaded() {
        Path dists = Paths.get(System.getProperty("user.home"), ".m2", "wrapper", "dists");
        if (!Files.isDirectory(dists)) {
            return false;
        }
        try (Stream<Path> walk = Files.walk(dists, 4)) {
            return walk.anyMatch(p -> p.getFileName().toString().startsWith("apache-maven-")
                    && Files.isDirectory(p.resolve("bin")));
        } catch (IOException e) {
            return false;
        }
    }
}
