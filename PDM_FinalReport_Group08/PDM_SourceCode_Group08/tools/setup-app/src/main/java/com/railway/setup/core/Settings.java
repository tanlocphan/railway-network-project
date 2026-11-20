package com.railway.setup.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Which MySQL the team member uses and how to sign in to it.
 *
 * <ul>
 *   <li>config/setup.properties: the admin sign-in the setup tool uses to create the database.</li>
 *   <li>config/connection.properties: what the Railway app uses (read by AppConfig).</li>
 * </ul>
 * Both files are ignored by git, so passwords never leave the computer.
 */
public final class Settings {

    public enum Mode { INSTALLED, PORTABLE }

    public static final String APP_ACCOUNT = "railway_app";

    private static Settings current;

    public Mode mode = Mode.INSTALLED;
    public String installedHost = "localhost";
    public int installedPort = 3306;
    public String installedUser = "root";
    public String installedPassword = "";
    /** When true the Railway app signs in with the EXECUTE-only railway_app account. */
    public boolean appUsesRestrictedAccount;
    public String appPassword = "";

    private Settings() {
    }

    public static synchronized Settings get() {
        if (current == null) {
            current = load();
        }
        return current;
    }

    // ----- effective connection -----------------------------------------

    public String host() {
        // 127.0.0.1, not localhost: the portable server only listens on IPv4
        return mode == Mode.PORTABLE ? "127.0.0.1" : installedHost;
    }

    public int port() {
        return mode == Mode.PORTABLE ? PortableMySql.PORT : installedPort;
    }

    public String user() {
        return mode == Mode.PORTABLE ? "root" : installedUser;
    }

    public String password() {
        return mode == Mode.PORTABLE ? PortableMySql.ROOT_PASSWORD : installedPassword;
    }

    public String describe() {
        return user() + "@" + host() + ":" + port();
    }

    public String appUser() {
        return appUsesRestrictedAccount ? APP_ACCOUNT : user();
    }

    public String appPasswordEffective() {
        return appUsesRestrictedAccount ? appPassword : password();
    }

    // ----- load / save ---------------------------------------------------

    private static Settings load() {
        Settings s = new Settings();
        Properties setup = read(Project.setupSettingsFile());
        if (setup != null) {
            s.mode = "portable".equalsIgnoreCase(setup.getProperty("mysql.mode")) ? Mode.PORTABLE : Mode.INSTALLED;
            s.installedHost = setup.getProperty("installed.host", "localhost").trim();
            s.installedPort = parsePort(setup.getProperty("installed.port"), 3306);
            s.installedUser = setup.getProperty("installed.user", "root").trim();
            s.installedPassword = setup.getProperty("installed.password", "");
            s.appUsesRestrictedAccount = Boolean.parseBoolean(setup.getProperty("app.restricted", "false"));
            s.appPassword = setup.getProperty("app.password", "");
            return s;
        }

        // First start: take over what the team member already typed for the app.
        Properties app = read(Project.appConnectionFile());
        if (app != null) {
            int port = parsePort(app.getProperty("server.port"), 3306);
            String user = app.getProperty("server.user", "root").trim();
            String password = app.getProperty("server.password", "");
            if (port == PortableMySql.PORT && PortableMySql.isInstalled()) {
                s.mode = Mode.PORTABLE;
            } else {
                s.installedHost = app.getProperty("server.host", "localhost").trim();
                s.installedPort = port;
            }
            if (APP_ACCOUNT.equalsIgnoreCase(user)) {
                s.appUsesRestrictedAccount = true;
                s.appPassword = password;
            } else if (s.mode == Mode.INSTALLED) {
                s.installedUser = user;
                s.installedPassword = password;
            }
            return s;
        }

        // Nothing configured yet: a fresh clone.
        if (PortableMySql.isInstalled()) {
            s.mode = Mode.PORTABLE;
        } else if (!MySqlService.list().isEmpty() || Net.isOpen("localhost", 3306)) {
            s.mode = Mode.INSTALLED;
        } else {
            s.mode = Mode.PORTABLE;
        }
        return s;
    }

    /** Writes config/setup.properties and config/connection.properties. */
    public synchronized void save() throws IOException {
        Files.createDirectories(Project.configDir());

        StringBuilder setup = new StringBuilder();
        setup.append("# Railway Setup - which MySQL this computer uses (written by Setup.exe).\n");
        setup.append("# Ignored by git: it contains passwords.\n");
        setup.append("mysql.mode=").append(mode == Mode.PORTABLE ? "portable" : "installed").append('\n');
        setup.append("installed.host=").append(escape(installedHost)).append('\n');
        setup.append("installed.port=").append(installedPort).append('\n');
        setup.append("installed.user=").append(escape(installedUser)).append('\n');
        setup.append("installed.password=").append(escape(installedPassword)).append('\n');
        setup.append("app.restricted=").append(appUsesRestrictedAccount).append('\n');
        setup.append("app.password=").append(escape(appPassword)).append('\n');
        Files.writeString(Project.setupSettingsFile(), setup.toString(), StandardCharsets.ISO_8859_1);

        writeAppConnection();
    }

    public void writeAppConnection() throws IOException {
        Files.createDirectories(Project.configDir());
        StringBuilder app = new StringBuilder();
        app.append("# Railway Network and Timetable - MySQL 8.0 connection settings\n");
        app.append("# Written by Railway Setup. Ignored by git so the password never leaves this computer.\n");
        app.append("server.host=").append(escape(host())).append('\n');
        app.append("server.port=").append(port()).append('\n');
        app.append("server.database=").append(Project.DATABASE).append('\n');
        app.append("server.user=").append(escape(appUser())).append('\n');
        app.append("server.password=").append(escape(appPasswordEffective())).append('\n');
        Files.writeString(Project.appConnectionFile(), app.toString(), StandardCharsets.ISO_8859_1);
    }

    private static Properties read(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
            return p;
        } catch (IOException e) {
            return null;
        }
    }

    private static int parsePort(String text, int fallback) {
        try {
            return Integer.parseInt(text == null ? "" : text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Properties-file escaping, so that any password survives Properties.load (ISO-8859-1). */
    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\') {
                out.append("\\\\");
            } else if (c == ' ' && i == 0) {
                out.append("\\ ");
            } else if (c < 0x20 || c > 0x7E) {
                out.append(String.format("\\u%04X", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
