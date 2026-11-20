package com.railway.app.ui;

/**
 * Lets pages move the user to another page.
 */
public interface Navigator {

    void go(String pageKey);

    /** Opens the Tickets page filtered by a ticket number or name. */
    void openTickets(String search);

    /** Opens the booking wizard for a specific train. */
    void bookOnTrain(String trainId);
}
