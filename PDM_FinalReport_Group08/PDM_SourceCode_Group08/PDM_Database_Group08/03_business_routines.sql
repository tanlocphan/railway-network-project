
USE RailwayManagement;

DELIMITER $$


DROP PROCEDURE IF EXISTS sp_raise$$
CREATE PROCEDURE sp_raise(IN p_message VARCHAR(500))
BEGIN
    DECLARE v_text VARCHAR(128);
    SET v_text = LEFT(p_message, 128);
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_text;
END$$

DROP FUNCTION IF EXISTS fn_format_code$$
CREATE FUNCTION fn_format_code(p_prefix VARCHAR(5), p_number INT, p_width INT)
RETURNS VARCHAR(10)
DETERMINISTIC NO SQL
BEGIN
    DECLARE v_digits VARCHAR(10);
    SET v_digits = CAST(p_number AS CHAR);
    IF CHAR_LENGTH(v_digits) < p_width THEN
        SET v_digits = LPAD(v_digits, p_width, '0');
    END IF;
    RETURN CONCAT(p_prefix, v_digits);
END$$

DROP FUNCTION IF EXISTS fn_clean_text$$
CREATE FUNCTION fn_clean_text(p_text VARCHAR(500))
RETURNS VARCHAR(500)
DETERMINISTIC NO SQL
BEGIN
    IF p_text IS NULL THEN
        RETURN NULL;
    END IF;
    RETURN NULLIF(TRIM(REGEXP_REPLACE(p_text, '[[:space:]]+', ' ')), '');
END$$

-- ---------------------------------------------------------------------
-- Next identifiers (Passenger P001, Booking BKG0001, Payment PAY0001,
-- Track TS01, Coach C01, Seat S01/B01/V01)
-- ---------------------------------------------------------------------
DROP FUNCTION IF EXISTS fn_next_passenger_id$$
CREATE FUNCTION fn_next_passenger_id()
RETURNS VARCHAR(10)
NOT DETERMINISTIC READS SQL DATA
BEGIN
    DECLARE v_max INT;
    SET v_max = (SELECT COALESCE(MAX(CAST(SUBSTRING(Passenger_ID, 2) AS UNSIGNED)), 0)
                 FROM Passenger WHERE Passenger_ID REGEXP '^P[0-9]+$');
    RETURN fn_format_code('P', v_max + 1, 3);
END$$

DROP FUNCTION IF EXISTS fn_next_booking_id$$
CREATE FUNCTION fn_next_booking_id()
RETURNS VARCHAR(10)
NOT DETERMINISTIC READS SQL DATA
BEGIN
    DECLARE v_max INT;
    SET v_max = (SELECT COALESCE(MAX(CAST(SUBSTRING(Booking_ID, 4) AS UNSIGNED)), 0)
                 FROM Booking WHERE Booking_ID REGEXP '^BKG[0-9]+$');
    RETURN fn_format_code('BKG', v_max + 1, 4);
END$$

DROP FUNCTION IF EXISTS fn_next_payment_id$$
CREATE FUNCTION fn_next_payment_id()
RETURNS VARCHAR(10)
NOT DETERMINISTIC READS SQL DATA
BEGIN
    DECLARE v_max INT;
    SET v_max = (SELECT COALESCE(MAX(CAST(SUBSTRING(Pay_ID, 4) AS UNSIGNED)), 0)
                 FROM Payment WHERE Pay_ID REGEXP '^PAY[0-9]+$');
    RETURN fn_format_code('PAY', v_max + 1, 4);
END$$

DROP FUNCTION IF EXISTS fn_next_track_id$$
CREATE FUNCTION fn_next_track_id()
RETURNS VARCHAR(10)
NOT DETERMINISTIC READS SQL DATA
BEGIN
    DECLARE v_max INT;
    SET v_max = (SELECT COALESCE(MAX(CAST(SUBSTRING(Track_ID, 3) AS UNSIGNED)), 0)
                 FROM Station_Connection WHERE Track_ID REGEXP '^TS[0-9]+$');
    RETURN fn_format_code('TS', v_max + 1, 2);
END$$

DROP FUNCTION IF EXISTS fn_next_coach_id$$
CREATE FUNCTION fn_next_coach_id(p_train_id VARCHAR(10))
RETURNS VARCHAR(10)
NOT DETERMINISTIC READS SQL DATA
BEGIN
    DECLARE v_max INT;
    SET v_max = (SELECT COALESCE(MAX(CAST(SUBSTRING(C_ID, 2) AS UNSIGNED)), 0)
                 FROM Coach WHERE Train_ID = p_train_id AND C_ID REGEXP '^C[0-9]+$');
    RETURN fn_format_code('C', v_max + 1, 2);
END$$

-- =====================================================================
-- 2. REFERENCE LISTS (train types, coach classes, payment methods)
-- =====================================================================

DROP FUNCTION IF EXISTS fn_is_valid_train_type$$
CREATE FUNCTION fn_is_valid_train_type(p_type VARCHAR(50))
RETURNS TINYINT
DETERMINISTIC NO SQL
BEGIN
    RETURN IF(p_type IN ('Express', 'Local', 'Night'), 1, 0);
END$$

-- Seats/berths a coach of each class can physically hold.
DROP FUNCTION IF EXISTS fn_coach_capacity$$
CREATE FUNCTION fn_coach_capacity(p_class VARCHAR(50))
RETURNS INT
DETERMINISTIC NO SQL
BEGIN
    RETURN CASE p_class
        WHEN 'Soft Seat AC'        THEN 64
        WHEN 'Hard Seat'           THEN 80
        WHEN 'Sleeper 6-berth'     THEN 42
        WHEN 'Sleeper 4-berth'     THEN 28
        WHEN 'VIP Sleeper 2-berth' THEN 14
        ELSE NULL
    END;
END$$

DROP FUNCTION IF EXISTS fn_seat_prefix$$
CREATE FUNCTION fn_seat_prefix(p_class VARCHAR(50))
RETURNS CHAR(1)
DETERMINISTIC NO SQL
BEGIN
    RETURN CASE
        WHEN p_class LIKE 'VIP%' THEN 'V'
        WHEN p_class LIKE '%Sleeper%' OR p_class LIKE '%berth%' THEN 'B'
        ELSE 'S'
    END;
END$$

DROP FUNCTION IF EXISTS fn_seat_kind$$
CREATE FUNCTION fn_seat_kind(p_class VARCHAR(50))
RETURNS VARCHAR(10)
DETERMINISTIC NO SQL
BEGIN
    RETURN IF(fn_seat_prefix(p_class) = 'S', 'Seat', 'Berth');
END$$

DROP FUNCTION IF EXISTS fn_is_valid_payment_method$$
CREATE FUNCTION fn_is_valid_payment_method(p_method VARCHAR(30))
RETURNS TINYINT
DETERMINISTIC NO SQL
BEGIN
    RETURN IF(p_method IN ('Cash', 'Card', 'Bank Transfer', 'E-Wallet'), 1, 0);
END$$

DROP PROCEDURE IF EXISTS sp_train_type_list$$
CREATE PROCEDURE sp_train_type_list()
BEGIN
    SELECT 'Express' AS Train_Type
    UNION ALL SELECT 'Local'
    UNION ALL SELECT 'Night';
END$$

DROP PROCEDURE IF EXISTS sp_coach_class_list$$
CREATE PROCEDURE sp_coach_class_list()
BEGIN
    SELECT c.Coach_Class, fn_coach_capacity(c.Coach_Class) AS Capacity, fn_seat_kind(c.Coach_Class) AS Seat_Kind
    FROM (SELECT 'Soft Seat AC' AS Coach_Class
          UNION ALL SELECT 'Hard Seat'
          UNION ALL SELECT 'Sleeper 6-berth'
          UNION ALL SELECT 'Sleeper 4-berth'
          UNION ALL SELECT 'VIP Sleeper 2-berth') c;
END$$

DROP PROCEDURE IF EXISTS sp_payment_method_list$$
CREATE PROCEDURE sp_payment_method_list()
BEGIN
    SELECT 'Cash' AS Method
    UNION ALL SELECT 'Card'
    UNION ALL SELECT 'Bank Transfer'
    UNION ALL SELECT 'E-Wallet';
END$$

-- =====================================================================
-- 3. TIMETABLE TIME ARITHMETIC (overnight trains)
-- =====================================================================

-- Converts a clock time into "minutes since midnight of the departure
-- day", moving it to the next day when it is earlier than p_previous.
DROP FUNCTION IF EXISTS fn_roll_forward$$
CREATE FUNCTION fn_roll_forward(p_previous INT, p_time TIME)
RETURNS INT
DETERMINISTIC NO SQL
BEGIN
    DECLARE v_minutes INT;
    DECLARE v_candidate INT;
    IF p_time IS NULL THEN
        RETURN NULL;
    END IF;
    SET v_minutes = TIME_TO_SEC(p_time) DIV 60;
    IF p_previous IS NULL THEN
        RETURN v_minutes;
    END IF;
    SET v_candidate = p_previous - MOD(p_previous, 1440) + v_minutes;
    IF v_candidate < p_previous THEN
        SET v_candidate = v_candidate + 1440;
    END IF;
    RETURN v_candidate;
END$$

-- Minutes from midnight of the travel date to the arrival ('A') or
-- departure ('D') of a stop. DIV 1440 gives the day offset (+1 day ...).
DROP FUNCTION IF EXISTS fn_stop_minutes$$
CREATE FUNCTION fn_stop_minutes(p_train_id VARCHAR(10), p_stop_no INT, p_kind CHAR(1))
RETURNS INT
NOT DETERMINISTIC READS SQL DATA
BEGIN
    DECLARE v_done INT DEFAULT 0;
    DECLARE v_stop INT;
    DECLARE v_arrival TIME;
    DECLARE v_departure TIME;
    DECLARE v_last INT DEFAULT NULL;
    DECLARE v_arrival_abs INT;
    DECLARE v_departure_abs INT;
    DECLARE v_result INT DEFAULT NULL;
    DECLARE c_stops CURSOR FOR
        SELECT Stop_No, Arrival, Departure
        FROM Timetable
        WHERE Train_ID = p_train_id AND Stop_No <= p_stop_no
        ORDER BY Stop_No;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

    OPEN c_stops;
    stop_loop: LOOP
        FETCH c_stops INTO v_stop, v_arrival, v_departure;
        IF v_done = 1 THEN
            LEAVE stop_loop;
        END IF;
        SET v_arrival_abs = fn_roll_forward(v_last, v_arrival);
        IF v_arrival_abs IS NOT NULL THEN
            SET v_last = v_arrival_abs;
        END IF;
        SET v_departure_abs = fn_roll_forward(v_last, v_departure);
        IF v_departure_abs IS NOT NULL THEN
            SET v_last = v_departure_abs;
        END IF;
        IF v_stop = p_stop_no THEN
            SET v_result = IF(p_kind = 'A', v_arrival_abs, v_departure_abs);
            LEAVE stop_loop;
        END IF;
    END LOOP;
    CLOSE c_stops;
    RETURN v_result;
END$$

DROP FUNCTION IF EXISTS fn_first_stop_no$$
CREATE FUNCTION fn_first_stop_no(p_train_id VARCHAR(10))
RETURNS INT
NOT DETERMINISTIC READS SQL DATA
BEGIN
    RETURN (SELECT MIN(Stop_No) FROM Timetable WHERE Train_ID = p_train_id);
END$$

DROP FUNCTION IF EXISTS fn_last_stop_no$$
CREATE FUNCTION fn_last_stop_no(p_train_id VARCHAR(10))
RETURNS INT
NOT DETERMINISTIC READS SQL DATA
BEGIN
    RETURN (SELECT MAX(Stop_No) FROM Timetable WHERE Train_ID = p_train_id);
END$$

-- Date and time the train leaves its first station on a travel date.
DROP FUNCTION IF EXISTS fn_trip_departure$$
CREATE FUNCTION fn_trip_departure(p_train_id VARCHAR(10), p_travel_date DATE)
RETURNS DATETIME
NOT DETERMINISTIC READS SQL DATA
BEGIN
    DECLARE v_minutes INT;
    SET v_minutes = fn_stop_minutes(p_train_id, fn_first_stop_no(p_train_id), 'D');
    IF v_minutes IS NULL THEN
        RETURN NULL;
    END IF;
    RETURN TIMESTAMP(p_travel_date) + INTERVAL v_minutes MINUTE;
END$$

