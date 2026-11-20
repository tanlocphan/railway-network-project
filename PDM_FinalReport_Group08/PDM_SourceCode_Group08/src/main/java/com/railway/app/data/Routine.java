package com.railway.app.data;

/**
 * Every MySQL routine the application is allowed to call.
 * The application never sends any other statement to the database.
 */
public enum Routine {

    // Reference lists
    TRAIN_TYPE_LIST("sp_train_type_list"),
    COACH_CLASS_LIST("sp_coach_class_list"),
    PAYMENT_METHOD_LIST("sp_payment_method_list"),
    CITY_LIST("sp_city_list"),

    // Stations and tracks
    STATION_LIST("sp_station_list"),
    STATION_CREATE("sp_station_create"),
    STATION_UPDATE("sp_station_update"),
    STATION_DELETE("sp_station_delete"),
    TRACK_LIST("sp_track_list"),
    TRACK_CREATE("sp_track_create"),
    TRACK_UPDATE("sp_track_update"),
    TRACK_DELETE("sp_track_delete"),

    // Trains, timetables, coaches, seats
    TRAIN_LIST("sp_train_list"),
    TRAIN_GET("sp_train_get"),
    TRAIN_CREATE("sp_train_create"),
    TRAIN_UPDATE("sp_train_update"),
    TRAIN_DELETE("sp_train_delete"),
    TIMETABLE_GET("sp_timetable_get"),
    STOP_ADD("sp_stop_add"),
    STOP_UPDATE("sp_stop_update"),
    STOP_DELETE("sp_stop_delete"),
    COACH_LIST("sp_coach_list"),
    COACH_ADD("sp_coach_add"),
    COACH_UPDATE("sp_coach_update"),
    COACH_DELETE("sp_coach_delete"),
    SEAT_LIST("sp_seat_list"),
    SEAT_ADD("sp_seat_add"),
    SEAT_DELETE("sp_seat_delete"),

    // Passengers
    PASSENGER_LIST("sp_passenger_list"),
    PASSENGER_CREATE("sp_passenger_create"),
    PASSENGER_UPDATE("sp_passenger_update"),
    PASSENGER_DELETE("sp_passenger_delete"),

    // Booking
    TRIP_SEARCH("sp_trip_search"),
    SEAT_MAP("sp_seat_map"),
    FN_SUGGEST_FARE("fn_suggest_fare"),
    BOOKING_CREATE("sp_booking_create"),
    BOOKING_LIST("sp_booking_list"),
    BOOKING_GET("sp_booking_get"),
    BOOKING_UPDATE("sp_booking_update"),
    BOOKING_CANCEL("sp_booking_cancel"),

    // Dashboard
    DASHBOARD_SUMMARY("sp_dashboard_summary"),
    DASHBOARD_DEPARTURES("sp_dashboard_departures"),

    // Reports
    REPORT_TRAIN_STOPS("sp_report_train_stops"),
    REPORT_DEPARTURES_BETWEEN("sp_report_departures_between"),
    REPORT_ROUTE_DISTANCES("sp_report_route_distances"),
    REPORT_TRAINS_COVERING_LINE("sp_report_trains_covering_line"),
    FN_LINE_DESCRIPTION("fn_line_description"),
    REPORT_PASSENGERS_MULTIPLE_BOOKINGS("sp_report_passengers_multiple_bookings"),
    REPORT_TRAINS_BY_TYPE("sp_report_trains_by_type"),
    REPORT_STATIONS_IN_CITY("sp_report_stations_in_city"),
    REPORT_AVERAGE_FARE_BY_TYPE("sp_report_average_fare_by_type"),
    REPORT_COACHES_OF_CLASS("sp_report_coaches_of_class"),
    REPORT_LONGEST_TRACK("sp_report_longest_track"),
    REPORT_PASSENGERS_FARE_ABOVE("sp_report_passengers_fare_above"),
    REPORT_STATIONS_PER_CITY("sp_report_stations_per_city"),
    REPORT_TICKET_SALES_BY_TRAIN("sp_report_ticket_sales_by_train"),
    FN_GET_PASSENGER_FARE("fn_get_passenger_fare");

    private final String routineName;

    Routine(String routineName) {
        this.routineName = routineName;
    }

    public String routineName() {
        return routineName;
    }

    public boolean isFunction() {
        return routineName.startsWith("fn_");
    }
}
