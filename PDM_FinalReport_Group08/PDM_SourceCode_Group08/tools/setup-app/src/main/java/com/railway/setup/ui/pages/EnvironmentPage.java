package com.railway.setup.ui.pages;

import com.railway.setup.core.MySqlService;
import com.railway.setup.core.Ops;
import com.railway.setup.core.PortableMySql;
import com.railway.setup.core.Project;
import com.railway.setup.core.RailwayApp;
import com.railway.setup.core.Settings;
import com.railway.setup.core.Status;
import com.railway.setup.core.TaskContext;
import com.railway.setup.ui.Card;
import com.railway.setup.ui.Page;
import com.railway.setup.ui.Pill;
import com.railway.setup.ui.Theme;
import com.railway.setup.ui.Ui;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * The first page: a checklist of everything the Railway app needs, which
 * MySQL to use, how to sign in to it, and one button that does it all.
 */
public class EnvironmentPage extends Page {

    private final CheckRow javaRow = new CheckRow("java", "Java (JDK 17 or newer)");
    private final CheckRow mavenRow = new CheckRow("wrench", "Build tool (Maven Wrapper)");
    private final CheckRow mysqlRow = new CheckRow("server", "MySQL server");
    private final CheckRow loginRow = new CheckRow("key", "Sign-in to MySQL");
    private final CheckRow dbRow = new CheckRow("database", "Database " + Project.DATABASE);
    private final CheckRow buildRow = new CheckRow("rocket", "Railway app build");

    private final JButton installJdk = Ui.secondary("Install JDK 21", "download");
    private final JButton startMysql = Ui.secondary("Start", "play");
    private final JButton fixLogin = Ui.secondary("Fix", "edit");
    private final JButton createDb = Ui.secondary("Create", "database");
    private final JButton build = Ui.secondary("Build", "wrench");

    private final Card installedCard = new Card(new BorderLayout(0, 14), 18);
    private final Pill installedPill = new Pill();
    private final JLabel installedDetail = Ui.label(" ", 12.5f, Theme.TEXT_SECONDARY);
    private final JButton useInstalled = Ui.secondary("Use this", "check");
    private final JButton startService = Ui.secondary("Start service", "play");
    private final JButton stopService = Ui.secondary("Stop service", "stop");

    private final Card portableCard = new Card(new BorderLayout(0, 14), 18);
    private final Pill portablePill = new Pill();
    private final JLabel portableDetail = Ui.label(" ", 12.5f, Theme.TEXT_SECONDARY);
    private final JButton usePortable = Ui.secondary("Use this", "check");
    private final JButton installPortable = Ui.primary("Install (233 MB)", "download");
    private final JButton startPortable = Ui.secondary("Start", "play");
    private final JButton stopPortable = Ui.secondary("Stop", "stop");
    private final JButton removePortable = Ui.danger("Remove", "trash");

    private final JTextField host = new JTextField(14);
    private final JTextField port = new JTextField(5);
    private final JTextField user = new JTextField(10);
    private final JPasswordField password = new JPasswordField(12);
    private final JButton save = Ui.primary("Save & test sign-in", "key");
    private final JLabel connectionNote = Ui.label(" ", 12, Theme.TEXT_MUTED);

    public EnvironmentPage() {
        super("Environment", "Everything the Railway app needs. All green means you can start testing.",
                "Environment console");

        JButton refresh = Ui.secondary("Refresh", "refresh");
        refresh.addActionListener(e -> Status.refresh());
        JButton everything = Ui.primary("Set up everything & open the app", "rocket");
        everything.addActionListener(e -> setUpEverything());
        actions.add(refresh);
        actions.add(everything);

        addSection("Checklist");
        block(checklist());
        addSection("MySQL server");
        block(grid(2, installedCard(), portableCard()));
        addSection("Connection");
        block(connectionCard());

        installJdk.addActionListener(e -> installJdk());
        startMysql.addActionListener(e -> Ui.run("Start MySQL", console, ctx -> Ops.startMySql(ctx, Settings.get())));
        fixLogin.addActionListener(e -> password.requestFocusInWindow());
        createDb.addActionListener(e -> Ui.run("Create database", console, ctx -> Ops.resetDatabase(ctx, Settings.get())));
        build.addActionListener(e -> Ui.run("Build the Railway app", console, ctx -> RailwayApp.build(ctx, false)));

        console.line(com.railway.setup.core.Output.Kind.DIM, "Project folder: " + Project.root());
        console.line(com.railway.setup.core.Output.Kind.DIM, "Java: " + Project.javaHome());
    }

