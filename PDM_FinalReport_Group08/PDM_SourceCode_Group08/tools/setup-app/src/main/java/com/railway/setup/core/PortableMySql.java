package com.railway.setup.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A private MySQL 8.0 that belongs to this project: downloaded once into
 * %USERPROFILE%\.railway-mysql, listening on port 3307 so it never collides
 * with a MySQL the team member already has on 3306. Sign-in is root / root.
 */
public final class PortableMySql {

    public static final String VERSION = "8.0.45";
    public static final int PORT = 3307;
    public static final String ROOT_PASSWORD = "root";
    private static final String FOLDER = "mysql-" + VERSION + "-winx64";
    private static final List<String> URLS = List.of(
            "https://cdn.mysql.com/archives/mysql-8.0/" + FOLDER + ".zip",
            "https://cdn.mysql.com/Downloads/MySQL-8.0/" + FOLDER + ".zip");
    /** Folders of the ZIP the server does not need: saves about 300 MB of disk. */
    private static final List<String> SKIP = List.of("include/", "docs/", "lib/mysqlclient.lib", "lib/libmysql.lib",
            "bin/mysqld-debug.exe");

    private PortableMySql() {
    }

    public static Path home() {
        return Paths.get(System.getProperty("user.home"), ".railway-mysql");
    }

    public static Path base() {
        return home().resolve(FOLDER);
    }

    public static Path data() {
        return home().resolve("data");
    }

    public static Path ini() {
        return home().resolve("my.ini");
    }

    public static Path errorLog() {
        return home().resolve("mysqld.err");
    }

    private static Path mysqld() {
        return base().resolve("bin").resolve("mysqld.exe");
    }

    public static boolean isSupported() {
        return Project.isWindows();
    }

    public static boolean isInstalled() {
        return Files.exists(mysqld()) && Files.isDirectory(data().resolve("mysql"));
    }

    public static boolean isRunning() {
        return process().isPresent() || (isInstalled() && Net.isOpen("127.0.0.1", PORT));
    }

    private static Optional<ProcessHandle> process() {
        String exe = mysqld().toString().toLowerCase();
        return ProcessHandle.allProcesses()
                .filter(p -> p.info().command().map(c -> c.toLowerCase().equals(exe)).orElse(false))
                .findFirst();
    }

    // ----- install --------------------------------------------------------

    public static void install(TaskContext ctx) throws Exception {
        if (!isSupported()) {
            throw new TaskContext.Failure("The portable MySQL is only available on Windows. Install MySQL 8.0 yourself.");
        }
        if (Net.isOpen("127.0.0.1", PORT) && process().isEmpty()) {
            throw new TaskContext.Failure("Port " + PORT + " is already used by another program, so the portable MySQL cannot use it.");
        }
        Files.createDirectories(home());

        if (!Files.exists(mysqld())) {
            Path zip = home().resolve(FOLDER + ".zip");
            if (!Files.exists(zip)) {
                download(ctx, zip);
            }
            extract(ctx, zip);
            Files.deleteIfExists(zip);
        } else {
            ctx.ok("MySQL " + VERSION + " files are already in " + base());
        }

        writeIni();
        ctx.dim("Settings written to " + ini());

        if (!Files.isDirectory(data().resolve("mysql"))) {
            if (Files.exists(data())) {
                deleteTree(data());
            }
            ctx.step("Preparing an empty MySQL server (about 20 seconds)");
            ctx.progress("Initialising MySQL", -1, 0);
            int code = ctx.exec(List.of(mysqld().toString(), "--defaults-file=" + ini(), "--initialize-insecure", "--console"),
                    home(), null, StandardCharsets.UTF_8, line -> ctx.dim("  " + line));
            if (code != 0 || !Files.isDirectory(data().resolve("mysql"))) {
                throw new TaskContext.Failure("MySQL could not prepare its data folder (exit code " + code + ").");
            }
            ctx.ok("Data folder ready: " + data());
        }

        start(ctx);

        ctx.step("Setting the root password to \"" + ROOT_PASSWORD + "\"");
        try (Connection c = Sql.connect("127.0.0.1", PORT, "root", "", null);
             Statement st = c.createStatement()) {
            st.execute("ALTER USER 'root'@'localhost' IDENTIFIED BY '" + ROOT_PASSWORD + "'");
            ctx.ok("Sign in with root / " + ROOT_PASSWORD + " on port " + PORT + ".");
        } catch (SQLException e) {
            if (e.getErrorCode() == 1045) {
                ctx.ok("The root password was already set.");
            } else {
                throw e;
            }
        }
    }