-- Date and time the train reaches its final station on a travel date.
DROP FUNCTION IF EXISTS fn_trip_arrival$$
CREATE FUNCTION fn_trip_arrival(p_train_id VARCHAR(10), p_travel_date DATE)
RETURNS DATETIME
NOT DETERMINISTIC READS SQL DATA
BEGIN
    DECLARE v_first INT;
    DECLARE v_last INT;
    DECLARE v_minutes INT;
    SET v_first = fn_first_stop_no(p_train_id);
    SET v_last = fn_last_stop_no(p_train_id);
    IF v_last IS NULL OR v_last = v_first THEN
        RETURN NULL;
    END IF;
    SET v_minutes = fn_stop_minutes(p_train_id, v_last, 'A');
    IF v_minutes IS NULL THEN
        RETURN NULL;
    END IF;
    RETURN TIMESTAMP(p_travel_date) + INTERVAL v_minutes MINUTE;
END$$

-- Upcoming / On the way / Completed, compared with the current time.
DROP FUNCTION IF EXISTS fn_trip_status$$
CREATE FUNCTION fn_trip_status(p_train_id VARCHAR(10), p_travel_date DATE)
RETURNS VARCHAR(20)
NOT DETERMINISTIC READS SQL DATA
BEGIN
    DECLARE v_departure DATETIME;
    DECLARE v_arrival DATETIME;
    SET v_departure = fn_trip_departure(p_train_id, p_travel_date);
    SET v_arrival = fn_trip_arrival(p_train_id, p_travel_date);
    IF v_departure IS NULL THEN
        RETURN IF(p_travel_date >= CURDATE(), 'Upcoming', 'Completed');
    END IF;
    IF NOW() < v_departure THEN
        RETURN 'Upcoming';
    END IF;
    IF v_arrival IS NOT NULL AND NOW() < v_arrival THEN
        RETURN 'On the way';
    END IF;
    RETURN 'Completed';
END$$

-- =====================================================================
-- 4. RAILWAY NETWORK (two-way tracks, shortest path)
-- =====================================================================

-- Shortest distance in km between two stations over the track network.
-- NULL when no chain of tracks connects them.
DROP FUNCTION IF EXISTS fn_track_distance$$
CREATE FUNCTION fn_track_distance(p_from VARCHAR(10), p_to VARCHAR(10))
RETURNS INT
NOT DETERMINISTIC READS SQL DATA
BEGIN
    DECLARE v_km INT DEFAULT NULL;
    IF p_from IS NULL OR p_to IS NULL THEN
        RETURN NULL;
    END IF;
    IF p_from = p_to THEN
        RETURN 0;
    END IF;
    WITH RECURSIVE
        edges (a, b, km) AS (
            SELECT From_Station, To_Station, Length_km FROM Station_Connection
            UNION ALL
            SELECT To_Station, From_Station, Length_km FROM Station_Connection
        ),
        walk (node, km, visited) AS (
            SELECT CAST(p_from AS CHAR(10)), 0, CAST(CONCAT(',', p_from, ',') AS CHAR(4000))
            UNION ALL
            SELECT e.b, w.km + e.km, CONCAT(w.visited, e.b, ',')
            FROM walk w
            JOIN edges e ON e.a = w.node
            WHERE w.node <> p_to
              AND LOCATE(CONCAT(',', e.b, ','), w.visited) = 0
        )
    SELECT MIN(km) INTO v_km FROM walk WHERE node = p_to;
    RETURN v_km;
END$$

-- Station codes along the shortest track path, as ",SGN,BHO,BTT,".
DROP FUNCTION IF EXISTS fn_track_path$$
CREATE FUNCTION fn_track_path(p_from VARCHAR(10), p_to VARCHAR(10))
RETURNS VARCHAR(4000)
NOT DETERMINISTIC READS SQL DATA
BEGIN
    DECLARE v_path VARCHAR(4000) DEFAULT NULL;
    IF p_from IS NULL OR p_to IS NULL THEN
        RETURN NULL;
    END IF;
    IF p_from = p_to THEN
        RETURN CONCAT(',', p_from, ',');
    END IF;
    WITH RECURSIVE
        edges (a, b, km) AS (
            SELECT From_Station, To_Station, Length_km FROM Station_Connection
            UNION ALL
            SELECT To_Station, From_Station, Length_km FROM Station_Connection
        ),
        walk (node, km, visited) AS (
            SELECT CAST(p_from AS CHAR(10)), 0, CAST(CONCAT(',', p_from, ',') AS CHAR(4000))
            UNION ALL
            SELECT e.b, w.km + e.km, CONCAT(w.visited, e.b, ',')
            FROM walk w
            JOIN edges e ON e.a = w.node
            WHERE w.node <> p_to
              AND LOCATE(CONCAT(',', e.b, ','), w.visited) = 0
        )
    SELECT (SELECT visited FROM walk WHERE node = p_to ORDER BY km LIMIT 1) INTO v_path;
    RETURN v_path;
END$$

-- Distance travelled by a train between two of its stops (inclusive).
-- NULL when one of the legs is not connected by track.
DROP FUNCTION IF EXISTS fn_train_segment_km$$
CREATE FUNCTION fn_train_segment_km(p_train_id VARCHAR(10), p_from_stop INT, p_to_stop INT)
RETURNS INT
NOT DETERMINISTIC READS SQL DATA
BEGIN
    DECLARE v_total INT DEFAULT 0;
    DECLARE v_missing INT DEFAULT 0;
    DECLARE v_legs INT DEFAULT 0;
    SELECT COALESCE(SUM(legs.km), 0), COALESCE(SUM(legs.km IS NULL), 0), COUNT(*)
      INTO v_total, v_missing, v_legs
    FROM (
        SELECT fn_track_distance(pairs.prev_code, pairs.code) AS km
        FROM (
            SELECT Station_Code AS code,
                   LAG(Station_Code) OVER (ORDER BY Stop_No) AS prev_code
            FROM Timetable
            WHERE Train_ID = p_train_id AND Stop_No BETWEEN p_from_stop AND p_to_stop
        ) pairs
        WHERE pairs.prev_code IS NOT NULL
    ) legs;
    IF v_legs = 0 OR v_missing > 0 THEN
        RETURN NULL;
    END IF;
    RETURN v_total;
END$$

-- Total length of a train's route (first stop to last stop).
DROP FUNCTION IF EXISTS fn_train_route_km$$
CREATE FUNCTION fn_train_route_km(p_train_id VARCHAR(10))
RETURNS INT
NOT DETERMINISTIC READS SQL DATA
BEGIN
    RETURN fn_train_segment_km(p_train_id, fn_first_stop_no(p_train_id), fn_last_stop_no(p_train_id));
END$$

-- =====================================================================
-- 5. TIMETABLE VALIDATION
-- =====================================================================

-- Returns NULL when the timetable of a train follows every rule,
-- otherwise a plain-language description of the first problem found.
DROP FUNCTION IF EXISTS fn_timetable_problem$$
CREATE FUNCTION fn_timetable_problem(p_train_id VARCHAR(10))
RETURNS VARCHAR(255)
NOT DETERMINISTIC READS SQL DATA
BEGIN
    DECLARE v_done INT DEFAULT 0;
    DECLARE v_total INT DEFAULT 0;
    DECLARE v_index INT DEFAULT 0;
    DECLARE v_code VARCHAR(10);
    DECLARE v_name VARCHAR(100);
    DECLARE v_arrival TIME;
    DECLARE v_departure TIME;
    DECLARE v_prev_code VARCHAR(10) DEFAULT NULL;
    DECLARE v_prev_name VARCHAR(100) DEFAULT NULL;
    DECLARE v_last INT DEFAULT NULL;
    DECLARE v_abs INT;
    DECLARE v_problem VARCHAR(255) DEFAULT NULL;
    DECLARE c_stops CURSOR FOR
        SELECT t.Station_Code, s.Station_Name, t.Arrival, t.Departure
        FROM Timetable t
        JOIN Station s ON s.Station_Code = t.Station_Code
        WHERE t.Train_ID = p_train_id
        ORDER BY t.Stop_No;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

    SET v_total = (SELECT COUNT(*) FROM Timetable WHERE Train_ID = p_train_id);
    IF v_total = 0 THEN
        RETURN NULL;
    END IF;

    OPEN c_stops;
    stop_loop: LOOP
        FETCH c_stops INTO v_code, v_name, v_arrival, v_departure;
        IF v_done = 1 THEN
            LEAVE stop_loop;
        END IF;
        SET v_index = v_index + 1;

        -- Which times each kind of stop needs
        IF v_index = 1 THEN
            IF v_departure IS NULL THEN
                SET v_problem = CONCAT('The first stop, ', v_name, ', needs a departure time.');
                LEAVE stop_loop;
            END IF;
        ELSEIF v_index < v_total THEN
            IF v_arrival IS NULL OR v_departure IS NULL THEN
                SET v_problem = CONCAT(v_name, ' is a middle stop, so it needs both an arrival and a departure time.');
                LEAVE stop_loop;
            END IF;
        ELSE
            IF v_arrival IS NULL THEN
                SET v_problem = CONCAT('The final stop, ', v_name, ', needs an arrival time.');
                LEAVE stop_loop;
            END IF;
        END IF;

        -- Only tracks that exist in the system may be used by a route
        IF v_prev_code IS NOT NULL AND fn_track_distance(v_prev_code, v_code) IS NULL THEN
            SET v_problem = CONCAT('No railway track links ', v_prev_name, ' and ', v_name, '.');
            LEAVE stop_loop;
        END IF;

        -- Travel time between consecutive stops must move forward
        IF v_arrival IS NOT NULL THEN
            SET v_abs = fn_roll_forward(v_last, v_arrival);
            IF v_last IS NOT NULL AND v_abs = v_last THEN
                SET v_problem = CONCAT('The arrival at ', v_name, ' must be later than the departure from ', v_prev_name, '.');
                LEAVE stop_loop;
            END IF;
            SET v_last = v_abs;
        END IF;

        -- Departure must follow arrival at the same station (dwell up to 3 hours).
        -- A departure time written on the final stop is not used.
        IF v_departure IS NOT NULL AND (v_index < v_total OR v_total = 1) THEN
            SET v_abs = fn_roll_forward(v_last, v_departure);
            IF v_arrival IS NOT NULL AND (v_abs = v_last OR v_abs - v_last > 180) THEN
                SET v_problem = CONCAT('At ', v_name, ', the departure must come after the arrival (stops can last up to 3 hours).');
                LEAVE stop_loop;
            END IF;
            SET v_last = v_abs;
        END IF;

        SET v_prev_code = v_code;
        SET v_prev_name = v_name;
    END LOOP;
    CLOSE c_stops;
    RETURN v_problem;
END$$

DROP PROCEDURE IF EXISTS sp_assert_timetable_valid$$
CREATE PROCEDURE sp_assert_timetable_valid(IN p_train_id VARCHAR(10))
BEGIN
    DECLARE v_problem VARCHAR(255);
    SET v_problem = fn_timetable_problem(p_train_id);
    IF v_problem IS NOT NULL THEN
        CALL sp_raise(v_problem);
    END IF;
END$$

-- Overall sales readiness of a train.
DROP FUNCTION IF EXISTS fn_train_status$$
CREATE FUNCTION fn_train_status(p_train_id VARCHAR(10))
RETURNS VARCHAR(30)
NOT DETERMINISTIC READS SQL DATA
BEGIN
    DECLARE v_stops INT;
    DECLARE v_seats INT;
    SET v_stops = (SELECT COUNT(*) FROM Timetable WHERE Train_ID = p_train_id);
    SET v_seats = (SELECT COUNT(*) FROM Seat WHERE Train_ID = p_train_id);
    IF v_stops = 0 THEN
        RETURN 'No timetable';
    END IF;
    IF v_stops = 1 THEN
        RETURN 'Timetable incomplete';
    END IF;
    IF fn_timetable_problem(p_train_id) IS NOT NULL THEN
        RETURN 'Timetable needs attention';
    END IF;
    IF v_seats = 0 THEN
        RETURN 'No seats set up';
    END IF;
    RETURN 'On sale';
END$$

-- =====================================================================
-- 6. STATIONS
-- =====================================================================

DROP PROCEDURE IF EXISTS sp_station_list$$
CREATE PROCEDURE sp_station_list(IN p_search VARCHAR(100), IN p_city VARCHAR(100))
BEGIN
    DECLARE v_search VARCHAR(100);
    SET v_search = fn_clean_text(p_search);
    SELECT s.Station_Code,
           s.Station_Name,
           s.City,
           (SELECT COUNT(DISTINCT t.Train_ID) FROM Timetable t WHERE t.Station_Code = s.Station_Code) AS Train_Count,
           (SELECT COUNT(*) FROM Station_Connection c
             WHERE c.From_Station = s.Station_Code OR c.To_Station = s.Station_Code) AS Track_Count
    FROM Station s
    WHERE (v_search IS NULL
           OR s.Station_Code LIKE CONCAT('%', v_search, '%')
           OR s.Station_Name LIKE CONCAT('%', v_search, '%')
           OR s.City LIKE CONCAT('%', v_search, '%'))
      AND (fn_clean_text(p_city) IS NULL OR s.City = p_city)
    ORDER BY s.Station_Name;
