package com.railway.app.ui.pages;

import com.railway.app.data.Row;
import com.railway.app.service.NetworkService;
import com.railway.app.ui.Navigator;
import com.railway.app.ui.Page;
import com.railway.app.ui.components.Async;
import com.railway.app.ui.components.Card;
import com.railway.app.ui.components.DataTable;
import com.railway.app.ui.components.DataTable.Column;
import com.railway.app.ui.components.DataTable.Kind;
import com.railway.app.ui.components.Dialogs;
import com.railway.app.ui.components.RowCombo;
import com.railway.app.ui.components.SearchField;
import com.railway.app.ui.components.Toast;
import com.railway.app.ui.components.Ui;
import com.railway.app.ui.dialogs.StationDialog;
import com.railway.app.ui.dialogs.TrackDialog;
import com.railway.app.ui.theme.AppTheme;
import com.railway.app.ui.theme.Icons;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.util.List;

/**
 * Stations and the track sections that join them.
 */
public class NetworkPage extends Page {

    private final SearchField stationSearch = new SearchField("Search stations or cities", t -> loadStations());
    private final RowCombo cityFilter = new RowCombo("City", r -> r.str("City") + "  (" + r.str("Station_Count") + ")",
            "All cities");
    private final DataTable stations;
    private final JLabel stationCount = Ui.muted(" ");

    private final RowCombo trackStation = new RowCombo("Station_Code", r -> r.str("Station_Name"), "All stations");
    private final DataTable tracks;
    private final JLabel trackCount = Ui.muted(" ");
    private boolean loadingFilters;

    public NetworkPage(Navigator navigator) {
        super("Stations & Tracks", "The places trains stop at, and the railway tracks between them.");

        stations = new DataTable(List.of(
                Column.of("Code", "Station_Code", Kind.STRONG, 80),
                Column.of("Station", "Station_Name", Kind.TEXT, 220),
                Column.of("City / province", "City", Kind.TEXT, 180),
                Column.of("Trains stopping", "Train_Count", Kind.NUMBER, 120),
                Column.of("Tracks", "Track_Count", Kind.NUMBER, 80)));
        stations.setEmptyMessage("No stations match your search");
        stations.onDoubleClick(r -> editStation());

        tracks = new DataTable(List.of(
                Column.of("Track", "Track_ID", Kind.STRONG, 80),
                Column.of("From", "From_Name", Kind.TEXT, 220),
                Column.of("To", "To_Name", Kind.TEXT, 220),
                Column.of("Length", "Length_km", Kind.KM, 100)));
        tracks.setEmptyMessage("No tracks for this station");
        tracks.onDoubleClick(r -> editTrack());

        cityFilter.addActionListener(e -> {
            if (!loadingFilters) {
                loadStations();
            }
        });
        trackStation.addActionListener(e -> {
            if (!loadingFilters) {
                loadTracks();
            }
        });

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(AppTheme.semibold(13));
        tabs.addTab("Stations", Icons.get("pin", 16), stationsTab());
        tabs.addTab("Tracks", Icons.get("route", 16), tracksTab());
        add(tabs, BorderLayout.CENTER);
    }

    private JPanel stationsTab() {
        JButton add = Ui.primary("New station", "plus");
        add.addActionListener(e -> {
            if (new StationDialog(this, null).open()) {
                Toast.success(this, "Station added");
                refresh();
            }
        });
        JButton edit = Ui.secondary("Edit", "edit");
        edit.addActionListener(e -> editStation());
        JButton remove = Ui.danger("Remove", "trash");
        remove.addActionListener(e -> removeStation());

        JPanel toolbar = Ui.transparent(new BorderLayout());
        toolbar.add(Ui.flow(12, stationSearch, cityFilter), BorderLayout.WEST);
        toolbar.add(Ui.flowRight(10, edit, remove, add), BorderLayout.EAST);
        return tab(toolbar, stations, stationCount);
    }

