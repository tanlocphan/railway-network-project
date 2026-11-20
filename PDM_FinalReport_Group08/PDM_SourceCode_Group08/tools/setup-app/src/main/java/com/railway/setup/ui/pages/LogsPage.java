package com.railway.setup.ui.pages;

import com.railway.setup.core.Project;
import com.railway.setup.core.Status;
import com.railway.setup.ui.Page;
import com.railway.setup.ui.Theme;
import com.railway.setup.ui.Ui;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JToggleButton;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads the log files in logs/: the Railway app's technical log
 * (railway-app.0.log) and Setup's own log. Follows the file while the app runs.
 */
public class LogsPage extends Page {

    private static final long MAX_BYTES = 1_500_000;
    private static final Pattern HEADER = Pattern.compile("^\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d (\\w+)\\s");
    private static final String[] LEVELS = {"Everything", "Warnings and errors", "Errors only"};

    private final JComboBox<String> file = new JComboBox<>();
    private final JComboBox<String> level = new JComboBox<>(LEVELS);
    private final JTextField search = new JTextField(16);
    private final JToggleButton follow = new JToggleButton("Follow", Theme.icon("eye", 16, Theme.TEXT), true);
    private final JTextPane view = new JTextPane();
    private final JLabel info = Ui.label(" ", 12, Theme.TEXT_MUTED);
    private final SimpleAttributeSet normal = style(Theme.CONSOLE_TEXT);
    private final SimpleAttributeSet fine = style(Theme.CONSOLE_DIM);
    private final SimpleAttributeSet warning = style(Theme.CONSOLE_WARN);
    private final SimpleAttributeSet severe = style(Theme.CONSOLE_ERROR);
    private long lastSize = -1;
    private long lastModified = -1;
    private String lastKey = "";

