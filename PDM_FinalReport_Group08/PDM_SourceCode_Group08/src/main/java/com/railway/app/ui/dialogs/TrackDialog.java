package com.railway.app.ui.dialogs;

import com.railway.app.data.Row;
import com.railway.app.service.NetworkService;
import com.railway.app.ui.components.Async;
import com.railway.app.ui.components.Formats;
import com.railway.app.ui.components.FormDialog;
import com.railway.app.ui.components.Inputs;
import com.railway.app.ui.components.RowCombo;
import com.railway.app.ui.theme.AppTheme;

import javax.swing.JLabel;
import javax.swing.JTextField;
import java.awt.Component;

/**
 * Adds a track between two stations, or changes the length of a track.
 */
public class TrackDialog extends FormDialog {

    private final Row existing;
    private final RowCombo from = new RowCombo("Station_Code", r -> r.str("Station_Name"), null);
    private final RowCombo to = new RowCombo("Station_Code", r -> r.str("Station_Name"), null);
    private final JTextField length = Inputs.text("e.g. 120");

    public TrackDialog(Component owner, Row existing) {
        super(owner, existing == null ? "New track" : "Track " + existing.str("Track_ID"),
                existing == null ? "Tracks can be used by trains in both directions."
                        : existing.str("From_Name") + " – " + existing.str("To_Name"),
                existing == null ? "Add track" : "Save");
        this.existing = existing;
        JLabel unit = new JLabel("km  ");
        unit.setForeground(AppTheme.TEXT_MUTED);
        length.putClientProperty("JTextField.trailingComponent", unit);
        if (existing == null) {
            addField("From station", from, null);
            addField("To station", to, null);
            Async.run(this, NetworkService::allStations, rows -> {
                from.setRows(rows);
                to.setRows(rows);
                if (rows.size() > 1) {
                    to.setSelectedIndex(1);
                }
            });
        } else {
            length.setText(String.valueOf(existing.integer("Length_km")));
        }
        addField("Length", length, "Whole kilometres, more than 0");
    }

    @Override
    protected Async.Action buildSave() {
        Integer km = Formats.parseInteger(length.getText());
        if (existing == null) {
            String fromCode = from.selectedKey();
            String toCode = to.selectedKey();
            return () -> NetworkService.createTrack(fromCode, toCode, km);
        }
        String id = existing.str("Track_ID");
        return () -> NetworkService.updateTrack(id, km);
    }
}