    // ----- layout -------------------------------------------------------------

    private JComponent checklist() {
        Card card = new Card(new BorderLayout(), 6);
        JPanel rows = Ui.column(0,
                javaRow, Ui.separator(), mavenRow, Ui.separator(), mysqlRow, Ui.separator(),
                loginRow, Ui.separator(), dbRow, Ui.separator(), buildRow);
        javaRow.action(installJdk);
        mysqlRow.action(startMysql);
        loginRow.action(fixLogin);
        dbRow.action(createDb);
        buildRow.action(build);
        card.add(rows, BorderLayout.CENTER);
        return card;
    }

    private JComponent installedCard() {
        JPanel top = new JPanel(new BorderLayout(12, 0));
        top.setOpaque(false);
        top.add(Ui.heading("server", "Installed MySQL", "Windows service from the MySQL installer"),
                BorderLayout.CENTER);
        top.add(pillBox(installedPill), BorderLayout.EAST);
        installedCard.add(top, BorderLayout.NORTH);
        installedCard.add(installedDetail, BorderLayout.CENTER);
        installedCard.add(Ui.row(8, useInstalled, startService, stopService), BorderLayout.SOUTH);

        useInstalled.addActionListener(e -> switchMode(Settings.Mode.INSTALLED));
        startService.addActionListener(e -> serviceControl(true));
        stopService.addActionListener(e -> serviceControl(false));
        return installedCard;
    }

    private JComponent portableCard() {
        JPanel top = new JPanel(new BorderLayout(12, 0));
        top.setOpaque(false);
        top.add(Ui.heading("download", "Portable MySQL " + PortableMySql.VERSION,
                "Port " + PortableMySql.PORT + " · root / " + PortableMySql.ROOT_PASSWORD + " · only for this project"),
                BorderLayout.CENTER);
        top.add(pillBox(portablePill), BorderLayout.EAST);
        portableCard.add(top, BorderLayout.NORTH);
        portableCard.add(portableDetail, BorderLayout.CENTER);
        portableCard.add(Ui.row(8, usePortable, installPortable, startPortable, stopPortable, removePortable), BorderLayout.SOUTH);

        usePortable.addActionListener(e -> switchMode(Settings.Mode.PORTABLE));
        installPortable.addActionListener(e -> {
            if (Ui.confirm("Install the portable MySQL",
                    "Setup downloads MySQL " + PortableMySql.VERSION + " (233 MB) into<br><b>" + PortableMySql.home()
                            + "</b><br><br>It uses about 1 GB of disk, runs on port " + PortableMySql.PORT
                            + " and does not touch any MySQL you already have.", "Download and install")) {
                Settings s = Settings.get();
                Ui.run("Install the portable MySQL", console, ctx -> {
                    PortableMySql.install(ctx);
                    s.mode = Settings.Mode.PORTABLE;
                    s.save();
                    ctx.ok("This project now uses the portable MySQL (config files updated).");
                });
            }
        });
        startPortable.addActionListener(e -> Ui.run("Start the portable MySQL", console, PortableMySql::start));
        stopPortable.addActionListener(e -> Ui.run("Stop the portable MySQL", console, PortableMySql::stop));
        removePortable.addActionListener(e -> {
            if (Ui.confirm("Remove the portable MySQL",
                    "This deletes <b>" + PortableMySql.home() + "</b> including every database inside it.", "Remove")) {
                Ui.run("Remove the portable MySQL", console, PortableMySql::remove);
            }
        });
        return portableCard;
    }

