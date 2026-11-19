package com.railway.setup.ui.pages;

import com.railway.setup.core.Project;
import com.railway.setup.core.RailwayApp;
import com.railway.setup.core.Status;
import com.railway.setup.ui.Card;
import com.railway.setup.ui.Page;
import com.railway.setup.ui.Pill;
import com.railway.setup.ui.Theme;
import com.railway.setup.ui.Ui;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/** Build the Railway app with the Maven Wrapper, then open and close it. */
public class BuildPage extends Page {

    private final Pill buildPill = new Pill();
    private final JLabel buildDetail = Ui.label(" ", 12.5f, Theme.TEXT_SECONDARY);
    private final Pill appPill = new Pill();
    private final JLabel appDetail = Ui.label(" ", 12.5f, Theme.TEXT_SECONDARY);

    private final JButton build = Ui.secondary("Build", "wrench");
    private final JCheckBox clean = new JCheckBox("Clean first");
    private final JButton buildAndRun = Ui.primary("Build & open", "rocket");
    private final JButton run = Ui.primary("Open the app", "play");
    private final JButton stop = Ui.danger("Close the app", "stop");
    private final JLabel buildChip = Ui.commandChip("");

    public BuildPage() {
        super("Build & Run", "Compile the Railway app with the Maven Wrapper and open it — no Maven installation needed.",
                "Build & app console");

        JButton folder = Ui.secondary("Open target folder", "folder");
        folder.addActionListener(e -> Ui.open(Project.root().resolve("target").toFile().exists()
                ? Project.root().resolve("target") : Project.root()));
        actions.add(folder);
        actions.add(buildAndRun);

        block(grid(2, buildCard(), appCard()));
        addSection("Good to know");
        block(tips());

        clean.setOpaque(false);
        clean.addActionListener(e -> updateChip());
        updateChip();
        build.addActionListener(e -> Ui.run("Build the Railway app", console, ctx -> RailwayApp.build(ctx, clean.isSelected())));
        buildAndRun.addActionListener(e -> Ui.run("Build & open the Railway app", console, ctx -> {
            if (clean.isSelected() || RailwayApp.isOutdated()) {
                RailwayApp.build(ctx, clean.isSelected());
            } else {
                ctx.ok("The build is up to date.");
            }
            RailwayApp.start(ctx, console, Status::refresh);
        }));
        run.addActionListener(e -> Ui.run("Open the Railway app", console,
                ctx -> RailwayApp.start(ctx, console, Status::refresh)));
        stop.addActionListener(e -> Ui.run("Close the Railway app", console, RailwayApp::stop));
    }

    private void updateChip() {
        String wrapper = Project.isWindows() ? "mvnw.cmd" : "./mvnw";
        Ui.setChip(buildChip, wrapper + " -B" + (clean.isSelected() ? " clean" : "") + " package -DskipTests");
    }

    private JComponent buildCard() {
        Card card = new Card(new BorderLayout(0, 14), 18);
        JPanel top = new JPanel(new BorderLayout(10, 0));
        top.setOpaque(false);
        top.add(Ui.heading("wrench", "Build", "Creates target/railway-manager.jar with every library inside"), BorderLayout.CENTER);
        top.add(pillBox(buildPill), BorderLayout.EAST);
        card.add(top, BorderLayout.NORTH);
        card.add(Ui.column(10, buildDetail, buildChip), BorderLayout.CENTER);
        card.add(Ui.row(10, build, clean), BorderLayout.SOUTH);
        return card;
    }

    private JComponent appCard() {
        Card card = new Card(new BorderLayout(0, 14), 18);
        JPanel top = new JPanel(new BorderLayout(10, 0));
        top.setOpaque(false);
        top.add(Ui.heading("train", "Railway app", "Opens with the sign-in from config/connection.properties"), BorderLayout.CENTER);
        top.add(pillBox(appPill), BorderLayout.EAST);
        card.add(top, BorderLayout.NORTH);
        card.add(Ui.column(10, appDetail, Ui.commandChip("java -jar target/railway-manager.jar")), BorderLayout.CENTER);
        card.add(Ui.row(10, run, stop), BorderLayout.SOUTH);
        return card;
    }

    private static JComponent pillBox(Pill pill) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(pill, BorderLayout.NORTH);
        return p;
    }

    private JComponent tips() {
        Card card = new Card(new BorderLayout(), 18);
        card.add(Ui.paragraph(
                "• The first build downloads Maven and the libraries once (about 60 MB); later builds take about 10 seconds.<br>"
                        + "• Building closes a running Railway app first, because Windows locks the jar while it is open.<br>"
                        + "• The app's own messages (and errors) appear in the console below while it is open. "
                        + "Its detailed log is on the <b>Logs</b> page.<br>"
                        + "• In an IDE: open the project folder as a Maven project and run <b>com.railway.app.RailwayApp</b>.",
                760), BorderLayout.CENTER);
        return card;
    }

    @Override
    protected void showStatus(Status s) {
        if (!s.appBuilt) {
            buildPill.set("Not built", Pill.Tone.ERROR);
            buildDetail.setText("Nothing built yet. Press Build.");
        } else if (s.appOutdated) {
            buildPill.set("Outdated", Pill.Tone.WARN);
            buildDetail.setText("The code changed after the last build (" + EnvironmentPage.ago(s.appBuiltAt) + "). Build again.");
        } else {
            buildPill.set("Up to date", Pill.Tone.OK);
            buildDetail.setText("Built " + EnvironmentPage.ago(s.appBuiltAt) + " with Java " + s.javaVersion + ".");
        }
        if (!s.jdk) {
            buildDetail.setText("This Java has no compiler (javac). Install a JDK on the Environment page.");
        }
        Ui.allow(build, s.jdk);
        Ui.allow(buildAndRun, s.jdk && !s.appRunning);

        appPill.set(s.appRunning ? "Open" : "Closed", s.appRunning ? Pill.Tone.OK : Pill.Tone.NEUTRAL);
        appDetail.setText(s.appRunning ? "The Railway app is open." :
                (s.loginOk ? "Ready to open." : "MySQL is not ready yet — the app will show \"can't reach the booking system\"."));
        Ui.allow(run, s.appBuilt && !s.appRunning);
        Ui.allow(stop, s.appRunning);
    }
}
