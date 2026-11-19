package com.railway.app.ui.pages;

import com.formdev.flatlaf.FlatClientProperties;
import com.github.lgooddatepicker.components.DatePicker;
import com.github.lgooddatepicker.components.TimePicker;
import com.railway.app.data.AppException;
import com.railway.app.data.Routine;
import com.railway.app.data.Row;
import com.railway.app.service.BookingService;
import com.railway.app.service.NetworkService;
import com.railway.app.service.ReportService;
import com.railway.app.service.TrainService;
import com.railway.app.ui.Navigator;
import com.railway.app.ui.Page;
import com.railway.app.ui.components.Async;
import com.railway.app.ui.components.Card;
import com.railway.app.ui.components.DataTable;
import com.railway.app.ui.components.DataTable.Column;
import com.railway.app.ui.components.DataTable.Kind;
import com.railway.app.ui.components.Dialogs;
import com.railway.app.ui.components.EmptyState;
import com.railway.app.ui.components.Formats;
import com.railway.app.ui.components.Inputs;
import com.railway.app.ui.components.RowCombo;
import com.railway.app.ui.components.TrackingPanel;
import com.railway.app.ui.components.Ui;
import com.railway.app.ui.theme.AppTheme;

import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.print.PrinterException;
import java.math.BigDecimal;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ready-made reports, including the project's required questions.
 */
public class ReportsPage extends Page {

    private static final Logger LOG = Logger.getLogger(ReportsPage.class.getName());

    private enum Param { TRAIN, STATION, STATION_TO, CITY, TRAIN_TYPE, TIME_FROM, TIME_TO, AMOUNT, CLASS_WORD, PASSENGER, DATE }

    private record Report(String group, String title, String description, Routine routine,
                          List<Param> params, Map<Param, Object> defaults, List<Column> columns) {
    }

