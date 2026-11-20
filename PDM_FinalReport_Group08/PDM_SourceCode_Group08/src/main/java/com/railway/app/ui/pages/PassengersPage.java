package com.railway.app.ui.pages;

import com.railway.app.data.Row;
import com.railway.app.service.BookingService;
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
import com.railway.app.ui.components.Toast;
import com.railway.app.ui.components.Ui;
import com.railway.app.ui.dialogs.PassengerDialog;
import com.railway.app.ui.theme.AppTheme;
import com.railway.app.ui.theme.Icons;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.util.List;

/**
 * Passengers, their phone numbers and their ticket history.
 */
public class PassengersPage extends Page {

    private final SearchField search = new SearchField("Search by name, phone or passenger number", t -> load());
    private final DataTable table;
    private final JLabel count = Ui.muted(" ");
    private final CardLayout detailCards = new CardLayout();
    private final JPanel detail = new JPanel(detailCards);

    private final JLabel avatar = new JLabel("", SwingConstants.CENTER) {
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(AppTheme.ORANGE_SOFT);
            g.fillOval(0, 0, getWidth(), getHeight());
            g.dispose();
            super.paintComponent(graphics);
        }
    };
    private final JLabel name = Ui.strong(" ", 18);
    private final JLabel idLine = Ui.muted(" ");
    private final JPanel phones = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 6));
    private final JLabel ticketCount = Ui.strong("0", 20);
    private final JLabel spent = Ui.strong("0", 20);
    private final DataTable history;
    private Row current;

    public PassengersPage(Navigator navigator) {
        super("Passengers", "People who travel with us, how to reach them, and the tickets they hold.");

        table = new DataTable(List.of(
                Column.of("No.", "Passenger_ID", Kind.STRONG, 70),
                Column.of("Name", "Passenger_Name", Kind.TEXT, 200),
                Column.of("Phone numbers", "Phones", Kind.MUTED, 220),
                Column.of("Tickets", "Ticket_Count", Kind.NUMBER, 70),
                Column.of("Total spent", "Total_Spent", Kind.MONEY, 130)));
        table.setEmptyMessage("No passengers match your search");
        table.onSelect(this::show);
        table.onDoubleClick(r -> edit());

        history = new DataTable(List.of(
                Column.of("Ticket", "Booking_ID", Kind.STRONG, 100),
                Column.of("Train", "Train_ID", Kind.TEXT, 64),
                Column.custom("Date", 105, r -> Formats.shortDate(r.date("Travel_Date"))),
                Column.of("Status", "Status", Kind.BADGE, 110)));
        history.setEmptyMessage("No tickets yet");
        history.onDoubleClick(r -> navigator.openTickets(r.str("Booking_ID")));

        JButton add = Ui.primary("New passenger", "user-plus");
        add.addActionListener(e -> {
            if (new PassengerDialog(this, null).open()) {
                Toast.success(this, "Passenger added");
                load();
            }
        });
        JPanel toolbar = Ui.transparent(new BorderLayout());
        toolbar.add(Ui.flow(0, search), BorderLayout.WEST);
        toolbar.add(Ui.flowRight(0, add), BorderLayout.EAST);

        Card listCard = new Card(new BorderLayout(), 0);
        listCard.add(table, BorderLayout.CENTER);
        JPanel footer = Ui.transparent(new BorderLayout());
        footer.setBorder(new EmptyBorder(10, 16, 10, 16));
        footer.add(count, BorderLayout.WEST);
        listCard.add(footer, BorderLayout.SOUTH);

        detail.setOpaque(false);
        detail.setPreferredSize(new Dimension(390, 0));
        detail.add(EmptyState.create("user", "Choose a passenger", "Their details and tickets will appear here.", null),
                "empty");
        detail.add(buildDetail(), "detail");

        JPanel body = Ui.transparent(new BorderLayout(16, 0));
        body.add(listCard, BorderLayout.CENTER);
        body.add(detail, BorderLayout.EAST);

        add(toolbar, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
    }

    private JComponent buildDetail() {
        avatar.setFont(AppTheme.semibold(18));
        avatar.setForeground(AppTheme.ORANGE_DARK);
        avatar.setPreferredSize(new Dimension(56, 56));
        JPanel head = Ui.transparent(new BorderLayout(14, 0));
        head.add(avatar, BorderLayout.WEST);
        head.add(Ui.stack(name, Ui.vgap(3), idLine), BorderLayout.CENTER);

        phones.setOpaque(false);

        JPanel stats = new JPanel(new GridLayout(1, 2, 12, 0));
        stats.setOpaque(false);
        stats.add(statBox("Tickets", ticketCount));
        stats.add(statBox("Total spent", spent));

        JButton edit = Ui.secondary("Edit", "edit");
        edit.addActionListener(e -> edit());
        JButton remove = Ui.danger("Remove", "trash");
        remove.addActionListener(e -> remove());

        Card top = new Card(new BorderLayout(0, 14), 20);
        top.add(head, BorderLayout.NORTH);
        top.add(Ui.stack(Ui.caption("Phone numbers"), phones, Ui.vgap(10), stats), BorderLayout.CENTER);
        top.add(Ui.flowRight(10, edit, remove), BorderLayout.SOUTH);

        Card historyCard = new Card(new BorderLayout(0, 10), 0);
        JLabel historyTitle = Ui.heading("Ticket history");
        historyTitle.setBorder(new EmptyBorder(14, 16, 0, 16));
        historyCard.add(historyTitle, BorderLayout.NORTH);
        historyCard.add(history, BorderLayout.CENTER);

        JPanel panel = Ui.transparent(new BorderLayout(0, 14));
        panel.add(top, BorderLayout.NORTH);
        panel.add(historyCard, BorderLayout.CENTER);
        return panel;
    }

    private static JComponent statBox(String caption, JLabel value) {
        Card box = new Card(new BorderLayout(), 12).fill(AppTheme.SURFACE_ALT);
        box.add(Ui.stack(Ui.caption(caption), Ui.vgap(4), value), BorderLayout.CENTER);
        return box;
    }

    @Override
    public void refresh() {
        load();
    }

    private void load() {
        String text = search.getText();
        String keep = current == null ? null : current.str("Passenger_ID");
        Async.run(this, () -> BookingService.passengers(text), rows -> {
            table.setRows(rows);
            count.setText(rows.size() + (rows.size() == 1 ? " passenger" : " passengers"));
            if (keep != null) {
                table.select("Passenger_ID", keep);
            }
            if (table.selected() == null) {
                table.selectFirst();
            }
            if (rows.isEmpty()) {
                show(null);
            }
        });
    }

    private void show(Row row) {
        current = row;
        if (row == null) {
            detailCards.show(detail, "empty");
            return;
        }
        detailCards.show(detail, "detail");
        String fullName = row.str("Passenger_Name", "");
        name.setText(fullName);
        idLine.setText("Passenger " + row.str("Passenger_ID"));
        avatar.setText(initials(fullName));
        phones.removeAll();
        String list = row.str("Phones");
        if (list == null) {
            phones.add(Ui.muted("No phone number on file"));
        } else {
            for (String phone : list.split(",\\s*")) {
                Badge chip = new Badge(phone, AppTheme.TEXT, AppTheme.GRAY_SOFT);
                chip.setIcon(Icons.get("phone", 13, AppTheme.TEXT_SECONDARY));
                chip.setFont(AppTheme.font(java.awt.Font.PLAIN, 12.5f));
                phones.add(chip);
                phones.add(Ui.hgap(6));
            }
        }
        phones.revalidate();
        phones.repaint();
        ticketCount.setText(Formats.count(row.integer("Ticket_Count")));
        spent.setText(Formats.money(row.decimal("Total_Spent")));
        String id = row.str("Passenger_ID");
        Async.run(this, () -> BookingService.tickets(null, null, null, id), history::setRows);
    }

    private static String initials(String fullName) {
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return "?";
        }
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase();
    }

    private void edit() {
        if (current == null) {
            return;
        }
        if (new PassengerDialog(this, current).open()) {
            Toast.success(this, "Passenger saved");
            load();
        }
    }

    private void remove() {
        if (current == null) {
            return;
        }
        String id = current.str("Passenger_ID");
        if (!Dialogs.confirm(this, "Remove " + current.str("Passenger_Name") + "?",
                "The passenger and their phone numbers will be removed. This cannot be undone.",
                "Remove passenger", true)) {
            return;
        }
        Async.action(this, () -> BookingService.deletePassenger(id), () -> {
            Toast.success(this, "Passenger removed");
            current = null;
            load();
        }, problem -> Dialogs.error(this, problem));
    }
}
