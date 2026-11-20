package com.railway.app.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Connection settings read from config/connection.properties.
 * The file is prepared once by the support team; when it does not exist a
 * template with default values is written so it can be filled in.
 */
public final class AppConfig {

    private static final Logger LOG = Logger.getLogger(AppConfig.class.getName());
    private static AppConfig instance;

    private final String host;
    private final int port;
    private final String database;
    private final String user;
    private final String password;

    private AppConfig(Properties properties) {
        this.host = properties.getProperty("server.host", "localhost").trim();
        this.port = parsePort(properties.getProperty("server.port", "3306"));
        this.database = properties.getProperty("server.database", "RailwayManagement").trim();
        this.user = properties.getProperty("server.user", "root").trim();
        this.password = properties.getProperty("server.password", "");
    }

    public static synchronized AppConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    /** Re-reads the file, so a corrected file is picked up by "Try again". */
    public static synchronized AppConfig reload() {
        instance = load();
        return instance;
    }

    public String jdbcUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false"
                + "&allowPublicKeyRetrieval=true"
                + "&characterEncoding=UTF-8"
                + "&connectTimeout=5000"
                + "&socketTimeout=60000";
    }

    public String user() {
        return user;
    }

    public String password() {
        return password;
    }

    public String describeForLog() {
        return user + "@" + host + ":" + port + "/" + database;
    }

    private static AppConfig load() {
        Path file = AppPaths.configFile();
        Properties properties = new Properties();
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                properties.load(in);
                LOG.info("Connection settings loaded from " + file);
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Connection settings could not be read from " + file, e);
            }
        } else {
            writeTemplate(file);
        }
        return new AppConfig(properties);
    }

    private static void writeTemplate(Path file) {
        Properties template = new Properties();
        template.setProperty("server.host", "localhost");
        template.setProperty("server.port", "3306");
        template.setProperty("server.database", "RailwayManagement");
        template.setProperty("server.user", "root");
        template.setProperty("server.password", "");
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                template.store(out, "Railway Network and Timetable - MySQL 8.0 connection settings (edit, then restart the app)");
            }
            LOG.info("Connection settings template created at " + file);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Connection settings template could not be created at " + file, e);
        }
    }

    private static int parsePort(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            LOG.warning("Invalid server.port value '" + value + "', using 3306");
            return 3306;
        }
    }
}