END$$

DROP PROCEDURE IF EXISTS sp_city_list$$
CREATE PROCEDURE sp_city_list()
BEGIN
    SELECT City, COUNT(*) AS Station_Count
    FROM Station
    GROUP BY City
    ORDER BY City;
END$$

DROP PROCEDURE IF EXISTS sp_station_create$$
CREATE PROCEDURE sp_station_create(IN p_code VARCHAR(10), IN p_name VARCHAR(100), IN p_city VARCHAR(100))
BEGIN
    DECLARE v_code VARCHAR(10);
    DECLARE v_name VARCHAR(100);
    DECLARE v_city VARCHAR(100);
    SET v_code = UPPER(fn_clean_text(p_code));
    SET v_name = fn_clean_text(p_name);
    SET v_city = fn_clean_text(p_city);

    IF v_code IS NULL THEN
        CALL sp_raise('Please enter a station code.');
    END IF;
    IF v_code NOT REGEXP '^[A-Z0-9]{2,10}$' THEN
        CALL sp_raise('A station code must be 2 to 10 letters or digits, for example SGN.');
    END IF;
    IF v_name IS NULL THEN
        CALL sp_raise('Please enter the station name.');
    END IF;
    IF v_city IS NULL THEN
        CALL sp_raise('Please enter the city or province of the station.');
    END IF;
    IF EXISTS (SELECT 1 FROM Station WHERE Station_Code = v_code) THEN
        CALL sp_raise(CONCAT('The station code ', v_code, ' is already used by another station.'));
    END IF;
    IF EXISTS (SELECT 1 FROM Station WHERE Station_Name = v_name) THEN
        CALL sp_raise(CONCAT('A station named "', v_name, '" already exists.'));
    END IF;

    INSERT INTO Station (Station_Code, Station_Name, City) VALUES (v_code, v_name, v_city);
    SELECT v_code AS Station_Code;
END$$

DROP PROCEDURE IF EXISTS sp_station_update$$
CREATE PROCEDURE sp_station_update(IN p_code VARCHAR(10), IN p_name VARCHAR(100), IN p_city VARCHAR(100))
BEGIN
    DECLARE v_name VARCHAR(100);
    DECLARE v_city VARCHAR(100);
    SET v_name = fn_clean_text(p_name);
    SET v_city = fn_clean_text(p_city);

    IF NOT EXISTS (SELECT 1 FROM Station WHERE Station_Code = p_code) THEN
        CALL sp_raise('We could not find that station. It may have been removed.');
    END IF;
    IF v_name IS NULL THEN
        CALL sp_raise('Please enter the station name.');
    END IF;
    IF v_city IS NULL THEN
        CALL sp_raise('Please enter the city or province of the station.');
    END IF;
    IF EXISTS (SELECT 1 FROM Station WHERE Station_Name = v_name AND Station_Code <> p_code) THEN
        CALL sp_raise(CONCAT('A station named "', v_name, '" already exists.'));
    END IF;

    UPDATE Station SET Station_Name = v_name, City = v_city WHERE Station_Code = p_code;
END$$

DROP PROCEDURE IF EXISTS sp_station_delete$$
CREATE PROCEDURE sp_station_delete(IN p_code VARCHAR(10))
BEGIN
    DECLARE v_name VARCHAR(100);
    DECLARE v_trains VARCHAR(500);
    SET v_name = (SELECT Station_Name FROM Station WHERE Station_Code = p_code);
    IF v_name IS NULL THEN
        CALL sp_raise('We could not find that station. It may have been removed.');
    END IF;
    SET v_trains = (SELECT GROUP_CONCAT(DISTINCT Train_ID ORDER BY Train_ID SEPARATOR ', ')
                    FROM Timetable WHERE Station_Code = p_code);
    IF v_trains IS NOT NULL THEN
        CALL sp_raise(CONCAT(v_name, ' is still a stop of train ', v_trains, '. Remove it from those timetables first.'));
    END IF;
    IF EXISTS (SELECT 1 FROM Station_Connection WHERE From_Station = p_code OR To_Station = p_code) THEN
        CALL sp_raise(CONCAT(v_name, ' is still linked to other stations by track. Remove those tracks first.'));
    END IF;
    DELETE FROM Station WHERE Station_Code = p_code;
END$$

-- =====================================================================
-- 7. TRACKS (Station_Connection)
-- =====================================================================

DROP PROCEDURE IF EXISTS sp_track_list$$
CREATE PROCEDURE sp_track_list(IN p_station VARCHAR(10))
BEGIN
    SELECT c.Track_ID,
           c.From_Station, f.Station_Name AS From_Name,
           c.To_Station,   t.Station_Name AS To_Name,
           c.Length_km
    FROM Station_Connection c
    JOIN Station f ON f.Station_Code = c.From_Station
    JOIN Station t ON t.Station_Code = c.To_Station
    WHERE fn_clean_text(p_station) IS NULL
       OR c.From_Station = p_station
       OR c.To_Station = p_station
    ORDER BY CAST(REGEXP_SUBSTR(c.Track_ID, '[0-9]+') AS UNSIGNED), c.Track_ID;
END$$

DROP PROCEDURE IF EXISTS sp_track_create$$
CREATE PROCEDURE sp_track_create(IN p_from VARCHAR(10), IN p_to VARCHAR(10), IN p_length_km INT)
BEGIN
    DECLARE v_id VARCHAR(10);
    IF p_from IS NULL OR p_to IS NULL THEN
        CALL sp_raise('Please choose both stations of the track.');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM Station WHERE Station_Code = p_from)
       OR NOT EXISTS (SELECT 1 FROM Station WHERE Station_Code = p_to) THEN
        CALL sp_raise('We could not find one of the chosen stations.');
    END IF;
    IF p_from = p_to THEN
        CALL sp_raise('A track must join two different stations.');
    END IF;
    IF p_length_km IS NULL OR p_length_km <= 0 THEN
        CALL sp_raise('The track length must be greater than 0 km.');
    END IF;
    IF p_length_km > 2000 THEN
        CALL sp_raise('The track length looks too long for a single section. Please check it.');
    END IF;
    IF EXISTS (SELECT 1 FROM Station_Connection
               WHERE (From_Station = p_from AND To_Station = p_to)
                  OR (From_Station = p_to AND To_Station = p_from)) THEN
        CALL sp_raise('These two stations are already joined by a track.');
    END IF;

    SET v_id = fn_next_track_id();
    INSERT INTO Station_Connection (Track_ID, From_Station, To_Station, Length_km)
    VALUES (v_id, p_from, p_to, p_length_km);
    SELECT v_id AS Track_ID;
END$$

DROP PROCEDURE IF EXISTS sp_track_update$$
CREATE PROCEDURE sp_track_update(IN p_track_id VARCHAR(10), IN p_length_km INT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM Station_Connection WHERE Track_ID = p_track_id) THEN
        CALL sp_raise('We could not find that track. It may have been removed.');
    END IF;
    IF p_length_km IS NULL OR p_length_km <= 0 THEN
        CALL sp_raise('The track length must be greater than 0 km.');
    END IF;
    IF p_length_km > 2000 THEN
        CALL sp_raise('The track length looks too long for a single section. Please check it.');
    END IF;
    UPDATE Station_Connection SET Length_km = p_length_km WHERE Track_ID = p_track_id;
END$$

-- A track can only be removed when every train route stays connected.
DROP PROCEDURE IF EXISTS sp_track_delete$$
CREATE PROCEDURE sp_track_delete(IN p_track_id VARCHAR(10))
BEGIN
    DECLARE v_broken_train VARCHAR(10);
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    IF NOT EXISTS (SELECT 1 FROM Station_Connection WHERE Track_ID = p_track_id) THEN
        CALL sp_raise('We could not find that track. It may have been removed.');
    END IF;

    START TRANSACTION;
    DELETE FROM Station_Connection WHERE Track_ID = p_track_id;
    SET v_broken_train = (
        SELECT pairs.Train_ID
        FROM (SELECT Train_ID, Station_Code,
                     LAG(Station_Code) OVER (PARTITION BY Train_ID ORDER BY Stop_No) AS Prev_Code
              FROM Timetable) pairs
        WHERE pairs.Prev_Code IS NOT NULL
          AND fn_track_distance(pairs.Prev_Code, pairs.Station_Code) IS NULL
        ORDER BY pairs.Train_ID
        LIMIT 1);
    IF v_broken_train IS NOT NULL THEN
        CALL sp_raise(CONCAT('Removing this track would cut the route of train ', v_broken_train, ', so it was kept.'));
    END IF;
    COMMIT;
END$$

-- =====================================================================
-- 8. TRAINS
-- =====================================================================

DROP PROCEDURE IF EXISTS sp_train_list$$
CREATE PROCEDURE sp_train_list(IN p_search VARCHAR(100), IN p_type VARCHAR(50), IN p_train_id VARCHAR(10))
BEGIN
    DECLARE v_search VARCHAR(100);
    SET v_search = fn_clean_text(p_search);
    SELECT x.*,
           x.Arrival_Minutes DIV 1440 AS Arrival_Day,
           x.Arrival_Minutes - x.Departure_Minutes AS Duration_Minutes
    FROM (
        SELECT t.Train_ID,
               t.Train_Name,
               t.Train_Type,
               fo.Station_Code AS Origin_Code,
               so.Station_Name AS Origin_Name,
               fd.Station_Code AS Destination_Code,
               sd.Station_Name AS Destination_Name,
               fo.Departure AS Departure_Time,
               fd.Arrival AS Arrival_Time,
               fn_stop_minutes(t.Train_ID, fo.Stop_No, 'D') AS Departure_Minutes,
               fn_stop_minutes(t.Train_ID, fd.Stop_No, 'A') AS Arrival_Minutes,
               (SELECT COUNT(*) FROM Timetable x WHERE x.Train_ID = t.Train_ID) AS Stop_Count,
               (SELECT COUNT(*) FROM Coach c WHERE c.Train_ID = t.Train_ID) AS Coach_Count,
               (SELECT COUNT(*) FROM Seat s WHERE s.Train_ID = t.Train_ID) AS Seat_Count,
               fn_train_route_km(t.Train_ID) AS Route_Km,
               fn_train_status(t.Train_ID) AS Status,
               fn_timetable_problem(t.Train_ID) AS Timetable_Problem
        FROM Train t
        LEFT JOIN Timetable fo
               ON fo.Train_ID = t.Train_ID AND fo.Stop_No = fn_first_stop_no(t.Train_ID)
        LEFT JOIN Station so ON so.Station_Code = fo.Station_Code
        LEFT JOIN Timetable fd
               ON fd.Train_ID = t.Train_ID AND fd.Stop_No = fn_last_stop_no(t.Train_ID)
              AND fd.Stop_No > fo.Stop_No
        LEFT JOIN Station sd ON sd.Station_Code = fd.Station_Code
        WHERE (fn_clean_text(p_train_id) IS NULL OR t.Train_ID = p_train_id)
          AND (fn_clean_text(p_type) IS NULL OR t.Train_Type = p_type)
          AND (v_search IS NULL
               OR t.Train_ID LIKE CONCAT('%', v_search, '%')
               OR t.Train_Name LIKE CONCAT('%', v_search, '%')
               OR so.Station_Name LIKE CONCAT('%', v_search, '%')
               OR sd.Station_Name LIKE CONCAT('%', v_search, '%'))
    ) x
    ORDER BY REGEXP_REPLACE(x.Train_ID, '[0-9]+$', ''),
             CAST(REGEXP_SUBSTR(x.Train_ID, '[0-9]+$') AS UNSIGNED),
             x.Train_ID;
END$$

DROP PROCEDURE IF EXISTS sp_train_get$$
CREATE PROCEDURE sp_train_get(IN p_train_id VARCHAR(10))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM Train WHERE Train_ID = p_train_id) THEN
        CALL sp_raise('We could not find that train. It may have been removed.');
    END IF;
    CALL sp_train_list(NULL, NULL, p_train_id);
END$$

