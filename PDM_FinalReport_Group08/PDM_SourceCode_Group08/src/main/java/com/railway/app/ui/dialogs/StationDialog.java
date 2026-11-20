package com.railway.app.ui.dialogs;

import com.railway.app.data.Row;
import com.railway.app.service.NetworkService;
import com.railway.app.ui.components.Async;
import com.railway.app.ui.components.FormDialog;
import com.railway.app.ui.components.Inputs;

import javax.swing.JTextField;
import java.awt.Component;

/**
 * Adds a station or changes its name and city.
 */
public class StationDialog extends FormDialog {

    private final Row existing;
    private final JTextField code = Inputs.text("e.g. QNH");
    private final JTextField name = Inputs.text("e.g. Quy Nhon Station");
    private final JTextField city = Inputs.text("e.g. Binh Dinh");

    public StationDialog(Component owner, Row existing) {
        super(owner, existing == null ? "New station" : "Edit " + existing.str("Station_Name"),
                existing == null ? "Stations are identified by a short code." : "Change the name or the city.",
                existing == null ? "Add station" : "Save");
        this.existing = existing;
        addField("Station code", code, existing == null ? "2 to 10 letters or digits" : null);
        addField("Name", name, null);
        addField("City / province", city, null);
        if (existing != null) {
            code.setText(existing.str("Station_Code"));
            code.setEnabled(false);
            name.setText(existing.str("Station_Name"));
            city.setText(existing.str("City"));
        }
    }

    @Override
    protected Async.Action buildSave() {
        String stationCode = code.getText();
        String stationName = name.getText();
        String stationCity = city.getText();
        if (existing == null) {
            return () -> NetworkService.createStation(stationCode, stationName, stationCity);
        }
        String key = existing.str("Station_Code");
        return () -> NetworkService.updateStation(key, stationName, stationCity);
    }
}