    public LogsPage() {
        super("Logs", "Technical details the Railway app writes for the support team, and Setup's own log.", null);

        JButton folder = Ui.secondary("Open logs folder", "folder");
        folder.addActionListener(e -> {
            try {
                Files.createDirectories(Project.logsDir());
            } catch (IOException ignored) {
                // Ui.open reports it
            }
            Ui.open(Project.logsDir());
        });
        JButton copy = new JButton("Copy", Theme.icon("copy", 16, Theme.TEXT));
        copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(view.getText()), null));
        actions.add(copy);
        actions.add(folder);

        search.putClientProperty("JTextField.placeholderText", "Search…");
        search.putClientProperty("JTextField.leadingIcon", Theme.icon("search", 16, Theme.TEXT_MUTED));
        search.putClientProperty("JTextField.showClearButton", true);
        file.setPreferredSize(new Dimension(220, 34));
        level.setPreferredSize(new Dimension(190, 34));
        search.setPreferredSize(new Dimension(220, 34));
        JButton refresh = new JButton("Refresh", Theme.icon("refresh", 16, Theme.TEXT));

        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(0, 0, 12, 0));
        bar.add(Ui.row(8, file, level, search, follow, refresh), BorderLayout.WEST);
        bar.add(info, BorderLayout.EAST);

        view.setEditable(false);
        view.setBackground(Theme.CONSOLE_BG);
        view.setForeground(Theme.CONSOLE_TEXT);
        view.setFont(Theme.mono(12.5f));
        view.setBorder(new EmptyBorder(10, 14, 10, 14));
        view.setSelectionColor(new Color(0x2A3F75));
        view.setSelectedTextColor(Color.WHITE);
        ((DefaultCaret) view.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        JScrollPane scroll = new JScrollPane(view);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.CONSOLE_BG);

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(bar, BorderLayout.NORTH);
        body.add(scroll, BorderLayout.CENTER);
        fill(body);

        file.addActionListener(e -> reload(true));
        level.addActionListener(e -> reload(true));
        refresh.addActionListener(e -> {
            listFiles();
            reload(true);
        });
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                reload(true);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                reload(true);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                reload(true);
            }
        });
        Timer timer = new Timer(1500, e -> {
            if (follow.isSelected() && isShowing()) {
                reload(false);
            }
        });
        timer.start();
    }

    private static SimpleAttributeSet style(Color c) {
        SimpleAttributeSet s = new SimpleAttributeSet();
        StyleConstants.setForeground(s, c);
        return s;
    }

    @Override
    public void shown() {
        listFiles();
        reload(true);
    }

    private void listFiles() {
        Object keep = file.getSelectedItem();
        List<String> names = new ArrayList<>();
        if (Files.isDirectory(Project.logsDir())) {
            try (Stream<Path> files = Files.list(Project.logsDir())) {
                files.filter(p -> p.getFileName().toString().contains(".log") && !p.toString().endsWith(".lck"))
                        .sorted(Comparator.comparing((Path p) -> !p.getFileName().toString().startsWith("railway-app.0"))
                                .thenComparing(p -> p.getFileName().toString()))
                        .forEach(p -> names.add(p.getFileName().toString()));
            } catch (IOException ignored) {
                // empty list
            }
        }
        file.setModel(new DefaultComboBoxModel<>(names.toArray(new String[0])));
        if (keep != null && names.contains(keep)) {
            file.setSelectedItem(keep);
        }
    }

    private void reload(boolean force) {
        String name = (String) file.getSelectedItem();
        if (name == null) {
            view.setText("No log files yet.\n\nThe Railway app writes logs/railway-app.0.log the first time it is opened.");
            info.setText(" ");
            return;
        }
        Path path = Project.logsDir().resolve(name);
        long size;
        long modified;
        try {
            size = Files.size(path);
            modified = Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            listFiles();
            return;
        }
        String key = name + "|" + level.getSelectedIndex() + "|" + search.getText();
        if (!force && size == lastSize && modified == lastModified && key.equals(lastKey)) {
            return;
        }
        lastSize = size;
        lastModified = modified;
        lastKey = key;
        Ui.async(() -> read(path, size), lines -> render(lines, size));
    }

    private static List<String> read(Path path, long size) {
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            long start = Math.max(0, size - MAX_BYTES);
            raf.seek(start);
            byte[] bytes = new byte[(int) (size - start)];
            raf.readFully(bytes);
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (start > 0 && text.indexOf('\n') >= 0) {
                text = text.substring(text.indexOf('\n') + 1);
            }
            return List.of(text.split("\\R"));
        } catch (IOException e) {
            return List.of("Could not read " + path + ": " + e.getMessage());
        }
    }

    private void render(List<String> lines, long size) {
        int minLevel = level.getSelectedIndex();
        String needle = search.getText().trim().toLowerCase(Locale.ROOT);
        StyledDocument doc = view.getStyledDocument();
        JScrollPane scroll = (JScrollPane) javax.swing.SwingUtilities.getAncestorOfClass(JScrollPane.class, view);
        boolean atBottom = scroll == null || scroll.getVerticalScrollBar().getValue()
                + scroll.getVerticalScrollBar().getVisibleAmount() >= scroll.getVerticalScrollBar().getMaximum() - 30;

        // group continuation lines (stack traces) with their record
        List<List<String>> records = new ArrayList<>();
        List<Integer> severity = new ArrayList<>();
        for (String line : lines) {
            Matcher m = HEADER.matcher(line);
            if (m.find() || records.isEmpty()) {
                records.add(new ArrayList<>());
                severity.add(m.find(0) ? rank(m.group(1)) : 1);
            }
            records.get(records.size() - 1).add(line);
        }
        int shown = 0;
        try {
            doc.remove(0, doc.getLength());
            for (int r = 0; r < records.size(); r++) {
                int sev = severity.get(r);
                if (minLevel == 1 && sev < 2 || minLevel == 2 && sev < 3) {
                    continue;
                }
                List<String> rec = records.get(r);
                if (!needle.isEmpty() && rec.stream().noneMatch(l -> l.toLowerCase(Locale.ROOT).contains(needle))) {
                    continue;
                }
                SimpleAttributeSet st = sev >= 3 ? severe : sev == 2 ? warning : sev == 0 ? fine : normal;
                doc.insertString(doc.getLength(), String.join("\n", rec) + "\n", st);
                shown++;
            }
        } catch (BadLocationException ignored) {
            // offsets come from the document itself
        }
        info.setText(shown + " of " + records.size() + " entries · " + (size / 1024) + " KB");
        if (atBottom || lastKey.isEmpty()) {
            view.setCaretPosition(doc.getLength());
        }
    }

    private static int rank(String level) {
        return switch (level) {
            case "SEVERE" -> 3;
            case "WARNING" -> 2;
            case "FINE", "FINER", "FINEST", "CONFIG" -> 0;
            default -> 1;
        };
    }

    @Override
    protected void showStatus(Status status) {
        // logs are read on their own timer
    }
}