    private final List<Report> reports = List.of(
            new Report("Timetable & network", "Stops of a train",
                    "Every station a train calls at, in the order it calls at them.",
                    Routine.REPORT_TRAIN_STOPS, List.of(Param.TRAIN), Map.of(Param.TRAIN, "SE7"),
                    List.of(Column.of("#", "Stop_No", Kind.MUTED, 40),
                            Column.of("Station", "Station_Name", Kind.STRONG, 200),
                            Column.of("City", "City", Kind.MUTED, 150),
                            Column.timeWithDay("Arrives", "Arrival", "Arrival_Day", 110),
                            Column.timeWithDay("Departs", "Departure", "Departure_Day", 110),
                            Column.of("From start", "Km_From_Origin", Kind.KM, 100))),
            new Report("Timetable & network", "Departures in a time window",
                    "How many trains leave a station between two times of day.",
                    Routine.REPORT_DEPARTURES_BETWEEN, List.of(Param.STATION, Param.TIME_FROM, Param.TIME_TO),
                    Map.of(Param.STATION, "SGN", Param.TIME_FROM, LocalTime.of(6, 0), Param.TIME_TO, LocalTime.of(12, 0)),
                    List.of(Column.of("Train", "Train_ID", Kind.STRONG, 80),
                            Column.of("Name", "Train_Name", Kind.TEXT, 220),
                            Column.of("Type", "Train_Type", Kind.BADGE, 100),
                            Column.of("Leaves at", "Departure", Kind.TIME, 90),
                            Column.of("Going to", "Destination_Name", Kind.TEXT, 200))),
            new Report("Timetable & network", "Route length of every train",
                    "Total distance each train travels from its first to its last station.",
                    Routine.REPORT_ROUTE_DISTANCES, List.of(), Map.of(),
                    List.of(Column.of("Train", "Train_ID", Kind.STRONG, 80),
                            Column.of("Name", "Train_Name", Kind.TEXT, 220),
                            Column.of("From", "Origin_Name", Kind.TEXT, 170),
                            Column.of("To", "Destination_Name", Kind.TEXT, 170),
                            Column.of("Stops", "Stop_Count", Kind.NUMBER, 70),
                            Column.of("Distance", "Total_Distance_Km", Kind.KM, 110))),
            new Report("Timetable & network", "Trains serving a whole line",
                    "Trains that stop at every station along the line between two stations.",
                    Routine.REPORT_TRAINS_COVERING_LINE, List.of(Param.STATION, Param.STATION_TO),
                    Map.of(Param.STATION, "SGN", Param.STATION_TO, "NTR"),
                    List.of(Column.of("Train", "Train_ID", Kind.STRONG, 80),
                            Column.of("Name", "Train_Name", Kind.TEXT, 260),
                            Column.of("Type", "Train_Type", Kind.BADGE, 110))),
            new Report("Timetable & network", "Trains of one type",
                    "All trains of a chosen type, sorted by name.",
                    Routine.REPORT_TRAINS_BY_TYPE, List.of(Param.TRAIN_TYPE), Map.of(Param.TRAIN_TYPE, "Express"),
                    List.of(Column.of("Train", "Train_ID", Kind.STRONG, 80),
                            Column.of("Name", "Train_Name", Kind.TEXT, 280),
                            Column.of("Type", "Train_Type", Kind.BADGE, 110))),
            new Report("Timetable & network", "Coaches of a class",
                    "Coaches of a train whose class contains a word, e.g. Sleeper.",
                    Routine.REPORT_COACHES_OF_CLASS, List.of(Param.TRAIN, Param.CLASS_WORD),
                    Map.of(Param.TRAIN, "SE3", Param.CLASS_WORD, "Sleeper"),
                    List.of(Column.of("Train", "Train_ID", Kind.STRONG, 80),
                            Column.of("Coach", "C_ID", Kind.TEXT, 80),
                            Column.of("Class", "Coach_Class", Kind.TEXT, 220),
                            Column.of("Places", "Seat_Count", Kind.NUMBER, 80))),
            new Report("Timetable & network", "Longest track section",
                    "The longest stretch of track between two neighbouring stations.",
                    Routine.REPORT_LONGEST_TRACK, List.of(), Map.of(),
                    List.of(Column.of("Track", "Track_ID", Kind.STRONG, 80),
                            Column.of("From", "From_Name", Kind.TEXT, 220),
                            Column.of("To", "To_Name", Kind.TEXT, 220),
                            Column.of("Length", "Max_Track_Length", Kind.KM, 100))),
            new Report("Timetable & network", "Stations in a city",
                    "Every station located in the chosen city or province.",
                    Routine.REPORT_STATIONS_IN_CITY, List.of(Param.CITY), Map.of(Param.CITY, "Hanoi"),
                    List.of(Column.of("Code", "Station_Code", Kind.STRONG, 80),
                            Column.of("Station", "Station_Name", Kind.TEXT, 260),
                            Column.of("City", "City", Kind.TEXT, 200))),
            new Report("Timetable & network", "Stations per city",
                    "How many stations each city or province has.",
                    Routine.REPORT_STATIONS_PER_CITY, List.of(), Map.of(),
                    List.of(Column.of("City / province", "City", Kind.TEXT, 260),
                            Column.of("Stations", "Number_Of_Stations", Kind.NUMBER, 100))),
            new Report("Tickets & passengers", "Several tickets on one day",
                    "Passengers holding two or more tickets for the same travel date.",
                    Routine.REPORT_PASSENGERS_MULTIPLE_BOOKINGS, List.of(), Map.of(),
                    List.of(Column.of("No.", "Passenger_ID", Kind.STRONG, 70),
                            Column.of("Passenger", "Passenger_Name", Kind.TEXT, 200),
                            Column.of("Travel date", "Travel_Date", Kind.DATE, 150),
                            Column.of("Tickets", "Tickets", Kind.NUMBER, 70),
                            Column.of("Ticket numbers", "Ticket_Numbers", Kind.MUTED, 220))),
            new Report("Tickets & passengers", "Passengers above a fare",
                    "Passengers who hold a ticket costing more than an amount.",
                    Routine.REPORT_PASSENGERS_FARE_ABOVE, List.of(Param.AMOUNT),
                    Map.of(Param.AMOUNT, new BigDecimal(700_000)),
                    List.of(Column.of("No.", "Passenger_ID", Kind.STRONG, 70),
                            Column.of("Passenger", "Passenger_Name", Kind.TEXT, 220),
                            Column.of("Tickets above", "Tickets_Above", Kind.NUMBER, 110),
                            Column.of("Highest fare", "Highest_Fare", Kind.MONEY, 140))),
            new Report("Tickets & passengers", "Fare paid on a date",
                    "The fare a passenger paid for travel on a given date.",
                    Routine.FN_GET_PASSENGER_FARE, List.of(Param.PASSENGER, Param.DATE),
                    Map.of(Param.PASSENGER, "P001", Param.DATE, LocalDate.of(2026, 10, 15)), List.of()),
            new Report("Sales", "Average fare by train type",
                    "Average ticket price for each type of train.",
                    Routine.REPORT_AVERAGE_FARE_BY_TYPE, List.of(), Map.of(),
                    List.of(Column.of("Train type", "Train_Type", Kind.BADGE, 140),
                            Column.of("Tickets", "Tickets", Kind.NUMBER, 90),
                            Column.of("Average fare", "Average_Fare", Kind.MONEY, 150))),
            new Report("Sales", "Ticket sales per train",
                    "Tickets sold, revenue and average fare for every train with sales.",
                    Routine.REPORT_TICKET_SALES_BY_TRAIN, List.of(), Map.of(),
                    List.of(Column.of("Train", "Train_ID", Kind.STRONG, 70),
                            Column.of("Name", "Train_Name", Kind.TEXT, 200),
                            Column.of("Places", "Seats", Kind.NUMBER, 70),
                            Column.of("Tickets", "Tickets_Sold", Kind.NUMBER, 70),
                            Column.of("Trips", "Trips_With_Sales", Kind.NUMBER, 60),
                            Column.of("Revenue", "Revenue", Kind.MONEY, 130),
                            Column.of("Average fare", "Average_Fare", Kind.MONEY, 120))));

