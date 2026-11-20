package com.railway.app.service;

import com.railway.app.data.AppException;
import com.railway.app.data.Database;
import com.railway.app.data.Routine;
import com.railway.app.data.Row;

import java.util.List;

/**
 * Stations, tracks and the shared reference lists.
 */
public final class NetworkService {

    private NetworkService() {
    }

    public static List<Row> stations(String search, String city) throws AppException {
        return Database.call(Routine.STATION_LIST, search, city);
    }

    public static List<Row> allStations() throws AppException {
        return stations(null, null);
    }

    public static List<Row> cities() throws AppException {
        return Database.call(Routine.CITY_LIST);
    }

    public static void createStation(String code, String name, String city) throws AppException {
        Database.call(Routine.STATION_CREATE, code, name, city);
    }

    public static void updateStation(String code, String name, String city) throws AppException {
        Database.call(Routine.STATION_UPDATE, code, name, city);
    }

    public static void deleteStation(String code) throws AppException {
        Database.call(Routine.STATION_DELETE, code);
    }

    public static List<Row> tracks(String stationCode) throws AppException {
        return Database.call(Routine.TRACK_LIST, stationCode);
    }

    public static void createTrack(String from, String to, Integer lengthKm) throws AppException {
        Database.call(Routine.TRACK_CREATE, from, to, lengthKm);
    }

    public static void updateTrack(String trackId, Integer lengthKm) throws AppException {
        Database.call(Routine.TRACK_UPDATE, trackId, lengthKm);
    }

    public static void deleteTrack(String trackId) throws AppException {
        Database.call(Routine.TRACK_DELETE, trackId);
    }

    public static List<Row> trainTypes() throws AppException {
        return Database.call(Routine.TRAIN_TYPE_LIST);
    }

    public static List<Row> coachClasses() throws AppException {
        return Database.call(Routine.COACH_CLASS_LIST);
    }

    public static List<Row> paymentMethods() throws AppException {
        return Database.call(Routine.PAYMENT_METHOD_LIST);
    }
}
