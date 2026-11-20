package com.railway.app.ui.components;

import com.railway.app.data.Row;
import com.railway.app.ui.theme.AppTheme;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A read-only table of rows returned by the booking system.
 */
public class DataTable extends JPanel {

    public enum Kind { TEXT, STRONG, MUTED, NUMBER, MONEY, DATE, TIME, TIME_DAY, DATETIME, KM, DURATION, BADGE, CUSTOM }

    /** One column. {@code extraKey} is used by TIME_DAY (day offset column). */
    public record Column(String header, String key, Kind kind, int width, String extraKey,
                         Function<Row, String> custom) {

        public static Column of(String header, String key, Kind kind, int width) {
            return new Column(header, key, kind, width, null, null);
        }

        public static Column timeWithDay(String header, String timeKey, String dayKey, int width) {
            return new Column(header, timeKey, Kind.TIME_DAY, width, dayKey, null);
        }

        public static Column custom(String header, int width, Function<Row, String> text) {
            return new Column(header, null, Kind.CUSTOM, width, null, text);
        }
    }

    private final List<Column> columns;
    private final RowModel model = new RowModel();
    private final JTable table;
    private String emptyMessage = "Nothing to show yet";

    public DataTable(List<Column> columns) {
        super(new BorderLayout());
        setOpaque(false);
        this.columns = columns;
        this.table = new JTable(model) {
            @Override
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                if (getRowCount() == 0) {
                    paintEmpty(graphics, this);
                }
            }
        };
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(false);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setFont(AppTheme.semibold(12));
        table.setFont(AppTheme.font(Font.PLAIN, 13));
        table.setBackground(AppTheme.SURFACE);
        TableCellRenderer headerDefault = table.getTableHeader().getDefaultRenderer();
        table.getTableHeader().setDefaultRenderer((t, value, selected, focus, row, column) -> {
            Component c = headerDefault.getTableCellRendererComponent(t, value, selected, focus, row, column);
            if (c instanceof JLabel label) {
                Kind kind = columns.get(column).kind();
                label.setHorizontalAlignment(isRightAligned(kind) ? SwingConstants.RIGHT : SwingConstants.LEFT);
                label.setBorder(new EmptyBorder(0, 14, 0, 14));
            }
            return c;
        });

