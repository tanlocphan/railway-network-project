package com.railway.setup.core;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Where things live in the cloned repository. The setup tool normally runs
 * from SetupApp.jar at the repository root, but it also works when started
 * from tools/setup-app/target or an IDE, because it climbs up until it finds
 * the folder that holds pom.xml and database/.
 */
public final class Project {

    public static final String DATABASE = "RailwayManagement";

    private static Path root;

    private Project() {
    }

    public static synchronized Path root() {
        if (root == null) {
            root = detectRoot();
        }
        return root;
    }

    public static Path databaseDir() {
        return root().resolve("database");
    }

    public static Path configDir() {
        return root().resolve("config");
    }

    public static Path appConnectionFile() {
        return configDir().resolve("connection.properties");
    }

    public static Path setupSettingsFile() {
        return configDir().resolve("setup.properties");
    }

    public static Path logsDir() {
        return root().resolve("logs");
    }

    public static Path appJar() {
        return root().resolve("target").resolve("railway-manager.jar");
    }

    public static Path mavenWrapper() {
        return root().resolve(isWindows() ? "mvnw.cmd" : "mvnw");
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** The Java the setup tool runs on; the launcher already picked a JDK. */
    public static Path javaHome() {
        return Paths.get(System.getProperty("java.home"));
    }

    public static Path javaExecutable(boolean windowless) {
        String name = isWindows() ? (windowless ? "javaw.exe" : "java.exe") : "java";
        return javaHome().resolve("bin").resolve(name);
    }

    public static boolean isProjectRoot(Path folder) {
        return folder != null
                && Files.isRegularFile(folder.resolve("pom.xml"))
                && Files.isDirectory(folder.resolve("database"));
    }

    private static Path detectRoot() {
        try {
            Path code = Paths.get(Project.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path found = climb(Files.isDirectory(code) ? code : code.getParent());
            if (found != null) {
                return found;
            }
        } catch (Exception ignored) {
            // fall back to the working directory
        }
        Path found = climb(new File(".").getAbsoluteFile().toPath().normalize());
        return found != null ? found : new File(".").getAbsoluteFile().toPath().normalize();
    }

    private static Path climb(Path start) {
        Path folder = start;
        for (int i = 0; folder != null && i < 8; i++) {
            if (isProjectRoot(folder)) {
                return folder.toAbsolutePath();
            }
            folder = folder.getParent();
        }
        return null;
    }
}
