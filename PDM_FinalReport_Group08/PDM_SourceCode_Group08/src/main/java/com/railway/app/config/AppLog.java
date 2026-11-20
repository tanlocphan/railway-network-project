package com.railway.app.config;

import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Writes technical details to logs/railway-app.log. Users never see this
 * information on screen; it is meant for the support team.
 */
public final class AppLog {

    private static final String FORMAT = "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$-7s [%3$s] %5$s%6$s%n";

    private AppLog() {
    }

    public static void init() {
        System.setProperty("java.util.logging.SimpleFormatter.format", FORMAT);
        Logger root = Logger.getLogger("");
        for (Handler handler : root.getHandlers()) {
            root.removeHandler(handler);
        }
        root.setLevel(Level.INFO);

        ConsoleHandler console = new ConsoleHandler();
        console.setLevel(Level.INFO);
        console.setFormatter(new SimpleFormatter());
        root.addHandler(console);

        try {
            Files.createDirectories(AppPaths.logFolder());
            String pattern = AppPaths.logFolder().resolve("railway-app.%g.log").toString();
            FileHandler file = new FileHandler(pattern, 2_000_000, 3, true);
            file.setEncoding("UTF-8");
            file.setLevel(Level.FINE);
            file.setFormatter(new SimpleFormatter());
            root.addHandler(file);
            Logger.getLogger("com.railway").setLevel(Level.FINE);
        } catch (IOException | SecurityException e) {
            root.log(Level.WARNING, "Log file could not be opened; logging to console only", e);
        }
    }
}
