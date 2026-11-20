package com.railway.app.ui;

import com.railway.app.config.AppConfig;
import com.railway.app.data.Database;
import com.railway.app.ui.components.Async;
import com.railway.app.ui.components.EmptyState;
import com.railway.app.ui.components.Ui;
import com.railway.app.ui.pages.BookingPage;
import com.railway.app.ui.pages.HomePage;
import com.railway.app.ui.pages.NetworkPage;
import com.railway.app.ui.pages.PassengersPage;
import com.railway.app.ui.pages.ReportsPage;
import com.railway.app.ui.pages.TicketsPage;
import com.railway.app.ui.pages.TrainsPage;
import com.railway.app.ui.theme.AppTheme;
import com.railway.app.ui.theme.Icons;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * The application window: side menu, page header and the pages.
 */
public class MainFrame extends JFrame implements Navigator {

    private static final Logger LOG = Logger.getLogger(MainFrame.class.getName());

    private final CardLayout bodyCards = new CardLayout();
    private final JPanel body = new JPanel(bodyCards);
    private final CardLayout pageCards = new CardLayout();
    private final JPanel pagesPanel = new JPanel(pageCards);
    private final Map<String, Page> pages = new LinkedHashMap<>();
    private final Sidebar sidebar = new Sidebar(this::go);
    private final JLabel pageTitle = Ui.pageTitle("");
    private final JLabel pageSubtitle = Ui.muted("");
    private final JButton quickBook = Ui.primary("Book a ticket", "ticket");
    private final JPanel offlinePanel = new JPanel(new BorderLayout());
    private String currentKey;

    private BookingPage bookingPage;
    private TicketsPage ticketsPage;

    public MainFrame() {
        super("Railway Network & Timetable");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setIconImages(appIcons());
        setMinimumSize(new Dimension(1180, 740));
        setSize(new Dimension(1400, 880));
        setLocationRelativeTo(null);
        getRootPane().putClientProperty("JRootPane.titleBarBackground", AppTheme.NAVY);
        getRootPane().putClientProperty("JRootPane.titleBarForeground", Color.WHITE);

        buildPages();

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(AppTheme.BACKGROUND);
        header.setBorder(new EmptyBorder(24, 28, 16, 28));
        header.add(Ui.stack(pageTitle, Ui.vgap(4), pageSubtitle), BorderLayout.WEST);
        quickBook.addActionListener(e -> go("book"));
        header.add(Ui.flowRight(0, quickBook), BorderLayout.EAST);

        pagesPanel.setBackground(AppTheme.BACKGROUND);
        JPanel appPanel = new JPanel(new BorderLayout());
        appPanel.setBackground(AppTheme.BACKGROUND);
        appPanel.add(header, BorderLayout.NORTH);
        appPanel.add(pagesPanel, BorderLayout.CENTER);

        offlinePanel.setBackground(AppTheme.BACKGROUND);
        JPanel connecting = new JPanel(new BorderLayout());
        connecting.setBackground(AppTheme.BACKGROUND);
        connecting.add(EmptyState.create("refresh", "Connecting to the booking system…",
                "This only takes a moment.", null), BorderLayout.CENTER);

        body.add(connecting, "connecting");
        body.add(appPanel, "app");
        body.add(offlinePanel, "offline");

        JPanel root = new JPanel(new BorderLayout());
        root.add(sidebar, BorderLayout.WEST);
        root.add(body, BorderLayout.CENTER);
        setContentPane(root);
    }

    private void buildPages() {
        bookingPage = new BookingPage(this);
        ticketsPage = new TicketsPage(this);

        sidebar.addSection("Overview");
        register("home", "Home", "home", new HomePage(this));
        sidebar.addSection("Ticket office");
        register("book", "Book Tickets", "ticket", bookingPage);
        register("tickets", "Tickets", "receipt", ticketsPage);
        register("passengers", "Passengers", "users", new PassengersPage(this));
        sidebar.addSection("Operations");
        register("trains", "Trains & Timetables", "train", new TrainsPage(this));
        register("network", "Stations & Tracks", "pin", new NetworkPage(this));
        sidebar.addSection("Insights");
        register("reports", "Reports", "chart", new ReportsPage(this));
    }

    private void register(String key, String label, String icon, Page page) {
        pages.put(key, page);
        pagesPanel.add(page, key);
        sidebar.addItem(key, label, icon);
    }

    /** Checks the connection, then opens the home page. */
    public void start() {
        bodyCards.show(body, "connecting");
        Async.action(this, Database::ping, () -> {
            LOG.info("Connected to " + AppConfig.get().describeForLog());
            bodyCards.show(body, "app");
            go(currentKey == null ? "home" : currentKey);
        }, problem -> showOffline(problem.title(), problem.getMessage()));
    }

    private void showOffline(String title, String message) {
        JButton retry = Ui.primary("Try again", "refresh");
        retry.addActionListener(e -> {
            AppConfig.reload();
            start();
        });
        offlinePanel.removeAll();
        offlinePanel.add(EmptyState.create("unplug", title, "<html><div style='text-align:center;width:420px'>"
                + message + "</div></html>", retry), BorderLayout.CENTER);
        offlinePanel.revalidate();
        offlinePanel.repaint();
        bodyCards.show(body, "offline");
        sidebar.select(null);
    }

    @Override
    public void go(String key) {
        Page page = pages.get(key);
        if (page == null) {
            return;
        }
        currentKey = key;
        pageTitle.setText(page.title());
        pageSubtitle.setText(page.subtitle());
        quickBook.setVisible(!"book".equals(key));
        sidebar.select(key);
        pageCards.show(pagesPanel, key);
        page.refresh();
    }

    @Override
    public void openTickets(String search) {
        ticketsPage.presetSearch(search);
        go("tickets");
    }

    @Override
    public void bookOnTrain(String trainId) {
        bookingPage.presetTrain(trainId);
        go("book");
    }

    private static List<Image> appIcons() {
        List<Image> images = new ArrayList<>();
        for (int size : new int[]{16, 24, 32, 48, 64, 128}) {
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(AppTheme.ORANGE);
            g.fillRoundRect(0, 0, size, size, size / 3, size / 3);
            int iconSize = (int) Math.round(size * 0.72);
            int offset = (size - iconSize) / 2;
            Icons.get("logo", iconSize, Color.WHITE).paintIcon(null, g, offset, offset);
            g.dispose();
            images.add(image);
        }
        return images;
    }
}
