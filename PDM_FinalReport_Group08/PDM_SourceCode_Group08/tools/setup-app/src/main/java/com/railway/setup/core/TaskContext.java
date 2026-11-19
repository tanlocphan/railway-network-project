package com.railway.setup.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Handed to a running task: write output, report progress, run commands and
 * notice when the user pressed Cancel.
 */
public final class TaskContext {

    /** Thrown when the user cancels; the runner reports it calmly. */
    public static final class Cancelled extends RuntimeException {
        public Cancelled() {
            super("Cancelled");
        }
    }

    /** A failure whose message is already written for the user. */
    public static final class Failure extends RuntimeException {
        public Failure(String message) {
            super(message);
        }
    }

    private final Output output;
    private final Tasks.ProgressListener progress;
    private volatile boolean cancelled;
    private volatile Process process;

    TaskContext(Output output, Tasks.ProgressListener progress) {
        this.output = output;
        this.progress = progress;
    }

    public void text(String s) {
        output.line(Output.Kind.TEXT, s);
    }

    public void dim(String s) {
        output.line(Output.Kind.DIM, s);
    }

    public void ok(String s) {
        output.line(Output.Kind.OK, s);
    }

    public void warn(String s) {
        output.line(Output.Kind.WARN, s);
    }

    public void error(String s) {
        output.line(Output.Kind.ERROR, s);
    }

    public void command(String s) {
        output.line(Output.Kind.COMMAND, "$ " + s);
    }

    public void step(String s) {
        output.line(Output.Kind.TITLE, s);
    }

    public Output output() {
        return output;
    }

    /** value &lt; 0 means "busy, no idea how long". */
    public void progress(String label, long value, long max) {
        progress.update(label, value, max);
    }

    public void checkCancelled() {
        if (cancelled) {
            throw new Cancelled();
        }
    }

    public boolean isCancelled() {
        return cancelled;
    }

    void cancel() {
        cancelled = true;
        Process p = process;
        if (p != null) {
            Proc.killTree(p);
        }
    }

    public int exec(List<String> command, Path dir) throws IOException {
        return exec(command, dir, null, StandardCharsets.UTF_8, null);
    }

    /**
     * Runs a program, streams every output line to the console and returns its
     * exit code. Cancel kills the program and everything it started.
     */
    public int exec(List<String> command, Path dir, Map<String, String> extraEnv, Charset charset,
                    Consumer<String> lineFilter) throws IOException {
        checkCancelled();
        command(String.join(" ", Proc.display(command)));
        ProcessBuilder builder = Proc.builder(command, dir, extraEnv);
        builder.redirectErrorStream(true);
        Process p = builder.start();
        process = p;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), charset))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (lineFilter != null) {
                    lineFilter.accept(line);
                } else {
                    text(line);
                }
            }
            return p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Proc.killTree(p);
            throw new Cancelled();
        } finally {
            process = null;
            checkCancelled();
        }
    }
}
