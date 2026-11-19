package com.railway.setup.core;

import javax.swing.SwingUtilities;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs one task at a time in the background. While a task runs every action
 * button in the window is disabled, so two jobs never fight over MySQL or the
 * build folder.
 */
public final class Tasks {

    public interface Body {
        void run(TaskContext ctx) throws Exception;
    }

    public interface ProgressListener {
        void update(String label, long value, long max);
    }

    /** Notified on the Swing thread. title == null means idle. */
    public interface StateListener {
        void changed(String title, Output output);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "setup-task");
        t.setDaemon(true);
        return t;
    });
    private static final List<StateListener> STATE = new CopyOnWriteArrayList<>();
    private static final List<ProgressListener> PROGRESS = new CopyOnWriteArrayList<>();
    private static final List<Runnable> FINISHED = new CopyOnWriteArrayList<>();

    private static volatile TaskContext running;
    private static volatile String runningTitle;

    private Tasks() {
    }

    public static boolean isBusy() {
        return running != null;
    }

    public static String runningTitle() {
        return runningTitle;
    }

    public static void onState(StateListener listener) {
        STATE.add(listener);
    }

    public static void onProgress(ProgressListener listener) {
        PROGRESS.add(listener);
    }

    /** Called (on the Swing thread) after every task, to refresh status displays. */
    public static void onFinished(Runnable listener) {
        FINISHED.add(listener);
    }

    public static void cancel() {
        TaskContext ctx = running;
        if (ctx != null) {
            ctx.cancel();
        }
    }

    /** Starts a task; returns false when another one is still running. */
    public static boolean run(String title, Output output, Body body) {
        return run(title, output, body, null);
    }

    public static synchronized boolean run(String title, Output output, Body body, Runnable afterSuccess) {
        if (running != null) {
            return false;
        }
        TaskContext ctx = new TaskContext(output, (label, value, max) ->
                SwingUtilities.invokeLater(() -> PROGRESS.forEach(l -> l.update(label, value, max))));
        running = ctx;
        runningTitle = title;
        fireState(title, output);
        EXECUTOR.execute(() -> {
            Instant start = Instant.now();
            boolean success = false;
            output.line(Output.Kind.TITLE, "▶ " + title);
            try {
                body.run(ctx);
                success = !ctx.isCancelled();
                output.line(Output.Kind.OK, "✔ " + title + " — done in " + seconds(start));
            } catch (TaskContext.Cancelled e) {
                output.line(Output.Kind.WARN, "■ " + title + " — cancelled.");
            } catch (TaskContext.Failure e) {
                output.line(Output.Kind.ERROR, "✖ " + e.getMessage());
            } catch (Exception e) {
                output.line(Output.Kind.ERROR, "✖ " + title + " failed: " + describe(e));
                SetupLog.error(title, e);
            } finally {
                output.line(Output.Kind.DIM, "");
                running = null;
                runningTitle = null;
                boolean ok = success;
                SwingUtilities.invokeLater(() -> {
                    PROGRESS.forEach(l -> l.update(null, 0, 0));
                    fireState(null, output);
                    if (ok && afterSuccess != null) {
                        afterSuccess.run();
                    }
                    FINISHED.forEach(Runnable::run);
                });
            }
        });
        return true;
    }

    private static void fireState(String title, Output output) {
        Runnable fire = () -> STATE.forEach(l -> l.changed(title, output));
        if (SwingUtilities.isEventDispatchThread()) {
            fire.run();
        } else {
            SwingUtilities.invokeLater(fire);
        }
    }

    private static String seconds(Instant start) {
        long ms = Duration.between(start, Instant.now()).toMillis();
        return ms < 1000 ? ms + " ms" : String.format("%.1f s", ms / 1000.0);
    }

    public static String describe(Throwable e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return message;
    }
}
