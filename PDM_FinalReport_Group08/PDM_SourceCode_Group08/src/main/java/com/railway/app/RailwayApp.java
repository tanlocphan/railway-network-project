package com.railway.app;

import com.railway.app.config.AppLog;
import com.railway.app.ui.MainFrame;
import com.railway.app.ui.theme.AppTheme;

import javax.swing.SwingUtilities;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point of the Railway Network and Timetable desktop application.
 */
public final class RailwayApp {

    private static final Logger LOG = Logger.getLogger(RailwayApp.class.getName());

    private RailwayApp() {
    }

    public static void main(String[] args) {
        AppLog.init();
        LOG.info("Application starting");
        Thread.setDefaultUncaughtExceptionHandler((thread, error) ->
                LOG.log(Level.SEVERE, "Uncaught error on thread " + thread.getName(), error));

        AppTheme.install();
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
            frame.start();
        });
    }
}
