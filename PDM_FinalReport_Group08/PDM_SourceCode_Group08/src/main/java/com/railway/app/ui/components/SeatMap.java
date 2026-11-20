package com.railway.app.ui.components;

import com.railway.app.ui.theme.AppTheme;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
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
import java.util.function.Consumer;

/**
 * Drawing of one coach: seats in rows with an aisle, or berths grouped in
 * compartments. Free places can be clicked; sold places are greyed out.
 */
public class SeatMap extends JComponent {

    public record Place(String id, boolean sold) {
    }

    private static final int PAD = 28;
    private static final Color FREE_BORDER = new Color(0x18864B);
    private static final Color FREE_HOVER = new Color(0xE5F5EC);
    private static final Color SOLD_FILL = new Color(0xE6EAF1);

    private List<Place> places = new ArrayList<>();
    private final List<Rectangle> boxes = new ArrayList<>();
    private final List<Rectangle> compartments = new ArrayList<>();
    private String title = "";
    private boolean berths;
    private int perCompartment = 4;
    private String selectedId;
    private int hoverIndex = -1;
    private Consumer<String> onSelect;
    private Rectangle body = new Rectangle();

    public SeatMap() {
        setToolTipText("");
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int index = indexAt(e.getX(), e.getY());
                if (index != hoverIndex) {
                    hoverIndex = index;
                    setCursor(index >= 0 && !places.get(index).sold()
                            ? java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
                            : java.awt.Cursor.getDefaultCursor());
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverIndex = -1;
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                int index = indexAt(e.getX(), e.getY());
                if (index >= 0 && !places.get(index).sold()) {
                    selectedId = places.get(index).id();
                    repaint();
                    if (onSelect != null) {
                        onSelect.accept(selectedId);
                    }
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    public void onSelect(Consumer<String> listener) {
        this.onSelect = listener;
    }

    /**
     * @param coachClass e.g. "Sleeper 4-berth"
     * @param berthCoach true when the coach has berths instead of seats
     */
    public void setCoach(String title, String coachClass, boolean berthCoach, List<Place> places, String selectedId) {
        this.title = title;
        this.berths = berthCoach;
        this.places = new ArrayList<>(places);
        this.selectedId = selectedId;
        this.perCompartment = compartmentSize(coachClass);
        layoutPlaces();
        revalidate();
        repaint();
    }

    public String selectedId() {
        return selectedId;
    }

    private static int compartmentSize(String coachClass) {
        if (coachClass == null) {
            return 4;
        }
        if (coachClass.contains("6")) {
            return 6;
        }
        if (coachClass.contains("2-berth") || coachClass.startsWith("VIP")) {
            return 2;
        }
        return 4;
    }

    private void layoutPlaces() {
        boxes.clear();
        compartments.clear();
        int top = PAD + 34;
        int left = PAD + 34;
        int right;
        int bottom;
        if (!berths) {
            int w = 46;
            int h = 40;
            int gap = 8;
            int aisle = 26;
            int columns = Math.max(1, (places.size() + 3) / 4);
            for (int i = 0; i < places.size(); i++) {
                int column = i / 4;
                int position = i % 4;
                int x = left + column * (w + gap);
                int y = top + position * (h + gap) + (position >= 2 ? aisle : 0);
                boxes.add(new Rectangle(x, y, w, h));
            }
            right = left + columns * (w + gap) - gap;
            bottom = top + 4 * (h + gap) - gap + aisle;
        } else {
            int w = 58;
            int h = 30;
            int gap = 6;
            int levels = Math.max(1, perCompartment / 2);
            int compartmentWidth = 2 * w + gap + 20;
            int compartmentHeight = levels * (h + gap) - gap + 20;
            int count = Math.max(1, (places.size() + perCompartment - 1) / perCompartment);
            for (int c = 0; c < count; c++) {
                int cx = left + c * (compartmentWidth + 12);
                compartments.add(new Rectangle(cx, top, compartmentWidth, compartmentHeight));
            }
            for (int i = 0; i < places.size(); i++) {
                int c = i / perCompartment;
                int inside = i % perCompartment;
                int side = inside % 2;
                int level = inside / 2;
                Rectangle comp = compartments.get(c);
                int x = comp.x + 10 + side * (w + gap);
                int y = comp.y + 10 + level * (h + gap);
                boxes.add(new Rectangle(x, y, w, h));
            }
            right = left + count * (compartmentWidth + 12) - 12;
            bottom = top + compartmentHeight + 30;
        }
        body = new Rectangle(PAD, PAD + 20, Math.max(260, right - PAD + 30), bottom - PAD - 20 + 18);
        setPreferredSize(new Dimension(body.x + body.width + PAD + 10, body.y + body.height + PAD + 30));
    }

    private int indexAt(int x, int y) {
        for (int i = 0; i < boxes.size(); i++) {
            if (boxes.get(i).contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        int index = indexAt(event.getX(), event.getY());
        if (index < 0) {
            return null;
        }
        Place place = places.get(index);
        return (berths ? "Berth " : "Seat ") + place.id() + (place.sold() ? " · already sold" : " · free");
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setFont(AppTheme.semibold(13));
        g.setColor(AppTheme.TEXT);
        g.drawString(title, PAD, PAD + 4);

        if (places.isEmpty()) {
            g.setFont(AppTheme.font(Font.PLAIN, 13));
            g.setColor(AppTheme.TEXT_MUTED);
            g.drawString("This coach has no seats yet.", PAD, PAD + 40);
            g.dispose();
            return;
        }

        // Coach body with a rounded front end
        g.setColor(AppTheme.SURFACE_ALT);
        g.fillRoundRect(body.x, body.y, body.width, body.height, 34, 34);
        g.setColor(AppTheme.BORDER);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(body.x, body.y, body.width, body.height, 34, 34);
        // Doors at both ends
        g.setColor(AppTheme.BORDER);
        g.fillRoundRect(body.x + 8, body.y + body.height / 2 - 18, 6, 36, 6, 6);
        g.fillRoundRect(body.x + body.width - 14, body.y + body.height / 2 - 18, 6, 36, 6, 6);

        if (!berths) {
            g.setFont(AppTheme.font(Font.PLAIN, 10.5f));
            g.setColor(AppTheme.TEXT_MUTED);
            int aisleY = boxes.isEmpty() ? 0 : boxes.get(0).y + 2 * (40 + 8) + 4;
            g.drawString("AISLE", body.x + 34, aisleY + 12);
        } else {
            g.setStroke(new BasicStroke(1.2f));
            for (Rectangle comp : compartments) {
                g.setColor(AppTheme.SURFACE);
                g.fillRoundRect(comp.x, comp.y, comp.width, comp.height, 12, 12);
                g.setColor(AppTheme.BORDER);
                g.drawRoundRect(comp.x, comp.y, comp.width, comp.height, 12, 12);
            }
        }

        Font seatFont = AppTheme.semibold(11.5f);
        g.setFont(seatFont);
        FontMetrics metrics = g.getFontMetrics();
        for (int i = 0; i < places.size(); i++) {
            Place place = places.get(i);
            Rectangle r = boxes.get(i);
            boolean selected = place.id().equals(selectedId);
            boolean hover = i == hoverIndex && !place.sold();
            Color fill;
            Color stroke;
            Color text;
            if (place.sold()) {
                fill = SOLD_FILL;
                stroke = SOLD_FILL;
                text = AppTheme.TEXT_MUTED;
            } else if (selected) {
                fill = AppTheme.ORANGE;
                stroke = AppTheme.ORANGE_DARK;
                text = Color.WHITE;
            } else {
                fill = hover ? FREE_HOVER : AppTheme.SURFACE;
                stroke = FREE_BORDER;
                text = AppTheme.TEXT;
            }
            g.setColor(fill);
            g.fillRoundRect(r.x, r.y, r.width, r.height, 10, 10);
            g.setColor(stroke);
            g.setStroke(new BasicStroke(selected ? 2f : 1.4f));
            g.drawRoundRect(r.x, r.y, r.width, r.height, 10, 10);
            if (!berths) {
                // headrest
                g.setColor(place.sold() ? new Color(0xD5DBE5) : (selected ? AppTheme.ORANGE_DARK : new Color(0xBFE3CD)));
                g.fillRoundRect(r.x + 6, r.y + 4, r.width - 12, 5, 4, 4);
            }
            g.setColor(text);
            g.drawString(place.id(), r.x + (r.width - metrics.stringWidth(place.id())) / 2,
                    r.y + (r.height + metrics.getAscent() - metrics.getDescent()) / 2 + (berths ? 0 : 3));
            if (place.sold()) {
                g.setColor(new Color(0xB8C0CE));
                g.setStroke(new BasicStroke(1.2f));
                g.drawLine(r.x + 6, r.y + r.height - 6, r.x + r.width - 6, r.y + 6);
            }
        }

        // Legend
        int ly = body.y + body.height + 26;
        int lx = body.x;
        lx = legend(g, lx, ly, AppTheme.SURFACE, FREE_BORDER, "Free");
        lx = legend(g, lx, ly, AppTheme.ORANGE, AppTheme.ORANGE_DARK, "Your choice");
        legend(g, lx, ly, SOLD_FILL, SOLD_FILL, "Sold");
        g.dispose();
    }

    private int legend(Graphics2D g, int x, int y, Color fill, Color stroke, String label) {
        g.setColor(fill);
        g.fillRoundRect(x, y - 11, 16, 14, 5, 5);
        g.setColor(stroke);
        g.setStroke(new BasicStroke(1.4f));
        g.drawRoundRect(x, y - 11, 16, 14, 5, 5);
        g.setFont(AppTheme.font(Font.PLAIN, 12));
        g.setColor(AppTheme.TEXT_SECONDARY);
        g.drawString(label, x + 24, y);
        return x + 24 + g.getFontMetrics().stringWidth(label) + 26;
    }
}