    // Parameter inputs, shared between reports
    private final RowCombo train = new RowCombo("Train_ID", r -> r.str("Train_ID") + " · " + r.str("Train_Name"), null);
    private final RowCombo station = new RowCombo("Station_Code", r -> r.str("Station_Name"), null);
    private final RowCombo stationTo = new RowCombo("Station_Code", r -> r.str("Station_Name"), null);
    private final RowCombo city = new RowCombo("City", r -> r.str("City"), null);
    private final RowCombo trainType = new RowCombo("Train_Type", r -> r.str("Train_Type"), null);
    private final TimePicker timeFrom = Inputs.time();
    private final TimePicker timeTo = Inputs.time();
    private final JTextField amount = Inputs.money();
    private final JComboBox<String> classWord = new JComboBox<>(new String[]{"Sleeper", "Seat", "VIP", "AC"});
    private final RowCombo passenger = new RowCombo("Passenger_ID",
            r -> r.str("Passenger_ID") + " · " + r.str("Passenger_Name"), null);
    private final DatePicker date = Inputs.date(LocalDate.of(2026, 10, 15));
    private final Map<Param, JComponent> inputs = new EnumMap<>(Param.class);
    private final Map<Param, String> labels = new EnumMap<>(Param.class);

    private final JLabel title = Ui.strong(" ", 18);
    private final JLabel description = Ui.muted(" ");
    private final JPanel paramRow = Ui.flow(14);
    private final JLabel summary = Ui.label(" ", AppTheme.semibold(13.5f), AppTheme.ORANGE_DARK);
    private final JPanel resultHolder = Ui.transparent(new BorderLayout());
    private final JButton printButton = Ui.secondary("Print", "printer");
    private Report currentReport;
    private DataTable currentTable;
    private boolean lookupsLoaded;

