package com.railway.app.ui.dialogs;

import com.github.lgooddatepicker.components.DatePicker;
import com.railway.app.data.Row;
import com.railway.app.service.BookingService;
import com.railway.app.ui.components.Async;
import com.railway.app.ui.components.Formats;
import com.railway.app.ui.components.FormDialog;
import com.railway.app.ui.components.Inputs;
import com.railway.app.ui.components.RowCombo;

import javax.swing.JTextField;
import java.awt.Component;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Changes the travel date, the place and/or the fare of an upcoming ticket.
 */
public class ChangeTicketDialog extends FormDialog {

    private final String bookingId;
    private final String trainId;
    private final DatePicker date;
    private final RowCombo coach = new RowCombo("C_ID",
            r -> "Coach " + r.str("C_ID") + " · " + r.str("Coach_Class") + " · " + r.str("Free") + " free", null);
    private final RowCombo seat = new RowCombo("S_ID", r -> r.str("Seat_Kind") + " " + r.str("S_ID")
            + (r.flag("Current") ? "  (current)" : ""), null);
    private final JTextField fare = Inputs.money();
    private List<Row> seatRows = new ArrayList<>();
    private String wantedCoach;
    private String wantedSeat;
    private boolean loading;

    public ChangeTicketDialog(Component owner, Row ticket) {
        super(owner, "Change ticket " + ticket.str("Booking_ID"),
                ticket.str("Passenger_Name") + " · train " + ticket.str("Train_ID"), "Save changes");
        this.bookingId = ticket.str("Booking_ID");
        this.trainId = ticket.str("Train_ID");
        this.wantedCoach = ticket.str("C_ID");
        this.wantedSeat = ticket.str("S_ID");
        this.date = Inputs.date(ticket.date("Travel_Date"));
        Inputs.setMoney(fare, ticket.decimal("Fare"));

        addField("Travel date", date, "The date the train leaves its first station");
        addField("Coach", coach, null);
        addField("Place", seat, "Only free places are listed");
        addField("Fare", fare, null);

        date.addDateChangeListener(e -> loadSeats());
        coach.addActionListener(e -> {
            if (!loading) {
                wantedCoach = coach.selectedKey();
                fillSeats();
            }
        });
        loadSeats();
    }

    private void loadSeats() {
        LocalDate day = date.getDate();
        Async.run(this, () -> BookingService.seatMap(trainId, day), rows -> {
            seatRows = rows;
            fillCoaches();
        });
    }

    private void fillCoaches() {
        Map<String, Row> coaches = new LinkedHashMap<>();
        Map<String, Integer> free = new LinkedHashMap<>();
        for (Row row : seatRows) {
            coaches.putIfAbsent(row.str("C_ID"), row);
            if (isChoosable(row)) {
                free.merge(row.str("C_ID"), 1, Integer::sum);
            }
        }
        List<Row> list = new ArrayList<>();
        for (Map.Entry<String, Row> entry : coaches.entrySet()) {
            Map<String, Object> values = new LinkedHashMap<>(entry.getValue().asMap());
            values.put("Free", free.getOrDefault(entry.getKey(), 0));
            list.add(new Row(values));
        }
        loading = true;
        coach.setRows(list);
        coach.selectKey(wantedCoach);
        loading = false;
        fillSeats();
    }

    private boolean isChoosable(Row row) {
        return !row.flag("Is_Booked") || bookingId.equals(row.str("Booking_ID"));
    }

    private void fillSeats() {
        String coachId = coach.selectedKey();
        List<Row> list = new ArrayList<>();
        for (Row row : seatRows) {
            if (row.str("C_ID").equals(coachId) && isChoosable(row)) {
                Map<String, Object> values = new LinkedHashMap<>(row.asMap());
                values.put("Current", bookingId.equals(row.str("Booking_ID")) ? 1 : 0);
                list.add(new Row(values));
            }
        }
        seat.setRows(list);
        seat.selectKey(wantedSeat);
    }

    @Override
    protected Async.Action buildSave() {
        LocalDate day = date.getDate();
        String coachId = coach.selectedKey();
        String seatId = seat.selectedKey();
        BigDecimal amount = Formats.parseMoney(fare.getText());
        return () -> BookingService.changeTicket(bookingId, day, coachId, seatId, amount);
    }
}