DROP PROCEDURE IF EXISTS sp_train_create$$
CREATE PROCEDURE sp_train_create(IN p_train_id VARCHAR(10), IN p_name VARCHAR(100), IN p_type VARCHAR(50))
BEGIN
    DECLARE v_id VARCHAR(10);
    DECLARE v_name VARCHAR(100);
    SET v_id = UPPER(fn_clean_text(p_train_id));
    SET v_name = fn_clean_text(p_name);
    IF v_id IS NULL THEN
        CALL sp_raise('Please enter a train number, for example SE9.');
    END IF;
    IF v_id NOT REGEXP '^[A-Z0-9-]{1,10}$' THEN
        CALL sp_raise('A train number can only use letters, digits and "-" (up to 10 characters).');
    END IF;
    IF v_name IS NULL THEN
        CALL sp_raise('Please enter the train name.');
    END IF;
    IF fn_is_valid_train_type(p_type) = 0 THEN
        CALL sp_raise('Please choose the train type: Express, Local or Night.');
    END IF;
    IF EXISTS (SELECT 1 FROM Train WHERE Train_ID = v_id) THEN
        CALL sp_raise(CONCAT('Train number ', v_id, ' is already in use.'));
    END IF;
    INSERT INTO Train (Train_ID, Train_Name, Train_Type) VALUES (v_id, v_name, p_type);
    SELECT v_id AS Train_ID;
END$$

DROP PROCEDURE IF EXISTS sp_train_update$$
CREATE PROCEDURE sp_train_update(IN p_train_id VARCHAR(10), IN p_name VARCHAR(100), IN p_type VARCHAR(50))
BEGIN
    DECLARE v_name VARCHAR(100);
    SET v_name = fn_clean_text(p_name);
    IF NOT EXISTS (SELECT 1 FROM Train WHERE Train_ID = p_train_id) THEN
        CALL sp_raise('We could not find that train. It may have been removed.');
    END IF;
    IF v_name IS NULL THEN
        CALL sp_raise('Please enter the train name.');
    END IF;
    IF fn_is_valid_train_type(p_type) = 0 THEN
        CALL sp_raise('Please choose the train type: Express, Local or Night.');
    END IF;
    UPDATE Train SET Train_Name = v_name, Train_Type = p_type WHERE Train_ID = p_train_id;
END$$

-- A train that has tickets on record can never be removed.
DROP PROCEDURE IF EXISTS sp_train_delete$$
CREATE PROCEDURE sp_train_delete(IN p_train_id VARCHAR(10))
BEGIN
    DECLARE v_tickets INT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    IF NOT EXISTS (SELECT 1 FROM Train WHERE Train_ID = p_train_id) THEN
        CALL sp_raise('We could not find that train. It may have been removed.');
    END IF;
    SET v_tickets = (SELECT COUNT(*) FROM Booking WHERE Train_ID = p_train_id);
    IF v_tickets > 0 THEN
        CALL sp_raise(CONCAT('Train ', p_train_id, ' has ', v_tickets,
                             ' ticket(s) on record, so it cannot be removed.'));
    END IF;
    START TRANSACTION;
    -- Timetable, Coach and Seat rows are removed by ON DELETE CASCADE.
    DELETE FROM Train WHERE Train_ID = p_train_id;
    COMMIT;
END$$

-- =====================================================================
-- 9. TIMETABLE (stops of a train)
-- =====================================================================

DROP PROCEDURE IF EXISTS sp_timetable_get$$
CREATE PROCEDURE sp_timetable_get(IN p_train_id VARCHAR(10))
BEGIN
    SELECT y.Stop_No,
           y.Station_Code,
           y.Station_Name,
           y.City,
           y.Arrival,
           y.Departure,
           y.Arrival_Minutes DIV 1440 AS Arrival_Day,
           y.Departure_Minutes DIV 1440 AS Departure_Day,
           IF(y.Arrival_Minutes IS NULL OR y.Departure_Minutes IS NULL OR y.Position = y.Total,
              NULL, y.Departure_Minutes - y.Arrival_Minutes) AS Dwell_Minutes,
           y.Leg_Km,
           SUM(y.Leg_Km) OVER (ORDER BY y.Stop_No) AS Km_From_Origin,
           CASE WHEN y.Position = 1 THEN 'Origin'
                WHEN y.Position = y.Total THEN 'Destination'
                ELSE 'Stop' END AS Stop_Role
    FROM (
        SELECT p.Stop_No,
               p.Station_Code,
               s.Station_Name,
               s.City,
               p.Arrival,
               p.Departure,
               p.Position,
               p.Total,
               fn_stop_minutes(p_train_id, p.Stop_No, 'A') AS Arrival_Minutes,
               fn_stop_minutes(p_train_id, p.Stop_No, 'D') AS Departure_Minutes,
               IF(p.Prev_Code IS NULL, 0, fn_track_distance(p.Prev_Code, p.Station_Code)) AS Leg_Km
        FROM (SELECT t.*,
                     LAG(t.Station_Code) OVER (ORDER BY t.Stop_No) AS Prev_Code,
                     ROW_NUMBER() OVER (ORDER BY t.Stop_No) AS Position,
                     COUNT(*) OVER () AS Total
              FROM Timetable t
              WHERE t.Train_ID = p_train_id) p
        JOIN Station s ON s.Station_Code = p.Station_Code
    ) y
    ORDER BY y.Stop_No;
END$$

DROP PROCEDURE IF EXISTS sp_check_stop_times$$
CREATE PROCEDURE sp_check_stop_times(IN p_arrival TIME, IN p_departure TIME)
BEGIN
    IF p_arrival IS NULL AND p_departure IS NULL THEN
        CALL sp_raise('Please enter an arrival time, a departure time, or both.');
    END IF;
    IF (p_arrival IS NOT NULL AND (p_arrival < '00:00:00' OR p_arrival >= '24:00:00'))
       OR (p_departure IS NOT NULL AND (p_departure < '00:00:00' OR p_departure >= '24:00:00')) THEN
        CALL sp_raise('Times must be between 00:00 and 23:59.');
    END IF;
END$$

-- Adds a stop at a position (NULL = after the current final stop).
DROP PROCEDURE IF EXISTS sp_stop_add$$
CREATE PROCEDURE sp_stop_add(IN p_train_id VARCHAR(10), IN p_station VARCHAR(10),
                             IN p_arrival TIME, IN p_departure TIME, IN p_position INT)
BEGIN
    DECLARE v_max INT;
    DECLARE v_pos INT;
    DECLARE v_neighbour_name VARCHAR(100);
    DECLARE EXIT HANDLER FOR 1062
    BEGIN
        ROLLBACK;
        CALL sp_raise('This train already stops at that station.');
    END;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    IF NOT EXISTS (SELECT 1 FROM Train WHERE Train_ID = p_train_id) THEN
        CALL sp_raise('We could not find that train. It may have been removed.');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM Station WHERE Station_Code = p_station) THEN
        CALL sp_raise('Please choose a station for this stop.');
    END IF;
    IF EXISTS (SELECT 1 FROM Timetable WHERE Train_ID = p_train_id AND Station_Code = p_station) THEN
        CALL sp_raise('This train already stops at that station.');
    END IF;
    CALL sp_check_stop_times(p_arrival, p_departure);

    START TRANSACTION;
    SET v_max = (SELECT COALESCE(MAX(Stop_No), 0) FROM Timetable WHERE Train_ID = p_train_id FOR UPDATE);
    IF p_position IS NULL OR p_position < 1 OR p_position > v_max THEN
        SET v_pos = v_max + 1;
    ELSE
        SET v_pos = p_position;
    END IF;

    -- Friendly guidance for the two most common mistakes
    IF v_pos = v_max + 1 AND v_max > 0 THEN
        SET v_neighbour_name = (SELECT s.Station_Name FROM Timetable t JOIN Station s ON s.Station_Code = t.Station_Code
                                WHERE t.Train_ID = p_train_id AND t.Stop_No = v_max AND t.Departure IS NULL);
        IF v_neighbour_name IS NOT NULL THEN
            CALL sp_raise(CONCAT('Before adding a stop after ', v_neighbour_name, ', give ', v_neighbour_name, ' a departure time.'));
        END IF;
    END IF;
    IF v_pos = 1 AND v_max > 0 THEN
        SET v_neighbour_name = (SELECT s.Station_Name FROM Timetable t JOIN Station s ON s.Station_Code = t.Station_Code
                                WHERE t.Train_ID = p_train_id AND t.Stop_No = fn_first_stop_no(p_train_id) AND t.Arrival IS NULL);
        IF v_neighbour_name IS NOT NULL THEN
            CALL sp_raise(CONCAT('Before adding a stop ahead of ', v_neighbour_name, ', give ', v_neighbour_name, ' an arrival time.'));
        END IF;
    END IF;

    UPDATE Timetable SET Stop_No = Stop_No + 1
    WHERE Train_ID = p_train_id AND Stop_No >= v_pos
    ORDER BY Stop_No DESC;

    INSERT INTO Timetable (Train_ID, Stop_No, Station_Code, Arrival, Departure)
    VALUES (p_train_id, v_pos, p_station, p_arrival, p_departure);

    CALL sp_assert_timetable_valid(p_train_id);
    COMMIT;
    SELECT v_pos AS Stop_No;
END$$

DROP PROCEDURE IF EXISTS sp_stop_update$$
CREATE PROCEDURE sp_stop_update(IN p_train_id VARCHAR(10), IN p_stop_no INT, IN p_station VARCHAR(10),
                                IN p_arrival TIME, IN p_departure TIME)
BEGIN
    DECLARE EXIT HANDLER FOR 1062
    BEGIN
        ROLLBACK;
        CALL sp_raise('This train already stops at that station.');
    END;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    IF NOT EXISTS (SELECT 1 FROM Timetable WHERE Train_ID = p_train_id AND Stop_No = p_stop_no) THEN
        CALL sp_raise('We could not find that stop. It may have been removed.');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM Station WHERE Station_Code = p_station) THEN
        CALL sp_raise('Please choose a station for this stop.');
    END IF;
    IF EXISTS (SELECT 1 FROM Timetable
               WHERE Train_ID = p_train_id AND Station_Code = p_station AND Stop_No <> p_stop_no) THEN
        CALL sp_raise('This train already stops at that station.');
    END IF;
    CALL sp_check_stop_times(p_arrival, p_departure);

    START TRANSACTION;
    UPDATE Timetable
    SET Station_Code = p_station, Arrival = p_arrival, Departure = p_departure
    WHERE Train_ID = p_train_id AND Stop_No = p_stop_no;
    CALL sp_assert_timetable_valid(p_train_id);
    COMMIT;
END$$

DROP PROCEDURE IF EXISTS sp_stop_delete$$
CREATE PROCEDURE sp_stop_delete(IN p_train_id VARCHAR(10), IN p_stop_no INT)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    IF NOT EXISTS (SELECT 1 FROM Timetable WHERE Train_ID = p_train_id AND Stop_No = p_stop_no) THEN
        CALL sp_raise('We could not find that stop. It may have been removed.');
    END IF;

    START TRANSACTION;
    DELETE FROM Timetable WHERE Train_ID = p_train_id AND Stop_No = p_stop_no;
    UPDATE Timetable SET Stop_No = Stop_No - 1
    WHERE Train_ID = p_train_id AND Stop_No > p_stop_no
    ORDER BY Stop_No ASC;
    CALL sp_assert_timetable_valid(p_train_id);
    COMMIT;
END$$

-- =====================================================================
-- 10. COACHES AND SEATS
-- =====================================================================

DROP PROCEDURE IF EXISTS sp_coach_list$$
CREATE PROCEDURE sp_coach_list(IN p_train_id VARCHAR(10))
BEGIN
    SELECT c.C_ID,
           c.Coach_Class,
           fn_seat_kind(c.Coach_Class) AS Seat_Kind,
           fn_coach_capacity(c.Coach_Class) AS Capacity,
           (SELECT COUNT(*) FROM Seat s WHERE s.Train_ID = c.Train_ID AND s.C_ID = c.C_ID) AS Seat_Count,
           (SELECT COUNT(*) FROM Booking b WHERE b.Train_ID = c.Train_ID AND b.C_ID = c.C_ID) AS Ticket_Count
    FROM Coach c
    WHERE c.Train_ID = p_train_id
    ORDER BY CAST(REGEXP_SUBSTR(c.C_ID, '[0-9]+') AS UNSIGNED), c.C_ID;
END$$

