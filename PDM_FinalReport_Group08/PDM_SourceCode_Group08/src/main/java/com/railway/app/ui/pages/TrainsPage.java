package com.railway.app.ui.pages;

import com.railway.app.data.Row;
import com.railway.app.service.TrainService;
import com.railway.app.ui.Navigator;
import com.railway.app.ui.Page;
import com.railway.app.ui.components.Async;
import com.railway.app.ui.components.Badge;
import com.railway.app.ui.components.Card;
import com.railway.app.ui.components.DataTable;
import com.railway.app.ui.components.DataTable.Column;
import com.railway.app.ui.components.DataTable.Kind;
import com.railway.app.ui.components.Dialogs;
import com.railway.app.ui.components.EmptyState;
import com.railway.app.ui.components.Formats;
import com.railway.app.ui.components.SearchField;
import com.railway.app.ui.components.Segmented;
import com.railway.app.ui.components.Toast;
import com.railway.app.ui.components.Ui;
import com.railway.app.ui.dialogs.AddSeatsDialog;
import com.railway.app.ui.dialogs.CoachDialog;
import com.railway.app.ui.dialogs.StopDialog;
import com.railway.app.ui.dialogs.TrainDialog;
import com.railway.app.ui.theme.AppTheme;
import com.railway.app.ui.theme.Icons;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.List;

/**
 * Trains with their timetable (stops) and their coaches and seats.
 */
public class TrainsPage extends Page {

    private final Navigator navigator;
    private final SearchField search = new SearchField("Search trains or stations", t -> loadTrains());
    private final Segmented typeFilter = new Segmented();
    private final DataTable trains;
    private final CardLayout detailCards = new CardLayout();
    private final JPanel detail = new JPanel(detailCards);

    // Detail header
    private final Badge trainBadge = new Badge("", Color.WHITE, AppTheme.NAVY);
    private final JLabel trainName = Ui.strong("", 19);
    private final Badge typeBadge = new Badge("", AppTheme.GRAY, AppTheme.GRAY_SOFT);
    private final Badge statusBadge = new Badge("", AppTheme.GRAY, AppTheme.GRAY_SOFT);
    private final JLabel routeLine = Ui.text(" ");
    private final JLabel factsLine = Ui.muted(" ");
    private final Card problemBanner = new Card(new BorderLayout(10, 0), 12);
    private final JTextArea problemText = Ui.paragraph("", AppTheme.AMBER);
    private final JButton sell = Ui.primary("Sell tickets", "ticket");

    // Timetable and coaches
    private final DataTable stops;
    private final DataTable coaches;
    private final DataTable seats;
    private final JLabel seatsTitle = Ui.heading("Places");

    private Row current;

    public TrainsPage(Navigator navigator) {
        super("Trains & Timetables", "Each train's stops and times, and the coaches and places it carries.");
        this.navigator = navigator;

        typeFilter.add("All", "").add("Express", "Express").add("Local", "Local").add("Night", "Night");
        typeFilter.onChange(v -> loadTrains());

        trains = new DataTable(List.of(
                Column.of("Train", "Train_ID", Kind.STRONG, 66),
                Column.of("Name", "Train_Name", Kind.TEXT, 170),
                Column.of("Status", "Status", Kind.BADGE, 135)));
        trains.setEmptyMessage("No trains match your search");
        trains.onSelect(this::showTrain);

        JButton newTrain = Ui.primary("New train", "plus");
        newTrain.addActionListener(e -> {
            if (new TrainDialog(this, null).open()) {
                Toast.success(this, "Train added. Next, give it a timetable and coaches.");
                loadTrains();
            }
        });

        Card listCard = new Card(new BorderLayout(0, 12), 14);
        listCard.setPreferredSize(new Dimension(400, 0));
        search.setPreferredSize(new Dimension(170, 36));
        JPanel listTop = Ui.stack(Ui.transparent(new BorderLayout(10, 0)), Ui.vgap(10), Ui.flow(0, typeFilter));
        ((JPanel) listTop.getComponent(0)).add(search, BorderLayout.CENTER);
        ((JPanel) listTop.getComponent(0)).add(newTrain, BorderLayout.EAST);
        listCard.add(listTop, BorderLayout.NORTH);
        listCard.add(trains, BorderLayout.CENTER);

        stops = new DataTable(List.of(
                Column.of("#", "Stop_No", Kind.MUTED, 46),
                Column.of("Station", "Station_Name", Kind.STRONG, 150),
                Column.timeWithDay("Arrives", "Arrival", "Arrival_Day", 118),
                Column.timeWithDay("Departs", "Departure", "Departure_Day", 118),
                Column.custom("Stop", 72, r -> r.integer("Dwell_Minutes") == null ? "" : r.integer("Dwell_Minutes") + " min"),
                Column.of("From start", "Km_From_Origin", Kind.KM, 90),
                Column.of("", "Stop_Role", Kind.BADGE, 112)));
        stops.setEmptyMessage("No stops yet — add the first station the train leaves from");
        stops.onDoubleClick(r -> editStop());

        coaches = new DataTable(List.of(
                Column.of("Coach", "C_ID", Kind.STRONG, 70),
                Column.of("Class", "Coach_Class", Kind.TEXT, 170),
                Column.custom("Places", 90, r -> r.integer("Seat_Count", 0) + " of " + r.integer("Capacity", 0)),
                Column.of("Tickets", "Ticket_Count", Kind.NUMBER, 70)));
        coaches.setEmptyMessage("No coaches yet");
        coaches.onSelect(r -> loadSeats());

        seats = new DataTable(List.of(
                Column.of("Place", "S_ID", Kind.STRONG, 90),
                Column.of("Tickets on record", "Ticket_Count", Kind.NUMBER, 120)));
        seats.setEmptyMessage("No places in this coach yet");

        detail.setOpaque(false);
        detail.add(EmptyState.create("train", "Choose a train", "Its timetable, coaches and places will appear here.",
                null), "empty");
        detail.add(buildDetail(), "detail");
        detailCards.show(detail, "empty");

        JPanel body = Ui.transparent(new BorderLayout(16, 0));
        body.add(listCard, BorderLayout.WEST);
        body.add(detail, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);
    }

