package com.railway.app.ui.dialogs;

import com.github.lgooddatepicker.components.TimePicker;
import com.railway.app.data.Row;
import com.railway.app.service.NetworkService;
import com.railway.app.service.TrainService;
import com.railway.app.ui.components.Async;
import com.railway.app.ui.components.FormDialog;
import com.railway.app.ui.components.Inputs;
import com.railway.app.ui.components.RowCombo;
import com.railway.app.ui.components.Ui;
import com.railway.app.ui.theme.AppTheme;

import java.awt.Component;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adds a stop to a train's timetable or changes one.
 */
public class StopDialog extends FormDialog {

    private final String trainId;
    private final Row existing;
    private final RowCombo station = new RowCombo("Station_Code",
            r -> r.str("Station_Name") + "  (" + r.str("City") + ")", null);
    private final TimePicker arrival = Inputs.time();
    private final TimePicker departure = Inputs.time();
    private final RowCombo position = new RowCombo("Position", r -> r.str("Label"), null);

    public StopDialog(Component owner, String trainId, Row existing, List<Row> currentStops) {
        super(owner, existing == null ? "Add a stop to " + trainId : "Edit stop " + existing.integer("Stop_No"),
                existing == null ? "Choose the station and the times the train arrives and leaves."
                        : "Change the station or its times.",
                existing == null ? "Add stop" : "Save");
        this.trainId = trainId;
        this.existing = existing;

        addField("Station", station, null);
        addField("Arrives at", arrival, "Leave empty at the first station");
        addField("Leaves at", departure, "Leave empty at the final station");
        if (existing == null) {
            List<Row> positions = new ArrayList<>();
            positions.add(option(null, "At the end of the route"));
            for (Row stop : currentStops) {
                positions.add(option(stop.integer("Stop_No"), "Before stop " + stop.integer("Stop_No") + " · "
                        + stop.str("Station_Name")));
            }
            position.setRows(positions);
            addField("Position", position, null);
        } else {
            arrival.setTime(existing.time("Arrival"));
            departure.setTime(existing.time("Departure"));
        }
        addWide(Ui.label("A time earlier than the stop before it is read as the next day.",
                AppTheme.font(java.awt.Font.PLAIN, 12), AppTheme.TEXT_MUTED));

        Async.run(this, NetworkService::allStations, rows -> {
            station.setRows(rows);
            if (existing != null) {
                station.selectKey(existing.str("Station_Code"));
            }
        });
    }

    private static Row option(Integer stopNo, String label) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("Position", stopNo == null ? "end" : String.valueOf(stopNo));
        values.put("Label", label);
        values.put("Stop_No", stopNo);
        return new Row(values);
    }

    @Override
    protected Async.Action buildSave() {
        String code = station.selectedKey();
        LocalTime arrives = arrival.getTime();
        LocalTime leaves = departure.getTime();
        if (existing == null) {
            Row chosen = position.selectedRow();
            Integer at = chosen == null ? null : chosen.integer("Stop_No");
            return () -> TrainService.addStop(trainId, code, arrives, leaves, at);
        }
        int stopNo = existing.integer("Stop_No", 0);
        return () -> TrainService.updateStop(trainId, stopNo, code, arrives, leaves);
    }
}
