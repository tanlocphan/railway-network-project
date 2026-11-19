package com.railway.app.ui.components;

import com.railway.app.data.AppException;
import com.railway.app.ui.theme.AppTheme;
import com.railway.app.ui.theme.Icons;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyEvent;

/**
 * Base for the small "add / change" windows: a heading, labelled fields,
 * an inline message area for problems, and Cancel / Save buttons.
 * The save itself runs in the background; rules are checked by the
 * booking system and its message is shown inside the window.
 */
public abstract class FormDialog extends JDialog {

    private final JPanel form = new JPanel(new GridBagLayout());
    private final Card problem = new Card(new BorderLayout(10, 0), 12);
    private final JTextArea problemText = Ui.paragraph("", AppTheme.RED);
    private final JButton saveButton;
    private int nextRow;
    private boolean saved;

    protected FormDialog(Component owner, String title, String subtitle, String saveText) {
        super(owner instanceof Window w ? w : SwingUtilities.getWindowAncestor(owner), title,
                ModalityType.APPLICATION_MODAL);

        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setBackground(AppTheme.SURFACE);
        content.setBorder(new EmptyBorder(22, 24, 18, 24));

        JPanel header = Ui.stack(Ui.strong(title, 18), Ui.vgap(4), Ui.muted(subtitle));
        content.add(header, BorderLayout.NORTH);

        form.setOpaque(false);
        JPanel middle = Ui.transparent(new BorderLayout(0, 12));
        middle.add(form, BorderLayout.CENTER);
        problem.fill(AppTheme.RED_SOFT).outline(null).radius(10);
        problem.add(new JLabel(Icons.get("alert", 18, AppTheme.RED)), BorderLayout.WEST);
        problem.add(problemText, BorderLayout.CENTER);
        problem.setVisible(false);
        middle.add(problem, BorderLayout.SOUTH);
        content.add(middle, BorderLayout.CENTER);

        JButton cancel = Ui.secondary("Cancel", null);
        cancel.addActionListener(e -> dispose());
        saveButton = Ui.primary(saveText, "check");
        saveButton.addActionListener(e -> submit());
        JPanel buttons = Ui.flowRight(10, cancel, saveButton);
        buttons.setBorder(new EmptyBorder(8, 0, 0, 0));
        content.add(buttons, BorderLayout.SOUTH);

        setContentPane(content);
        getRootPane().setDefaultButton(saveButton);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        setResizable(false);
    }

    /** Adds a labelled field; hint may be null. */
    protected void addField(String label, JComponent field, String hint) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = nextRow;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(9, 0, 4, 18);
        JLabel name = Ui.label(label, AppTheme.semibold(12.5f), AppTheme.TEXT_SECONDARY);
        form.add(name, c);

        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, hint == null ? 12 : 2, 0);
        form.add(field, c);
        nextRow++;
        if (hint != null) {
            c.gridy = nextRow;
            c.insets = new Insets(0, 2, 12, 0);
            form.add(Ui.label(hint, AppTheme.font(java.awt.Font.PLAIN, 11.5f), AppTheme.TEXT_MUTED), c);
            nextRow++;
        }
    }

    /** Adds a full-width component (e.g. a note). */
    protected void addWide(JComponent component) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = nextRow++;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 12, 0);
        form.add(component, c);
    }

    /**
     * Reads the fields (on the screen thread) and returns the work that
     * saves them (run in the background).
     */
    protected abstract Async.Action buildSave();

    private void submit() {
        Async.Action action = buildSave();
        saveButton.setEnabled(false);
        Async.action(this, action, () -> {
            saved = true;
            dispose();
        }, this::showProblem);
    }

    private void showProblem(AppException e) {
        saveButton.setEnabled(true);
        problemText.setText(e.getMessage());
        problem.setVisible(true);
        pack();
    }

    /** Shows the window and returns true when something was saved. */
    public boolean open() {
        pack();
        Dimension size = getSize();
        setSize(new Dimension(Math.max(size.width, 480), size.height));
        setLocationRelativeTo(getOwner());
        setVisible(true);
        return saved;
    }
}
