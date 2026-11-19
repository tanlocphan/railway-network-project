package com.railway.setup.ui.pages;

import com.railway.setup.core.Git;
import com.railway.setup.core.Project;
import com.railway.setup.core.Status;
import com.railway.setup.core.TaskContext;
import com.railway.setup.core.Tasks;
import com.railway.setup.ui.Card;
import com.railway.setup.ui.Page;
import com.railway.setup.ui.Pill;
import com.railway.setup.ui.Theme;
import com.railway.setup.ui.Ui;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

/**
 * The team's daily Git routine as buttons. Every button shows the real git
 * command it runs, so the team learns the command line at the same time.
 */
public class GitPage extends Page {

    private Git.Info info = new Git.Info();

    private final Pill branchPill = new Pill("…", Pill.Tone.ACCENT);
    private final JLabel headLabel = Ui.label(" ", 12.5f, Theme.TEXT_SECONDARY);
    private final Pill syncPill = new Pill();
    private final Pill changesPill = new Pill();
    private final JButton openWeb = Ui.secondary("Open on GitHub", "external");

    private final JButton status = Ui.secondary("Status", "eye");
    private final JButton history = Ui.secondary("History", "commit");
    private final JButton fetch = Ui.secondary("Fetch", "download");
    private final JButton pull = Ui.primary("Pull", "download");
    private final JComboBox<String> switchTo = new JComboBox<>();
    private final JButton switchButton = Ui.secondary("Switch", "git");
    private final JTextField newBranch = new JTextField(18);
    private final JButton createBranch = Ui.secondary("Create branch", "git");
    private final JTextField message = new JTextField(30);
    private final JButton commit = Ui.primary("Commit all changes", "commit");
    private final JComboBox<String> mergeFrom = new JComboBox<>();
    private final JButton merge = Ui.secondary("Merge into current", "merge");
    private final JButton abortMerge = Ui.danger("Abort merge", "close");
    private final JCheckBox force = new JCheckBox("--force-with-lease");
    private final JButton push = Ui.primary("Push", "upload");

    private final JLabel switchChip = Ui.commandChip("");
    private final JLabel createChip = Ui.commandChip("");
    private final JLabel commitChip = Ui.commandChip("");
    private final JLabel mergeChip = Ui.commandChip("");
    private final JLabel pushChip = Ui.commandChip("");

    public GitPage() {
        super("Git", "Get the team's latest work, save yours and share it. Each button shows the git command it runs.",
                "Git console");

        JButton refresh = Ui.secondary("Refresh", "refresh");
        refresh.addActionListener(e -> reload());
        openWeb.addActionListener(e -> {
            if (info.webUrl() != null) {
                Ui.browse(info.webUrl());
            }
        });
        actions.add(openWeb);
        actions.add(refresh);

        block(header());
        addSection("1 · Look");
        block(stage("eye", "See what changed", "Nothing is modified by these.",
                Ui.row(10, status, Ui.commandChip("git status")),
                Ui.row(10, history, Ui.commandChip("git log --oneline --graph -20"))));
        addSection("2 · Get the latest");
        block(stage("download", "Bring in the team's commits", "Fetch only downloads; Pull also moves your branch forward (never creates a merge commit).",
                Ui.row(10, fetch, Ui.commandChip("git fetch --all --prune")),
                Ui.row(10, pull, Ui.commandChip("git pull --ff-only"))));
        addSection("3 · Branch");
        block(stage("git", "Work on your own branch", "Suggested names: feature/booking-ui, sql/report-12, fix/seat-map.",
                Ui.row(10, switchTo, switchButton, switchChip),
                Ui.row(10, newBranch, createBranch, createChip)));
        addSection("4 · Save");
        block(stage("commit", "Commit your changes", "Stages every changed file (git add -A) and commits it with your message.",
                Ui.row(10, message, commit), Ui.row(10, commitChip)));
        addSection("5 · Combine");
        block(stage("merge", "Merge another branch into yours", "Usually origin/main, so your branch has the newest work before you share it.",
                Ui.row(10, mergeFrom, merge, abortMerge), Ui.row(10, mergeChip)));
        addSection("6 · Share");
        block(stage("upload", "Push your branch to GitHub", "Then open a Pull Request on GitHub so the team can review it.",
                Ui.row(10, push, force), Ui.row(10, pushChip)));

        switchTo.setPreferredSize(new Dimension(260, 34));
        mergeFrom.setPreferredSize(new Dimension(260, 34));
        newBranch.putClientProperty("JTextField.placeholderText", "feature/my-change");
        message.putClientProperty("JTextField.placeholderText", "What did you change? e.g. Add seat map legend");
        newBranch.setPreferredSize(new Dimension(260, 34));
        message.setPreferredSize(new Dimension(420, 34));
        force.setOpaque(false);

        switchTo.addActionListener(e -> updateChips());
        mergeFrom.addActionListener(e -> updateChips());
        force.addActionListener(e -> updateChips());
        onType(newBranch);
        onType(message);

        status.addActionListener(e -> git("git status", "status"));
        history.addActionListener(e -> git("git log", "log", "--oneline", "--graph", "--decorate", "-20"));
        fetch.addActionListener(e -> git("git fetch", "fetch", "--all", "--prune"));
        pull.addActionListener(e -> git("git pull", "pull", "--ff-only"));
        switchButton.addActionListener(e -> {
            String target = switchTarget();
            if (target != null) {
                git("git switch " + target, "switch", target);
            }
        });
        createBranch.addActionListener(e -> {
            String name = newBranch.getText().trim();
            Ui.async(() -> Git.validBranchName(name), ok -> {
                if (!ok) {
                    Ui.info("Branch name", "\"" + Ui.escape(name) + "\" is not a valid branch name. Use letters, digits, - and /.");
                } else {
                    git("git switch -c " + name, "switch", "-c", name);
                    newBranch.setText("");
                }
            });
        });
        commit.addActionListener(e -> commit());
        merge.addActionListener(e -> {
            String from = (String) mergeFrom.getSelectedItem();
            if (from != null) {
                git("git merge " + from, "merge", "--no-edit", from);
            }
        });
        abortMerge.addActionListener(e -> {
            if (Ui.confirm("Abort the merge", "Undo the unfinished merge and go back to how your branch was before?", "Abort merge")) {
                git("git merge --abort", "merge", "--abort");
            }
        });
        push.addActionListener(e -> {
            List<String> args = new ArrayList<>(List.of("push", "-u"));
            if (force.isSelected()) {
                if (!Ui.confirm("Force push", "--force-with-lease replaces the branch on GitHub with yours. "
                        + "Only do this on your own branch, never on main.", "Force push")) {
                    return;
                }
                args.add("--force-with-lease");
            }
            args.addAll(List.of("origin", info.branch));
            git("git push", args.toArray(new String[0]));
        });

        Tasks.onFinished(this::reload);
        updateChips();
    }

