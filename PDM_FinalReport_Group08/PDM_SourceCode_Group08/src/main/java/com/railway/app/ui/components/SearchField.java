package com.railway.app.ui.components;

import com.formdev.flatlaf.FlatClientProperties;
import com.railway.app.ui.theme.AppTheme;
import com.railway.app.ui.theme.Icons;

import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Dimension;
import java.util.function.Consumer;

/**
 * Search box that reacts a moment after the user stops typing.
 */
public class SearchField extends JTextField {

    public SearchField(String placeholder, Consumer<String> onSearch) {
        super(22);
        putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, Icons.get("search", 16, AppTheme.TEXT_MUTED));
        putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        Timer debounce = new Timer(300, e -> onSearch.accept(getText().trim()));
        debounce.setRepeats(false);
        getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                debounce.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                debounce.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                debounce.restart();
            }
        });
        setPreferredSize(new Dimension(300, 36));
    }
}
