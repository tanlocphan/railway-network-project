package com.railway.app.ui.components;

import com.railway.app.data.Row;
import com.railway.app.ui.theme.AppTheme;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.List;

/**
 * Station-style departure board for today's trains.
 */
public class DepartureBoard extends JPanel {

    private static final Color BOARD = new Color(0x0E1B3D);
    private static final Color ROW_LINE = new Color(0x22335E);
    private static final Color AMBER_TEXT = new Color(0xFFB57A);
    private static final Color DIM = new Color(0x8E9BBE);

    private final JPanel rows = new JPanel(new GridBagLayout());
    private final JLabel dateLabel = new JLabel();

    public DepartureBoard() {
        super(new BorderLayout(0, 14));
        setOpaque(false);
        setBorder(new EmptyBorder(20, 22, 18, 22));

        JLabel title = new JLabel("Today's departures");
        title.setFont(AppTheme.semibold(16));
        title.setForeground(Color.WHITE);
        dateLabel.setFont(AppTheme.font(Font.PLAIN, 12));
        dateLabel.setForeground(DIM);
        JPanel header = Ui.transparent(new BorderLayout());
        header.add(title, BorderLayout.WEST);
        header.add(dateLabel, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        rows.setOpaque(false);
        TrackingPanel holder = new TrackingPanel(new BorderLayout());
        holder.add(rows, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(holder);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scroll, BorderLayout.CENTER);
    }

    public void setDate(String text) {
        dateLabel.setText(text);
    }

    public void setRows(List<Row> departures) {
        rows.removeAll();
        String[] headers = {"DEPARTS", "TRAIN", "ROUTE", "ARRIVES", "SOLD", "STATUS"};
        for (int c = 0; c < headers.length; c++) {
            JLabel label = new JLabel(headers[c]);
            label.setFont(AppTheme.semibold(10.5f));
            label.setForeground(DIM);
            rows.add(label, cell(c, 0, c == 2 ? 1 : 0));
        }
        int r = 1;
        for (Row row : departures) {
            rows.add(divider(), line(r++));
            JLabel time = new JLabel(Formats.time(row.time("Departure_Time")));
            time.setFont(AppTheme.semibold(19));
            time.setForeground(AMBER_TEXT);

            JLabel train = new JLabel(row.str("Train_ID"));
            train.setFont(AppTheme.semibold(13));
            train.setForeground(Color.WHITE);

            String routeText = row.str("Origin_Name", "") + "  →  " + row.str("Destination_Name", "");
            JLabel route = new JLabel(routeText);
            route.setFont(AppTheme.font(Font.PLAIN, 13));
            route.setForeground(new Color(0xDCE3F3));
            route.setToolTipText(routeText);
            java.awt.Dimension routeSize = new java.awt.Dimension(120, route.getPreferredSize().height);
            route.setPreferredSize(routeSize);
            route.setMinimumSize(routeSize);

            String day = Formats.dayOffset(row.integer("Arrival_Day"));
            JLabel arrives = new JLabel(Formats.time(row.time("Arrival_Time")) + (day.isEmpty() ? "" : "  " + day));
            arrives.setFont(AppTheme.font(Font.PLAIN, 13));
            arrives.setForeground(new Color(0xDCE3F3));

            int total = row.integer("Total_Seats", 0);
            JLabel sold = new JLabel(total == 0 ? "no seats" : row.integer("Seats_Sold", 0) + " / " + total);
            sold.setFont(AppTheme.font(Font.PLAIN, 13));
            sold.setForeground(total == 0 ? DIM : new Color(0xDCE3F3));

            Badge status = boardBadge(row.str("Board_Status"));

            rows.add(time, cell(0, r, 0));
            rows.add(train, cell(1, r, 0));
            rows.add(route, cell(2, r, 1));
            rows.add(arrives, cell(3, r, 0));
            rows.add(sold, cell(4, r, 0));
            rows.add(status, cell(5, r, 0));
            r++;
        }
        if (departures.isEmpty()) {
            JLabel none = new JLabel("No trains are scheduled to leave today.");
            none.setForeground(DIM);
            none.setFont(AppTheme.font(Font.PLAIN, 13));
            GridBagConstraints c = cell(0, 1, 1);
            c.gridwidth = 6;
            c.insets = new Insets(18, 0, 0, 0);
            rows.add(none, c);
        }
        rows.revalidate();
        rows.repaint();
    }

    private static Badge boardBadge(String status) {
        Badge badge = new Badge(status, Color.WHITE, ROW_LINE);
        if ("Scheduled".equals(status)) {
            badge.setColors(new Color(0x9CC0FF), new Color(0x1D3A75));
        } else if ("On the way".equals(status)) {
            badge.setColors(AMBER_TEXT, new Color(0x4A3020));
        } else {
            badge.setColors(DIM, ROW_LINE);
        }
        return badge;
    }

    private static JComponent divider() {
        JPanel line = new JPanel(null);
        line.setBackground(ROW_LINE);
        line.setPreferredSize(new java.awt.Dimension(1, 1));
        line.setMinimumSize(new java.awt.Dimension(1, 1));
        return line;
    }

    private static GridBagConstraints line(int row) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 6;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(8, 0, 8, 0);
        return c;
    }

    private static GridBagConstraints cell(int column, int row, double weight) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = column;
        c.gridy = row;
        c.weightx = weight;
        c.anchor = GridBagConstraints.WEST;
        c.fill = weight > 0 ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
        c.insets = new Insets(0, 0, 0, column == 5 ? 0 : 16);
        return c;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(BOARD);
        g.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
        g.dispose();
        super.paintComponent(graphics);
    }
}
