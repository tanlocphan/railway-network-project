package com.railway.app.service;

import com.railway.app.data.AppException;
import com.railway.app.data.Database;
import com.railway.app.data.Routine;
import com.railway.app.data.Row;

import java.time.LocalTime;
import java.util.List;

/**
 * Trains, their timetables, coaches and seats.
 */
public final class TrainService {

    private TrainService() {
    }

    public static List<Row> trains(String search, String type) throws AppException {
        return Database.call(Routine.TRAIN_LIST, search, type, null);
    }

    public static List<Row> allTrains() throws AppException {
        return trains(null, null);
    }

    public static Row train(String trainId) throws AppException {
        List<Row> rows = Database.call(Routine.TRAIN_GET, trainId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public static void createTrain(String trainId, String name, String type) throws AppException {
        Database.call(Routine.TRAIN_CREATE, trainId, name, type);
    }

    public static void updateTrain(String trainId, String name, String type) throws AppException {
        Database.call(Routine.TRAIN_UPDATE, trainId, name, type);
    }

    public static void deleteTrain(String trainId) throws AppException {
        Database.call(Routine.TRAIN_DELETE, trainId);
    }

    public static List<Row> timetable(String trainId) throws AppException {
        return Database.call(Routine.TIMETABLE_GET, trainId);
    }

    public static void addStop(String trainId, String station, LocalTime arrival, LocalTime departure,
                               Integer position) throws AppException {
        Database.call(Routine.STOP_ADD, trainId, station, arrival, departure, position);
    }

    public static void updateStop(String trainId, int stopNo, String station, LocalTime arrival,
                                  LocalTime departure) throws AppException {
        Database.call(Routine.STOP_UPDATE, trainId, stopNo, station, arrival, departure);
    }

    public static void deleteStop(String trainId, int stopNo) throws AppException {
        Database.call(Routine.STOP_DELETE, trainId, stopNo);
    }

    public static List<Row> coaches(String trainId) throws AppException {
        return Database.call(Routine.COACH_LIST, trainId);
    }

    public static void addCoach(String trainId, String coachClass) throws AppException {
        Database.call(Routine.COACH_ADD, trainId, coachClass);
    }

    public static void updateCoach(String trainId, String coachId, String coachClass) throws AppException {
        Database.call(Routine.COACH_UPDATE, trainId, coachId, coachClass);
    }

    public static void deleteCoach(String trainId, String coachId) throws AppException {
        Database.call(Routine.COACH_DELETE, trainId, coachId);
    }

    public static List<Row> seats(String trainId, String coachId) throws AppException {
        return Database.call(Routine.SEAT_LIST, trainId, coachId);
    }

    public static void addSeats(String trainId, String coachId, int count) throws AppException {
        Database.call(Routine.SEAT_ADD, trainId, coachId, count);
    }

    public static void deleteSeat(String trainId, String coachId, String seatId) throws AppException {
        Database.call(Routine.SEAT_DELETE, trainId, coachId, seatId);
    }
}
