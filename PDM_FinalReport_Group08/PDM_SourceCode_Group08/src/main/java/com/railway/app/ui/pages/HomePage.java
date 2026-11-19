package com.railway.app.ui.pages;

import com.railway.app.data.Row;
import com.railway.app.service.BookingService;
import com.railway.app.ui.Navigator;
import com.railway.app.ui.Page;
import com.railway.app.ui.components.Async;
import com.railway.app.ui.components.BarChart;
import com.railway.app.ui.components.Card;
import com.railway.app.ui.components.DepartureBoard;
import com.railway.app.ui.components.Formats;
import com.railway.app.ui.components.StatTile;
import com.railway.app.ui.components.Ui;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Dashboard: headline numbers, today's departures and sales per train.
 */
public class HomePage extends Page {

    private final StatTile tickets = new StatTile("Tickets sold", "ticket");
    private final StatTile revenue = new StatTile("Revenue", "trend");
    private final StatTile trains = new StatTile("Trains on sale", "train");
    private final StatTile network = new StatTile("Railway network", "route");
    private final DepartureBoard board = new DepartureBoard();
    private final BarChart chart = new BarChart();

    public HomePage(Navigator navigator) {
        super("Good day!", "Here is how the railway is doing today.");

        JPanel tiles = new JPanel(new GridLayout(1, 4, 16, 0));
        tiles.setOpaque(false);
        tiles.add(tickets);
        tiles.add(revenue);
        tiles.add(trains);
        tiles.add(network);
        add(tiles, BorderLayout.NORTH);

        Card chartCard = new Card(new BorderLayout(0, 14), 20);
        chartCard.add(Ui.stack(Ui.heading("Revenue by train"), Ui.vgap(3),
                Ui.muted("All tickets sold so far · hover a bar for details")), BorderLayout.NORTH);
        chartCard.add(chart, BorderLayout.CENTER);

        // Zero preferred widths let the weights alone decide the 62 / 38 split.
        board.setPreferredSize(new java.awt.Dimension(0, 0));
        chartCard.setPreferredSize(new java.awt.Dimension(0, 0));
        JPanel middle = new JPanel(new GridBagLayout());
        middle.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.gridx = 0;
        c.weightx = 0.62;
        c.insets = new Insets(0, 0, 0, 16);
        middle.add(board, c);
        c.gridx = 1;
        c.weightx = 0.38;
        c.insets = new Insets(0, 0, 0, 0);
        middle.add(chartCard, c);
        add(middle, BorderLayout.CENTER);
    }

    @Override
    public String title() {
        int hour = java.time.LocalTime.now().getHour();
        String greeting = hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
        return greeting + "!";
    }

    @Override
    public void refresh() {
        Async.run(this, BookingService::summary, this::showSummary);
        LocalDate today = LocalDate.now();
        board.setDate(Formats.date(today));
        Async.run(this, () -> BookingService.departures(today), board::setRows);
        Async.run(this, BookingService::salesByTrain, this::showSales);
    }

    private void showSummary(Row row) {
        if (row == null) {
            return;
        }
        tickets.set(Formats.count(row.integer("Tickets_Sold")),
                Formats.count(row.integer("Upcoming_Tickets")) + " for upcoming trips");
        revenue.set(Formats.money(row.decimal("Revenue")), "From every ticket sold");
        trains.set(Formats.count(row.integer("Trains_On_Sale")) + " of " + Formats.count(row.integer("Trains")),
                "Have a timetable and seats");
        network.set(Formats.km(row.integer("Network_Km")),
                Formats.count(row.integer("Stations")) + " stations · "
                        + Formats.count(row.integer("Tracks")) + " tracks");
    }

    private void showSales(List<Row> rows) {
        List<BarChart.Bar> bars = new ArrayList<>();
        for (Row row : rows) {
            bars.add(new BarChart.Bar(row.str("Train_ID"), row.decimal("Revenue"),
                    "<html><b>" + Formats.html(row.str("Train_ID")) + "</b> · " + Formats.html(row.str("Train_Name"))
                            + "<br>" + Formats.count(row.integer("Tickets_Sold")) + " tickets · "
                            + Formats.money(row.decimal("Revenue")) + "</html>"));
        }
        chart.setBars(bars);
    }
}
