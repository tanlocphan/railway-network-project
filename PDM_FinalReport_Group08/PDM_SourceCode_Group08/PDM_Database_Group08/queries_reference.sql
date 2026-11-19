-- =========================================================================
-- QUERIES.SQL (IT079IU - Principles of Database Management)
-- =========================================================================
-- Query 1: List every station where train SE7 stops, in the order it calls at them
-- Multi-table Join + ORDER BY
SELECT S.Station_Name
FROM Station S
JOIN Timetable T
    ON S.Station_Code = T.Station_Code
WHERE T.Train_ID = 'SE7'
ORDER BY T.Stop_No ASC;

-- Query 2: How many trains depart from Saigon station between 06:00 and 12:00?
-- Aggregation COUNT
SELECT COUNT(Train_ID) AS Number_Of_Trains
FROM Timetable
WHERE Station_Code = 'SGN'
  AND Departure >= '06:00:00'
  AND Departure <= '12:00:00';

-- Query 3: For each train, the total distance of its route.
-- Self-Join + Multi-table Join + GROUP BY
SELECT
    T1.Train_ID,
    SUM(SC.Length_km) AS Total_Distance
FROM Timetable T1
JOIN Timetable T2
    ON T1.Train_ID = T2.Train_ID
   AND T1.Stop_No + 1 = T2.Stop_No
JOIN Station_Connection SC
    ON T1.Station_Code = SC.From_Station
   AND T2.Station_Code = SC.To_Station
GROUP BY T1.Train_ID;


-- Query 4: Which trains call at every station on the Saigon–Nha Trang line?
-- Aggregation with GROUP BY and HAVING
SELECT Train_ID
FROM Timetable
WHERE Station_Code IN ('SGN', 'BHO', 'BTT', 'TCV', 'NTR')
GROUP BY Train_ID
HAVING COUNT(DISTINCT Station_Code) = 5;

-- Query 5: Find passengers holding two bookings on the same date
-- Self-Join + Multi-table Join
SELECT DISTINCT P.Passenger_ID, P.Passenger_Name, B1.Travel_Date
FROM Booking B1
JOIN Booking B2
    ON B1.Passenger_ID = B2.Passenger_ID
   AND B1.Travel_Date = B2.Travel_Date
   AND B1.Booking_ID <> B2.Booking_ID
JOIN Passenger P
    ON B1.Passenger_ID = P.Passenger_ID;


-- Query 6: List all stations and the total number of trains stopping at each station, including stations with zero stops.
-- OUTER JOIN (LEFT JOIN) + GROUP BY
SELECT 
    S.Station_Code, 
    S.Station_Name, 
    S.City,
    COUNT(T.Train_ID) AS Total_Train_Stops
FROM Station S
LEFT JOIN Timetable T 
    ON S.Station_Code = T.Station_Code
GROUP BY S.Station_Code, S.Station_Name, S.City
ORDER BY Total_Train_Stops DESC;

-- Query 7: Find the names and cities of stations that appear in the timetable of Express trains.
-- Nested Query with IN (Subquery)
SELECT Station_Code, Station_Name, City
FROM Station
WHERE Station_Code IN (
    SELECT DISTINCT TT.Station_Code
    FROM Timetable TT
    JOIN Train TR ON TT.Train_ID = TR.Train_ID
    WHERE TR.Train_Type = 'Express'
);

-- Query 8: Find passengers who have made at least one booking with a fare greater than 600,000 VND.
-- Nested Query with EXISTS
SELECT P.Passenger_ID, P.Passenger_Name
FROM Passenger P
WHERE EXISTS (
    SELECT 1
    FROM Booking B
    WHERE B.Passenger_ID = P.Passenger_ID
      AND B.Fare > 600000.00
);

-- Query 9: Find trains that stop at ALL major hub stations ('HNO', 'DNG', 'SGN') using double negation.
-- NOT EXISTS 
SELECT TR.Train_ID, TR.Train_Name
FROM Train TR
WHERE NOT EXISTS (
    SELECT S.Station_Code
    FROM Station S
    WHERE S.Station_Code IN ('HNO', 'DNG', 'SGN')
      AND NOT EXISTS (
          SELECT 1
          FROM Timetable TT
          WHERE TT.Train_ID = TR.Train_ID
            AND TT.Station_Code = S.Station_Code
      )
);

-- Query 10: List all distinct station codes that act as either a departure point or an arrival point in the track network.
-- Set Operation (UNION)
SELECT From_Station AS Active_Station_Code FROM Station_Connection
UNION
SELECT To_Station AS Active_Station_Code FROM Station_Connection
ORDER BY Active_Station_Code ASC;

-- Query 11: Calculate total revenue and average fare for each train type, showing only train types with total revenue above 1,000,000 VND.
-- Multi-table Join + GROUP BY + HAVING
SELECT 
    T.Train_Type,
    COUNT(B.Booking_ID) AS Total_Bookings,
    SUM(B.Fare) AS Total_Revenue,
    AVG(B.Fare) AS Average_Fare
FROM Train T
JOIN Booking B ON T.Train_ID = B.Train_ID
GROUP BY T.Train_Type
HAVING SUM(B.Fare) > 1000000.00;

-- Query 12: Create a view summarizing passenger booking details including passenger name, phone numbers, train name, and seat info.
-- CREATE VIEW + Multi-table Join
CREATE OR REPLACE VIEW vw_Passenger_Booking_Summary AS
SELECT 
    B.Booking_ID,
    P.Passenger_ID,
    P.Passenger_Name,
    GROUP_CONCAT(PP.Phone_Number SEPARATOR ', ') AS Phone_Numbers,
    T.Train_ID,
    T.Train_Name,
    B.Travel_Date,
    B.C_ID AS Coach_ID,
    B.S_ID AS Seat_ID,
    C.Coach_Class,
    B.Fare
FROM Booking B
JOIN Passenger P ON B.Passenger_ID = P.Passenger_ID
LEFT JOIN Passenger_Phone PP ON P.Passenger_ID = PP.Passenger_ID
JOIN Train T ON B.Train_ID = T.Train_ID
JOIN Coach C ON B.Train_ID = C.Train_ID AND B.C_ID = C.C_ID
GROUP BY 
    B.Booking_ID, P.Passenger_ID, P.Passenger_Name, 
    T.Train_ID, T.Train_Name, B.Travel_Date, 
    B.C_ID, B.S_ID, C.Coach_Class, B.Fare;

SELECT * FROM vw_Passenger_Booking_Summary;