DROP PROCEDURE IF EXISTS sp_coach_add$$
CREATE PROCEDURE sp_coach_add(IN p_train_id VARCHAR(10), IN p_class VARCHAR(50))
BEGIN
    DECLARE v_id VARCHAR(10);
    DECLARE EXIT HANDLER FOR 1062
    BEGIN
        CALL sp_raise('Another coach was added at the same moment. Please try again.');
    END;
    IF NOT EXISTS (SELECT 1 FROM Train WHERE Train_ID = p_train_id) THEN
        CALL sp_raise('We could not find that train. It may have been removed.');
    END IF;
    IF fn_coach_capacity(p_class) IS NULL THEN
        CALL sp_raise('Please choose a coach class.');
    END IF;
    SET v_id = fn_next_coach_id(p_train_id);
    INSERT INTO Coach (Train_ID, C_ID, Coach_Class) VALUES (p_train_id, v_id, p_class);
    SELECT v_id AS C_ID;
END$$

DROP PROCEDURE IF EXISTS sp_coach_update$$
CREATE PROCEDURE sp_coach_update(IN p_train_id VARCHAR(10), IN p_coach_id VARCHAR(10), IN p_class VARCHAR(50))
BEGIN
    DECLARE v_current VARCHAR(50);
    DECLARE v_seats INT;
    SET v_current = (SELECT Coach_Class FROM Coach WHERE Train_ID = p_train_id AND C_ID = p_coach_id);
    IF v_current IS NULL THEN
        CALL sp_raise('We could not find that coach. It may have been removed.');
    END IF;
    IF fn_coach_capacity(p_class) IS NULL THEN
        CALL sp_raise('Please choose a coach class.');
    END IF;
    IF v_current <> p_class AND EXISTS (
        SELECT 1 FROM Booking b
        WHERE b.Train_ID = p_train_id AND b.C_ID = p_coach_id
          AND fn_trip_status(b.Train_ID, b.Travel_Date) <> 'Completed') THEN
        CALL sp_raise('Tickets are already sold for upcoming trips in this coach, so its class cannot be changed.');
    END IF;
    SET v_seats = (SELECT COUNT(*) FROM Seat WHERE Train_ID = p_train_id AND C_ID = p_coach_id);
    IF v_seats > fn_coach_capacity(p_class) THEN
        CALL sp_raise(CONCAT('A ', p_class, ' coach holds at most ', fn_coach_capacity(p_class),
                             ' places, but this coach has ', v_seats, '.'));
    END IF;
    UPDATE Coach SET Coach_Class = p_class WHERE Train_ID = p_train_id AND C_ID = p_coach_id;
END$$

DROP PROCEDURE IF EXISTS sp_coach_delete$$
CREATE PROCEDURE sp_coach_delete(IN p_train_id VARCHAR(10), IN p_coach_id VARCHAR(10))
BEGIN
    DECLARE v_tickets INT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    IF NOT EXISTS (SELECT 1 FROM Coach WHERE Train_ID = p_train_id AND C_ID = p_coach_id) THEN
        CALL sp_raise('We could not find that coach. It may have been removed.');
    END IF;
    SET v_tickets = (SELECT COUNT(*) FROM Booking WHERE Train_ID = p_train_id AND C_ID = p_coach_id);
    IF v_tickets > 0 THEN
        CALL sp_raise(CONCAT('Coach ', p_coach_id, ' has ', v_tickets, ' ticket(s) on record, so it cannot be removed.'));
    END IF;
    START TRANSACTION;
    -- Seats of the coach are removed by ON DELETE CASCADE.
    DELETE FROM Coach WHERE Train_ID = p_train_id AND C_ID = p_coach_id;
    COMMIT;
END$$

DROP PROCEDURE IF EXISTS sp_seat_list$$
CREATE PROCEDURE sp_seat_list(IN p_train_id VARCHAR(10), IN p_coach_id VARCHAR(10))
BEGIN
    SELECT s.S_ID,
           (SELECT COUNT(*) FROM Booking b
             WHERE b.Train_ID = s.Train_ID AND b.C_ID = s.C_ID AND b.S_ID = s.S_ID) AS Ticket_Count
    FROM Seat s
    WHERE s.Train_ID = p_train_id AND s.C_ID = p_coach_id
    ORDER BY CAST(REGEXP_SUBSTR(s.S_ID, '[0-9]+') AS UNSIGNED), s.S_ID;
END$$

-- Adds p_count new seats/berths to a coach, numbered after the last one.
DROP PROCEDURE IF EXISTS sp_seat_add$$
CREATE PROCEDURE sp_seat_add(IN p_train_id VARCHAR(10), IN p_coach_id VARCHAR(10), IN p_count INT)
BEGIN
    DECLARE v_class VARCHAR(50);
    DECLARE v_existing INT;
    DECLARE v_capacity INT;
    DECLARE v_next INT;
    DECLARE v_added INT DEFAULT 0;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    SET v_class = (SELECT Coach_Class FROM Coach WHERE Train_ID = p_train_id AND C_ID = p_coach_id);
    IF v_class IS NULL THEN
        CALL sp_raise('We could not find that coach. It may have been removed.');
    END IF;
    IF p_count IS NULL OR p_count < 1 THEN
        CALL sp_raise('Please add at least one place.');
    END IF;
    SET v_capacity = fn_coach_capacity(v_class);
    SET v_existing = (SELECT COUNT(*) FROM Seat WHERE Train_ID = p_train_id AND C_ID = p_coach_id);
    IF v_capacity IS NOT NULL AND v_existing + p_count > v_capacity THEN
        CALL sp_raise(CONCAT('A ', v_class, ' coach holds at most ', v_capacity, ' places. This coach already has ',
                             v_existing, ', so you can add up to ', v_capacity - v_existing, ' more.'));
    END IF;

    START TRANSACTION;
    SET v_next = (SELECT COALESCE(MAX(CAST(REGEXP_SUBSTR(S_ID, '[0-9]+') AS UNSIGNED)), 0)
                  FROM Seat WHERE Train_ID = p_train_id AND C_ID = p_coach_id FOR UPDATE) + 1;
    WHILE v_added < p_count DO
        INSERT INTO Seat (Train_ID, C_ID, S_ID)
        VALUES (p_train_id, p_coach_id, fn_format_code(fn_seat_prefix(v_class), v_next + v_added, 2));
        SET v_added = v_added + 1;
    END WHILE;
    COMMIT;
    SELECT v_added AS Seats_Added;
END$$

DROP PROCEDURE IF EXISTS sp_seat_delete$$
CREATE PROCEDURE sp_seat_delete(IN p_train_id VARCHAR(10), IN p_coach_id VARCHAR(10), IN p_seat_id VARCHAR(10))
BEGIN
    DECLARE v_tickets INT;
    IF NOT EXISTS (SELECT 1 FROM Seat WHERE Train_ID = p_train_id AND C_ID = p_coach_id AND S_ID = p_seat_id) THEN
        CALL sp_raise('We could not find that place. It may have been removed.');
    END IF;
    SET v_tickets = (SELECT COUNT(*) FROM Booking
                     WHERE Train_ID = p_train_id AND C_ID = p_coach_id AND S_ID = p_seat_id);
    IF v_tickets > 0 THEN
        CALL sp_raise(CONCAT(p_seat_id, ' has ', v_tickets, ' ticket(s) on record, so it cannot be removed.'));
    END IF;
    DELETE FROM Seat WHERE Train_ID = p_train_id AND C_ID = p_coach_id AND S_ID = p_seat_id;
END$$

-- =====================================================================
-- 11. PASSENGERS
-- =====================================================================

-- Normalises one phone number; returns NULL when the format is invalid.
DROP FUNCTION IF EXISTS fn_normalize_phone$$
CREATE FUNCTION fn_normalize_phone(p_phone VARCHAR(40))
RETURNS VARCHAR(20)
DETERMINISTIC NO SQL
BEGIN
    DECLARE v_phone VARCHAR(40);
    SET v_phone = REGEXP_REPLACE(COALESCE(p_phone, ''), '[ .()-]', '');
    IF v_phone LIKE '+84%' THEN
        SET v_phone = CONCAT('0', SUBSTRING(v_phone, 4));
    END IF;
    IF v_phone REGEXP '^0[0-9]{9}$' THEN
        RETURN v_phone;
    END IF;
    RETURN NULL;
END$$

DROP PROCEDURE IF EXISTS sp_check_passenger_name$$
CREATE PROCEDURE sp_check_passenger_name(IN p_name VARCHAR(100))
BEGIN
    IF p_name IS NULL THEN
        CALL sp_raise('Please enter the passenger''s full name.');
    END IF;
    IF p_name REGEXP '[0-9]' OR p_name NOT REGEXP '^[\\p{L}\\p{M} .''-]+$' THEN
        CALL sp_raise('A name can only contain letters, spaces, apostrophes and hyphens.');
    END IF;
END$$

-- Replaces the phone list of a passenger with a comma-separated list.
-- No transaction control: the caller owns the transaction.
DROP PROCEDURE IF EXISTS sp_passenger_set_phones$$
CREATE PROCEDURE sp_passenger_set_phones(IN p_passenger_id VARCHAR(10), IN p_phones VARCHAR(500))
BEGIN
    DECLARE v_rest VARCHAR(500);
    DECLARE v_item VARCHAR(100);
    DECLARE v_phone VARCHAR(20);
    DECLARE v_comma INT;

    DELETE FROM Passenger_Phone WHERE Passenger_ID = p_passenger_id;
    SET v_rest = COALESCE(p_phones, '');
    WHILE TRIM(v_rest) <> '' DO
        SET v_comma = LOCATE(',', v_rest);
        IF v_comma = 0 THEN
            SET v_item = TRIM(v_rest);
            SET v_rest = '';
        ELSE
            SET v_item = TRIM(LEFT(v_rest, v_comma - 1));
            SET v_rest = SUBSTRING(v_rest, v_comma + 1);
        END IF;
        IF v_item <> '' THEN
            SET v_phone = fn_normalize_phone(v_item);
            IF v_phone IS NULL THEN
                CALL sp_raise(CONCAT('"', LEFT(v_item, 30), '" is not a valid phone number. Use 10 digits starting with 0.'));
            END IF;
            IF NOT EXISTS (SELECT 1 FROM Passenger_Phone
                           WHERE Passenger_ID = p_passenger_id AND Phone_Number = v_phone) THEN
                INSERT INTO Passenger_Phone (Passenger_ID, Phone_Number) VALUES (p_passenger_id, v_phone);
            END IF;
        END IF;
    END WHILE;
END$$

-- Inserts a passenger. No transaction control: the caller owns it.
DROP PROCEDURE IF EXISTS sp_passenger_insert_core$$
CREATE PROCEDURE sp_passenger_insert_core(IN p_name VARCHAR(100), IN p_phones VARCHAR(500),
                                          OUT p_passenger_id VARCHAR(10))
BEGIN
    DECLARE v_name VARCHAR(100);
    SET v_name = fn_clean_text(p_name);
    CALL sp_check_passenger_name(v_name);
    SET p_passenger_id = fn_next_passenger_id();
    INSERT INTO Passenger (Passenger_ID, Passenger_Name) VALUES (p_passenger_id, v_name);
    CALL sp_passenger_set_phones(p_passenger_id, p_phones);
END$$

DROP PROCEDURE IF EXISTS sp_passenger_list$$
CREATE PROCEDURE sp_passenger_list(IN p_search VARCHAR(100))
BEGIN
    DECLARE v_search VARCHAR(100);
    SET v_search = fn_clean_text(p_search);
    SELECT p.Passenger_ID,
           p.Passenger_Name,
           (SELECT GROUP_CONCAT(ph.Phone_Number ORDER BY ph.Phone_Number SEPARATOR ', ')
              FROM Passenger_Phone ph WHERE ph.Passenger_ID = p.Passenger_ID) AS Phones,
           (SELECT COUNT(*) FROM Booking b WHERE b.Passenger_ID = p.Passenger_ID) AS Ticket_Count,
           (SELECT COALESCE(SUM(b.Fare), 0) FROM Booking b WHERE b.Passenger_ID = p.Passenger_ID) AS Total_Spent
    FROM Passenger p
    WHERE v_search IS NULL
       OR p.Passenger_ID LIKE CONCAT('%', v_search, '%')
       OR p.Passenger_Name LIKE CONCAT('%', v_search, '%')
       OR EXISTS (SELECT 1 FROM Passenger_Phone ph
                  WHERE ph.Passenger_ID = p.Passenger_ID
                    AND ph.Phone_Number LIKE CONCAT('%', REGEXP_REPLACE(v_search, '[ .()-]', ''), '%'))
    ORDER BY CAST(REGEXP_SUBSTR(p.Passenger_ID, '[0-9]+') AS UNSIGNED), p.Passenger_ID;
END$$

