package com.railway.app.ui.components;

import com.railway.app.data.AppException;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Window;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs work in the background so the window never freezes, then hands the
 * result (or a friendly error) back on the screen thread.
 */
public final class Async {

    private static final Logger LOG = Logger.getLogger(Async.class.getName());

    @FunctionalInterface
    public interface Task<T> {
        T run() throws AppException;
    }

    @FunctionalInterface
    public interface Action {
        void run() throws AppException;
    }

    private Async() {
    }

    public static <T> void run(Component owner, Task<T> task, Consumer<T> onSuccess) {
        run(owner, task, onSuccess, null);
    }

    public static void action(Component owner, Action action, Runnable onSuccess, Consumer<AppException> onError) {
        run(owner, () -> {
            action.run();
            return Boolean.TRUE;
        }, ignored -> {
            if (onSuccess != null) {
                onSuccess.run();
            }
        }, onError);
    }

    public static <T> void run(Component owner, Task<T> task, Consumer<T> onSuccess, Consumer<AppException> onError) {
        Window window = owner == null ? null : SwingUtilities.getWindowAncestor(owner);
        if (owner instanceof Window w) {
            window = w;
        }
        Window busyWindow = window;
        if (busyWindow != null) {
            busyWindow.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return task.run();
            }

            @Override
            protected void done() {
                if (busyWindow != null) {
                    busyWindow.setCursor(Cursor.getDefaultCursor());
                }
                try {
                    T result = get();
                    if (onSuccess != null) {
                        onSuccess.accept(result);
                    }
                } catch (ExecutionException e) {
                    AppException problem = asAppException(e.getCause());
                    if (onError != null) {
                        onError.accept(problem);
                    } else {
                        Dialogs.error(owner, problem);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    private static AppException asAppException(Throwable cause) {
        if (cause instanceof AppException appException) {
            return appException;
        }
        LOG.log(Level.SEVERE, "Unexpected error in background task", cause);
        return new AppException(AppException.Kind.UNEXPECTED,
                "Something went wrong and nothing was changed. Please try again. "
                        + "If this keeps happening, contact your administrator.", cause);
    }
}