    public ReportsPage(Navigator navigator) {
        super("Reports", "Answers to common questions about the network, timetables and ticket sales.");

        inputs.put(Param.TRAIN, train);
        inputs.put(Param.STATION, station);
        inputs.put(Param.STATION_TO, stationTo);
        inputs.put(Param.CITY, city);
        inputs.put(Param.TRAIN_TYPE, trainType);
        inputs.put(Param.TIME_FROM, timeFrom);
        inputs.put(Param.TIME_TO, timeTo);
        inputs.put(Param.AMOUNT, amount);
        inputs.put(Param.CLASS_WORD, classWord);
        inputs.put(Param.PASSENGER, passenger);
        inputs.put(Param.DATE, date);
        labels.put(Param.TRAIN, "Train");
        labels.put(Param.STATION, "Station");
        labels.put(Param.STATION_TO, "To station");
        labels.put(Param.CITY, "City / province");
        labels.put(Param.TRAIN_TYPE, "Train type");
        labels.put(Param.TIME_FROM, "From");
        labels.put(Param.TIME_TO, "Until");
        labels.put(Param.AMOUNT, "More than");
        labels.put(Param.CLASS_WORD, "Class contains");
        labels.put(Param.PASSENGER, "Passenger");
        labels.put(Param.DATE, "Travel date");
        classWord.setPreferredSize(new Dimension(150, 36));
        train.setPreferredSize(new Dimension(300, 36));
        passenger.setPreferredSize(new Dimension(260, 36));

        add(buildCatalog(), BorderLayout.WEST);
        add(buildResultArea(), BorderLayout.CENTER);
    }

