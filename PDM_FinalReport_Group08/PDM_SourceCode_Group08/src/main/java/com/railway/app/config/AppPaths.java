package com.railway.app.config;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves the folder the application runs from, so that the configuration
 * and log folders sit next to the application whether it is started from
 * the packaged jar or from the development environment.
 */
public final class AppPaths {

    private static Path home;

    private AppPaths() {
    }

    public static synchronized Path home() {
        if (home == null) {
            home = detectHome();
        }
        return home;
    }

    public static Path configFile() {
        return home().resolve("config").resolve("connection.properties");
    }

    public static Path logFolder() {
        return home().resolve("logs");
    }

    private static Path detectHome() {
        try {
            Path codeSource = Paths.get(AppPaths.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path folder = Files.isDirectory(codeSource) ? codeSource : codeSource.getParent();
            // When started from target/classes or target/*.jar, use the project folder.
            if (folder != null && folder.getFileName() != null) {
                String name = folder.getFileName().toString();
                if ("classes".equals(name) && folder.getParent() != null) {
                    folder = folder.getParent();
                }
                if (folder != null && folder.getFileName() != null && "target".equals(folder.getFileName().toString())) {
                    folder = folder.getParent();
                }
            }
            if (folder != null) {
                return folder.toAbsolutePath();
            }
        } catch (URISyntaxException | SecurityException | NullPointerException ignored) {
            // fall back to the working directory below
        }
        return new File(".").getAbsoluteFile().toPath().normalize();
    }
}
