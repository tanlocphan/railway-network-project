package com.railway.app.ui.pages;

import com.formdev.flatlaf.FlatClientProperties;
import com.github.lgooddatepicker.components.DatePicker;
import com.railway.app.data.Row;
import com.railway.app.service.BookingService;
import com.railway.app.service.NetworkService;
import com.railway.app.ui.Navigator;
import com.railway.app.ui.Page;
import com.railway.app.ui.components.Async;
import com.railway.app.ui.components.Badge;
import com.railway.app.ui.components.Card;
import com.railway.app.ui.components.Dialogs;
import com.railway.app.ui.components.EmptyState;
import com.railway.app.ui.components.Formats;
import com.railway.app.ui.components.Inputs;
import com.railway.app.ui.components.RowCombo;
import com.railway.app.ui.components.SearchField;
import com.railway.app.ui.components.SeatMap;
import com.railway.app.ui.components.Segmented;
import com.railway.app.ui.components.StepIndicator;
import com.railway.app.ui.components.TicketView;
import com.railway.app.ui.components.Toast;
import com.railway.app.ui.components.TrackingPanel;
import com.railway.app.ui.components.Ui;
import com.railway.app.ui.theme.AppTheme;
import com.railway.app.ui.theme.Icons;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Four-step ticket booking: trip, seat, passenger and payment, ticket.
 */
public class BookingPage extends Page {

    private final Navigator navigator;
    private final StepIndicator steps = new StepIndicator("Choose a trip", "Pick a seat", "Passenger & payment", "Ticket");
    private final CardLayout stepCards = new CardLayout();
    private final JPanel stepPanel = new JPanel(stepCards);
    private int step;

    // Step 1 - trip
    private final RowCombo fromCombo = new RowCombo("Station_Code", r -> r.str("Station_Name"), "Any station");
    private final RowCombo toCombo = new RowCombo("Station_Code", r -> r.str("Station_Name"), "Any station");
    private final DatePicker datePicker = Inputs.date(LocalDate.now());
    private final JPanel results = new JPanel();
    private final JLabel resultsTitle = Ui.heading(" ");
    private final JLabel resultsNote = Ui.muted(" ");
    private String preferredTrain;
    private boolean lookupsLoaded;
    private LocalDate searchedDate;

    // Booking state
    private Row trip;
    private LocalDate travelDate;
    private List<Row> seatRows = new ArrayList<>();
    private String coachId;
    private String coachClass;
    private String seatKind = "Seat";
    private String seatId;
    private BigDecimal suggestedFare;

    // Step 2 - seat
    private final JLabel tripHeadline = Ui.strong(" ", 16);
    private final JLabel tripDetails = Ui.muted(" ");
    private final JPanel coachList = Ui.stack();
    private final SeatMap seatMap = new SeatMap();
    private final JLabel pickedSeat = Ui.strong("No place chosen", 20);
    private final JLabel pickedCoach = Ui.muted("Click a free place on the coach");
    private final JTextArea fareHint = Ui.paragraph(" ", AppTheme.TEXT_SECONDARY);
    private final JButton toPassenger = Ui.primary("Continue", "arrow-right");

    // Step 3 - passenger and payment
    private final Segmented passengerMode = new Segmented();
    private final CardLayout passengerCards = new CardLayout();
    private final JPanel passengerPanel = new JPanel(passengerCards);
    private final DefaultListModel<Row> passengerModel = new DefaultListModel<>();
    private final JList<Row> passengerList = new JList<>(passengerModel);
    private final JTextField newName = Inputs.text("Full name, e.g. Nguyen Van An");
    private final JTextField newPhones = Inputs.text("e.g. 0901234567 (optional)");
    private final JTextField fareField = Inputs.money();
    private final Segmented paymentMethod = new Segmented();
    private final JPanel summaryBox = Ui.stack();
    private final JButton confirmButton = Ui.primary("Confirm booking", "check");

