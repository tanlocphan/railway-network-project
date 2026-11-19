package com.railway.app.ui.dialogs;

import com.railway.app.data.Row;
import com.railway.app.service.TrainService;
import com.railway.app.ui.components.Async;
import com.railway.app.ui.components.FormDialog;
import com.railway.app.ui.components.Inputs;

import javax.swing.JSpinner;
import java.awt.Component;

/**
 * Adds a number of seats or berths to a coach.
 */
public class AddSeatsDialog extends FormDialog {

    private final String trainId;
    private final String coachId;
    private final JSpinner count;

    public AddSeatsDialog(Component owner, String trainId, Row coach) {
        super(owner, "Add places to coach " + coach.str("C_ID"),
                coach.str("Coach_Class") + " · " + coach.integer("Seat_Count", 0) + " of "
                        + coach.integer("Capacity", 0) + " places set up",
                "Add places");
        this.trainId = trainId;
        this.coachId = coach.str("C_ID");
        int room = Math.max(1, coach.integer("Capacity", 1) - coach.integer("Seat_Count", 0));
        this.count = Inputs.number(Math.min(10, room), 1, 200);
        addField("How many?", count, "They are numbered after the last place in this coach");
    }

    @Override
    protected Async.Action buildSave() {
        int amount = (Integer) count.getValue();
        return () -> TrainService.addSeats(trainId, coachId, amount);
    }
}
