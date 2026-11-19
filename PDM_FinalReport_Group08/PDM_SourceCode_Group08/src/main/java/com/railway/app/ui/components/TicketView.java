package com.railway.app.ui.components;

import com.railway.app.data.Row;
import com.railway.app.ui.theme.AppTheme;
import com.railway.app.ui.theme.Icons;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A ticket drawn like a boarding pass. It can also be printed.
 */
public class TicketView extends JPanel implements Printable {

    private static final Logger LOG = Logger.getLogger(TicketView.class.getName());
    private static final int BAND = 74;
    private static final int STUB = 96;

    private final JLabel ticketNo = label("", AppTheme.semibold(20), Color.WHITE);
    private final Badge status = new Badge("", AppTheme.BLUE, AppTheme.BLUE_SOFT);
    private final JLabel trainLine = label("", AppTheme.font(Font.PLAIN, 12.5f), new Color(0xB7C2DC));
    private final JLabel fromName = label("", AppTheme.semibold(15), AppTheme.TEXT);
    private final JLabel fromTime = label("", AppTheme.semibold(30), AppTheme.TEXT);
    private final JLabel fromDate = label("", AppTheme.font(Font.PLAIN, 12.5f), AppTheme.TEXT_SECONDARY);
    private final JLabel toName = label("", AppTheme.semibold(15), AppTheme.TEXT);
    private final JLabel toTime = label("", AppTheme.semibold(30), AppTheme.TEXT);
    private final JLabel toDate = label("", AppTheme.font(Font.PLAIN, 12.5f), AppTheme.TEXT_SECONDARY);
    private final JLabel middle = label("", AppTheme.font(Font.PLAIN, 12), AppTheme.TEXT_MUTED);
    private final JLabel passenger = value();
    private final JLabel travelDate = value();
    private final JLabel coach = value();
    private final JLabel seat = value();
    private final JLabel seatCaption = caption("SEAT");
    private final JLabel fare = value();
    private final JLabel payment = value();

    public TicketView() {
        super(new BorderLayout());
        setOpaque(false);
        setPreferredSize(new Dimension(700, 330));

        // Navy band
        JPanel band = Ui.transparent(new BorderLayout());
        band.setPreferredSize(new Dimension(0, BAND));
        band.setBorder(new EmptyBorder(14, 26, 12, 24));
        JLabel caption = label("TRAIN TICKET", AppTheme.semibold(10.5f), new Color(0xFFB57A));
        band.add(Ui.stack(caption, Ui.vgap(2), ticketNo), BorderLayout.WEST);
        JPanel bandRight = Ui.stack(Ui.flowRight(0, status), Ui.vgap(6), trainLine);
        trainLine.setHorizontalAlignment(SwingConstants.RIGHT);
        trainLine.setAlignmentX(RIGHT_ALIGNMENT);
        band.add(bandRight, BorderLayout.EAST);
        add(band, BorderLayout.NORTH);

        // Journey
        JPanel journey = Ui.transparent(new BorderLayout(18, 0));
        journey.setBorder(new EmptyBorder(20, 28, 10, 28));
        journey.add(Ui.stack(fromName, Ui.vgap(2), fromTime, fromDate), BorderLayout.WEST);
        JLabel arrow = new JLabel(Icons.get("arrow-right", 22, AppTheme.ORANGE), SwingConstants.CENTER);
        middle.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel center = Ui.transparent(new BorderLayout(0, 6));
        center.setBorder(new EmptyBorder(26, 0, 0, 0));
        center.add(arrow, BorderLayout.NORTH);
        center.add(middle, BorderLayout.CENTER);
        journey.add(center, BorderLayout.CENTER);
        JPanel toStack = Ui.stack(toName, Ui.vgap(2), toTime, toDate);
        for (java.awt.Component c : toStack.getComponents()) {
            if (c instanceof JLabel l) {
                l.setAlignmentX(RIGHT_ALIGNMENT);
                l.setHorizontalAlignment(SwingConstants.RIGHT);
            }
        }
        journey.add(toStack, BorderLayout.EAST);
        add(journey, BorderLayout.CENTER);

        // Stub with details
        JPanel stub = new JPanel(new GridLayout(2, 6, 14, 2));
        stub.setOpaque(false);
        stub.setPreferredSize(new Dimension(0, STUB));
        stub.setBorder(new EmptyBorder(22, 28, 18, 28));
        stub.add(caption("PASSENGER"));
        stub.add(caption("TRAVEL DATE"));
        stub.add(caption("COACH"));
        stub.add(seatCaption);
        stub.add(caption("FARE"));
        stub.add(caption("PAYMENT"));
        stub.add(passenger);
        stub.add(travelDate);
        stub.add(coach);
        stub.add(seat);
        stub.add(fare);
        stub.add(payment);
        add(stub, BorderLayout.SOUTH);
    }