    // Step 4 - ticket
    private final TicketView ticketView = new TicketView();
    private final JLabel doneTitle = Ui.strong("Ticket booked!", 22);
    private String lastBookingId;

    public BookingPage(Navigator navigator) {
        super("Book Tickets", "Find a train, choose a place and issue the ticket in four short steps.");
        this.navigator = navigator;

        Card stepCard = new Card(new BorderLayout(), 10);
        stepCard.add(steps, BorderLayout.CENTER);
        add(stepCard, BorderLayout.NORTH);

        stepPanel.setOpaque(false);
        stepPanel.add(buildTripStep(), "0");
        stepPanel.add(buildSeatStep(), "1");
        stepPanel.add(buildPassengerStep(), "2");
        stepPanel.add(buildDoneStep(), "3");
        add(stepPanel, BorderLayout.CENTER);
        goStep(0);
    }

    // ======================================================================
    // Step 1: choose a trip
    // ======================================================================

    private JComponent buildTripStep() {
        Card form = new Card(new GridBagLayout(), 18);
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.SOUTHWEST;
        c.insets = new Insets(0, 0, 0, 12);
        c.gridy = 0;
        c.gridx = 0;
        form.add(fieldBlock("From", fromCombo), c);
        JButton swap = Ui.secondary(null, "swap");
        swap.setToolTipText("Swap stations");
        swap.setPreferredSize(new Dimension(40, 36));
        swap.addActionListener(e -> {
            String from = fromCombo.selectedKey();
            fromCombo.selectKey(toCombo.selectedKey());
            toCombo.selectKey(from);
        });
        c.gridx = 1;
        form.add(swap, c);
        c.gridx = 2;
        form.add(fieldBlock("To", toCombo), c);
        c.gridx = 3;
        form.add(fieldBlock("Travel date (train leaves its first station)", datePicker), c);
        JButton search = Ui.primary("Search trains", "search");
        search.setPreferredSize(new Dimension(160, 36));
        search.addActionListener(e -> search());
        c.gridx = 4;
        c.weightx = 1;
        form.add(search, c);

        results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS));
        results.setOpaque(false);
        TrackingPanel holder = new TrackingPanel(new BorderLayout());
        holder.add(results, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(holder);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(18);

        JPanel header = Ui.transparent(new BorderLayout());
        header.setBorder(new EmptyBorder(18, 2, 10, 2));
        header.add(Ui.stack(resultsTitle, Ui.vgap(2), resultsNote), BorderLayout.WEST);

        JPanel panel = Ui.transparent(new BorderLayout());
        panel.setBorder(new EmptyBorder(16, 0, 0, 0));
        panel.add(form, BorderLayout.NORTH);
        JPanel body = Ui.transparent(new BorderLayout());
        body.add(header, BorderLayout.NORTH);
        body.add(scroll, BorderLayout.CENTER);
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    private static JComponent fieldBlock(String label, JComponent field) {
        JLabel caption = Ui.label(label, AppTheme.semibold(12), AppTheme.TEXT_SECONDARY);
        return Ui.stack(caption, Ui.vgap(6), field);
    }

    private void search() {
        String from = fromCombo.selectedKey();
        String to = toCombo.selectedKey();
        LocalDate date = datePicker.getDate();
        Async.run(this, () -> BookingService.searchTrips(from, to, date), rows -> {
            searchedDate = date;
            showResults(rows);
        }, problem -> {
            results.removeAll();
            resultsTitle.setText("No trains to show");
            resultsNote.setText(problem.getMessage());
            results.revalidate();
            results.repaint();
        });
    }

    private void showResults(List<Row> rows) {
        results.removeAll();
        List<Row> ordered = new ArrayList<>(rows);
        if (preferredTrain != null) {
            ordered.sort((a, b) -> Boolean.compare(!preferredTrain.equals(a.str("Train_ID")),
                    !preferredTrain.equals(b.str("Train_ID"))));
        }
        long bookable = rows.stream().filter(r -> r.flag("Can_Book")).count();
        resultsTitle.setText(rows.isEmpty() ? "No trains found"
                : rows.size() + (rows.size() == 1 ? " train" : " trains") + " on " + Formats.date(searchedDate));
        resultsNote.setText(rows.isEmpty()
                ? "Try another date, or choose \"Any station\" to see every train."
                : bookable + " can be booked. A ticket holds its place for the train's whole journey.");
        if (rows.isEmpty()) {
            results.add(EmptyState.create("train", "No trains match this search",
                    "No train stops at both stations in that order.", null));
        }
        for (Row row : ordered) {
            JComponent card = tripCard(row, row.str("Train_ID").equals(preferredTrain));
            card.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
            results.add(card);
            results.add(Box.createRigidArea(new Dimension(0, 12)));
        }
        preferredTrain = null;
        results.revalidate();
        results.repaint();
    }

    private JComponent tripCard(Row row, boolean highlight) {
        Card card = new Card(new BorderLayout(24, 0), 18);
        if (highlight) {
            card.outline(AppTheme.ORANGE);
        }

        Badge number = new Badge(row.str("Train_ID"), Color.WHITE, AppTheme.NAVY);
        number.setFont(AppTheme.semibold(13));
        JPanel left = Ui.stack(
                Ui.flow(8, number, Badge.of(row.str("Train_Type"))),
                Ui.vgap(8),
                Ui.strong(row.str("Train_Name"), 13.5f),
                Ui.vgap(3),
                Ui.muted("Whole route: " + row.str("Origin_Name") + " → " + row.str("Destination_Name")));
        left.setPreferredSize(new Dimension(310, left.getPreferredSize().height));
        card.add(left, BorderLayout.WEST);

        JComponent journey = journeyBlock(
                Formats.time(row.time("Board_Time")), Formats.dayOffset(row.integer("Board_Day")), row.str("Board_Name"),
                Formats.time(row.time("Alight_Time")), Formats.dayOffset(row.integer("Alight_Day")), row.str("Alight_Name"),
                Formats.duration(row.integer("Duration_Minutes")) + "  ·  " + Formats.km(row.integer("Distance_Km")));
        card.add(journey, BorderLayout.CENTER);

        String availability = row.str("Availability");
        int free = row.integer("Free_Seats", 0);
        int total = row.integer("Total_Seats", 0);
        Badge state = Badge.of(availability);
        JLabel seats = Ui.muted(total == 0 ? "No seats set up" : free + " of " + total + " places free");
        JButton select = Ui.primary("Select", "arrow-right");
        select.setHorizontalTextPosition(SwingConstants.LEFT);
        select.setEnabled(row.flag("Can_Book"));
        select.addActionListener(e -> selectTrip(row));
        JPanel right = Ui.stack(Ui.flowRight(0, state), Ui.vgap(6), Ui.flowRight(0, seats), Ui.vgap(10),
                Ui.flowRight(0, select));
        right.setPreferredSize(new Dimension(190, right.getPreferredSize().height));
        card.add(right, BorderLayout.EAST);
        return card;
    }

    /** Departure time, a line with duration, arrival time. */
    private static JComponent journeyBlock(String fromTime, String fromDay, String fromName,
                                           String toTime, String toDay, String toName, String middle) {
        JPanel panel = Ui.transparent(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        panel.add(timeLabel(fromTime, fromDay), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 16, 0, 16);
        panel.add(new RouteLine(middle), c);
        c.gridx = 2;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(0, 0, 0, 0);
        c.anchor = GridBagConstraints.EAST;
        panel.add(timeLabel(toTime, toDay), c);
        c.gridy = 1;
        c.gridx = 0;
        c.anchor = GridBagConstraints.WEST;
        panel.add(Ui.muted(fromName), c);
        c.gridx = 2;
        c.anchor = GridBagConstraints.EAST;
        panel.add(Ui.muted(toName), c);
        return panel;
    }

    private static JLabel timeLabel(String time, String day) {
        String text = "<html><span style='font-size:20px'>" + time + "</span>"
                + (day.isEmpty() ? "" : "&nbsp;<span style='color:" + AppTheme.hex(AppTheme.ORANGE_DARK)
                + ";font-size:10px'>" + day + "</span>") + "</html>";
        JLabel label = new JLabel(text);
        label.setFont(AppTheme.semibold(14));
        label.setForeground(AppTheme.TEXT);
        return label;
    }

    /** Thin line with dots at both ends and a caption in the middle. */
    private static final class RouteLine extends JComponent {
        private final String caption;

        RouteLine(String caption) {
            this.caption = caption;
            setPreferredSize(new Dimension(160, 34));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int y = getHeight() - 10;
            g.setColor(AppTheme.BORDER);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{4f, 5f}, 0f));
            g.drawLine(8, y, getWidth() - 8, y);
            g.setColor(AppTheme.ORANGE);
            g.fillOval(2, y - 4, 8, 8);
            g.fillOval(getWidth() - 10, y - 4, 8, 8);
            g.setFont(AppTheme.font(Font.PLAIN, 11.5f));
            g.setColor(AppTheme.TEXT_MUTED);
            FontMetrics metrics = g.getFontMetrics();
            g.drawString(caption, (getWidth() - metrics.stringWidth(caption)) / 2, y - 9);
            g.dispose();
        }
    }

    private void selectTrip(Row row) {
        trip = row;
        travelDate = searchedDate;
        coachId = null;
        seatId = null;
        suggestedFare = null;
        fareField.setText("");
        tripHeadline.setText(row.str("Train_ID") + "  ·  " + row.str("Origin_Name") + "  →  "
                + row.str("Destination_Name"));
        String arrival = row.dateTime("Trip_Arrival") == null ? "" : ", arrives "
                + Formats.dateTime(row.dateTime("Trip_Arrival"));
        tripDetails.setText(row.str("Train_Name") + "  ·  departs " + Formats.dateTime(row.dateTime("Trip_Departure"))
                + arrival);
        updateSelection();
        goStep(1);
        loadSeats();
    }

    // ======================================================================
    // Step 2: pick a seat
    // ======================================================================

    private JComponent buildSeatStep() {
        Card tripBar = new Card(new BorderLayout(16, 0), 16);
        JLabel trainIcon = new JLabel(Icons.get("train", 22, AppTheme.ORANGE));
        tripBar.add(trainIcon, BorderLayout.WEST);
        tripBar.add(Ui.stack(tripHeadline, Ui.vgap(3), tripDetails), BorderLayout.CENTER);
        JButton change = Ui.secondary("Change trip", "arrow-left");
        change.addActionListener(e -> goStep(0));
        tripBar.add(Ui.flowRight(0, change), BorderLayout.EAST);

        Card coaches = new Card(new BorderLayout(0, 12), 16);
        coaches.add(Ui.heading("Coaches"), BorderLayout.NORTH);
        TrackingPanel coachHolder = new TrackingPanel(new BorderLayout());
        coachHolder.add(coachList, BorderLayout.NORTH);
        JScrollPane coachScroll = new JScrollPane(coachHolder);
        coachScroll.setBorder(null);
        coachScroll.setOpaque(false);
        coachScroll.getViewport().setOpaque(false);
        coaches.add(coachScroll, BorderLayout.CENTER);
        coaches.setPreferredSize(new Dimension(250, 0));

        Card mapCard = new Card(new BorderLayout(), 6);
        seatMap.onSelect(id -> {
            seatId = id;
            updateSelection();
            loadSuggestedFare();
        });
        JScrollPane mapScroll = new JScrollPane(seatMap);
        mapScroll.setBorder(null);
        mapScroll.getViewport().setBackground(AppTheme.SURFACE);
        mapCard.add(mapScroll, BorderLayout.CENTER);

        Card selection = new Card(new BorderLayout(0, 12), 20);
        selection.setPreferredSize(new Dimension(290, 0));
        JPanel info = Ui.stack(Ui.caption("Your selection"), Ui.vgap(8), pickedSeat, Ui.vgap(4), pickedCoach,
                Ui.vgap(18), separator(), Ui.vgap(16), Ui.caption("Fare"), Ui.vgap(6), fareHint);
        selection.add(info, BorderLayout.NORTH);
        JButton back = Ui.secondary("Back", "arrow-left");
        back.addActionListener(e -> goStep(0));
        toPassenger.setHorizontalTextPosition(SwingConstants.LEFT);
        toPassenger.addActionListener(e -> openPassengerStep());
        selection.add(Ui.flowRight(10, back, toPassenger), BorderLayout.SOUTH);

        JPanel middle = Ui.transparent(new BorderLayout(16, 0));
        middle.add(coaches, BorderLayout.WEST);
        middle.add(mapCard, BorderLayout.CENTER);
        middle.add(selection, BorderLayout.EAST);

        JPanel panel = Ui.transparent(new BorderLayout(0, 16));
        panel.setBorder(new EmptyBorder(16, 0, 0, 0));
        panel.add(tripBar, BorderLayout.NORTH);
        panel.add(middle, BorderLayout.CENTER);
        return panel;
    }

    private static JComponent separator() {
        JSeparator separator = new JSeparator();
        separator.setForeground(AppTheme.BORDER);
        separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return separator;
    }

    private void loadSeats() {
        String trainId = trip.str("Train_ID");
        LocalDate date = travelDate;
        Async.run(this, () -> BookingService.seatMap(trainId, date), rows -> {
            seatRows = rows;
            buildCoachList();
        });
    }

    private void buildCoachList() {
        Map<String, List<Row>> byCoach = new LinkedHashMap<>();
        for (Row row : seatRows) {
            byCoach.computeIfAbsent(row.str("C_ID"), k -> new ArrayList<>()).add(row);
        }
        coachList.removeAll();
        ButtonGroup group = new ButtonGroup();
        String firstWithSpace = null;
        Map<String, JToggleButton> buttons = new LinkedHashMap<>();
        for (Map.Entry<String, List<Row>> entry : byCoach.entrySet()) {
            List<Row> places = entry.getValue();
            long free = places.stream().filter(r -> !r.flag("Is_Booked")).count();
            String cls = places.get(0).str("Coach_Class");
            JToggleButton button = new JToggleButton("<html><b>Coach " + entry.getKey() + "</b><br><span style='color:"
                    + AppTheme.hex(AppTheme.TEXT_SECONDARY) + "'>" + Formats.html(cls) + "<br>" + free + " of "
                    + places.size() + " free</span></html>");
            button.setHorizontalAlignment(SwingConstants.LEFT);
            button.setFont(AppTheme.font(Font.PLAIN, 12.5f));
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            button.setFocusable(false);
            button.putClientProperty(FlatClientProperties.STYLE,
                    "arc:10; margin:8,12,8,12; borderWidth:1; focusWidth:0; background:#FFFFFF; borderColor:#E3E8F1;"
                            + "hoverBackground:#FFF8F3; selectedBackground:#FFF1E7; selectedForeground:#18213A;"
                            + "hoverBorderColor:#F26B1D");
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 74));
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            String id = entry.getKey();
            button.addActionListener(e -> showCoach(id));
            group.add(button);
            coachList.add(button);
            coachList.add(Box.createRigidArea(new Dimension(0, 8)));
            buttons.put(id, button);
            if (firstWithSpace == null && free > 0) {
                firstWithSpace = id;
            }
        }
        if (byCoach.isEmpty()) {
            coachList.add(Ui.muted("This train has no seats yet."));
        }
        String initial = coachId != null && buttons.containsKey(coachId) ? coachId
                : firstWithSpace != null ? firstWithSpace : (buttons.isEmpty() ? null : buttons.keySet().iterator().next());
        if (initial != null) {
            buttons.get(initial).setSelected(true);
            showCoach(initial);
        }
        coachList.revalidate();
        coachList.repaint();
    }

    private void showCoach(String id) {
        if (!id.equals(coachId)) {
            seatId = null;
        }
        coachId = id;
        List<SeatMap.Place> places = new ArrayList<>();
        for (Row row : seatRows) {
            if (id.equals(row.str("C_ID"))) {
                coachClass = row.str("Coach_Class");
                seatKind = row.str("Seat_Kind", "Seat");
                places.add(new SeatMap.Place(row.str("S_ID"), row.flag("Is_Booked")));
            }
        }
        seatMap.setCoach("Coach " + id + "  ·  " + coachClass, coachClass, "Berth".equals(seatKind), places, seatId);
        updateSelection();
    }

    private void updateSelection() {
        if (seatId == null) {
            pickedSeat.setText("No place chosen");
            pickedCoach.setText("Click a free place on the coach");
            fareHint.setText("The fare will be suggested once you choose a place.");
            toPassenger.setEnabled(false);
        } else {
            pickedSeat.setText(seatKind + " " + seatId);
            pickedCoach.setText("Coach " + coachId + " · " + coachClass);
            toPassenger.setEnabled(true);
        }
    }

    private void loadSuggestedFare() {
        String trainId = trip.str("Train_ID");
        String coach = coachId;
        Async.run(this, () -> BookingService.suggestFare(trainId, coach), fare -> {
            suggestedFare = fare;
            fareHint.setText(fare == null
                    ? "No ticket has been sold for this class on this train yet. You will enter the fare in the next step."
                    : Formats.money(fare) + " is the usual fare for " + coachClass + " on " + trainId
                    + ". You can change it in the next step.");
        });
    }

    // ======================================================================
    // Step 3: passenger and payment
    // ======================================================================

    private JComponent buildPassengerStep() {
        passengerMode.add("Existing passenger", "existing").add("New passenger", "new");
        passengerMode.onChange(value -> passengerCards.show(passengerPanel, value));

        SearchField passengerSearch = new SearchField("Search by name, phone or passenger number", this::searchPassengers);
        passengerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        passengerList.setFixedCellHeight(52);
        passengerList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, false);
                Row row = (Row) value;
                setText("<html><b>" + Formats.html(row.str("Passenger_Name")) + "</b><br><span style='color:"
                        + AppTheme.hex(AppTheme.TEXT_SECONDARY) + "'>" + row.str("Passenger_ID") + "  ·  "
                        + Formats.html(row.str("Phones", "no phone number")) + "</span></html>");
                setIcon(Icons.get("user", 18, selected ? AppTheme.ORANGE : AppTheme.TEXT_MUTED));
                setIconTextGap(12);
                setBorder(new EmptyBorder(4, 12, 4, 12));
                return this;
            }
        });
        JScrollPane listScroll = new JScrollPane(passengerList);
        listScroll.setBorder(BorderFactory.createLineBorder(AppTheme.BORDER));
        listScroll.setPreferredSize(new Dimension(420, 230));
        JPanel existing = Ui.transparent(new BorderLayout(0, 10));
        existing.add(passengerSearch, BorderLayout.NORTH);
        existing.add(listScroll, BorderLayout.CENTER);

        JPanel created = Ui.transparent(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 12, 0);
        created.add(fieldBlock("Full name", newName), c);
        c.gridy = 1;
        created.add(fieldBlock("Phone numbers (separate several with commas)", newPhones), c);
        c.gridy = 2;
        c.weighty = 1;
        created.add(Ui.muted("The passenger is saved together with the ticket."), c);

        passengerPanel.setOpaque(false);
        passengerPanel.add(existing, "existing");
        passengerPanel.add(created, "new");

        Card form = new Card(new GridBagLayout(), 22);
        GridBagConstraints f = new GridBagConstraints();
        f.gridx = 0;
        f.gridy = 0;
        f.anchor = GridBagConstraints.NORTHWEST;
        f.fill = GridBagConstraints.HORIZONTAL;
        f.weightx = 1;
        f.insets = new Insets(0, 0, 10, 0);
        form.add(Ui.heading("Who is travelling?"), f);
        f.gridy++;
        form.add(Ui.flow(0, passengerMode), f);
        f.gridy++;
        f.fill = GridBagConstraints.BOTH;
        f.weighty = 1;
        f.insets = new Insets(0, 0, 22, 0);
        form.add(passengerPanel, f);
        f.gridy++;
        f.weighty = 0;
        f.fill = GridBagConstraints.HORIZONTAL;
        f.insets = new Insets(0, 0, 10, 0);
        JPanel payRow = Ui.transparent(new GridBagLayout());
        GridBagConstraints p = new GridBagConstraints();
        p.anchor = GridBagConstraints.NORTHWEST;
        p.insets = new Insets(0, 0, 0, 28);
        payRow.add(fieldBlock("Fare", fareField), p);
        p.weightx = 1;
        payRow.add(fieldBlock("How is the ticket paid?", paymentMethod), p);
        form.add(payRow, f);

        Card side = new Card(new BorderLayout(0, 12), 20);
        side.setPreferredSize(new Dimension(320, 0));
        side.add(Ui.stack(Ui.caption("Booking summary"), Ui.vgap(10), summaryBox), BorderLayout.NORTH);
        JButton back = Ui.secondary("Back", "arrow-left");
        back.addActionListener(e -> goStep(1));
        confirmButton.addActionListener(e -> confirm());
        side.add(Ui.flowRight(10, back, confirmButton), BorderLayout.SOUTH);

        JPanel panel = Ui.transparent(new BorderLayout(16, 0));
        panel.setBorder(new EmptyBorder(16, 0, 0, 0));
        panel.add(form, BorderLayout.CENTER);
        panel.add(side, BorderLayout.EAST);
        return panel;
    }

    private void openPassengerStep() {
        if (seatId == null) {
            return;
        }
        if (Formats.parseMoney(fareField.getText()) == null && suggestedFare != null) {
            Inputs.setMoney(fareField, suggestedFare);
        }
        summaryBox.removeAll();
        summaryLine("Train", trip.str("Train_ID") + " · " + trip.str("Train_Name"));
        summaryLine("Route", trip.str("Origin_Name") + " → " + trip.str("Destination_Name"));
        summaryLine("Departs", Formats.dateTime(trip.dateTime("Trip_Departure")));
        summaryLine("Arrives", Formats.dateTime(trip.dateTime("Trip_Arrival")));
        summaryLine("Place", "Coach " + coachId + " · " + seatKind + " " + seatId);
        summaryLine("Class", coachClass);
        summaryBox.revalidate();
        goStep(2);
        if (passengerModel.isEmpty()) {
            searchPassengers("");
        }
    }

    private void summaryLine(String label, String value) {
        JPanel line = Ui.transparent(new BorderLayout(12, 0));
        line.setBorder(new EmptyBorder(0, 0, 10, 0));
        line.add(Ui.muted(label), BorderLayout.WEST);
        JLabel text = Ui.label(value, AppTheme.semibold(12.5f), AppTheme.TEXT);
        text.setHorizontalAlignment(SwingConstants.RIGHT);
        line.add(text, BorderLayout.CENTER);
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        summaryBox.add(line);
    }

    private void searchPassengers(String text) {
        Async.run(this, () -> BookingService.passengers(text), rows -> {
            passengerModel.clear();
            rows.forEach(passengerModel::addElement);
        });
    }

    private void confirm() {
        boolean existing = "existing".equals(passengerMode.value());
        Row passenger = passengerList.getSelectedValue();
        String passengerId = existing && passenger != null ? passenger.str("Passenger_ID") : null;
        String name = existing ? null : newName.getText();
        String phones = existing ? null : newPhones.getText();
        BigDecimal fare = Formats.parseMoney(fareField.getText());
        String method = paymentMethod.value();
        String trainId = trip.str("Train_ID");
        LocalDate date = travelDate;
        String coach = coachId;
        String seat = seatId;

        confirmButton.setEnabled(false);
        Async.run(this, () -> {
            String bookingId = BookingService.book(passengerId, name, phones, trainId, date, coach, seat, fare, method);
            return BookingService.ticket(bookingId);
        }, ticket -> {
            confirmButton.setEnabled(true);
            lastBookingId = ticket.str("Booking_ID");
            ticketView.setTicket(ticket);
            doneTitle.setText("Ticket " + lastBookingId + " is booked!");
            goStep(3);
            Toast.success(this, "Ticket " + lastBookingId + " booked for " + ticket.str("Passenger_Name"));
        }, problem -> {
            confirmButton.setEnabled(true);
            Dialogs.error(this, problem);
            if (problem.getMessage() != null && problem.getMessage().contains("already sold")) {
                goStep(1);
                loadSeats();
            }
        });
    }

    // ======================================================================
    // Step 4: ticket
    // ======================================================================

    private JComponent buildDoneStep() {
        JButton print = Ui.secondary("Print ticket", "printer");
        print.addActionListener(e -> ticketView.print());
        JButton another = Ui.primary("Book another ticket", "plus");
        another.addActionListener(e -> startOver());
        JButton view = Ui.secondary("Open in Tickets", "receipt");
        view.addActionListener(e -> navigator.openTickets(lastBookingId));

        JPanel content = Ui.stack(doneTitle, Ui.vgap(4),
                Ui.muted("Give the passenger their ticket, or print it below."), Ui.vgap(20), ticketView,
                Ui.vgap(20), Ui.flow(10, print, view, another));
        for (Component c : content.getComponents()) {
            if (c instanceof JComponent jc) {
                jc.setAlignmentX(Component.CENTER_ALIGNMENT);
            }
        }
        doneTitle.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel panel = Ui.transparent(new GridBagLayout());
        panel.setBorder(new EmptyBorder(16, 0, 0, 0));
        panel.add(content);
        return panel;
    }

    private void startOver() {
        trip = null;
        seatId = null;
        coachId = null;
        newName.setText("");
        newPhones.setText("");
        fareField.setText("");
        passengerList.clearSelection();
        goStep(0);
        search();
    }

    // ======================================================================

    private void goStep(int index) {
        step = index;
        steps.setCurrent(index);
        stepCards.show(stepPanel, String.valueOf(index));
    }

    /** Called from other pages: start a booking for this train. */
    public void presetTrain(String trainId) {
        preferredTrain = trainId;
        fromCombo.selectKey(null);
        toCombo.selectKey(null);
        goStep(0);
    }

    @Override
    public void refresh() {
        if (!lookupsLoaded) {
            Async.run(this, NetworkService::allStations, rows -> {
                fromCombo.setRows(rows);
                toCombo.setRows(rows);
                lookupsLoaded = true;
                if (step == 0) {
                    search();
                }
            });
            Async.run(this, NetworkService::paymentMethods, rows -> {
                for (Row row : rows) {
                    paymentMethod.add(row.str("Method"), row.str("Method"));
                }
                paymentMethod.revalidate();
            });
            return;
        }
        if (step == 0) {
            search();
        } else if (step == 1 && trip != null) {
            loadSeats();
        }
    }
}