    private static JComponent pillBox(Pill pill) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(pill, BorderLayout.NORTH);
        return p;
    }

    private JComponent connectionCard() {
        Card card = new Card(new BorderLayout(0, 14), 18);
        card.add(Ui.heading("key", "How Setup and the app sign in",
                        "Saved in config/setup.properties and config/connection.properties — both ignored by git."),
                BorderLayout.NORTH);

        password.putClientProperty("JPasswordField.showRevealButton", true);
        host.putClientProperty("JTextField.placeholderText", "localhost");
        password.putClientProperty("JTextField.placeholderText", "MySQL root password");

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(0, 0, 4, 12);
        c.anchor = GridBagConstraints.WEST;
        String[] labels = {"Server", "Port", "User", "Password"};
        JComponent[] fields = {host, port, user, password};
        for (int i = 0; i < labels.length; i++) {
            c.gridx = i;
            c.gridy = 0;
            form.add(Ui.caption(labels[i]), c);
            c.gridy = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = i == 0 || i == 3 ? 1 : 0;
            fields[i].setPreferredSize(new Dimension(i == 1 ? 80 : (i == 2 ? 120 : 170), 34));
            form.add(fields[i], c);
            c.fill = GridBagConstraints.NONE;
        }
        c.gridx = labels.length;
        c.gridy = 1;
        c.weightx = 0;
        c.insets = new Insets(0, 0, 4, 0);
        form.add(save, c);
        card.add(form, BorderLayout.CENTER);
        card.add(connectionNote, BorderLayout.SOUTH);

        save.addActionListener(e -> saveConnection());
        fillConnection();
        return card;
    }

    // ----- actions -------------------------------------------------------------

    private void fillConnection() {
        Settings s = Settings.get();
        boolean portable = s.mode == Settings.Mode.PORTABLE;
        host.setText(s.host());
        port.setText(String.valueOf(s.port()));
        user.setText(s.user());
        password.setText(s.password());
        for (JComponent f : new JComponent[]{host, port, user, password}) {
            f.setEnabled(!portable);
        }
        Ui.allow(save, !portable);
        connectionNote.setText(portable
                ? "The portable MySQL always uses 127.0.0.1:" + PortableMySql.PORT + " with root / " + PortableMySql.ROOT_PASSWORD + "."
                : "Type the password you chose when installing MySQL. Setup needs an account that may create databases (root).");
    }

    private void saveConnection() {
        Settings s = Settings.get();
        int p;
        try {
            p = Integer.parseInt(port.getText().trim());
        } catch (NumberFormatException e) {
            Ui.info("Port", "The port must be a number, usually 3306.");
            return;
        }
        s.installedHost = host.getText().isBlank() ? "localhost" : host.getText().trim();
        s.installedPort = p;
        s.installedUser = user.getText().isBlank() ? "root" : user.getText().trim();
        s.installedPassword = new String(password.getPassword());
        Ui.run("Save & test sign-in", console, ctx -> {
            s.save();
            ctx.ok("Saved config/setup.properties and config/connection.properties.");
            Ops.testSignIn(ctx, s);
        });
    }

    private void switchMode(Settings.Mode mode) {
        Settings s = Settings.get();
        s.mode = mode;
        try {
            s.save();
            console.line(com.railway.setup.core.Output.Kind.OK, "Now using the "
                    + (mode == Settings.Mode.PORTABLE ? "portable MySQL (127.0.0.1:" + PortableMySql.PORT + ")"
                    : "installed MySQL (" + s.installedHost + ":" + s.installedPort + ")") + ". Config files updated.");
        } catch (Exception e) {
            console.line(com.railway.setup.core.Output.Kind.ERROR, "Could not save the settings: " + e.getMessage());
        }
        fillConnection();
        Status.refresh();
    }

    private void serviceControl(boolean start) {
        Status s = Status.last();
        if (s == null || s.services.isEmpty()) {
            Ui.info("No MySQL service", "No Windows service named MySQL… was found on this computer.");
            return;
        }
        MySqlService.Service service = s.services.stream()
                .filter(x -> start != x.running()).findFirst().orElse(s.services.get(0));
        Ui.run((start ? "Start" : "Stop") + " service " + service.name(), console,
                ctx -> MySqlService.control(ctx, service.name(), start));
    }

    private void installJdk() {
        if (Ui.confirm("Install JDK 21",
                "Setup asks winget to install Eclipse Temurin 21 JDK (about 190 MB). Windows may show a permission prompt."
                        + "<br><br>When it finishes, close Setup and open <b>Setup.exe</b> again: it finds the new JDK by itself.",
                "Install")) {
            Ui.run("Install JDK 21", console, ctx -> {
                int code = ctx.exec(java.util.List.of("winget", "install", "--id", "EclipseAdoptium.Temurin.21.JDK", "-e",
                        "--accept-package-agreements", "--accept-source-agreements"), null);
                if (code != 0) {
                    throw new TaskContext.Failure("winget could not install the JDK (exit code " + code
                            + "). Download it from https://adoptium.net instead.");
                }
                ctx.ok("JDK installed. Close Setup and open Setup.exe again.");
            });
        }
    }

    /** One click for a fresh clone. Questions are asked before the work starts. */
    private void setUpEverything() {
        Settings s = Settings.get();
        Status st = Status.last();
        if (s.mode == Settings.Mode.INSTALLED && st != null && !st.serverOpen && st.services.isEmpty()) {
            if (!Ui.confirm("No MySQL found",
                    "No MySQL is answering on " + s.installedHost + ":" + s.installedPort
                            + " and no MySQL service is installed.<br><br>Setup can download a portable MySQL "
                            + PortableMySql.VERSION + " (233 MB) just for this project.", "Use the portable MySQL")) {
                return;
            }
            switchMode(Settings.Mode.PORTABLE);
        } else if (s.mode == Settings.Mode.PORTABLE && !PortableMySql.isInstalled()) {
            if (!Ui.confirm("Install the portable MySQL",
                    "This project is set to use the portable MySQL, which is not installed yet.<br><br>"
                            + "Setup downloads MySQL " + PortableMySql.VERSION + " (233 MB) into " + PortableMySql.home()
                            + " first.", "Download and continue")) {
                return;
            }
        } else if (s.mode == Settings.Mode.INSTALLED && st != null && st.serverOpen && !st.loginOk) {
            Object[] options = {"Type the password", "Use the portable MySQL", "Cancel"};
            int choice = JOptionPane.showOptionDialog(this,
                    "<html><div style='width:380px'>MySQL on " + s.installedHost + ":" + s.installedPort
                            + " does not accept the saved sign-in.<br><br>" + Ui.escape(st.loginError)
                            + "<br><br>Type the right password in <b>Connection</b>, or let Setup run its own portable MySQL"
                            + " on port " + PortableMySql.PORT + ".</div></html>",
                    "Sign-in fails", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
            if (choice == 0) {
                password.requestFocusInWindow();
                password.selectAll();
                return;
            }
            if (choice != 1) {
                return;
            }
            switchMode(Settings.Mode.PORTABLE);
        }
        Settings now = Settings.get();
        Ui.run("Set up everything", console, ctx -> Ops.setUpEverything(ctx, now, console, Status::refresh));
    }

    // ----- status ----------------------------------------------------------------

    @Override
    protected void showStatus(Status s) {
        Settings settings = Settings.get();

        javaRow.set(s.jdk ? Pill.Tone.OK : Pill.Tone.ERROR, s.jdk ? "Ready" : "JRE only",
                "Java " + s.javaVersion + " — " + s.javaHome + (s.jdk ? "" : "  (the build needs a JDK, which includes javac)"));
        Ui.allow(installJdk, !s.jdk && Project.isWindows());
        installJdk.setVisible(!s.jdk);

        mavenRow.set(s.mavenReady ? Pill.Tone.OK : Pill.Tone.INFO, s.mavenReady ? "Ready" : "First build",
                s.mavenReady ? "mvnw.cmd — Maven is already downloaded, nothing to install"
                        : "mvnw.cmd downloads Maven by itself during the first build — nothing to install");

        String where = settings.host() + ":" + settings.port();
        String which = settings.mode == Settings.Mode.PORTABLE ? "Portable MySQL" : "Installed MySQL";
        mysqlRow.set(s.serverOpen ? Pill.Tone.OK : Pill.Tone.ERROR, s.serverOpen ? "Running" : "Stopped",
                which + " on " + where + (s.serverVersion != null ? " — version " + s.serverVersion : ""));
        boolean canStart = !s.serverOpen && (settings.mode == Settings.Mode.PORTABLE
                ? s.portableInstalled : s.stoppedService() != null);
        startMysql.setVisible(!s.serverOpen);
        Ui.allow(startMysql, canStart);
        startMysql.setToolTipText(canStart ? null : "Nothing to start: install the portable MySQL below");

        if (!s.serverOpen) {
            loginRow.set(Pill.Tone.NEUTRAL, "Waiting", "Start MySQL first");
        } else if (s.loginOk) {
            loginRow.set(Pill.Tone.OK, "Signed in", settings.describe()
                    + (settings.appUsesRestrictedAccount ? " — the app signs in as " + Settings.APP_ACCOUNT : ""));
        } else {
            loginRow.set(Pill.Tone.ERROR, "Refused", s.loginError);
        }
        fixLogin.setVisible(s.serverOpen && !s.loginOk && settings.mode == Settings.Mode.INSTALLED);

        if (!s.loginOk) {
            dbRow.set(Pill.Tone.NEUTRAL, "Waiting", "Sign in first");
        } else if (!s.dbExists) {
            dbRow.set(Pill.Tone.ERROR, "Missing", "Not created yet — Create runs 01, 02 and 03 from database/");
        } else if (!s.missingRoutines().isEmpty()) {
            dbRow.set(Pill.Tone.WARN, "Incomplete", s.missingRoutines().size() + " of " + s.routinesExpected.size()
                    + " routines missing — reload them on the Database page");
        } else {
            long rows = s.rowCounts.values().stream().mapToLong(Long::longValue).sum();
            dbRow.set(Pill.Tone.OK, "Ready", s.rowCounts.size() + " tables, " + rows + " rows, "
                    + s.routinesFound.size() + " routines");
        }
        createDb.setVisible(s.loginOk && !s.dbExists);

        if (!s.appBuilt) {
            buildRow.set(Pill.Tone.ERROR, "Not built", "target/railway-manager.jar does not exist yet");
        } else if (s.appOutdated) {
            buildRow.set(Pill.Tone.WARN, "Outdated", "The code changed after the last build (" + ago(s.appBuiltAt) + ")");
        } else {
            buildRow.set(Pill.Tone.OK, s.appRunning ? "Open" : "Built",
                    "target/railway-manager.jar — built " + ago(s.appBuiltAt));
        }
        build.setVisible(!s.appBuilt || s.appOutdated);

        // MySQL cards
        boolean installedActive = settings.mode == Settings.Mode.INSTALLED;
        installedCard.outline(installedActive ? Theme.ORANGE : Theme.BORDER, installedActive ? 2f : 1f);
        portableCard.outline(!installedActive ? Theme.ORANGE : Theme.BORDER, !installedActive ? 2f : 1f);

        if (s.services.isEmpty()) {
            installedPill.set(installedActive ? "In use · not found" : "Not found", installedActive ? Pill.Tone.WARN : Pill.Tone.NEUTRAL);
            installedDetail.setText("<html>No MySQL Windows service on this computer."
                    + (s.installedFolder != null ? "<br>Files found in " + s.installedFolder : "") + "</html>");
        } else {
            boolean anyRunning = s.services.stream().anyMatch(MySqlService.Service::running);
            installedPill.set((installedActive ? "In use · " : "") + (anyRunning ? "running" : "stopped"),
                    installedActive ? Pill.Tone.ACCENT : (anyRunning ? Pill.Tone.OK : Pill.Tone.NEUTRAL));
            installedDetail.setText("<html>Service " + s.services.stream()
                    .map(x -> "<b>" + x.name() + "</b> (" + x.state().toLowerCase() + ")").collect(Collectors.joining(", "))
                    + (s.installedFolder != null ? "<br>" + s.installedFolder : "") + "</html>");
        }
        useInstalled.setVisible(!installedActive);
        Ui.allow(startService, !s.services.isEmpty() && s.services.stream().anyMatch(x -> !x.running()));
        Ui.allow(stopService, s.services.stream().anyMatch(MySqlService.Service::running));

        if (!PortableMySql.isSupported()) {
            portablePill.set("Windows only", Pill.Tone.NEUTRAL);
            portableDetail.setText("On macOS / Linux install MySQL 8.0 with your package manager.");
        } else if (!s.portableInstalled) {
            portablePill.set(installedActive ? "Not installed" : "In use · not installed",
                    installedActive ? Pill.Tone.NEUTRAL : Pill.Tone.WARN);
            portableDetail.setText("<html>Best choice if you have no MySQL or forgot the root password.<br>"
                    + "Downloaded once into " + PortableMySql.home() + "</html>");
        } else {
            portablePill.set((installedActive ? "" : "In use · ") + (s.portableRunning ? "running" : "stopped"),
                    !installedActive ? Pill.Tone.ACCENT : (s.portableRunning ? Pill.Tone.OK : Pill.Tone.NEUTRAL));
            portableDetail.setText("<html>" + PortableMySql.home() + "<br>Keeps running after Setup is closed — press Stop when done.</html>");
        }
        usePortable.setVisible(installedActive && PortableMySql.isSupported());
        installPortable.setVisible(!s.portableInstalled && PortableMySql.isSupported());
        startPortable.setVisible(s.portableInstalled);
        stopPortable.setVisible(s.portableInstalled);
        removePortable.setVisible(s.portableInstalled);
        Ui.allow(startPortable, !s.portableRunning);
        Ui.allow(stopPortable, s.portableRunning);

        if (!host.isFocusOwner() && !port.isFocusOwner() && !user.isFocusOwner() && !password.isFocusOwner()) {
            fillConnection();
        }
    }

    static String ago(Instant when) {
        if (when == null) {
            return "never";
        }
        long minutes = Duration.between(when, Instant.now()).toMinutes();
        if (minutes < 1) {
            return "just now";
        }
        if (minutes < 60) {
            return minutes + " min ago";
        }
        if (minutes < 60 * 24) {
            return (minutes / 60) + " h ago";
        }
        return (minutes / (60 * 24)) + " days ago";
    }

    /** One line of the checklist: icon, title, detail, status pill, optional action. */
    private static final class CheckRow extends JPanel {
        private final JPanel iconHolder = new JPanel(new GridBagLayout());
        private final JLabel detail = Ui.label(" ", 12.5f, Theme.TEXT_SECONDARY);
        private final Pill pill = new Pill("Checking…", Pill.Tone.NEUTRAL);
        private final JPanel right = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 10, 0));
        private final String iconName;

        CheckRow(String iconName, String title) {
            super(new BorderLayout(14, 0));
            this.iconName = iconName;
            setOpaque(false);
            setBorder(new EmptyBorder(12, 14, 12, 14));
            iconHolder.setOpaque(false);
            setIcon(Theme.TEXT_MUTED, Theme.GRAY_SOFT);
            add(iconHolder, BorderLayout.WEST);
            add(Ui.column(2, Ui.strong(title, 13.5f), detail), BorderLayout.CENTER);
            right.setOpaque(false);
            right.add(pill);
            add(right, BorderLayout.EAST);
        }

        void action(JButton button) {
            right.add(button, 0);
            button.setVisible(false);
        }

        void set(Pill.Tone tone, String state, String text) {
            pill.set(state, tone);
            detail.setText(text == null ? " " : text);
            detail.setToolTipText(text);
            switch (tone) {
                case OK -> setIcon(Theme.GREEN, Theme.GREEN_SOFT);
                case WARN -> setIcon(Theme.AMBER, Theme.AMBER_SOFT);
                case ERROR -> setIcon(Theme.RED, Theme.RED_SOFT);
                case INFO -> setIcon(Theme.BLUE, Theme.BLUE_SOFT);
                default -> setIcon(Theme.TEXT_MUTED, Theme.GRAY_SOFT);
            }
        }

        private void setIcon(java.awt.Color fg, java.awt.Color bg) {
            iconHolder.removeAll();
            iconHolder.add(Ui.iconBadge(iconName, fg, bg));
            iconHolder.revalidate();
            iconHolder.repaint();
        }
    }
}
