package com.railway.app.ui.dialogs;

import com.railway.app.data.Row;
import com.railway.app.service.BookingService;
import com.railway.app.ui.components.Async;
import com.railway.app.ui.components.FormDialog;
import com.railway.app.ui.components.Inputs;

import javax.swing.JTextField;
import java.awt.Component;

/**
 * Adds a passenger or changes their name and phone numbers.
 */
public class PassengerDialog extends FormDialog {

    private final Row existing;
    private final JTextField name = Inputs.text("Full name, e.g. Nguyen Van An");
    private final JTextField phones = Inputs.text("e.g. 0901234567, 0912345678");

    public PassengerDialog(Component owner, Row existing) {
        super(owner, existing == null ? "New passenger" : "Edit " + existing.str("Passenger_Name"),
                existing == null ? "Only the name is required." : "Passenger " + existing.str("Passenger_ID"),
                existing == null ? "Add passenger" : "Save");
        this.existing = existing;
        addField("Full name", name, null);
        addField("Phone numbers", phones, "10 digits starting with 0. Separate several numbers with commas.");
        if (existing != null) {
            name.setText(existing.str("Passenger_Name"));
            phones.setText(existing.str("Phones", ""));
        }
    }

    @Override
    protected Async.Action buildSave() {
        String fullName = name.getText();
        String numbers = phones.getText();
        if (existing == null) {
            return () -> BookingService.createPassenger(fullName, numbers);
        }
        String id = existing.str("Passenger_ID");
        return () -> BookingService.updatePassenger(id, fullName, numbers);
    }
}