    private JComponent buildDetail() {
        trainBadge.setFont(AppTheme.semibold(15));
        Card header = new Card(new BorderLayout(16, 12), 20);
        JPanel titleRow = Ui.transparent(new BorderLayout(10, 0));
        titleRow.add(trainBadge, BorderLayout.WEST);
        titleRow.add(trainName, BorderLayout.CENTER);
        header.add(Ui.stack(Ui.flow(8, typeBadge, statusBadge), Ui.vgap(10), routeLine, Ui.vgap(4), factsLine),
                BorderLayout.SOUTH);

        JButton edit = Ui.secondary("Edit", "edit");
        edit.addActionListener(e -> {
            if (current != null && new TrainDialog(this, current).open()) {
                Toast.success(this, "Train details saved");
                loadTrains();
            }
        });
        JButton remove = Ui.danger("Remove", "trash");
        remove.addActionListener(e -> removeTrain());
        sell.addActionListener(e -> {
            if (current != null) {
                navigator.bookOnTrain(current.str("Train_ID"));
            }
        });
        JPanel top = Ui.transparent(new BorderLayout(12, 0));
        top.add(titleRow, BorderLayout.CENTER);
        top.add(Ui.flowRight(8, sell, edit, remove), BorderLayout.EAST);
        header.add(top, BorderLayout.NORTH);

        problemBanner.fill(AppTheme.AMBER_SOFT).outline(null).radius(10);
        problemBanner.add(new JLabel(Icons.get("alert", 18, AppTheme.AMBER)), BorderLayout.WEST);
        problemBanner.add(problemText, BorderLayout.CENTER);
        problemBanner.setVisible(false);

        // Timetable tab
        JButton addStop = Ui.secondary("Add stop", "plus");
        addStop.addActionListener(e -> addStop());
        JButton editStop = Ui.secondary("Edit stop", "edit");
        editStop.addActionListener(e -> editStop());
        JButton removeStop = Ui.danger("Remove stop", "trash");
        removeStop.addActionListener(e -> removeStop());
        JPanel timetableTab = Ui.transparent(new BorderLayout(0, 12));
        timetableTab.setBorder(new EmptyBorder(14, 0, 0, 0));
        timetableTab.add(Ui.flowRight(10, addStop, editStop, removeStop), BorderLayout.NORTH);
        Card ttCard = new Card(new BorderLayout(), 0);
        ttCard.add(stops, BorderLayout.CENTER);
        JLabel note = Ui.muted("Times past midnight are marked \"+1 day\". Distances follow the railway tracks.");
        note.setBorder(new EmptyBorder(10, 16, 10, 16));
        ttCard.add(note, BorderLayout.SOUTH);
        timetableTab.add(ttCard, BorderLayout.CENTER);

        // Coaches tab
        JButton addCoach = Ui.secondary("Add coach", "plus");
        addCoach.addActionListener(e -> addCoach());
        JButton changeClass = Ui.secondary("Change class", "edit");
        changeClass.addActionListener(e -> changeCoach());
        JButton removeCoach = Ui.danger("Remove", "trash");
        removeCoach.addActionListener(e -> removeCoach());
        JPanel coachSide = Ui.transparent(new BorderLayout(0, 10));
        JPanel coachTools = Ui.transparent(new BorderLayout());
        coachTools.add(Ui.heading("Coaches"), BorderLayout.WEST);
        coachTools.add(Ui.flowRight(8, addCoach, changeClass, removeCoach), BorderLayout.EAST);
        coachSide.add(coachTools, BorderLayout.NORTH);
        Card coachCard = new Card(new BorderLayout(), 0);
        coachCard.add(coaches, BorderLayout.CENTER);
        coachSide.add(coachCard, BorderLayout.CENTER);

        JButton addSeats = Ui.secondary("Add places", "plus");
        addSeats.addActionListener(e -> addSeats());
        JButton removeSeat = Ui.danger("Remove", "trash");
        removeSeat.addActionListener(e -> removeSeat());
        JPanel seatSide = Ui.transparent(new BorderLayout(0, 10));
        JPanel seatTools = Ui.transparent(new BorderLayout());
        seatTools.add(seatsTitle, BorderLayout.WEST);
        seatTools.add(Ui.flowRight(8, addSeats, removeSeat), BorderLayout.EAST);
        seatSide.add(seatTools, BorderLayout.NORTH);
        Card seatCard = new Card(new BorderLayout(), 0);
        seatCard.add(seats, BorderLayout.CENTER);
        seatSide.add(seatCard, BorderLayout.CENTER);

        JPanel coachTab = new JPanel(new GridLayout(1, 2, 16, 0));
        coachTab.setOpaque(false);
        coachTab.setBorder(new EmptyBorder(14, 0, 0, 0));
        coachTab.add(coachSide);
        coachTab.add(seatSide);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(AppTheme.semibold(13));
        tabs.addTab("Timetable", Icons.get("clock", 16), timetableTab);
        tabs.addTab("Coaches & places", Icons.get("coach", 16), coachTab);

        JPanel headerArea = Ui.stack(header, Ui.vgap(12), problemBanner);
        JPanel panel = Ui.transparent(new BorderLayout(0, 10));
        panel.add(headerArea, BorderLayout.NORTH);
        panel.add(tabs, BorderLayout.CENTER);
        return panel;
    }

