package com.railway.setup.ui;

import com.railway.setup.core.Project;
import com.railway.setup.core.Status;
import com.railway.setup.core.Tasks;
import com.railway.setup.ui.pages.BuildPage;
import com.railway.setup.ui.pages.DatabasePage;
import com.railway.setup.ui.pages.EnvironmentPage;
import com.railway.setup.ui.pages.GitPage;
import com.railway.setup.ui.pages.LogsPage;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.LinkedHashMap;
import java.util.Map;

/** Sidebar on the left, one page at a time on the right, task progress at the bottom. */
public class MainWindow extends JFrame {

    private final CardLayout cards = new CardLayout();
    private final JPanel pages = new JPanel(cards);
    private final Map<String, Page> byKey = new LinkedHashMap<>();
    private final Sidebar sidebar = new Sidebar(this::show);
    private final JLabel taskLabel = Ui.label("Ready", 12, Theme.TEXT_SECONDARY);
    private final JProgressBar progress = new JProgressBar();

    public MainWindow() {
        super("Railway Setup");
        setIconImages(Theme.windowIcons());
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        getRootPane().putClientProperty("JRootPane.titleBarBackground", Theme.NAVY);
        getRootPane().putClientProperty("JRootPane.titleBarForeground", java.awt.Color.WHITE);
        Ui.setDialogParent(this);

        sidebar.addSection("Get ready");
        page("environment", "Environment", "home", new EnvironmentPage());
        page("database", "Database", "database", new DatabasePage());
        page("build", "Build & Run", "rocket", new BuildPage());
        sidebar.addSection("Team");
        page("git", "Git", "git", new GitPage());
        page("logs", "Logs", "file", new LogsPage());

        JPanel right = new JPanel(new BorderLayout());
        right.add(pages, BorderLayout.CENTER);
        right.add(statusBar(), BorderLayout.SOUTH);

        JPanel root = new JPanel(new BorderLayout());
        root.add(sidebar, BorderLayout.WEST);
        root.add(right, BorderLayout.CENTER);
        setContentPane(root);

        setMinimumSize(new Dimension(1100, 720));
        setSize(new Dimension(1280, 860));
        setLocationRelativeTo(null);
        show("environment");

        byKey.values().forEach(Page::listen);
        Tasks.onFinished(Status::refresh);
        Status.refresh();
        // MySQL or the app may be started / stopped outside Setup
        Timer poll = new Timer(15_000, e -> {
            if (!Tasks.isBusy() && isActive()) {
                Status.refresh();
            }
        });
        poll.start();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
                if (!Tasks.isBusy()) {
                    Status.refresh();
                }
            }

            @Override
            public void windowClosed(WindowEvent e) {
                System.exit(0);
            }
        });
    }

    private void page(String key, String title, String icon, Page page) {
        byKey.put(key, page);
        pages.add(page, key);
        sidebar.addItem(key, title, icon);
    }

    private void show(String key) {
        cards.show(pages, key);
        sidebar.select(key);
        byKey.get(key).shown();
    }

    private JPanel statusBar() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setBackground(Theme.SURFACE);
        bar.setBorder(new javax.swing.border.CompoundBorder(new MatteBorder(1, 0, 0, 0, Theme.BORDER),
                new EmptyBorder(6, 16, 6, 16)));
        progress.setPreferredSize(new Dimension(220, 6));
        progress.setVisible(false);
        JLabel version = Ui.label("Java " + System.getProperty("java.version") + "  ·  " + Project.root(), 12, Theme.TEXT_MUTED);
        bar.add(taskLabel, BorderLayout.WEST);
        JPanel middle = new JPanel(new java.awt.GridBagLayout());
        middle.setOpaque(false);
        middle.add(progress);
        bar.add(middle, BorderLayout.CENTER);
        bar.add(version, BorderLayout.EAST);

        Tasks.onState((title, output) -> {
            taskLabel.setText(title == null ? "Ready" : "Working: " + title + "…");
            taskLabel.setForeground(title == null ? Theme.TEXT_SECONDARY : Theme.ORANGE_DARK);
            progress.setVisible(title != null);
            progress.setIndeterminate(true);
        });
        Tasks.onProgress((label, value, max) -> {
            if (label == null) {
                progress.setVisible(false);
                return;
            }
            progress.setVisible(true);
            if (value < 0 || max <= 0) {
                progress.setIndeterminate(true);
            } else {
                progress.setIndeterminate(false);
                progress.setMaximum(1000);
                progress.setValue((int) (value * 1000 / max));
            }
            String title = Tasks.runningTitle();
            taskLabel.setText((title == null ? "" : title + " · ") + label
                    + (max > 0 && value >= 0 ? " " + (value * 100 / max) + "%" : "") + "…");
        });
        return bar;
    }
}
