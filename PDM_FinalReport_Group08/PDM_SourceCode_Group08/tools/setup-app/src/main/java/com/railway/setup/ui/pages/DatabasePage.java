package com.railway.setup.ui.pages;

import com.railway.setup.core.Ops;
import com.railway.setup.core.Project;
import com.railway.setup.core.Settings;
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
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.Map;

/** Create, reset and inspect the RailwayManagement database from the files in database/. */
public class DatabasePage extends Page {

    private final Pill statePill = new Pill();
    private final DefaultTableModel tableModel = new DefaultTableModel(new Object[]{"Table", "Rows"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JLabel routinesLabel = Ui.label(" ", 12.5f, Theme.TEXT_SECONDARY);
    private final JLabel accountLabel = Ui.label(" ", 12.5f, Theme.TEXT_SECONDARY);

    private final JButton reset = Ui.primary("Reset to sample data", "refresh");
    private final JButton reload = Ui.secondary("Reload routines", "file");
    private final JButton queries = Ui.secondary("Run queries", "play");
    private final JButton drop = Ui.danger("Delete database", "trash");
    private final JButton createAccount = Ui.secondary("Create account", "key");
    private final JPasswordField accountPassword = new JPasswordField(14);
    private final JCheckBox useAccount = new JCheckBox("The Railway app signs in with this account");

    public DatabasePage() {
        super("Database", "Load " + Project.DATABASE + " from the files in database/, check it, or start again.",
                "Database console");

        JButton refresh = Ui.secondary("Refresh", "refresh");
        refresh.addActionListener(e -> Status.refresh());
        JButton folder = Ui.secondary("Open database folder", "folder");
        folder.addActionListener(e -> Ui.open(Project.databaseDir()));
        actions.add(folder);
        actions.add(refresh);

        addSection("What is in MySQL now");
        block(overview());
        addSection("Actions");
        block(grid(2,
                action("refresh", "Reset to sample data",
                        "Runs 01_schema_and_seed → 02_payment_table → 03_business_routines. "
                                + "01 starts with DROP DATABASE, so every booking made while testing is replaced by the sample data.",
                        "01 + 02 + 03 .sql", reset),
                action("file", "Reload routines only",
                        "Runs 03_business_routines.sql again: every function and procedure is replaced, the data stays. "
                                + "Use it after pulling SQL changes from the team.",
                        "03_business_routines.sql", reload),
                action("search", "Run the reference queries",
                        "Runs the team's queries_reference.sql and prints every result as a table in the console below.",
                        "queries_reference.sql", queries),
                action("trash", "Delete the database",
                        "DROP DATABASE " + Project.DATABASE + ". Nothing else on the MySQL server is touched.",
                        "DROP DATABASE " + Project.DATABASE, drop)));
        addSection("App account (optional)");
        block(accountCard());

        reset.addActionListener(e -> {
            Status s = Status.last();
            if (s != null && s.dbExists && !Ui.confirm("Reset the database",
                    "Every change made while testing (bookings, passengers, trains…) is replaced by the sample data.",
                    "Reset")) {
                return;
            }
            Ui.run("Reset database to sample data", console, ctx -> Ops.resetDatabase(ctx, Settings.get()));
        });
        reload.addActionListener(e -> Ui.run("Reload routines", console, ctx -> Ops.reloadRoutines(ctx, Settings.get())));
        queries.addActionListener(e -> Ui.run("Run reference queries", console, ctx -> Ops.runReferenceQueries(ctx, Settings.get())));
        drop.addActionListener(e -> {
            if (Ui.confirm("Delete the database", "Delete <b>" + Project.DATABASE + "</b> and all of its data?", "Delete")) {
                Ui.run("Delete database", console, ctx -> Ops.dropDatabase(ctx, Settings.get()));
            }
        });
        createAccount.addActionListener(e -> {
            String pw = new String(accountPassword.getPassword());
            if (pw.length() < 4) {
                Ui.info("Password", "Choose a password of at least 4 characters for " + Settings.APP_ACCOUNT + ".");
                return;
            }
            boolean use = useAccount.isSelected();
            Ui.run("Create the app account", console, ctx -> Ops.createAppAccount(ctx, Settings.get(), pw, use));
        });
        useAccount.addActionListener(e -> {
            Settings s = Settings.get();
            Status st = Status.last();
            if (useAccount.isSelected() && (st == null || !st.appAccountExists || s.appPassword.isEmpty())) {
                return; // applied when the account is created
            }
            s.appUsesRestrictedAccount = useAccount.isSelected();
            try {
                s.save();
                console.line(com.railway.setup.core.Output.Kind.OK, "The Railway app now signs in as " + s.appUser() + ".");
            } catch (Exception ex) {
                console.line(com.railway.setup.core.Output.Kind.ERROR, "Could not save: " + ex.getMessage());
            }
        });
    }

    private JComponent overview() {
        Card card = new Card(new BorderLayout(18, 0), 18);
        JPanel left = new JPanel(new BorderLayout(0, 10));
        left.setOpaque(false);
        JPanel title = new JPanel(new BorderLayout(10, 0));
        title.setOpaque(false);
        title.add(Ui.heading("database", Project.DATABASE, "Tables and their row counts"), BorderLayout.CENTER);
        JPanel pillBox = new JPanel(new BorderLayout());
        pillBox.setOpaque(false);
        pillBox.add(statePill, BorderLayout.NORTH);
        title.add(pillBox, BorderLayout.EAST);
        left.add(title, BorderLayout.NORTH);
        left.add(Ui.column(6, routinesLabel, accountLabel), BorderLayout.CENTER);
        card.add(left, BorderLayout.CENTER);

        JTable table = new JTable(tableModel);
        table.setRowHeight(28);
        table.setFocusable(false);
        table.setRowSelectionAllowed(false);
        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(JLabel.RIGHT);
        table.getColumnModel().getColumn(1).setCellRenderer(right);
        table.getColumnModel().getColumn(1).setMaxWidth(90);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(javax.swing.BorderFactory.createLineBorder(Theme.BORDER));
        scroll.setPreferredSize(new Dimension(320, 250));
        card.add(scroll, BorderLayout.EAST);
        return card;
    }

    private static JComponent action(String icon, String title, String text, String command, JButton button) {
        Card card = new Card(new BorderLayout(0, 12), 18);
        card.add(Ui.heading(icon, title, ""), BorderLayout.NORTH);
        card.add(Ui.paragraph(text, 330), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(10, 0));
        bottom.setOpaque(false);
        bottom.add(button, BorderLayout.WEST);
        JPanel chip = new JPanel(new BorderLayout());
        chip.setOpaque(false);
        chip.add(Ui.commandChip(command), BorderLayout.EAST);
        bottom.add(chip, BorderLayout.EAST);
        card.add(bottom, BorderLayout.SOUTH);
        return card;
    }

    private JComponent accountCard() {
        Card card = new Card(new BorderLayout(0, 12), 18);
        card.add(Ui.heading("key", Settings.APP_ACCOUNT + " — may only run routines",
                "04_app_account.sql: MySQL refuses any direct SELECT / INSERT from this account, which proves the app only calls routines."),
                BorderLayout.NORTH);
        accountPassword.putClientProperty("JTextField.placeholderText", "Password for " + Settings.APP_ACCOUNT);
        accountPassword.putClientProperty("JPasswordField.showRevealButton", true);
        accountPassword.setPreferredSize(new Dimension(220, 34));
        useAccount.setOpaque(false);
        useAccount.setSelected(true);
        card.add(Ui.row(12, accountPassword, createAccount, useAccount), BorderLayout.CENTER);
        return card;
    }

    @Override
    protected void showStatus(Status s) {
        Settings settings = Settings.get();
        setSubtitle("MySQL " + settings.describe() + "  ·  files in " + Project.databaseDir());
        tableModel.setRowCount(0);
        boolean canWork = s.loginOk;
        for (JButton b : new JButton[]{reset, createAccount}) {
            Ui.allow(b, canWork);
        }
        Ui.allow(reload, canWork && s.dbExists);
        Ui.allow(queries, canWork && s.dbExists);
        Ui.allow(drop, canWork && s.dbExists);

        if (!s.serverOpen) {
            statePill.set("MySQL stopped", Pill.Tone.ERROR);
            routinesLabel.setText("Start MySQL on the Environment page first.");
            accountLabel.setText(" ");
            return;
        }
        if (!s.loginOk) {
            statePill.set("Sign-in fails", Pill.Tone.ERROR);
            routinesLabel.setText("<html><div style='width:420px'>" + Ui.escape(String.valueOf(s.loginError)) + "</div></html>");
            accountLabel.setText(" ");
            return;
        }
        if (!s.dbExists) {
            statePill.set("Not created", Pill.Tone.ERROR);
            routinesLabel.setText("Press \"Reset to sample data\" to create it.");
        } else {
            for (Map.Entry<String, Long> e : s.rowCounts.entrySet()) {
                tableModel.addRow(new Object[]{e.getKey(), e.getValue()});
            }
            int missing = s.missingRoutines().size();
            statePill.set(missing == 0 ? "Ready" : "Routines missing", missing == 0 ? Pill.Tone.OK : Pill.Tone.WARN);
            String text = s.routinesFound.size() + " functions and procedures installed";
            if (missing > 0) {
                text = "<html>" + text + " — <b>" + missing + " missing</b>: "
                        + String.join(", ", s.missingRoutines().subList(0, Math.min(6, missing)))
                        + (missing > 6 ? "…" : "") + "<br>Press \"Reload routines\".</html>";
            }
            routinesLabel.setText(text);
        }
        accountLabel.setText(s.appAccountExists
                ? Settings.APP_ACCOUNT + " exists. The app signs in as " + settings.appUser() + "."
                : "The app signs in as " + settings.appUser() + " (" + Settings.APP_ACCOUNT + " not created).");
        useAccount.setSelected(settings.appUsesRestrictedAccount || !s.appAccountExists);
    }
}