    private JComponent buildCatalog() {
        JPanel list = Ui.stack();
        ButtonGroup group = new ButtonGroup();
        String lastGroup = null;
        boolean first = true;
        for (Report report : reports) {
            if (!report.group().equals(lastGroup)) {
                JLabel caption = Ui.caption(report.group());
                caption.setBorder(new EmptyBorder(lastGroup == null ? 0 : 14, 4, 6, 0));
                list.add(caption);
                lastGroup = report.group();
            }
            JToggleButton item = new JToggleButton("<html><b>" + Formats.html(report.title()) + "</b><br><span style='color:"
                    + AppTheme.hex(AppTheme.TEXT_SECONDARY) + "'>" + Formats.html(report.description()) + "</span></html>");
            item.setHorizontalAlignment(SwingConstants.LEFT);
            item.setFont(AppTheme.font(Font.PLAIN, 12.5f));
            item.setFocusable(false);
            item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            item.putClientProperty(FlatClientProperties.STYLE,
                    "arc:10; margin:8,12,8,12; borderWidth:0; focusWidth:0; background:#FFFFFF;"
                            + "hoverBackground:#F8FAFD; selectedBackground:#FFF1E7; selectedForeground:#18213A");
            item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
            item.setPreferredSize(new Dimension(280, 60));
            item.setAlignmentX(Component.LEFT_ALIGNMENT);
            item.addActionListener(e -> select(report));
            group.add(item);
            list.add(item);
            list.add(Box.createRigidArea(new Dimension(0, 4)));
            if (first) {
                item.setSelected(true);
                first = false;
            }
        }
        TrackingPanel holder = new TrackingPanel(new BorderLayout());
        holder.add(list, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(holder);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        Card card = new Card(new BorderLayout(), 12);
        card.add(scroll, BorderLayout.CENTER);
        card.setPreferredSize(new Dimension(320, 0));
        JPanel wrapper = Ui.transparent(new BorderLayout());
        wrapper.setBorder(new EmptyBorder(0, 0, 0, 16));
        wrapper.add(card, BorderLayout.CENTER);
        return wrapper;
    }

    private JComponent buildResultArea() {
        JButton run = Ui.primary("Show results", "chart");
        run.addActionListener(e -> runReport());
        printButton.addActionListener(e -> print());

        JPanel actions = Ui.flow(10, run, printButton);
        Card head = new Card(new BorderLayout(0, 14), 20);
        head.add(Ui.stack(title, Ui.vgap(4), description), BorderLayout.NORTH);
        JPanel controls = Ui.transparent(new BorderLayout(0, 12));
        controls.add(paramRow, BorderLayout.CENTER);
        controls.add(actions, BorderLayout.SOUTH);
        head.add(controls, BorderLayout.CENTER);

        Card results = new Card(new BorderLayout(0, 0), 0);
        summary.setBorder(new EmptyBorder(14, 16, 12, 16));
        results.add(summary, BorderLayout.NORTH);
        results.add(resultHolder, BorderLayout.CENTER);

        JPanel area = Ui.transparent(new BorderLayout(0, 14));
        area.add(head, BorderLayout.NORTH);
        area.add(results, BorderLayout.CENTER);
        return area;
    }

    @Override
    public void refresh() {
        Async.run(this, () -> {
            Map<Param, List<Row>> lookups = new LinkedHashMap<>();
            lookups.put(Param.TRAIN, TrainService.allTrains());
            lookups.put(Param.STATION, NetworkService.allStations());
            lookups.put(Param.CITY, NetworkService.cities());
            lookups.put(Param.TRAIN_TYPE, NetworkService.trainTypes());
            lookups.put(Param.PASSENGER, BookingService.passengers(null));
            return lookups;
        }, lookups -> {
            train.setRows(lookups.get(Param.TRAIN));
            station.setRows(lookups.get(Param.STATION));
            stationTo.setRows(lookups.get(Param.STATION));
            city.setRows(lookups.get(Param.CITY));
            trainType.setRows(lookups.get(Param.TRAIN_TYPE));
            passenger.setRows(lookups.get(Param.PASSENGER));
            if (!lookupsLoaded) {
                lookupsLoaded = true;
                select(reports.get(0));
            }
        });
    }

    private void select(Report report) {
        currentReport = report;
        title.setText(report.title());
        description.setText(report.description());
        paramRow.removeAll();
        for (Param param : report.params()) {
            applyDefault(param, report.defaults().get(param));
            JLabel caption = Ui.label(labels.get(param), AppTheme.semibold(12), AppTheme.TEXT_SECONDARY);
            paramRow.add(Ui.stack(caption, Ui.vgap(6), inputs.get(param)));
        }
        if (report.params().isEmpty()) {
            paramRow.add(Ui.muted("This report needs no choices."));
        }
        paramRow.revalidate();
        paramRow.repaint();
        runReport();
    }

    private void applyDefault(Param param, Object value) {
        if (value == null) {
            return;
        }
        switch (param) {
            case TRAIN -> train.selectKey(value);
            case STATION -> station.selectKey(value);
            case STATION_TO -> stationTo.selectKey(value);
            case CITY -> city.selectKey(value);
            case TRAIN_TYPE -> trainType.selectKey(value);
            case TIME_FROM -> timeFrom.setTime((LocalTime) value);
            case TIME_TO -> timeTo.setTime((LocalTime) value);
            case AMOUNT -> Inputs.setMoney(amount, (BigDecimal) value);
            case CLASS_WORD -> classWord.setSelectedItem(value);
            case PASSENGER -> passenger.selectKey(value);
            case DATE -> date.setDate((LocalDate) value);
            default -> {
            }
        }
    }

    private Object valueOf(Param param) {
        return switch (param) {
            case TRAIN -> train.selectedKey();
            case STATION -> station.selectedKey();
            case STATION_TO -> stationTo.selectedKey();
            case CITY -> city.selectedKey();
            case TRAIN_TYPE -> trainType.selectedKey();
            case TIME_FROM -> timeFrom.getTime();
            case TIME_TO -> timeTo.getTime();
            case AMOUNT -> Formats.parseMoney(amount.getText());
            case CLASS_WORD -> (String) classWord.getSelectedItem();
            case PASSENGER -> passenger.selectedKey();
            case DATE -> date.getDate();
        };
    }

    private void runReport() {
        Report report = currentReport;
        if (report == null) {
            return;
        }
        Object[] args = report.params().stream().map(this::valueOf).toArray();
        if (report.routine() == Routine.FN_GET_PASSENGER_FARE) {
            runFareOnDate(args);
            return;
        }
        Async.run(this, () -> ReportService.run(report.routine(), args), rows -> showRows(report, rows, args),
                this::showProblem);
    }

    private void showRows(Report report, List<Row> rows, Object[] args) {
        if (report != currentReport) {
            return;
        }
        DataTable table = new DataTable(report.columns());
        table.setEmptyMessage("No results");
        table.setRows(rows);
        currentTable = table;
        printButton.setEnabled(true);
        resultHolder.removeAll();
        resultHolder.add(table, BorderLayout.CENTER);
        resultHolder.revalidate();
        resultHolder.repaint();

        String text = rows.size() + (rows.size() == 1 ? " result" : " results");
        if (report.routine() == Routine.REPORT_DEPARTURES_BETWEEN) {
            int trains = rows.isEmpty() ? 0 : rows.get(0).integer("Number_Of_Trains", 0);
            Row chosen = station.selectedRow();
            String where = chosen == null ? "the station" : chosen.str("Station_Name");
            text = trains + (trains == 1 ? " train leaves " : " trains leave ") + where + " between "
                    + Formats.time((LocalTime) args[1]) + " and " + Formats.time((LocalTime) args[2]);
        } else if (report.routine() == Routine.REPORT_TRAINS_COVERING_LINE) {
            String from = (String) args[0];
            String to = (String) args[1];
            int count = rows.size();
            Async.run(this, () -> ReportService.lineDescription(from, to), line -> summary.setText(
                    (count == 0 ? "No train" : count == 1 ? "1 train" : count + " trains") + " stops at every station of the line: "
                            + (line == null ? "" : line)));
        }
        summary.setText(text);
    }

    private void runFareOnDate(Object[] args) {
        String passengerId = (String) args[0];
        LocalDate day = (LocalDate) args[1];
        Row chosen = passenger.selectedRow();
        String name = chosen == null ? "This passenger" : chosen.str("Passenger_Name");
        Async.run(this, () -> ReportService.fareOnDate(passengerId, day), fare -> {
            currentTable = null;
            printButton.setEnabled(false);
            JLabel big = Ui.label(fare == null ? "No ticket" : Formats.money(fare), AppTheme.semibold(34),
                    fare == null ? AppTheme.TEXT_MUTED : AppTheme.TEXT);
            JLabel note = Ui.muted(fare == null
                    ? name + " has no ticket for travel on " + Formats.date(day) + "."
                    : name + " paid this for travel on " + Formats.date(day) + " (latest ticket of that day).");
            JPanel center = Ui.transparent(new GridBagLayout());
            center.add(Ui.stack(big, Ui.vgap(6), note));
            resultHolder.removeAll();
            resultHolder.add(center, BorderLayout.CENTER);
            resultHolder.revalidate();
            resultHolder.repaint();
            summary.setText("Fare paid on a date");
        }, this::showProblem);
    }

    private void showProblem(AppException problem) {
        currentTable = null;
        printButton.setEnabled(false);
        summary.setText(" ");
        resultHolder.removeAll();
        resultHolder.add(EmptyState.create("info", "Please check your choices", problem.getMessage(), null),
                BorderLayout.CENTER);
        resultHolder.revalidate();
        resultHolder.repaint();
        if (problem.kind() != AppException.Kind.RULE) {
            Dialogs.error(this, problem);
        }
    }

    private void print() {
        if (currentTable == null || currentReport == null) {
            return;
        }
        try {
            currentTable.table().print(JTable.PrintMode.FIT_WIDTH, new MessageFormat(currentReport.title()),
                    new MessageFormat("Page {0}"));
        } catch (PrinterException e) {
            LOG.log(Level.WARNING, "Report printing failed", e);
            Dialogs.info(this, "Printing didn't work", "The report could not be printed. Please check the printer.");
        }
    }
}