    private static void download(TaskContext ctx, Path zip) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        Path part = zip.resolveSibling(zip.getFileName() + ".part");
        Exception last = null;
        for (String url : URLS) {
            ctx.step("Downloading MySQL " + VERSION + " (about 233 MB)");
            ctx.dim("  from " + url);
            try {
                HttpResponse<InputStream> response = client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                        HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() != 200) {
                    response.body().close();
                    ctx.warn("  The server answered " + response.statusCode() + ", trying the next mirror.");
                    continue;
                }
                long total = response.headers().firstValueAsLong("content-length").orElse(-1);
                long done = 0;
                int lastPercent = -1;
                try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(part)) {
                    byte[] buffer = new byte[1 << 16];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        ctx.checkCancelled();
                        out.write(buffer, 0, read);
                        done += read;
                        ctx.progress("Downloading MySQL", done, total);
                        int percent = total > 0 ? (int) (done * 100 / total) : -1;
                        if (percent >= 0 && percent / 10 != lastPercent / 10) {
                            lastPercent = percent;
                            ctx.dim(String.format("  %3d%%  %d / %d MB", percent, done >> 20, total >> 20));
                        }
                    }
                }
                if (total > 0 && done != total) {
                    throw new IOException("The download stopped early (" + (done >> 20) + " of " + (total >> 20) + " MB).");
                }
                Files.move(part, zip, StandardCopyOption.REPLACE_EXISTING);
                ctx.ok("  Downloaded " + (done >> 20) + " MB.");
                return;
            } catch (TaskContext.Cancelled e) {
                Files.deleteIfExists(part);
                throw e;
            } catch (Exception e) {
                last = e;
                ctx.warn("  " + Tasks.describe(e));
            }
        }
        Files.deleteIfExists(part);
        throw new TaskContext.Failure("MySQL could not be downloaded. Check the internet connection and try again."
                + (last != null ? " (" + Tasks.describe(last) + ")" : ""));
    }

    private static void extract(TaskContext ctx, Path zip) throws IOException {
        ctx.step("Unpacking MySQL into " + home());
        Path target = home().toAbsolutePath().normalize();
        try (ZipFile file = new ZipFile(zip.toFile())) {
            int total = file.size();
            int index = 0;
            Enumeration<? extends ZipEntry> entries = file.entries();
            while (entries.hasMoreElements()) {
                ctx.checkCancelled();
                ZipEntry entry = entries.nextElement();
                index++;
                ctx.progress("Unpacking MySQL", index, total);
                String name = entry.getName().replace('\\', '/');
                String inside = name.contains("/") ? name.substring(name.indexOf('/') + 1) : "";
                if (inside.isEmpty() || SKIP.stream().anyMatch(inside::startsWith)) {
                    continue;
                }
                Path out = target.resolve(name).normalize();
                if (!out.startsWith(target)) {
                    throw new IOException("Unsafe path in the MySQL archive: " + name);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    try (InputStream in = file.getInputStream(entry)) {
                        Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
        if (!Files.exists(mysqld())) {
            throw new TaskContext.Failure("The MySQL archive did not contain " + mysqld());
        }
        ctx.ok("  Unpacked into " + base());
    }

    private static void writeIni() throws IOException {
        String ini = "# Portable MySQL for the Railway project - written by Railway Setup\n"
                + "[mysqld]\n"
                + "basedir=\"" + slash(base()) + "\"\n"
                + "datadir=\"" + slash(data()) + "\"\n"
                + "port=" + PORT + "\n"
                + "bind-address=127.0.0.1\n"
                + "mysqlx=OFF\n"
                + "skip-log-bin\n"
                + "character-set-server=utf8mb4\n"
                + "collation-server=utf8mb4_0900_ai_ci\n"
                + "log-error=\"" + slash(errorLog()) + "\"\n"
                + "\n[client]\n"
                + "port=" + PORT + "\n";
        Files.writeString(ini(), ini, StandardCharsets.UTF_8);
    }

    private static String slash(Path p) {
        return p.toAbsolutePath().toString().replace('\\', '/');
    }

    // ----- start / stop ---------------------------------------------------

    public static void start(TaskContext ctx) throws Exception {
        if (!isInstalled()) {
            throw new TaskContext.Failure("The portable MySQL is not installed yet.");
        }
        if (Net.isOpen("127.0.0.1", PORT)) {
            ctx.ok("The portable MySQL is already running on port " + PORT + ".");
            return;
        }
        writeIni();
        ctx.step("Starting the portable MySQL on port " + PORT);
        List<String> command = List.of(mysqld().toString(), "--defaults-file=" + ini());
        ctx.command(String.join(" ", Proc.display(command)));
        ProcessBuilder builder = Proc.builder(command, home(), null);
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(home().resolve("mysqld.out").toFile()));
        // Not tied to this window: MySQL keeps running after Setup is closed.
        Process p = builder.start();
        for (int i = 0; i < 120; i++) {
            ctx.checkCancelled();
            ctx.progress("Waiting for MySQL", i, 120);
            if (Net.isOpen("127.0.0.1", PORT)) {
                ctx.ok("MySQL is ready on port " + PORT + " (process " + p.pid() + ").");
                return;
            }
            if (!p.isAlive()) {
                break;
            }
            Thread.sleep(500);
        }
        printErrorLogTail(ctx);
        throw new TaskContext.Failure("The portable MySQL did not start. The lines above come from " + errorLog());
    }

    public static void stop(TaskContext ctx) throws Exception {
        if (!Net.isOpen("127.0.0.1", PORT) && process().isEmpty()) {
            ctx.ok("The portable MySQL is not running.");
            return;
        }
        ctx.step("Stopping the portable MySQL");
        try (Connection c = Sql.connect("127.0.0.1", PORT, "root", ROOT_PASSWORD, null);
             Statement st = c.createStatement()) {
            ctx.command("SHUTDOWN");
            st.execute("SHUTDOWN");
        } catch (SQLException e) {
            ctx.warn("A clean shutdown was not possible (" + e.getMessage() + "); ending the process instead.");
            process().ifPresent(Proc::killTree);
        }
        for (int i = 0; i < 60; i++) {
            if (process().isEmpty() && !Net.isOpen("127.0.0.1", PORT)) {
                ctx.ok("The portable MySQL has stopped.");
                return;
            }
            Thread.sleep(500);
        }
        process().ifPresent(Proc::killTree);
        ctx.warn("MySQL took too long to stop, so the process was ended.");
    }

    public static void remove(TaskContext ctx) throws Exception {
        stop(ctx);
        ctx.step("Deleting " + home());
        deleteTree(home());
        ctx.ok("The portable MySQL and its data are gone.");
    }

    private static void printErrorLogTail(TaskContext ctx) {
        try {
            if (Files.exists(errorLog())) {
                List<String> lines = Files.readAllLines(errorLog(), StandardCharsets.UTF_8);
                lines.subList(Math.max(0, lines.size() - 15), lines.size()).forEach(l -> ctx.warn("  " + l));
            }
        } catch (IOException ignored) {
            // nothing more to show
        }
    }

    private static void deleteTree(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(folder)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}