    @Override
    public void refresh() {
        loadTrains();
    }

    private void loadTrains() {
        String text = search.getText();
        String type = typeFilter.value();
        String keep = current == null ? null : current.str("Train_ID");
        Async.run(this, () -> TrainService.trains(text, type), rows -> {
            trains.setRows(rows);
            if (keep != null) {
                trains.select("Train_ID", keep);
            }
            if (trains.selected() == null) {
                trains.selectFirst();
            }
            if (rows.isEmpty()) {
                showTrain(null);
            }
        });
    }

    private void showTrain(Row row) {
        current = row;
        if (row == null) {
            detailCards.show(detail, "empty");
            return;
        }
        detailCards.show(detail, "detail");
        trainBadge.setText(row.str("Train_ID"));
        trainName.setText(row.str("Train_Name"));
        typeBadge.applyStatus(row.str("Train_Type"));
        statusBadge.applyStatus(row.str("Status"));
        sell.setEnabled("On sale".equals(row.str("Status")));
        if (row.str("Origin_Name") == null) {
            routeLine.setText("No route yet — add the stops of this train in the timetable below.");
        } else {
            String arrival = row.str("Destination_Name") == null ? ""
                    : "  →  " + row.str("Destination_Name") + " " + Formats.time(row.time("Arrival_Time"))
                    + (Formats.dayOffset(row.integer("Arrival_Day")).isEmpty() ? "" : " (" + Formats.dayOffset(row.integer("Arrival_Day")) + ")");
            routeLine.setText(row.str("Origin_Name") + " " + Formats.time(row.time("Departure_Time")) + arrival);
        }
        factsLine.setText(Formats.count(row.integer("Stop_Count")) + " stops  ·  "
                + Formats.km(row.integer("Route_Km")) + "  ·  journey " + Formats.duration(row.integer("Duration_Minutes"))
                + "  ·  " + Formats.count(row.integer("Coach_Count")) + " coaches  ·  "
                + Formats.count(row.integer("Seat_Count")) + " places");
        String problem = row.str("Timetable_Problem");
        problemBanner.setVisible(problem != null);
        problemText.setText(problem == null ? "" : "Tickets can't be sold until this is fixed: " + problem);

        String trainId = row.str("Train_ID");
        Async.run(this, () -> TrainService.timetable(trainId), stops::setRows);
        Async.run(this, () -> TrainService.coaches(trainId), rows -> {
            coaches.setRows(rows, "C_ID");
            if (coaches.selected() == null) {
                coaches.selectFirst();
            }
            loadSeats();
        });
    }

    private void reloadCurrent() {
        loadTrains();
    }

