package com.railway.app.ui.dialogs;

import com.railway.app.data.Row;
import com.railway.app.service.NetworkService;
import com.railway.app.service.TrainService;
import com.railway.app.ui.components.Async;
import com.railway.app.ui.components.FormDialog;
import com.railway.app.ui.components.Inputs;
import com.railway.app.ui.components.RowCombo;

import javax.swing.JTextField;
import java.awt.Component;

/**
 * Adds a train or changes its name and type.
 */
public class TrainDialog extends FormDialog {

    private final Row existing;
    private final JTextField number = Inputs.text("e.g. SE9");
    private final JTextField name = Inputs.text("e.g. Reunification Express SE9");
    private final RowCombo type = new RowCombo("Train_Type", r -> r.str("Train_Type"), null);

    public TrainDialog(Component owner, Row existing) {
        super(owner, existing == null ? "New train" : "Edit train " + existing.str("Train_ID"),
                existing == null ? "Give the train a number, a name and a type." : "Change the name or the type.",
                existing == null ? "Add train" : "Save");
        this.existing = existing;
        addField("Train number", number, existing == null ? "Letters and digits, up to 10 characters" : null);
        addField("Name", name, null);
        addField("Type", type, null);
        if (existing != null) {
            number.setText(existing.str("Train_ID"));
            number.setEnabled(false);
            name.setText(existing.str("Train_Name"));
        }
        Async.run(this, NetworkService::trainTypes, rows -> {
            type.setRows(rows);
            if (existing != null) {
                type.selectKey(existing.str("Train_Type"));
            }
        });
    }

    @Override
    protected Async.Action buildSave() {
        String id = number.getText();
        String trainName = name.getText();
        String trainType = type.selectedKey();
        if (existing == null) {
            return () -> TrainService.createTrain(id, trainName, trainType);
        }
        String trainId = existing.str("Train_ID");
        return () -> TrainService.updateTrain(trainId, trainName, trainType);
    }
}