DROP PROCEDURE IF EXISTS sp_passenger_create$$
CREATE PROCEDURE sp_passenger_create(IN p_name VARCHAR(100), IN p_phones VARCHAR(500))
BEGIN
    DECLARE v_id VARCHAR(10);
    DECLARE EXIT HANDLER FOR 1062
    BEGIN
        ROLLBACK;
        CALL sp_raise('Another passenger was saved at the same moment. Please try again.');
    END;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    START TRANSACTION;
    CALL sp_passenger_insert_core(p_name, p_phones, v_id);
    COMMIT;
    SELECT v_id AS Passenger_ID;
END$$

DROP PROCEDURE IF EXISTS sp_passenger_update$$
CREATE PROCEDURE sp_passenger_update(IN p_passenger_id VARCHAR(10), IN p_name VARCHAR(100), IN p_phones VARCHAR(500))
BEGIN
    DECLARE v_name VARCHAR(100);
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    IF NOT EXISTS (SELECT 1 FROM Passenger WHERE Passenger_ID = p_passenger_id) THEN
        CALL sp_raise('We could not find that passenger. They may have been removed.');
    END IF;
    SET v_name = fn_clean_text(p_name);
    CALL sp_check_passenger_name(v_name);
    START TRANSACTION;
    UPDATE Passenger SET Passenger_Name = v_name WHERE Passenger_ID = p_passenger_id;
    CALL sp_passenger_set_phones(p_passenger_id, p_phones);
    COMMIT;
END$$

DROP PROCEDURE IF EXISTS sp_passenger_delete$$
CREATE PROCEDURE sp_passenger_delete(IN p_passenger_id VARCHAR(10))
BEGIN
    DECLARE v_tickets INT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    IF NOT EXISTS (SELECT 1 FROM Passenger WHERE Passenger_ID = p_passenger_id) THEN
        CALL sp_raise('We could not find that passenger. They may have been removed.');
    END IF;
    SET v_tickets = (SELECT COUNT(*) FROM Booking WHERE Passenger_ID = p_passenger_id);
    IF v_tickets > 0 THEN
        CALL sp_raise(CONCAT('This passenger has ', v_tickets, ' ticket(s) on record, so they cannot be removed.'));
    END IF;
    START TRANSACTION;
    -- Phone numbers are removed by ON DELETE CASCADE.
    DELETE FROM Passenger WHERE Passenger_ID = p_passenger_id;
    COMMIT;
END$$

-- =====================================================================
-- 12. BOOKING: SEARCH, SEAT MAP, FARE SUGGESTION
-- =====================================================================

-- Trains that call at p_from and later at p_to (NULL = first/last stop).
DROP PROCEDURE IF EXISTS sp_trip_search$$
CREATE PROCEDURE sp_trip_search(IN p_from VARCHAR(10), IN p_to VARCHAR(10), IN p_travel_date DATE)
BEGIN
    DECLARE v_from VARCHAR(10);
    DECLARE v_to VARCHAR(10);
    SET v_from = fn_clean_text(p_from);
    SET v_to = fn_clean_text(p_to);
    IF p_travel_date IS NULL THEN
        CALL sp_raise('Please choose a travel date.');
    END IF;
    IF p_travel_date < CURDATE() THEN
        CALL sp_raise('Please choose today or a later date.');
    END IF;
    IF v_from IS NOT NULL AND v_to IS NOT NULL AND v_from = v_to THEN
        CALL sp_raise('The departure and arrival stations must be different.');
    END IF;

    SELECT z.*,
           CASE
               WHEN z.Train_Status = 'No seats set up' THEN 'No seats set up'
               WHEN z.Trip_Departure <= NOW() THEN 'Already departed'
               WHEN z.Free_Seats = 0 THEN 'Sold out'
               ELSE 'Available'
           END AS Availability,
           IF(z.Train_Status = 'On sale' AND z.Trip_Departure > NOW() AND z.Free_Seats > 0, 1, 0) AS Can_Book
    FROM (
        SELECT t.Train_ID,
               t.Train_Name,
               t.Train_Type,
               f.Station_Code AS Board_Code,
               sf.Station_Name AS Board_Name,
               f.Departure AS Board_Time,
               fn_stop_minutes(t.Train_ID, f.Stop_No, 'D') DIV 1440 AS Board_Day,
               a.Station_Code AS Alight_Code,
               sa.Station_Name AS Alight_Name,
               a.Arrival AS Alight_Time,
               fn_stop_minutes(t.Train_ID, a.Stop_No, 'A') DIV 1440 AS Alight_Day,
               fn_stop_minutes(t.Train_ID, a.Stop_No, 'A') - fn_stop_minutes(t.Train_ID, f.Stop_No, 'D') AS Duration_Minutes,
               fn_train_segment_km(t.Train_ID, f.Stop_No, a.Stop_No) AS Distance_Km,
               so.Station_Name AS Origin_Name,
               sd.Station_Name AS Destination_Name,
               fn_trip_departure(t.Train_ID, p_travel_date) AS Trip_Departure,
               fn_trip_arrival(t.Train_ID, p_travel_date) AS Trip_Arrival,
               (SELECT COUNT(*) FROM Seat s WHERE s.Train_ID = t.Train_ID) AS Total_Seats,
               (SELECT COUNT(*) FROM Seat s WHERE s.Train_ID = t.Train_ID)
                 - (SELECT COUNT(*) FROM Booking b WHERE b.Train_ID = t.Train_ID AND b.Travel_Date = p_travel_date) AS Free_Seats,
               fn_train_status(t.Train_ID) AS Train_Status
        FROM Train t
        JOIN Timetable f
          ON f.Train_ID = t.Train_ID
         AND ((v_from IS NOT NULL AND f.Station_Code = v_from)
              OR (v_from IS NULL AND f.Stop_No = fn_first_stop_no(t.Train_ID)))
        JOIN Timetable a
          ON a.Train_ID = t.Train_ID
         AND ((v_to IS NOT NULL AND a.Station_Code = v_to)
              OR (v_to IS NULL AND a.Stop_No = fn_last_stop_no(t.Train_ID)))
         AND a.Stop_No > f.Stop_No
        JOIN Station sf ON sf.Station_Code = f.Station_Code
        JOIN Station sa ON sa.Station_Code = a.Station_Code
        JOIN Timetable o ON o.Train_ID = t.Train_ID AND o.Stop_No = fn_first_stop_no(t.Train_ID)
        JOIN Station so ON so.Station_Code = o.Station_Code
        JOIN Timetable d ON d.Train_ID = t.Train_ID AND d.Stop_No = fn_last_stop_no(t.Train_ID)
        JOIN Station sd ON sd.Station_Code = d.Station_Code
    ) z
    WHERE z.Train_Status IN ('On sale', 'No seats set up')
    ORDER BY z.Board_Day, z.Board_Time, z.Train_ID;
END$$

-- Every seat of a train, with whether it is already sold on a date.
DROP PROCEDURE IF EXISTS sp_seat_map$$
CREATE PROCEDURE sp_seat_map(IN p_train_id VARCHAR(10), IN p_travel_date DATE)
BEGIN
    SELECT c.C_ID,
           c.Coach_Class,
           fn_seat_kind(c.Coach_Class) AS Seat_Kind,
           s.S_ID,
           EXISTS (SELECT 1 FROM Booking b
                   WHERE b.Train_ID = s.Train_ID AND b.Travel_Date = p_travel_date
                     AND b.C_ID = s.C_ID AND b.S_ID = s.S_ID) AS Is_Booked,
           (SELECT b.Booking_ID FROM Booking b
             WHERE b.Train_ID = s.Train_ID AND b.Travel_Date = p_travel_date
               AND b.C_ID = s.C_ID AND b.S_ID = s.S_ID) AS Booking_ID
    FROM Seat s
    JOIN Coach c ON c.Train_ID = s.Train_ID AND c.C_ID = s.C_ID
    WHERE s.Train_ID = p_train_id
    ORDER BY CAST(REGEXP_SUBSTR(c.C_ID, '[0-9]+') AS UNSIGNED), c.C_ID,
             CAST(REGEXP_SUBSTR(s.S_ID, '[0-9]+') AS UNSIGNED), s.S_ID;
END$$

-- Suggested fare: the fare most often charged for the same coach class
-- on the same train (latest one on a tie). NULL when nothing was sold yet.
DROP FUNCTION IF EXISTS fn_suggest_fare$$
CREATE FUNCTION fn_suggest_fare(p_train_id VARCHAR(10), p_coach_id VARCHAR(10))
RETURNS DECIMAL(12,2)
NOT DETERMINISTIC READS SQL DATA
BEGIN
    DECLARE v_class VARCHAR(50);
    SET v_class = (SELECT Coach_Class FROM Coach WHERE Train_ID = p_train_id AND C_ID = p_coach_id);
    IF v_class IS NULL THEN
        RETURN NULL;
    END IF;
    RETURN (
        SELECT b.Fare
        FROM Booking b
        JOIN Coach c ON c.Train_ID = b.Train_ID AND c.C_ID = b.C_ID
        WHERE b.Train_ID = p_train_id AND c.Coach_Class = v_class
        GROUP BY b.Fare
        ORDER BY COUNT(*) DESC, MAX(b.Travel_Date) DESC, MAX(b.Booking_ID) DESC
        LIMIT 1);
END$$

-- =====================================================================
-- 13. BOOKING: CREATE / LIST / CHANGE / CANCEL
-- =====================================================================

-- Common checks for a seat on a trip (used by create and change).
DROP PROCEDURE IF EXISTS sp_check_trip_seat$$
CREATE PROCEDURE sp_check_trip_seat(IN p_train_id VARCHAR(10), IN p_travel_date DATE,
                                    IN p_coach_id VARCHAR(10), IN p_seat_id VARCHAR(10),
                                    IN p_fare DECIMAL(12,2))
BEGIN
    DECLARE v_departure DATETIME;
    IF p_travel_date IS NULL THEN
        CALL sp_raise('Please choose a travel date.');
    END IF;
    IF fn_train_status(p_train_id) <> 'On sale' THEN
        CALL sp_raise(CONCAT('Tickets for train ', p_train_id, ' are not on sale.'));
    END IF;
    SET v_departure = fn_trip_departure(p_train_id, p_travel_date);
    IF p_travel_date < CURDATE() OR v_departure <= NOW() THEN
        CALL sp_raise('This train has already departed on the chosen date. Please choose a later date.');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM Seat WHERE Train_ID = p_train_id AND C_ID = p_coach_id AND S_ID = p_seat_id) THEN
        CALL sp_raise('Please choose a seat on this train.');
    END IF;
    IF p_fare IS NULL THEN
        CALL sp_raise('Please enter the fare.');
    END IF;
    IF p_fare < 0 THEN
        CALL sp_raise('The fare cannot be negative.');
    END IF;
    IF p_fare > 100000000 THEN
        CALL sp_raise('The fare looks too high. Please check it.');
    END IF;
END$$

-- Books a seat and records its payment in one transaction.
-- Pass p_passenger_id for an existing passenger, or leave it NULL and
-- pass p_new_name / p_new_phones to register a new passenger as well.
DROP PROCEDURE IF EXISTS sp_booking_create$$
CREATE PROCEDURE sp_booking_create(IN p_passenger_id VARCHAR(10),
                                   IN p_new_name VARCHAR(100), IN p_new_phones VARCHAR(500),
                                   IN p_train_id VARCHAR(10), IN p_travel_date DATE,
                                   IN p_coach_id VARCHAR(10), IN p_seat_id VARCHAR(10),
                                   IN p_fare DECIMAL(12,2), IN p_method VARCHAR(30))