    private JComponent header() {
        Card card = new Card(new BorderLayout(16, 0), 18);
        JPanel left = new JPanel(new BorderLayout(12, 0));
        left.setOpaque(false);
        left.add(Ui.iconBadge("git", Theme.ORANGE, Theme.ORANGE_SOFT), BorderLayout.WEST);
        JPanel text = Ui.column(4, Ui.row(8, Ui.strong("Current branch", 14.5f), branchPill), headLabel);
        left.add(text, BorderLayout.CENTER);
        card.add(left, BorderLayout.CENTER);
        card.add(Ui.row(8, syncPill, changesPill), BorderLayout.EAST);
        return card;
    }

    private static JComponent stage(String icon, String title, String text, JComponent... rows) {
        Card card = new Card(new BorderLayout(0, 14), 18);
        card.add(Ui.heading(icon, title, text), BorderLayout.NORTH);
        card.add(Ui.column(10, rows), BorderLayout.CENTER);
        return card;
    }

    private void onType(JTextField field) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateChips();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateChips();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateChips();
            }
        });
    }

    /** For a remote-only branch "origin/x", git switch x creates the local tracking branch. */
    private String switchTarget() {
        String selected = (String) switchTo.getSelectedItem();
        if (selected == null) {
            return null;
        }
        return selected.startsWith("origin/") ? selected.substring("origin/".length()) : selected;
    }

    private void updateChips() {
        String target = switchTarget();
        Ui.setChip(switchChip, "git switch " + (target == null ? "<branch>" : target));
        String name = newBranch.getText().trim();
        Ui.setChip(createChip, "git switch -c " + (name.isEmpty() ? "<new-branch>" : name));
        String msg = message.getText().trim();
        Ui.setChip(commitChip, "git add -A && git commit -m \"" + (msg.isEmpty() ? "<message>" : msg) + "\"");
        String from = (String) mergeFrom.getSelectedItem();
        Ui.setChip(mergeChip, "git merge " + (from == null ? "<branch>" : from));
        Ui.setChip(pushChip, "git push -u" + (force.isSelected() ? " --force-with-lease" : "") + " origin "
                + (info.branch.isEmpty() ? "<branch>" : info.branch));
        Ui.allow(createBranch, info.repository && !name.isEmpty());
        Ui.allow(commit, info.repository && !msg.isEmpty() && !info.changes.isEmpty());
    }

    private void commit() {
        String msg = message.getText().trim();
        StringBuilder files = new StringBuilder();
        int shown = 0;
        for (String c : info.changes) {
            if (shown++ == 12) {
                files.append("…and ").append(info.changes.size() - 12).append(" more<br>");
                break;
            }
            files.append("<code>").append(Ui.escape(c)).append("</code><br>");
        }
        if (!Ui.confirm("Commit " + info.changes.size() + " change(s)",
                "These files go into the commit on <b>" + Ui.escape(info.branch) + "</b>:<br><br>" + files, "Commit")) {
            return;
        }
        Ui.run("git commit", console, ctx -> {
            gitStep(ctx, "add", "-A");
            gitStep(ctx, "commit", "-m", msg);
        });
        message.setText("");
    }

    private void git(String title, String... args) {
        Ui.run(title, console, ctx -> gitStep(ctx, args));
    }

    private static void gitStep(TaskContext ctx, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        int code = ctx.exec(command, Project.root());
        if (code != 0) {
            throw new TaskContext.Failure("git " + args[0] + " stopped with exit code " + code + ". Read the lines above.");
        }
    }

    private void reload() {
        Ui.async(Git::read, this::show);
    }

    @Override
    public void shown() {
        reload();
    }

    private void show(Git.Info i) {
        info = i;
        if (!i.available) {
            branchPill.set("Git not installed", Pill.Tone.ERROR);
            headLabel.setText("Install Git for Windows (winget install Git.Git) and open Setup again.");
            syncPill.setVisible(false);
            changesPill.setVisible(false);
            return;
        }
        if (!i.repository) {
            branchPill.set("Not a git folder", Pill.Tone.WARN);
            headLabel.setText("This folder was not cloned with git (downloaded as ZIP?).");
            return;
        }
        branchPill.set(i.branch, Pill.Tone.ACCENT);
        headLabel.setText("<html>Last commit <b>" + Ui.escape(i.head) + "</b> " + Ui.escape(i.subject)
                + (i.upstream != null ? "  ·  tracks " + Ui.escape(i.upstream) : "  ·  not on GitHub yet") + "</html>");
        syncPill.setVisible(true);
        changesPill.setVisible(true);
        if (i.upstream == null) {
            syncPill.set("Not pushed", Pill.Tone.NEUTRAL);
        } else if (i.ahead == 0 && i.behind == 0) {
            syncPill.set("In sync", Pill.Tone.OK);
        } else {
            syncPill.set((i.ahead > 0 ? i.ahead + " to push" : "") + (i.ahead > 0 && i.behind > 0 ? " · " : "")
                    + (i.behind > 0 ? i.behind + " to pull" : ""), Pill.Tone.WARN);
        }
        changesPill.set(i.changes.isEmpty() ? "No local changes" : i.changes.size() + " changed file(s)",
                i.changes.isEmpty() ? Pill.Tone.OK : Pill.Tone.INFO);
        if (i.merging) {
            changesPill.set("Merge in progress", Pill.Tone.ERROR);
        }

        Object keepSwitch = switchTo.getSelectedItem();
        Object keepMerge = mergeFrom.getSelectedItem();
        List<String> switchable = new ArrayList<>();
        for (String b : i.localBranches) {
            if (!b.equals(i.branch)) {
                switchable.add(b);
            }
        }
        for (String r : i.remoteBranches) {
            String local = r.substring(r.indexOf('/') + 1);
            if (!i.localBranches.contains(local)) {
                switchable.add(r);
            }
        }
        switchTo.setModel(new DefaultComboBoxModel<>(switchable.toArray(new String[0])));
        if (keepSwitch != null && switchable.contains(keepSwitch)) {
            switchTo.setSelectedItem(keepSwitch);
        }
        List<String> mergeable = new ArrayList<>(i.remoteBranches);
        for (String b : i.localBranches) {
            if (!b.equals(i.branch)) {
                mergeable.add(b);
            }
        }
        mergeFrom.setModel(new DefaultComboBoxModel<>(mergeable.toArray(new String[0])));
        if (keepMerge != null && mergeable.contains(keepMerge)) {
            mergeFrom.setSelectedItem(keepMerge);
        } else if (mergeable.contains("origin/main")) {
            mergeFrom.setSelectedItem("origin/main");
        }

        openWeb.setVisible(i.webUrl() != null);
        Ui.allow(switchButton, !switchable.isEmpty());
        Ui.allow(merge, !mergeable.isEmpty() && !i.merging);
        abortMerge.setVisible(i.merging);
        Ui.allow(pull, i.upstream != null);
        Ui.allow(push, !"HEAD".equals(i.branch));
        updateChips();
    }

    @Override
    protected void showStatus(Status status) {
        // Git state is read separately (see reload()).
    }
}