    public void setTicket(Row t) {
        ticketNo.setText("No. " + t.str("Booking_ID"));
        status.applyStatus(t.str("Status"));
        trainLine.setText(t.str("Train_ID") + " · " + t.str("Train_Name") + " · " + t.str("Train_Type"));
        fromName.setText(t.str("Origin_Name", "—"));
        toName.setText(t.str("Destination_Name", "—"));
        fromTime.setText(t.dateTime("Departure_At") == null ? "—" : Formats.time(t.dateTime("Departure_At").toLocalTime()));
        toTime.setText(t.dateTime("Arrival_At") == null ? "—" : Formats.time(t.dateTime("Arrival_At").toLocalTime()));
        fromDate.setText(t.dateTime("Departure_At") == null ? "" : Formats.date(t.dateTime("Departure_At").toLocalDate()));
        toDate.setText(t.dateTime("Arrival_At") == null ? "" : Formats.date(t.dateTime("Arrival_At").toLocalDate()));
        String km = t.integer("Route_Km") == null ? "" : Formats.km(t.integer("Route_Km"));
        String duration = "";
        if (t.dateTime("Departure_At") != null && t.dateTime("Arrival_At") != null) {
            long minutes = java.time.Duration.between(t.dateTime("Departure_At"), t.dateTime("Arrival_At")).toMinutes();
            duration = Formats.duration((int) minutes);
        }
        middle.setText(duration + (km.isEmpty() || duration.isEmpty() ? "" : " · ") + km
                + " · " + t.str("Coach_Class", ""));
        passenger.setText(t.str("Passenger_Name"));
        travelDate.setText(Formats.shortDate(t.date("Travel_Date")));
        coach.setText(t.str("C_ID"));
        seatCaption.setText(t.str("Seat_Kind", "Seat").toUpperCase());
        seat.setText(t.str("S_ID"));
        fare.setText(Formats.money(t.decimal("Fare")));
        payment.setText(t.str("Pay_Method", "No payment record"));
        coach.setToolTipText(coach.getText());
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        int stubTop = h - STUB;

        Area shape = new Area(new RoundRectangle2D.Double(0, 0, w - 1, h - 1, 22, 22));
        shape.subtract(new Area(new Ellipse2D.Double(-12, stubTop - 12, 24, 24)));
        shape.subtract(new Area(new Ellipse2D.Double(w - 13, stubTop - 12, 24, 24)));
        g.setColor(AppTheme.SURFACE);
        g.fill(shape);
        g.setColor(AppTheme.BORDER);
        g.draw(shape);

        // Navy band clipped to the ticket shape
        g.setClip(shape);
        g.setColor(AppTheme.NAVY);
        g.fillRect(0, 0, w, BAND);
        g.setColor(AppTheme.ORANGE);
        g.fillRect(0, BAND - 4, w, 4);
        g.setClip(null);

        // Perforation
        g.setColor(AppTheme.BORDER);
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{5f, 6f}, 0f));
        g.drawLine(20, stubTop, w - 20, stubTop);
        g.dispose();
        super.paintComponent(graphics);
    }

    public void print() {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName("Train ticket " + ticketNo.getText());
        job.setPrintable(this);
        if (job.printDialog()) {
            try {
                job.print();
            } catch (PrinterException e) {
                LOG.log(Level.WARNING, "Ticket printing failed", e);
            }
        }
    }

    @Override
    public int print(Graphics graphics, PageFormat format, int pageIndex) {
        if (pageIndex > 0) {
            return NO_SUCH_PAGE;
        }
        Graphics2D g = (Graphics2D) graphics;
        g.translate(format.getImageableX(), format.getImageableY());
        double scale = Math.min(1.0, format.getImageableWidth() / getWidth());
        g.scale(scale, scale);
        printAll(g);
        return PAGE_EXISTS;
    }

    private static JLabel label(String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    private static JLabel caption(String text) {
        return label(text, AppTheme.semibold(10), AppTheme.TEXT_MUTED);
    }

    private static JLabel value() {
        return label("", AppTheme.semibold(13), AppTheme.TEXT);
    }
}
