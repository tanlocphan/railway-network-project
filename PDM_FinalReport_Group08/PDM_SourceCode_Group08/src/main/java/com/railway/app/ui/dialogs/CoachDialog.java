package com.railway.app.ui.dialogs;

import com.railway.app.data.Row;
import com.railway.app.service.NetworkService;
import com.railway.app.service.TrainService;
import com.railway.app.ui.components.Async;
import com.railway.app.ui.components.FormDialog;
import com.railway.app.ui.components.RowCombo;

import java.awt.Component;

/**
 * Adds a coach to a train, or changes the class of a coach.
 */
public class CoachDialog extends FormDialog {

    private final String trainId;
    private final Row existing;
    private final RowCombo coachClass = new RowCombo("Coach_Class",
            r -> r.str("Coach_Class") + "  ·  up to " + r.str("Capacity") + " " + r.str("Seat_Kind").toLowerCase() + "s",
            null);

    public CoachDialog(Component owner, String trainId, Row existing) {
        super(owner, existing == null ? "Add a coach to " + trainId : "Change class of coach " + existing.str("C_ID"),
                existing == null ? "The coach gets the next free number. Add its places afterwards."
                        : "Pick the new class for this coach.",
                existing == null ? "Add coach" : "Save");
        this.trainId = trainId;
        this.existing = existing;
        addField("Class", coachClass, null);
        Async.run(this, NetworkService::coachClasses, rows -> {
            coachClass.setRows(rows);
            if (existing != null) {
                coachClass.selectKey(existing.str("Coach_Class"));
            }
        });
    }

    @Override
    protected Async.Action buildSave() {
        String chosen = coachClass.selectedKey();
        if (existing == null) {
            return () -> TrainService.addCoach(trainId, chosen);
        }
        String coachId = existing.str("C_ID");
        return () -> TrainService.updateCoach(trainId, coachId, chosen);
    }
}