BEGIN
    DECLARE v_passenger VARCHAR(10);
    DECLARE v_booking VARCHAR(10);
    DECLARE v_taken INT;
    DECLARE v_lock VARCHAR(10);
    DECLARE EXIT HANDLER FOR 1062
    BEGIN
        ROLLBACK;
        CALL sp_raise('Sorry, this seat was sold a moment ago. Please choose another seat.');
    END;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    IF fn_clean_text(p_passenger_id) IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM Passenger WHERE Passenger_ID = p_passenger_id) THEN
        CALL sp_raise('We could not find that passenger. They may have been removed.');
    END IF;
    IF fn_clean_text(p_passenger_id) IS NULL AND fn_clean_text(p_new_name) IS NULL THEN
        CALL sp_raise('Please choose a passenger or enter a new passenger''s name.');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM Train WHERE Train_ID = p_train_id) THEN
        CALL sp_raise('We could not find that train. It may have been removed.');
    END IF;
    CALL sp_check_trip_seat(p_train_id, p_travel_date, p_coach_id, p_seat_id, p_fare);
    IF fn_is_valid_payment_method(p_method) = 0 THEN
        CALL sp_raise('Please choose how the ticket is paid.');
    END IF;

    START TRANSACTION;
    -- Lock the seat so two staff members cannot sell it at the same time
    SELECT S_ID INTO v_lock FROM Seat
    WHERE Train_ID = p_train_id AND C_ID = p_coach_id AND S_ID = p_seat_id
    FOR UPDATE;
    SET v_taken = (SELECT COUNT(*) FROM Booking
                   WHERE Train_ID = p_train_id AND Travel_Date = p_travel_date
                     AND C_ID = p_coach_id AND S_ID = p_seat_id);
    IF v_taken > 0 THEN
        CALL sp_raise(CONCAT('Sorry, ', p_seat_id, ' in coach ', p_coach_id,
                             ' is already sold for this trip. Please choose another seat.'));
    END IF;

    IF fn_clean_text(p_passenger_id) IS NULL THEN
        CALL sp_passenger_insert_core(p_new_name, p_new_phones, v_passenger);
    ELSE
        SET v_passenger = p_passenger_id;
    END IF;

    SET v_booking = fn_next_booking_id();
    INSERT INTO Booking (Booking_ID, Passenger_ID, Train_ID, Travel_Date, C_ID, S_ID, Fare)
    VALUES (v_booking, v_passenger, p_train_id, p_travel_date, p_coach_id, p_seat_id, p_fare);
    INSERT INTO Payment (Pay_ID, Pay_Date, Method, Booking_ID)
    VALUES (fn_next_payment_id(), NOW(), p_method, v_booking);
    COMMIT;

    SELECT v_booking AS Booking_ID, v_passenger AS Passenger_ID;
END$$

DROP PROCEDURE IF EXISTS sp_booking_list$$
CREATE PROCEDURE sp_booking_list(IN p_search VARCHAR(100), IN p_status VARCHAR(20),
                                 IN p_train_id VARCHAR(10), IN p_passenger_id VARCHAR(10))
BEGIN
    DECLARE v_search VARCHAR(100);
    SET v_search = fn_clean_text(p_search);
    SELECT x.*
    FROM (
        SELECT b.Booking_ID,
               b.Passenger_ID,
               p.Passenger_Name,
               b.Train_ID,
               t.Train_Name,
               b.Travel_Date,
               fn_trip_departure(b.Train_ID, b.Travel_Date) AS Departure_At,
               so.Station_Name AS Origin_Name,
               sd.Station_Name AS Destination_Name,
               b.C_ID,
               c.Coach_Class,
               fn_seat_kind(c.Coach_Class) AS Seat_Kind,
               b.S_ID,
               b.Fare,
               pay.Method AS Pay_Method,
               pay.Pay_Date,
               fn_trip_status(b.Train_ID, b.Travel_Date) AS Status
        FROM Booking b
        JOIN Passenger p ON p.Passenger_ID = b.Passenger_ID
        JOIN Train t ON t.Train_ID = b.Train_ID
        JOIN Coach c ON c.Train_ID = b.Train_ID AND c.C_ID = b.C_ID
        LEFT JOIN Payment pay ON pay.Booking_ID = b.Booking_ID
        LEFT JOIN Timetable o ON o.Train_ID = b.Train_ID AND o.Stop_No = fn_first_stop_no(b.Train_ID)
        LEFT JOIN Station so ON so.Station_Code = o.Station_Code
        LEFT JOIN Timetable d ON d.Train_ID = b.Train_ID AND d.Stop_No = fn_last_stop_no(b.Train_ID)
                             AND d.Stop_No > o.Stop_No
        LEFT JOIN Station sd ON sd.Station_Code = d.Station_Code
        WHERE (fn_clean_text(p_train_id) IS NULL OR b.Train_ID = p_train_id)
          AND (fn_clean_text(p_passenger_id) IS NULL OR b.Passenger_ID = p_passenger_id)
          AND (v_search IS NULL
               OR b.Booking_ID LIKE CONCAT('%', v_search, '%')
               OR b.Passenger_ID LIKE CONCAT('%', v_search, '%')
               OR p.Passenger_Name LIKE CONCAT('%', v_search, '%')
               OR b.Train_ID LIKE CONCAT('%', v_search, '%')
               OR EXISTS (SELECT 1 FROM Passenger_Phone ph
                          WHERE ph.Passenger_ID = b.Passenger_ID
                            AND ph.Phone_Number LIKE CONCAT('%', v_search, '%')))
    ) x
    WHERE fn_clean_text(p_status) IS NULL OR x.Status = p_status
    ORDER BY x.Travel_Date DESC, x.Booking_ID DESC;
END$$

-- Everything printed on a ticket.
DROP PROCEDURE IF EXISTS sp_booking_get$$
CREATE PROCEDURE sp_booking_get(IN p_booking_id VARCHAR(10))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM Booking WHERE Booking_ID = p_booking_id) THEN
        CALL sp_raise('We could not find that ticket. It may have been cancelled.');
    END IF;
    SELECT b.Booking_ID,
           b.Passenger_ID,
           p.Passenger_Name,
           (SELECT GROUP_CONCAT(ph.Phone_Number ORDER BY ph.Phone_Number SEPARATOR ', ')
              FROM Passenger_Phone ph WHERE ph.Passenger_ID = b.Passenger_ID) AS Phones,
           b.Train_ID,
           t.Train_Name,
           t.Train_Type,
           b.Travel_Date,
           so.Station_Name AS Origin_Name,
           sd.Station_Name AS Destination_Name,
           fn_trip_departure(b.Train_ID, b.Travel_Date) AS Departure_At,
           fn_trip_arrival(b.Train_ID, b.Travel_Date) AS Arrival_At,
           fn_train_route_km(b.Train_ID) AS Route_Km,
           b.C_ID,
           c.Coach_Class,
           fn_seat_kind(c.Coach_Class) AS Seat_Kind,
           b.S_ID,
           b.Fare,
           pay.Pay_ID,
           pay.Method AS Pay_Method,
           pay.Pay_Date,
           fn_trip_status(b.Train_ID, b.Travel_Date) AS Status
    FROM Booking b
    JOIN Passenger p ON p.Passenger_ID = b.Passenger_ID
    JOIN Train t ON t.Train_ID = b.Train_ID
    JOIN Coach c ON c.Train_ID = b.Train_ID AND c.C_ID = b.C_ID
    LEFT JOIN Payment pay ON pay.Booking_ID = b.Booking_ID
    LEFT JOIN Timetable o ON o.Train_ID = b.Train_ID AND o.Stop_No = fn_first_stop_no(b.Train_ID)
    LEFT JOIN Station so ON so.Station_Code = o.Station_Code
    LEFT JOIN Timetable d ON d.Train_ID = b.Train_ID AND d.Stop_No = fn_last_stop_no(b.Train_ID)
                         AND d.Stop_No > o.Stop_No
    LEFT JOIN Station sd ON sd.Station_Code = d.Station_Code
    WHERE b.Booking_ID = p_booking_id;
END$$

-- Changes the date, seat and/or fare of an upcoming ticket.
DROP PROCEDURE IF EXISTS sp_booking_update$$
CREATE PROCEDURE sp_booking_update(IN p_booking_id VARCHAR(10), IN p_travel_date DATE,
                                   IN p_coach_id VARCHAR(10), IN p_seat_id VARCHAR(10),
                                   IN p_fare DECIMAL(12,2))
BEGIN
    DECLARE v_train VARCHAR(10);
    DECLARE v_old_date DATE;
    DECLARE v_taken INT;
    DECLARE v_lock VARCHAR(10);
    DECLARE EXIT HANDLER FOR 1062
    BEGIN
        ROLLBACK;
        CALL sp_raise('Sorry, this seat was sold a moment ago. Please choose another seat.');
    END;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    SELECT Train_ID, Travel_Date INTO v_train, v_old_date FROM Booking WHERE Booking_ID = p_booking_id;
    IF v_train IS NULL THEN
        CALL sp_raise('We could not find that ticket. It may have been cancelled.');
    END IF;
    IF fn_trip_status(v_train, v_old_date) <> 'Upcoming' THEN
        CALL sp_raise('This trip has already departed, so the ticket can no longer be changed.');
    END IF;
    CALL sp_check_trip_seat(v_train, p_travel_date, p_coach_id, p_seat_id, p_fare);

    START TRANSACTION;
    SELECT S_ID INTO v_lock FROM Seat
    WHERE Train_ID = v_train AND C_ID = p_coach_id AND S_ID = p_seat_id
    FOR UPDATE;
    SET v_taken = (SELECT COUNT(*) FROM Booking
                   WHERE Train_ID = v_train AND Travel_Date = p_travel_date
                     AND C_ID = p_coach_id AND S_ID = p_seat_id
                     AND Booking_ID <> p_booking_id);
    IF v_taken > 0 THEN
        CALL sp_raise(CONCAT('Sorry, ', p_seat_id, ' in coach ', p_coach_id,
                             ' is already sold for that trip. Please choose another seat.'));
    END IF;
    UPDATE Booking
    SET Travel_Date = p_travel_date, C_ID = p_coach_id, S_ID = p_seat_id, Fare = p_fare
    WHERE Booking_ID = p_booking_id;
    COMMIT;
END$$

-- Cancels an upcoming ticket: removes its payment and the booking in one
-- transaction, which releases the seat for re-booking.
DROP PROCEDURE IF EXISTS sp_booking_cancel$$
CREATE PROCEDURE sp_booking_cancel(IN p_booking_id VARCHAR(10))
BEGIN
    DECLARE v_train VARCHAR(10);
    DECLARE v_date DATE;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    SELECT Train_ID, Travel_Date INTO v_train, v_date FROM Booking WHERE Booking_ID = p_booking_id;
    IF v_train IS NULL THEN
        CALL sp_raise('We could not find that ticket. It may already be cancelled.');
    END IF;
    IF fn_trip_status(v_train, v_date) <> 'Upcoming' THEN
        CALL sp_raise('This trip has already departed, so the ticket can no longer be cancelled.');
    END IF;

    START TRANSACTION;
    DELETE FROM Payment WHERE Booking_ID = p_booking_id;
    DELETE FROM Booking WHERE Booking_ID = p_booking_id;
    COMMIT;
END$$

-- =====================================================================
-- 14. DASHBOARD
-- =====================================================================

DROP PROCEDURE IF EXISTS sp_dashboard_summary$$
CREATE PROCEDURE sp_dashboard_summary()
BEGIN
    SELECT (SELECT COUNT(*) FROM Booking) AS Tickets_Sold,
           (SELECT COUNT(*) FROM Booking b WHERE fn_trip_status(b.Train_ID, b.Travel_Date) = 'Upcoming') AS Upcoming_Tickets,
           (SELECT COALESCE(SUM(Fare), 0) FROM Booking) AS Revenue,
           (SELECT COUNT(*) FROM Train) AS Trains,
           (SELECT COUNT(*) FROM Train t WHERE fn_train_status(t.Train_ID) = 'On sale') AS Trains_On_Sale,
           (SELECT COUNT(*) FROM Station) AS Stations,
           (SELECT COUNT(*) FROM Station_Connection) AS Tracks,
           (SELECT COALESCE(SUM(Length_km), 0) FROM Station_Connection) AS Network_Km,
           (SELECT COUNT(*) FROM Passenger) AS Passengers;
END$$

-- Trains leaving their first station on a date, in departure order.
DROP PROCEDURE IF EXISTS sp_dashboard_departures$$
CREATE PROCEDURE sp_dashboard_departures(IN p_date DATE)
BEGIN
    DECLARE v_date DATE;
    SET v_date = COALESCE(p_date, CURDATE());
    SELECT x.*,
           CASE WHEN x.Departure_At > NOW() THEN 'Scheduled'
                WHEN x.Arrival_At IS NOT NULL AND x.Arrival_At > NOW() THEN 'On the way'
                ELSE 'Arrived' END AS Board_Status
    FROM (
        SELECT t.Train_ID,
               t.Train_Name,
               t.Train_Type,
               so.Station_Name AS Origin_Name,
               sd.Station_Name AS Destination_Name,
               o.Departure AS Departure_Time,
               d.Arrival AS Arrival_Time,
               fn_stop_minutes(t.Train_ID, d.Stop_No, 'A') DIV 1440 AS Arrival_Day,
               fn_trip_departure(t.Train_ID, v_date) AS Departure_At,
               fn_trip_arrival(t.Train_ID, v_date) AS Arrival_At,
               (SELECT COUNT(*) FROM Seat s WHERE s.Train_ID = t.Train_ID) AS Total_Seats,
               (SELECT COUNT(*) FROM Booking b WHERE b.Train_ID = t.Train_ID AND b.Travel_Date = v_date) AS Seats_Sold
        FROM Train t
        JOIN Timetable o ON o.Train_ID = t.Train_ID AND o.Stop_No = fn_first_stop_no(t.Train_ID)
        JOIN Timetable d ON d.Train_ID = t.Train_ID AND d.Stop_No = fn_last_stop_no(t.Train_ID)
                        AND d.Stop_No > o.Stop_No
        JOIN Station so ON so.Station_Code = o.Station_Code
        JOIN Station sd ON sd.Station_Code = d.Station_Code
        WHERE fn_timetable_problem(t.Train_ID) IS NULL
    ) x
    ORDER BY x.Departure_At, x.Train_ID;
