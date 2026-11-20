package com.railway.setup.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Starting other programs (git, Maven, sc.exe, mysqld) with the right Java on the PATH. */
public final class Proc {

    public record Result(int exitCode, String output) {
        public boolean ok() {
            return exitCode == 0;
        }
    }

    private Proc() {
    }

    /**
     * Every child process gets JAVA_HOME and PATH pointing to the JDK the setup
     * tool runs on. That is why a freshly installed JDK works without reopening
     * any window.
     */
    public static ProcessBuilder builder(List<String> command, Path dir, Map<String, String> extraEnv) {
        ProcessBuilder builder = new ProcessBuilder(command);
        if (dir != null) {
            builder.directory(dir.toFile());
        }
        Map<String, String> env = builder.environment();
        String javaHome = Project.javaHome().toString();
        env.put("JAVA_HOME", javaHome);
        String pathKey = env.keySet().stream().filter(k -> k.equalsIgnoreCase("PATH")).findFirst().orElse("PATH");
        String path = env.getOrDefault(pathKey, "");
        env.put(pathKey, javaHome + File.separator + "bin" + File.pathSeparator + path);
        // Ask Maven / Java children to print UTF-8, so Vietnamese text reads correctly.
        env.put("MAVEN_OPTS", "-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8");
        env.put("GIT_TERMINAL_PROMPT", "0");
        env.put("GIT_PAGER", "cat");
        env.put("LC_ALL", "C.UTF-8");
        if (extraEnv != null) {
            env.putAll(extraEnv);
        }
        return builder;
    }

    /** Runs a short command and collects its output (no console). */
    public static Result capture(List<String> command, Path dir, int timeoutSeconds) {
        return capture(command, dir, timeoutSeconds, StandardCharsets.UTF_8);
    }

    public static Result capture(List<String> command, Path dir, int timeoutSeconds, Charset charset) {
        try {
            ProcessBuilder builder = builder(command, dir, null);
            builder.redirectErrorStream(true);
            Process p = builder.start();
            p.getOutputStream().close();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            Thread pump = new Thread(() -> {
                try (InputStream in = p.getInputStream()) {
                    in.transferTo(buffer);
                } catch (IOException ignored) {
                    // process ended
                }
            }, "capture");
            pump.setDaemon(true);
            pump.start();
            if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                killTree(p);
                return new Result(-1, "timed out");
            }
            pump.join(2000);
            return new Result(p.exitValue(), buffer.toString(charset).stripTrailing());
        } catch (IOException e) {
            return new Result(-2, Tasks.describe(e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(-3, "interrupted");
        }
    }

    public static void killTree(Process p) {
        killTree(p.toHandle());
    }

    public static void killTree(ProcessHandle handle) {
        handle.descendants().forEach(ProcessHandle::destroyForcibly);
        handle.destroyForcibly();
    }

    /** For printing a command: quote arguments that contain spaces. */
    public static List<String> display(List<String> command) {
        List<String> out = new ArrayList<>();
        for (String arg : command) {
            out.add(arg.isEmpty() || arg.contains(" ") ? "\"" + arg + "\"" : arg);
        }
        return out;
    }

    /** Charset used by Windows console programs such as sc.exe. */
    public static Charset consoleCharset() {
        String name = System.getProperty("native.encoding");
        try {
            return name != null ? Charset.forName(name) : Charset.defaultCharset();
        } catch (Exception e) {
            return Charset.defaultCharset();
        }
    }
}