        for (int i = 0; i < columns.size(); i++) {
            TableColumn tableColumn = table.getColumnModel().getColumn(i);
            Column column = columns.get(i);
            if (column.width() > 0) {
                tableColumn.setPreferredWidth(column.width());
            }
            tableColumn.setCellRenderer(column.kind() == Kind.BADGE ? new BadgeRenderer(column) : new TextRenderer(column));
        }

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(AppTheme.SURFACE);
        add(scroll, BorderLayout.CENTER);
    }

    public JTable table() {
        return table;
    }

    public void setEmptyMessage(String message) {
        this.emptyMessage = message;
        table.repaint();
    }

    public void setRows(List<Row> rows) {
        setRows(rows, null);
    }

    /** Replaces the rows and keeps the same item selected (matched on keyColumn). */
    public void setRows(List<Row> rows, String keyColumn) {
        Object previousKey = null;
        Row previous = selected();
        if (previous != null && keyColumn != null) {
            previousKey = previous.get(keyColumn);
        }
        model.rows = new ArrayList<>(rows);
        model.fireTableDataChanged();
        if (previousKey != null) {
            select(keyColumn, previousKey);
        }
    }

    public List<Row> rows() {
        return model.rows;
    }

    public Row selected() {
        int index = table.getSelectedRow();
        return index < 0 || index >= model.rows.size() ? null : model.rows.get(index);
    }

    public void select(String keyColumn, Object value) {
        for (int i = 0; i < model.rows.size(); i++) {
            if (Objects.equals(String.valueOf(model.rows.get(i).get(keyColumn)), String.valueOf(value))) {
                table.setRowSelectionInterval(i, i);
                table.scrollRectToVisible(table.getCellRect(i, 0, true));
                return;
            }
        }
    }

    public void selectFirst() {
        if (!model.rows.isEmpty()) {
            table.setRowSelectionInterval(0, 0);
        }
    }

    public void onSelect(Consumer<Row> listener) {
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                listener.accept(selected());
            }
        });
    }

    public void onDoubleClick(Consumer<Row> listener) {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && selected() != null) {
                    listener.accept(selected());
                }
            }
        });
    }

    private void paintEmpty(Graphics graphics, JTable owner) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(AppTheme.font(Font.PLAIN, 13));
        g.setColor(AppTheme.TEXT_MUTED);
        Rectangle visible = owner.getVisibleRect();
        FontMetrics metrics = g.getFontMetrics();
        int x = visible.x + (visible.width - metrics.stringWidth(emptyMessage)) / 2;
        int y = visible.y + Math.max(48, visible.height / 3);
        g.drawString(emptyMessage, x, y);
        g.dispose();
    }

    private static boolean isRightAligned(Kind kind) {
        return kind == Kind.NUMBER || kind == Kind.MONEY || kind == Kind.KM;
    }

    static String format(Row row, Column column) {
        return switch (column.kind()) {
            case TEXT, STRONG, MUTED, BADGE -> row.str(column.key(), Formats.DASH);
            case NUMBER -> Formats.count(row.integer(column.key()));
            case MONEY -> Formats.money(row.decimal(column.key()));
            case DATE -> Formats.date(row.date(column.key()));
            case TIME -> Formats.time(row.time(column.key()));
            case TIME_DAY -> Formats.time(row.time(column.key()));
            case DATETIME -> Formats.dateTime(row.dateTime(column.key()));
            case KM -> Formats.km(row.integer(column.key()));
            case DURATION -> Formats.duration(row.integer(column.key()));
            case CUSTOM -> column.custom().apply(row);
        };
    }

    private final class RowModel extends AbstractTableModel {
        private List<Row> rows = new ArrayList<>();

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.size();
        }

        @Override
        public String getColumnName(int column) {
            return columns.get(column).header();
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            return rows.get(rowIndex);
        }
    }

    private static final class TextRenderer extends DefaultTableCellRenderer {
        private final Column column;

        TextRenderer(Column column) {
            this.column = column;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focus, int rowIndex, int columnIndex) {
            super.getTableCellRendererComponent(table, "", selected, false, rowIndex, columnIndex);
            Row row = (Row) value;
            String text = format(row, column);
            setBorder(new EmptyBorder(0, 14, 0, 14));
            setHorizontalAlignment(isRightAligned(column.kind()) ? SwingConstants.RIGHT : SwingConstants.LEFT);
            Color foreground = AppTheme.TEXT;
            Font font = AppTheme.font(Font.PLAIN, 13);
            if (column.kind() == Kind.STRONG) {
                font = AppTheme.semibold(13);
            } else if (column.kind() == Kind.MUTED) {
                foreground = AppTheme.TEXT_SECONDARY;
            }
            setFont(font);
            setForeground(foreground);
            if (column.kind() == Kind.TIME_DAY) {
                String day = Formats.dayOffset(row.integer(column.extraKey()));
                if (!day.isEmpty()) {
                    text = "<html><nobr>" + Formats.html(text) + "&nbsp;&nbsp;<span style='color:"
                            + AppTheme.hex(AppTheme.ORANGE_DARK) + ";font-size:9px'>" + day + "</span></nobr></html>";
                }
            }
            setText(text);
            setToolTipText(text.startsWith("<html>") || text.length() < 28 ? null : text);
            return this;
        }
    }

    private static final class BadgeRenderer implements TableCellRenderer {
        private final Column column;
        private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        private final Badge badge = new Badge("", AppTheme.GRAY, AppTheme.GRAY_SOFT);

        BadgeRenderer(Column column) {
            this.column = column;
            panel.add(badge);
            panel.setBorder(new EmptyBorder(0, 0, 0, 0));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focus, int rowIndex, int columnIndex) {
            Row row = (Row) value;
            badge.applyStatus(format(row, column));
            panel.setBackground(selected ? table.getSelectionBackground() : table.getBackground());
            int height = table.getRowHeight();
            int badgeHeight = badge.getPreferredSize().height;
            ((FlowLayout) panel.getLayout()).setVgap(Math.max(0, (height - badgeHeight) / 2));
            return panel;
        }
    }
}
