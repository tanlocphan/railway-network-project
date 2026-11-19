package com.railway.app.service;

import com.railway.app.data.AppException;
import com.railway.app.data.Database;
import com.railway.app.data.Routine;
import com.railway.app.data.Row;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;

/**
 * Passengers, trip search, seat maps and tickets.
 */
public final class BookingService {

    private BookingService() {
    }

    // ---- passengers -------------------------------------------------------

    public static List<Row> passengers(String search) throws AppException {
        return Database.call(Routine.PASSENGER_LIST, search);
    }

    public static String createPassenger(String name, String phones) throws AppException {
        List<Row> rows = Database.call(Routine.PASSENGER_CREATE, name, phones);
        return rows.isEmpty() ? null : rows.get(0).str("Passenger_ID");
    }

    public static void updatePassenger(String passengerId, String name, String phones) throws AppException {
        Database.call(Routine.PASSENGER_UPDATE, passengerId, name, phones);
    }

    public static void deletePassenger(String passengerId) throws AppException {
        Database.call(Routine.PASSENGER_DELETE, passengerId);
    }

    // ---- booking ----------------------------------------------------------

    public static List<Row> searchTrips(String from, String to, LocalDate date) throws AppException {
        return Database.call(Routine.TRIP_SEARCH, from, to, date);
    }

    public static List<Row> seatMap(String trainId, LocalDate date) throws AppException {
        return Database.call(Routine.SEAT_MAP, trainId, date);
    }

    public static BigDecimal suggestFare(String trainId, String coachId) throws AppException {
        Object value = Database.function(Routine.FN_SUGGEST_FARE, Types.DECIMAL, trainId, coachId);
        return value instanceof BigDecimal decimal ? decimal : null;
    }

    /** Returns the new ticket number. */
    public static String book(String passengerId, String newName, String newPhones, String trainId,
                              LocalDate date, String coachId, String seatId, BigDecimal fare,
                              String paymentMethod) throws AppException {
        List<Row> rows = Database.call(Routine.BOOKING_CREATE, passengerId, newName, newPhones,
                trainId, date, coachId, seatId, fare, paymentMethod);
        return rows.isEmpty() ? null : rows.get(0).str("Booking_ID");
    }

    public static List<Row> tickets(String search, String status, String trainId, String passengerId)
            throws AppException {
        return Database.call(Routine.BOOKING_LIST, search, status, trainId, passengerId);
    }

    public static Row ticket(String bookingId) throws AppException {
        List<Row> rows = Database.call(Routine.BOOKING_GET, bookingId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public static void changeTicket(String bookingId, LocalDate date, String coachId, String seatId,
                                    BigDecimal fare) throws AppException {
        Database.call(Routine.BOOKING_UPDATE, bookingId, date, coachId, seatId, fare);
    }

    public static void cancelTicket(String bookingId) throws AppException {
        Database.call(Routine.BOOKING_CANCEL, bookingId);
    }

    // ---- dashboard --------------------------------------------------------

    public static Row summary() throws AppException {
        List<Row> rows = Database.call(Routine.DASHBOARD_SUMMARY);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public static List<Row> departures(LocalDate date) throws AppException {
        return Database.call(Routine.DASHBOARD_DEPARTURES, date);
    }

    public static List<Row> salesByTrain() throws AppException {
        return Database.call(Routine.REPORT_TICKET_SALES_BY_TRAIN);
    }
}