    private JPanel tracksTab() {
        JButton add = Ui.primary("New track", "plus");
        add.addActionListener(e -> {
            if (new TrackDialog(this, null).open()) {
                Toast.success(this, "Track added");
                refresh();
            }
        });
        JButton edit = Ui.secondary("Change length", "edit");
        edit.addActionListener(e -> editTrack());
        JButton remove = Ui.danger("Remove", "trash");
        remove.addActionListener(e -> removeTrack());

        JPanel toolbar = Ui.transparent(new BorderLayout());
        toolbar.add(Ui.flow(12, Ui.muted("Show tracks at"), trackStation), BorderLayout.WEST);
        toolbar.add(Ui.flowRight(10, edit, remove, add), BorderLayout.EAST);
        return tab(toolbar, tracks, trackCount);
    }

    private static JPanel tab(JPanel toolbar, DataTable table, JLabel footerText) {
        Card card = new Card(new BorderLayout(), 0);
        card.add(table, BorderLayout.CENTER);
        JPanel footer = Ui.transparent(new BorderLayout());
        footer.setBorder(new EmptyBorder(10, 16, 10, 16));
        footer.add(footerText, BorderLayout.WEST);
        card.add(footer, BorderLayout.SOUTH);
        JPanel panel = Ui.transparent(new BorderLayout(0, 12));
        panel.setBorder(new EmptyBorder(14, 0, 0, 0));
        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(card, BorderLayout.CENTER);
        return panel;
    }

    @Override
    public void refresh() {
        Async.run(this, NetworkService::cities, rows -> {
            loadingFilters = true;
            cityFilter.setRows(rows);
            loadingFilters = false;
            loadStations();
        });
        Async.run(this, NetworkService::allStations, rows -> {
            loadingFilters = true;
            trackStation.setRows(rows);
            loadingFilters = false;
            loadTracks();
        });
    }

    private void loadStations() {
        String text = stationSearch.getText();
        String city = cityFilter.selectedKey();
        Async.run(this, () -> NetworkService.stations(text, city), rows -> {
            stations.setRows(rows, "Station_Code");
            stationCount.setText(rows.size() + (rows.size() == 1 ? " station" : " stations"));
        });
    }

    private void loadTracks() {
        String station = trackStation.selectedKey();
        Async.run(this, () -> NetworkService.tracks(station), rows -> {
            tracks.setRows(rows, "Track_ID");
            trackCount.setText(rows.size() + (rows.size() == 1 ? " track section" : " track sections")
                    + " · trains may use every track in both directions");
        });
    }

    private void editStation() {
        Row row = stations.selected();
        if (row == null) {
            Dialogs.info(this, "Choose a station", "Click a station in the list first.");
            return;
        }
        if (new StationDialog(this, row).open()) {
            Toast.success(this, "Station saved");
            refresh();
        }
    }

    private void removeStation() {
        Row row = stations.selected();
        if (row == null) {
            Dialogs.info(this, "Choose a station", "Click a station in the list first.");
            return;
        }
        String code = row.str("Station_Code");
        if (!Dialogs.confirm(this, "Remove " + row.str("Station_Name") + "?",
                "The station will be removed from the network. This cannot be undone.", "Remove station", true)) {
            return;
        }
        Async.action(this, () -> NetworkService.deleteStation(code), () -> {
            Toast.success(this, "Station removed");
            refresh();
        }, problem -> Dialogs.error(this, problem));
    }

    private void editTrack() {
        Row row = tracks.selected();
        if (row == null) {
            Dialogs.info(this, "Choose a track", "Click a track in the list first.");
            return;
        }
        if (new TrackDialog(this, row).open()) {
            Toast.success(this, "Track saved");
            refresh();
        }
    }

    private void removeTrack() {
        Row row = tracks.selected();
        if (row == null) {
            Dialogs.info(this, "Choose a track", "Click a track in the list first.");
            return;
        }
        String id = row.str("Track_ID");
        if (!Dialogs.confirm(this, "Remove track " + id + "?",
                row.str("From_Name") + " and " + row.str("To_Name") + " will no longer be joined by this track.",
                "Remove track", true)) {
            return;
        }
        Async.action(this, () -> NetworkService.deleteTrack(id), () -> {
            Toast.success(this, "Track removed");
            refresh();
        }, problem -> Dialogs.error(this, problem));
    }
}
