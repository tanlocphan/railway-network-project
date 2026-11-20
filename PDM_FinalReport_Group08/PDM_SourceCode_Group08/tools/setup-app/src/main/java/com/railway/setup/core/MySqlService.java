package com.railway.setup.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * A MySQL that the team member installed with the official installer: it runs
 * as a Windows service (usually "MySQL80") on port 3306.
 */
public final class MySqlService {

    public record Service(String name, String state) {
        public boolean running() {
            return "RUNNING".equals(state);
        }
    }

    private MySqlService() {
    }

    /** Windows services whose name mentions MySQL. */
    public static List<Service> list() {
        List<Service> services = new ArrayList<>();
        if (!Project.isWindows()) {
            return services;
        }
        Proc.Result r = Proc.capture(List.of("sc.exe", "query", "type=", "service", "state=", "all"), null, 10,
                Proc.consoleCharset());
        if (!r.ok()) {
            return services;
        }
        String name = null;
        for (String raw : r.output().split("\\R")) {
            String line = raw.trim();
            if (line.startsWith("SERVICE_NAME:")) {
                String candidate = line.substring("SERVICE_NAME:".length()).trim();
                name = candidate.toLowerCase(Locale.ROOT).contains("mysql") ? candidate : null;
            } else if (name != null && line.startsWith("STATE")) {
                String state = line.contains("RUNNING") ? "RUNNING"
                        : line.contains("STOPPED") ? "STOPPED"
                        : line.contains("START_PENDING") ? "STARTING"
                        : line.contains("STOP_PENDING") ? "STOPPING" : "UNKNOWN";
                services.add(new Service(name, state));
                name = null;
            }
        }
        return services;
    }

    /** mysqld.exe from an official install, if any (for display only). */
    public static Path installedServerFolder() {
        if (!Project.isWindows()) {
            return null;
        }
        for (String env : new String[]{"ProgramFiles", "ProgramW6432"}) {
            String pf = System.getenv(env);
            if (pf == null) {
                continue;
            }
            Path mysql = Paths.get(pf, "MySQL");
            if (!Files.isDirectory(mysql)) {
                continue;
            }
            try (Stream<Path> children = Files.list(mysql)) {
                Path found = children
                        .filter(p -> p.getFileName().toString().startsWith("MySQL Server"))
                        .filter(p -> Files.exists(p.resolve("bin").resolve("mysqld.exe")))
                        .sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()))
                        .findFirst().orElse(null);
                if (found != null) {
                    return found;
                }
            } catch (Exception ignored) {
                // keep looking
            }
        }
        return null;
    }

    /**
     * Starting or stopping a Windows service needs administrator rights, so
     * Windows shows its "Do you want to allow…" prompt.
     */
    public static void control(TaskContext ctx, String service, boolean start) throws Exception {
        String verb = start ? "start" : "stop";
        String safe = service.replace("'", "''");
        String script = "Start-Process -FilePath sc.exe -ArgumentList '" + verb + "','" + safe + "'"
                + " -Verb RunAs -WindowStyle Hidden -Wait";
        ctx.text("Windows will ask for administrator permission to " + verb + " the service " + service + ".");
        int code = ctx.exec(List.of("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script),
                null, null, Proc.consoleCharset(), null);
        if (code != 0) {
            throw new TaskContext.Failure("The service was not " + (start ? "started" : "stopped")
                    + ". Did you press \"No\" on the Windows permission prompt?");
        }
    }
}
