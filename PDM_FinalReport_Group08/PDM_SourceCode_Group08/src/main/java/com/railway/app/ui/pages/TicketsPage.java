package com.railway.app.ui.pages;

import com.railway.app.data.Row;
import com.railway.app.service.BookingService;
import com.railway.app.ui.Navigator;
import com.railway.app.ui.Page;
import com.railway.app.ui.components.Async;
import com.railway.app.ui.components.Card;
import com.railway.app.ui.components.DataTable;
import com.railway.app.ui.components.DataTable.Column;
import com.railway.app.ui.components.DataTable.Kind;
import com.railway.app.ui.components.Dialogs;
import com.railway.app.ui.components.SearchField;
import com.railway.app.ui.components.Segmented;
import com.railway.app.ui.components.Toast;
import com.railway.app.ui.components.Ui;
import com.railway.app.ui.dialogs.ChangeTicketDialog;
import com.railway.app.ui.dialogs.TicketDialog;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.List;

/**
 * All tickets: search, view and print, change, cancel.
 */
public class TicketsPage extends Page {

    private final SearchField search = new SearchField("Search by ticket number, passenger, phone or train", t -> load());
    private final Segmented status = new Segmented();
    private final DataTable table;
    private final JButton view = Ui.secondary("View ticket", "receipt");
    private final JButton change = Ui.secondary("Change", "edit");
    private final JButton cancel = Ui.danger("Cancel ticket", "trash");
    private final JLabel count = Ui.muted(" ");

    public TicketsPage(Navigator navigator) {
        super("Tickets", "Every ticket sold. Open one to print it, change its date or seat, or cancel it.");

        status.add("All", "").add("Upcoming", "Upcoming").add("On the way", "On the way").add("Completed", "Completed");
        status.onChange(v -> load());

        table = new DataTable(List.of(
                Column.of("Ticket no.", "Booking_ID", Kind.STRONG, 100),
                Column.of("Passenger", "Passenger_Name", Kind.TEXT, 150),
                Column.of("Train", "Train_ID", Kind.TEXT, 68),
                Column.custom("Route", 230, r -> r.str("Origin_Name", "—") + " → " + r.str("Destination_Name", "—")),
                Column.of("Departs", "Departure_At", Kind.DATETIME, 145),
                Column.custom("Place", 125, r -> r.str("C_ID") + " · " + r.str("Seat_Kind") + " " + r.str("S_ID")),
                Column.of("Fare", "Fare", Kind.MONEY, 105),
                Column.custom("Payment", 100, r -> r.str("Pay_Method", "Not recorded")),
                Column.of("Status", "Status", Kind.BADGE, 105)));
        table.setEmptyMessage("No tickets match your search");
        table.onSelect(row -> updateButtons());
        table.onDoubleClick(row -> openTicket());

        view.addActionListener(e -> openTicket());
        change.addActionListener(e -> changeTicket());
        cancel.addActionListener(e -> cancelTicket());
        JButton book = Ui.primary("New booking", "plus");
        book.addActionListener(e -> navigator.go("book"));

        JPanel toolbar = Ui.transparent(new BorderLayout(12, 0));
        toolbar.add(Ui.flow(12, search, status), BorderLayout.WEST);
        toolbar.add(Ui.flowRight(10, view, change, cancel), BorderLayout.EAST);

        Card card = new Card(new BorderLayout(0, 0), 0);
        card.add(table, BorderLayout.CENTER);
        JPanel footer = Ui.transparent(new BorderLayout());
        footer.setBorder(new javax.swing.border.EmptyBorder(10, 16, 10, 16));
        footer.add(count, BorderLayout.WEST);
        card.add(footer, BorderLayout.SOUTH);

        add(toolbar, BorderLayout.NORTH);
        add(card, BorderLayout.CENTER);
        updateButtons();
    }

    public void presetSearch(String text) {
        status.select("");
        search.setText(text == null ? "" : text);
    }

    @Override
    public void refresh() {
        load();
    }

    private void load() {
        String text = search.getText();
        String state = status.value();
        Async.run(this, () -> BookingService.tickets(text, state, null, null), rows -> {
            table.setRows(rows, "Booking_ID");
            count.setText(rows.size() + (rows.size() == 1 ? " ticket" : " tickets"));
            if (rows.size() == 1) {
                table.selectFirst();
            }
            updateButtons();
        });
    }

    private void updateButtons() {
        Row row = table.selected();
        boolean upcoming = row != null && "Upcoming".equals(row.str("Status"));
        view.setEnabled(row != null);
        change.setEnabled(upcoming);
        cancel.setEnabled(upcoming);
        String why = row != null && !upcoming ? "Only tickets for trips that have not departed yet can be changed" : null;
        change.setToolTipText(why);
        cancel.setToolTipText(why);
    }

    private void openTicket() {
        Row row = table.selected();
        if (row == null) {
            return;
        }
        String id = row.str("Booking_ID");
        Async.run(this, () -> BookingService.ticket(id), ticket -> new TicketDialog(this, ticket).setVisible(true));
    }

    private void changeTicket() {
        Row row = table.selected();
        if (row == null) {
            return;
        }
        if (new ChangeTicketDialog(this, row).open()) {
            Toast.success(this, "Ticket " + row.str("Booking_ID") + " has been changed");
            load();
        }
    }

    private void cancelTicket() {
        Row row = table.selected();
        if (row == null) {
            return;
        }
        String id = row.str("Booking_ID");
        boolean ok = Dialogs.confirm(this, "Cancel ticket " + id + "?",
                row.str("Passenger_Name") + "'s ticket on train " + row.str("Train_ID") + " will be cancelled and its "
                        + "place released for sale. The payment record is removed. This cannot be undone.",
                "Cancel ticket", true);
        if (!ok) {
            return;
        }
        Async.action(this, () -> BookingService.cancelTicket(id), () -> {
            Toast.success(this, "Ticket " + id + " was cancelled");
            load();
        }, problem -> Dialogs.error(this, problem));
    }
}
