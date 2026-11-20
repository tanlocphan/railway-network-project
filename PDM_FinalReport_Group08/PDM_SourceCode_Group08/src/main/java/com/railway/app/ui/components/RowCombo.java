package com.railway.app.ui.components;

import com.railway.app.data.Row;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JList;
import java.awt.Component;
import java.awt.Dimension;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Drop-down list of rows, e.g. stations or trains.
 * An optional first entry ("Any station") stands for "no choice".
 */
public class RowCombo extends JComboBox<Row> {

    private final String keyColumn;
    private final Function<Row, String> display;
    private final String noneText;

    public RowCombo(String keyColumn, Function<Row, String> display, String noneText) {
        this.keyColumn = keyColumn;
        this.display = display;
        this.noneText = noneText;
        setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                setText(value == null ? (RowCombo.this.noneText == null ? "" : RowCombo.this.noneText)
                        : RowCombo.this.display.apply((Row) value));
                return this;
            }
        });
        setMaximumRowCount(14);
        setPreferredSize(new Dimension(260, 36));
    }

    public void setRows(List<Row> rows) {
        Object previous = selectedKey();
        DefaultComboBoxModel<Row> model = new DefaultComboBoxModel<>();
        if (noneText != null) {
            model.addElement(null);
        }
        for (Row row : rows) {
            model.addElement(row);
        }
        setModel(model);
        if (previous != null) {
            selectKey(previous);
        } else if (getItemCount() > 0) {
            setSelectedIndex(0);
        }
    }

    public Row selectedRow() {
        return (Row) getSelectedItem();
    }

    /** Value of the key column of the chosen row, or null for "no choice". */
    public String selectedKey() {
        Row row = selectedRow();
        return row == null ? null : row.str(keyColumn);
    }

    public void selectKey(Object key) {
        for (int i = 0; i < getItemCount(); i++) {
            Row row = getItemAt(i);
            if (row != null && Objects.equals(row.str(keyColumn), key == null ? null : key.toString())) {
                setSelectedIndex(i);
                return;
            }
        }
        if (noneText != null && getItemCount() > 0) {
            setSelectedIndex(0);
        }
    }
}
