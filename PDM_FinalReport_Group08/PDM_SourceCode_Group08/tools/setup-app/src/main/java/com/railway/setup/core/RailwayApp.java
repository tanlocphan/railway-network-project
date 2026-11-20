package com.railway.setup.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Building, starting and stopping the Railway desktop app itself. */
public final class RailwayApp {

    private static final String MAIN_CLASS = "com.railway.app.RailwayApp";
    private static volatile Process ours;

    private RailwayApp() {
    }

    public static boolean isBuilt() {
        return Files.isRegularFile(Project.appJar());
    }

    /** True when a source file or pom.xml changed after the last build. */
    public static boolean isOutdated() {
        if (!isBuilt()) {
            return true;
        }
        try {
            FileTime built = Files.getLastModifiedTime(Project.appJar());
            if (Files.getLastModifiedTime(Project.root().resolve("pom.xml")).compareTo(built) > 0) {
                return true;
            }
            try (Stream<Path> files = Files.walk(Project.root().resolve("src"))) {
                return files.filter(Files::isRegularFile).anyMatch(p -> {
                    try {
                        return Files.getLastModifiedTime(p).compareTo(built) > 0;
                    } catch (IOException e) {
                        return false;
                    }
                });
            }
        } catch (IOException e) {
            return false;
        }
    }

    public static List<ProcessHandle> running() {
        List<ProcessHandle> list = new ArrayList<>();
        Process p = ours;
        if (p != null && p.isAlive()) {
            list.add(p.toHandle());
        }
        long self = ProcessHandle.current().pid();
        ProcessHandle.allProcesses()
                .filter(h -> h.pid() != self && (p == null || h.pid() != p.pid()))
                .filter(h -> h.info().commandLine()
                        .map(c -> c.contains("railway-manager.jar") || c.contains(MAIN_CLASS))
                        .orElse(false))
                .forEach(list::add);
        return list;
    }

    public static boolean isRunning() {
        return !running().isEmpty();
    }

    public static void build(TaskContext ctx, boolean clean) throws Exception {
        if (isRunning()) {
            ctx.warn("The Railway app is open; closing it so the build can replace its jar.");
            stop(ctx);
        }
        Path wrapper = Project.mavenWrapper();
        if (!Files.exists(wrapper)) {
            throw new TaskContext.Failure("The Maven Wrapper (" + wrapper.getFileName() + ") is missing from the project folder.");
        }
        if (!Status.mavenDownloaded()) {
            ctx.text("First build on this computer: Maven and the libraries are downloaded once (about 60 MB).");
        }
        List<String> command = new ArrayList<>();
        if (Project.isWindows()) {
            command.addAll(List.of("cmd.exe", "/c", "mvnw.cmd"));
        } else {
            command.addAll(List.of("sh", "mvnw"));
        }
        command.addAll(List.of("-B", "-ntp"));
        if (clean) {
            command.add("clean");
        }
        command.addAll(List.of("package", "-DskipTests"));
        ctx.progress("Building the Railway app", -1, 0);
        int code = ctx.exec(command, Project.root(), null, StandardCharsets.UTF_8, line -> {
            if (line.startsWith("[ERROR]")) {
                ctx.error(line);
            } else if (line.startsWith("[WARNING]")) {
                ctx.dim(line);
            } else if (line.contains("BUILD SUCCESS")) {
                ctx.ok(line);
            } else {
                ctx.text(line);
            }
        });
        if (code != 0) {
            throw new TaskContext.Failure("The build failed (exit code " + code + "). The [ERROR] lines above say why.");
        }
        ctx.ok("Built " + Project.root().relativize(Project.appJar()));
    }

    /**
     * Starts the app. Its console output keeps flowing into {@code output}
     * after the task has finished.
     */
    public static void start(TaskContext ctx, Output output, Runnable onExit) throws IOException {
        if (!isBuilt()) {
            throw new TaskContext.Failure("The app has not been built yet. Press Build first.");
        }
        List<ProcessHandle> already = running();
        if (!already.isEmpty()) {
            ctx.warn("The Railway app is already open (process " + already.get(0).pid() + ").");
            return;
        }
        List<String> command = List.of(Project.javaExecutable(false).toString(), "-Dfile.encoding=UTF-8",
                "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-jar",
                Project.root().relativize(Project.appJar()).toString());
        ctx.command(String.join(" ", Proc.display(command)));
        ProcessBuilder builder = Proc.builder(command, Project.root(), null);
        builder.redirectErrorStream(true);
        Process p = builder.start();
        ours = p;
        ctx.ok("The Railway app is starting (process " + p.pid() + "). Its messages appear below.");
        Thread pump = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.line(kindOf(line), "  app │ " + line);
                }
            } catch (IOException ignored) {
                // the app closed
            }
            try {
                int code = p.waitFor();
                output.line(code == 0 ? Output.Kind.DIM : Output.Kind.WARN,
                        "The Railway app closed (exit code " + code + ").");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (ours == p) {
                ours = null;
            }
            if (onExit != null) {
                onExit.run();
            }
        }, "railway-app-output");
        pump.setDaemon(true);
        pump.start();
    }

    private static Output.Kind kindOf(String line) {
        if (line.contains("SEVERE") || line.contains("Exception")) {
            return Output.Kind.ERROR;
        }
        if (line.contains("WARNING")) {
            return Output.Kind.WARN;
        }
        return Output.Kind.DIM;
    }

    public static void stop(TaskContext ctx) throws InterruptedException {
        List<ProcessHandle> list = running();
        if (list.isEmpty()) {
            ctx.ok("The Railway app is not running.");
            return;
        }
        for (ProcessHandle h : list) {
            ctx.text("Closing the Railway app (process " + h.pid() + ")");
            h.destroy();
        }
        for (int i = 0; i < 20 && list.stream().anyMatch(ProcessHandle::isAlive); i++) {
            Thread.sleep(250);
        }
        list.stream().filter(ProcessHandle::isAlive).forEach(Proc::killTree);
        ctx.ok("The Railway app is closed.");
    }
}