END$$

-- =====================================================================
-- 15. REPORTS (the project's required queries, in parameterised form)
-- =====================================================================

-- Query 1: every station where a train stops, in calling order.
DROP PROCEDURE IF EXISTS sp_report_train_stops$$
CREATE PROCEDURE sp_report_train_stops(IN p_train_id VARCHAR(10))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM Train WHERE Train_ID = p_train_id) THEN
        CALL sp_raise('Please choose a train.');
    END IF;
    CALL sp_timetable_get(p_train_id);
END$$

-- Query 2: trains departing a station between two clock times.
DROP PROCEDURE IF EXISTS sp_report_departures_between$$
CREATE PROCEDURE sp_report_departures_between(IN p_station VARCHAR(10), IN p_from_time TIME, IN p_to_time TIME)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM Station WHERE Station_Code = p_station) THEN
        CALL sp_raise('Please choose a station.');
    END IF;
    IF p_from_time IS NULL OR p_to_time IS NULL THEN
        CALL sp_raise('Please enter both the start and the end of the time window.');
    END IF;
    IF p_from_time > p_to_time THEN
        CALL sp_raise('The start of the time window must be before its end.');
    END IF;
    SELECT tt.Train_ID,
           t.Train_Name,
           t.Train_Type,
           tt.Departure,
           sd.Station_Name AS Destination_Name,
           COUNT(*) OVER () AS Number_Of_Trains
    FROM Timetable tt
    JOIN Train t ON t.Train_ID = tt.Train_ID
    LEFT JOIN Timetable d ON d.Train_ID = tt.Train_ID AND d.Stop_No = fn_last_stop_no(tt.Train_ID)
                         AND d.Stop_No > tt.Stop_No
    LEFT JOIN Station sd ON sd.Station_Code = d.Station_Code
    WHERE tt.Station_Code = p_station
      AND tt.Departure >= p_from_time
      AND tt.Departure <= p_to_time
    ORDER BY tt.Departure, tt.Train_ID;
END$$

-- Query 3: total route distance of each train.
DROP PROCEDURE IF EXISTS sp_report_route_distances$$
CREATE PROCEDURE sp_report_route_distances()
BEGIN
    SELECT x.Train_ID, x.Train_Name, x.Origin_Name, x.Destination_Name, x.Stop_Count, x.Total_Distance_Km
    FROM (
        SELECT t.Train_ID,
               t.Train_Name,
               so.Station_Name AS Origin_Name,
               sd.Station_Name AS Destination_Name,
               (SELECT COUNT(*) FROM Timetable x WHERE x.Train_ID = t.Train_ID) AS Stop_Count,
               fn_train_route_km(t.Train_ID) AS Total_Distance_Km
        FROM Train t
        JOIN Timetable o ON o.Train_ID = t.Train_ID AND o.Stop_No = fn_first_stop_no(t.Train_ID)
        JOIN Timetable d ON d.Train_ID = t.Train_ID AND d.Stop_No = fn_last_stop_no(t.Train_ID)
                        AND d.Stop_No > o.Stop_No
        JOIN Station so ON so.Station_Code = o.Station_Code
        JOIN Station sd ON sd.Station_Code = d.Station_Code
    ) x
    ORDER BY x.Total_Distance_Km DESC, x.Train_ID;
END$$

-- Query 4: trains that call at every station of the line between two
-- stations (relational division over the shortest track path).
DROP PROCEDURE IF EXISTS sp_report_trains_covering_line$$
CREATE PROCEDURE sp_report_trains_covering_line(IN p_from VARCHAR(10), IN p_to VARCHAR(10))
BEGIN
    DECLARE v_path VARCHAR(4000);
    DECLARE v_station_count INT;
    DECLARE v_line VARCHAR(2000);
    IF NOT EXISTS (SELECT 1 FROM Station WHERE Station_Code = p_from)
       OR NOT EXISTS (SELECT 1 FROM Station WHERE Station_Code = p_to) THEN
        CALL sp_raise('Please choose both ends of the line.');
    END IF;
    IF p_from = p_to THEN
        CALL sp_raise('The two ends of the line must be different stations.');
    END IF;
    SET v_path = fn_track_path(p_from, p_to);
    IF v_path IS NULL THEN
        CALL sp_raise('These two stations are not connected by railway track.');
    END IF;
    SET v_station_count = (SELECT COUNT(*) FROM Station
                           WHERE LOCATE(CONCAT(',', Station_Code, ','), v_path) > 0);
    SET v_line = (SELECT GROUP_CONCAT(s.Station_Name ORDER BY LOCATE(CONCAT(',', s.Station_Code, ','), v_path) SEPARATOR ' > ')
                  FROM Station s WHERE LOCATE(CONCAT(',', s.Station_Code, ','), v_path) > 0);
    SELECT t.Train_ID, t.Train_Name, t.Train_Type, v_station_count AS Stations_On_Line, v_line AS Line
    FROM Train t
    WHERE t.Train_ID IN (
        SELECT tt.Train_ID
        FROM Timetable tt
        WHERE LOCATE(CONCAT(',', tt.Station_Code, ','), v_path) > 0
        GROUP BY tt.Train_ID
        HAVING COUNT(DISTINCT tt.Station_Code) = v_station_count)
    ORDER BY t.Train_ID;
END$$

-- The line itself, so the screen can show it even when no train matches.
DROP FUNCTION IF EXISTS fn_line_description$$
CREATE FUNCTION fn_line_description(p_from VARCHAR(10), p_to VARCHAR(10))
RETURNS VARCHAR(2000)
NOT DETERMINISTIC READS SQL DATA
BEGIN
    DECLARE v_path VARCHAR(4000);
    SET v_path = fn_track_path(p_from, p_to);
    IF v_path IS NULL THEN
        RETURN NULL;
    END IF;
    RETURN (SELECT GROUP_CONCAT(s.Station_Name ORDER BY LOCATE(CONCAT(',', s.Station_Code, ','), v_path) SEPARATOR ' > ')
            FROM Station s WHERE LOCATE(CONCAT(',', s.Station_Code, ','), v_path) > 0);
END$$

-- Query 5: passengers holding two or more bookings on the same date.
DROP PROCEDURE IF EXISTS sp_report_passengers_multiple_bookings$$
CREATE PROCEDURE sp_report_passengers_multiple_bookings()
BEGIN
    SELECT b.Passenger_ID,
           p.Passenger_Name,
           b.Travel_Date,
           COUNT(*) AS Tickets,
           GROUP_CONCAT(b.Booking_ID ORDER BY b.Booking_ID SEPARATOR ', ') AS Ticket_Numbers
    FROM Booking b
    JOIN Passenger p ON p.Passenger_ID = b.Passenger_ID
    GROUP BY b.Passenger_ID, p.Passenger_Name, b.Travel_Date
    HAVING COUNT(*) >= 2
    ORDER BY b.Travel_Date, b.Passenger_ID;
END$$

-- Query 6: trains of a type, ordered by name.
DROP PROCEDURE IF EXISTS sp_report_trains_by_type$$
CREATE PROCEDURE sp_report_trains_by_type(IN p_type VARCHAR(50))
BEGIN
    IF fn_is_valid_train_type(p_type) = 0 THEN
        CALL sp_raise('Please choose a train type.');
    END IF;
    SELECT Train_ID, Train_Name, Train_Type
    FROM Train
    WHERE Train_Type = p_type
    ORDER BY Train_Name ASC;
END$$

-- Query 7: stations located in a city.
DROP PROCEDURE IF EXISTS sp_report_stations_in_city$$
CREATE PROCEDURE sp_report_stations_in_city(IN p_city VARCHAR(100))
BEGIN
    IF fn_clean_text(p_city) IS NULL THEN
        CALL sp_raise('Please choose a city.');
    END IF;
    SELECT Station_Code, Station_Name, City
    FROM Station
    WHERE City = p_city
    ORDER BY Station_Name;
END$$

-- Query 8: average fare for each train type.
DROP PROCEDURE IF EXISTS sp_report_average_fare_by_type$$
CREATE PROCEDURE sp_report_average_fare_by_type()
BEGIN
    SELECT t.Train_Type,
           COUNT(*) AS Tickets,
           ROUND(AVG(b.Fare), 0) AS Average_Fare
    FROM Train t
    JOIN Booking b ON t.Train_ID = b.Train_ID
    GROUP BY t.Train_Type
    ORDER BY t.Train_Type;
END$$

-- Query 9: coaches of a train whose class contains a keyword (e.g. Sleeper).
DROP PROCEDURE IF EXISTS sp_report_coaches_of_class$$
CREATE PROCEDURE sp_report_coaches_of_class(IN p_train_id VARCHAR(10), IN p_keyword VARCHAR(50))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM Train WHERE Train_ID = p_train_id) THEN
        CALL sp_raise('Please choose a train.');
    END IF;
    SELECT c.Train_ID,
           c.C_ID,
           c.Coach_Class,
           (SELECT COUNT(*) FROM Seat s WHERE s.Train_ID = c.Train_ID AND s.C_ID = c.C_ID) AS Seat_Count
    FROM Coach c
    WHERE c.Train_ID = p_train_id
      AND (fn_clean_text(p_keyword) IS NULL OR c.Coach_Class LIKE CONCAT('%', p_keyword, '%'))
    ORDER BY c.C_ID;
END$$

-- Query 10: the longest track section(s).
DROP PROCEDURE IF EXISTS sp_report_longest_track$$
CREATE PROCEDURE sp_report_longest_track()
BEGIN
    SELECT c.Track_ID,
           f.Station_Name AS From_Name,
           t.Station_Name AS To_Name,
           c.Length_km AS Max_Track_Length
    FROM Station_Connection c
    JOIN Station f ON f.Station_Code = c.From_Station
    JOIN Station t ON t.Station_Code = c.To_Station
    WHERE c.Length_km = (SELECT MAX(Length_km) FROM Station_Connection)
    ORDER BY c.Track_ID;
END$$

-- Query 11: passengers who have a booking with a fare above an amount.
DROP PROCEDURE IF EXISTS sp_report_passengers_fare_above$$
CREATE PROCEDURE sp_report_passengers_fare_above(IN p_amount DECIMAL(12,2))
BEGIN
    IF p_amount IS NULL OR p_amount < 0 THEN
        CALL sp_raise('Please enter an amount of 0 or more.');
    END IF;
    SELECT p.Passenger_ID,
           p.Passenger_Name,
           COUNT(*) AS Tickets_Above,
           MAX(b.Fare) AS Highest_Fare
    FROM Passenger p
    JOIN Booking b ON p.Passenger_ID = b.Passenger_ID
    WHERE b.Fare > p_amount
    GROUP BY p.Passenger_ID, p.Passenger_Name
    ORDER BY Highest_Fare DESC, p.Passenger_ID;
END$$

-- Query 12: number of stations in each city.
DROP PROCEDURE IF EXISTS sp_report_stations_per_city$$
CREATE PROCEDURE sp_report_stations_per_city()
BEGIN
    SELECT City, COUNT(Station_Code) AS Number_Of_Stations
    FROM Station
    GROUP BY City
    ORDER BY Number_Of_Stations DESC, City;
END$$

-- Statistics on booked tickets and seats per train.
DROP PROCEDURE IF EXISTS sp_report_ticket_sales_by_train$$
CREATE PROCEDURE sp_report_ticket_sales_by_train()
BEGIN
    SELECT t.Train_ID,
           t.Train_Name,
           t.Train_Type,
           (SELECT COUNT(*) FROM Seat s WHERE s.Train_ID = t.Train_ID) AS Seats,
           COUNT(b.Booking_ID) AS Tickets_Sold,
           COUNT(DISTINCT b.Travel_Date) AS Trips_With_Sales,
           COALESCE(SUM(b.Fare), 0) AS Revenue,
           ROUND(AVG(b.Fare), 0) AS Average_Fare
    FROM Train t
    JOIN Booking b ON b.Train_ID = t.Train_ID
    GROUP BY t.Train_ID, t.Train_Name, t.Train_Type
    ORDER BY Revenue DESC, t.Train_ID;
END$$

DELIMITER ;