    private void loadSeats() {
        Row coach = coaches.selected();
        if (current == null || coach == null) {
            seats.setRows(List.of());
            seatsTitle.setText("Places");
            return;
        }
        String trainId = current.str("Train_ID");
        String coachId = coach.str("C_ID");
        seatsTitle.setText("Places in coach " + coachId);
        Async.run(this, () -> TrainService.seats(trainId, coachId), seats::setRows);
    }

    // ---- train ------------------------------------------------------------

    private void removeTrain() {
        if (current == null) {
            return;
        }
        String id = current.str("Train_ID");
        if (!Dialogs.confirm(this, "Remove train " + id + "?",
                "Its timetable, coaches and places will be removed too. This cannot be undone.", "Remove train", true)) {
            return;
        }
        Async.action(this, () -> TrainService.deleteTrain(id), () -> {
            Toast.success(this, "Train " + id + " was removed");
            current = null;
            loadTrains();
        }, problem -> Dialogs.error(this, problem));
    }

    // ---- stops ------------------------------------------------------------

    private void addStop() {
        if (current != null && new StopDialog(this, current.str("Train_ID"), null, stops.rows()).open()) {
            Toast.success(this, "Stop added");
            reloadCurrent();
        }
    }

    private void editStop() {
        Row stop = stops.selected();
        if (current == null || stop == null) {
            Dialogs.info(this, "Choose a stop", "Click a stop in the timetable first.");
            return;
        }
        if (new StopDialog(this, current.str("Train_ID"), stop, stops.rows()).open()) {
            Toast.success(this, "Stop saved");
            reloadCurrent();
        }
    }

    private void removeStop() {
        Row stop = stops.selected();
        if (current == null || stop == null) {
            Dialogs.info(this, "Choose a stop", "Click a stop in the timetable first.");
            return;
        }
        String trainId = current.str("Train_ID");
        int stopNo = stop.integer("Stop_No", 0);
        if (!Dialogs.confirm(this, "Remove " + stop.str("Station_Name") + "?",
                "Train " + trainId + " will no longer stop there. The following stops move up by one.",
                "Remove stop", true)) {
            return;
        }
        Async.action(this, () -> TrainService.deleteStop(trainId, stopNo), () -> {
            Toast.success(this, "Stop removed");
            reloadCurrent();
        }, problem -> Dialogs.error(this, problem));
    }

    // ---- coaches and seats -------------------------------------------------

    private void addCoach() {
        if (current != null && new CoachDialog(this, current.str("Train_ID"), null).open()) {
            Toast.success(this, "Coach added. Now add its places.");
            reloadCurrent();
        }
    }

    private void changeCoach() {
        Row coach = coaches.selected();
        if (current == null || coach == null) {
            Dialogs.info(this, "Choose a coach", "Click a coach in the list first.");
            return;
        }
        if (new CoachDialog(this, current.str("Train_ID"), coach).open()) {
            Toast.success(this, "Coach class saved");
            reloadCurrent();
        }
    }

    private void removeCoach() {
        Row coach = coaches.selected();
        if (current == null || coach == null) {
            Dialogs.info(this, "Choose a coach", "Click a coach in the list first.");
            return;
        }
        String trainId = current.str("Train_ID");
        String coachId = coach.str("C_ID");
        if (!Dialogs.confirm(this, "Remove coach " + coachId + "?",
                "All places in this coach will be removed too.", "Remove coach", true)) {
            return;
        }
        Async.action(this, () -> TrainService.deleteCoach(trainId, coachId), () -> {
            Toast.success(this, "Coach " + coachId + " was removed");
            reloadCurrent();
        }, problem -> Dialogs.error(this, problem));
    }

    private void addSeats() {
        Row coach = coaches.selected();
        if (current == null || coach == null) {
            Dialogs.info(this, "Choose a coach", "Click a coach first, then add places to it.");
            return;
        }
        if (new AddSeatsDialog(this, current.str("Train_ID"), coach).open()) {
            Toast.success(this, "Places added to coach " + coach.str("C_ID"));
            reloadCurrent();
        }
    }

    private void removeSeat() {
        Row coach = coaches.selected();
        Row seat = seats.selected();
        if (current == null || coach == null || seat == null) {
            Dialogs.info(this, "Choose a place", "Click a place in the list first.");
            return;
        }
        String trainId = current.str("Train_ID");
        String coachId = coach.str("C_ID");
        String seatId = seat.str("S_ID");
        if (!Dialogs.confirm(this, "Remove place " + seatId + "?",
                "It will no longer be offered for sale in coach " + coachId + ".", "Remove place", true)) {
            return;
        }
        Async.action(this, () -> TrainService.deleteSeat(trainId, coachId, seatId), () -> {
            Toast.success(this, "Place " + seatId + " was removed");
            reloadCurrent();
        }, problem -> Dialogs.error(this, problem));
    }
}
