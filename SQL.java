DROP DATABASE IF EXISTS RailwayManagement;  
GO  
CREATE DATABASE RailwayManagement;  
GO  
USE RailwayManagement;  
GO  
   
-- 1. Station  
CREATE TABLE Station (  
    Station_Code VARCHAR(10) PRIMARY KEY,  
    Station_Name VARCHAR(100) NOT NULL,  
    City VARCHAR(100) NOT NULL  
);  
   
-- 2. Train  
CREATE TABLE Train (  
    Train_ID VARCHAR(10) PRIMARY KEY,  
    Train_Name VARCHAR(100) NOT NULL,  
    Train_Type VARCHAR(50) NOT NULL  
);  
   
-- 3. Passenger  
CREATE TABLE Passenger (  
    Passenger_ID VARCHAR(10) PRIMARY KEY,  
    Passenger_Name VARCHAR(100) NOT NULL  
);  
  
-- 3b. Passenger_Phone  
CREATE TABLE Passenger_Phone (  
    Passenger_ID VARCHAR(10) NOT NULL,  
    Phone_Number VARCHAR(20) NOT NULL,  
    PRIMARY KEY (Passenger_ID, Phone_Number),  
    FOREIGN KEY (Passenger_ID) REFERENCES Passenger(Passenger_ID)   
        ON DELETE CASCADE   
        ON UPDATE CASCADE  
);  
   
-- 4. Station_Connection  
CREATE TABLE Station_Connection (  
    Track_ID VARCHAR(10) PRIMARY KEY,  
    From_Station VARCHAR(10) NOT NULL,  
    To_Station VARCHAR(10) NOT NULL,  
    Length_km INT NOT NULL,  
    FOREIGN KEY (From_Station) REFERENCES Station(Station_Code)  
        ON DELETE NO ACTION   
        ON UPDATE CASCADE,  
    FOREIGN KEY (To_Station) REFERENCES Station(Station_Code)  
        ON DELETE NO ACTION   
        ON UPDATE CASCADE,  
    CONSTRAINT chk_track_diff CHECK (From_Station <> To_Station),  
    CONSTRAINT chk_track_length CHECK (Length_km > 0),  
    CONSTRAINT uq_track_pair UNIQUE (From_Station, To_Station)  
);  
   
-- 5. Timetable  
CREATE TABLE Timetable (  
    Train_ID VARCHAR(10) NOT NULL,  
    Stop_No INT NOT NULL,  
    Station_Code VARCHAR(10) NOT NULL,  
    Arrival TIME,  
    Departure TIME,  
    PRIMARY KEY (Train_ID, Stop_No),  
    FOREIGN KEY (Train_ID) REFERENCES Train(Train_ID)   
        ON DELETE CASCADE   
        ON UPDATE CASCADE,  
    FOREIGN KEY (Station_Code) REFERENCES Station(Station_Code)  
        ON DELETE NO ACTION   
        ON UPDATE CASCADE,  
    CONSTRAINT chk_stop_no CHECK (Stop_No > 0),  
    CONSTRAINT chk_time_notnull CHECK (Arrival IS NOT NULL OR Departure IS NOT NULL),  
    CONSTRAINT uq_train_station UNIQUE (Train_ID, Station_Code)  
);  
   
-- 6. Coach  
CREATE TABLE Coach (  
    Train_ID VARCHAR(10) NOT NULL,  
    C_ID VARCHAR(10) NOT NULL,  
    Coach_Class VARCHAR(50) NOT NULL,  
    PRIMARY KEY (Train_ID, C_ID),  
    FOREIGN KEY (Train_ID) REFERENCES Train(Train_ID)   
        ON DELETE CASCADE   
        ON UPDATE CASCADE  
);  
   
-- 7. Seat  
CREATE TABLE Seat (  
    Train_ID VARCHAR(10) NOT NULL,  
    C_ID VARCHAR(10) NOT NULL,  
    S_ID VARCHAR(10) NOT NULL,  
    PRIMARY KEY (Train_ID, C_ID, S_ID),  
    FOREIGN KEY (Train_ID, C_ID) REFERENCES Coach(Train_ID, C_ID)  
        ON DELETE CASCADE   
        ON UPDATE CASCADE  
);  
   
-- 8. Booking  
CREATE TABLE Booking (  
    Booking_ID VARCHAR(10) PRIMARY KEY,  
    Passenger_ID VARCHAR(10) NOT NULL,  
    Train_ID VARCHAR(10) NOT NULL,  
    Travel_Date DATE NOT NULL,  
    C_ID VARCHAR(10) NOT NULL,  
    S_ID VARCHAR(10) NOT NULL,  
    Fare DECIMAL(12,2) NOT NULL,  
    FOREIGN KEY (Passenger_ID) REFERENCES Passenger(Passenger_ID)   
        ON DELETE NO ACTION   
        ON UPDATE CASCADE,  
    FOREIGN KEY (Train_ID, C_ID, S_ID) REFERENCES Seat(Train_ID, C_ID, S_ID)   
        ON DELETE NO ACTION   
        ON UPDATE CASCADE,  
    CONSTRAINT chk_fare CHECK (Fare >= 0),  
    CONSTRAINT uq_seat_per_trip UNIQUE (Train_ID, Travel_Date, C_ID, S_ID)  
);  
  
  
-- ==========================================  
-- 1. Station  
-- ==========================================  
INSERT INTO Station (Station_Code, Station_Name, City) VALUES   
('SGN', 'Saigon Station', 'Ho Chi Minh City'),  
('BHO', 'Bien Hoa Station', 'Dong Nai'),  
('BTT', 'Binh Thuan Station', 'Binh Thuan'),  
('PTT', 'Phan Thiet Station', 'Binh Thuan'),  
('TCV', 'Thap Cham Station', 'Ninh Thuan'),  
('NTR', 'Nha Trang Station', 'Khanh Hoa'),  
('NHA', 'Ninh Hoa Station', 'Khanh Hoa'),  
('PYN', 'Tuy Hoa Station', 'Phu Yen'),  
('QNG', 'Quang Ngai Station', 'Quang Ngai'),  
('DNG', 'Da Nang Station', 'Da Nang'),  
('HUI', 'Hue Station', 'Thua Thien Hue'),  
('DHA', 'Dong Hoi Station', 'Quang Binh'),  
('VIN', 'Vinh Station', 'Nghe An'),  
('THA', 'Thanh Hoa Station', 'Thanh Hoa'),  
('NAM', 'Nam Dinh Station', 'Nam Dinh'),  
('HNO', 'Hanoi Station', 'Hanoi'),  
('LBE', 'Long Bien Station', 'Hanoi'),  
('GLA', 'Gia Lam Station', 'Hanoi'),  
('HPH', 'Hai Phong Station', 'Hai Phong'),  
('LCI', 'Lao Cai Station', 'Lao Cai'),  
('YBA', 'Yen Bai Station', 'Yen Bai'),  
('VTR', 'Viet Tri Station', 'Phu Tho'),  
('TNG', 'Thai Nguyen Station', 'Thai Nguyen'),  
('LSN', 'Lang Son Station', 'Lang Son'),  
('DDG', 'Dong Dang Station', 'Lang Son');  
   
-- ==========================================  
-- 2. Train  
-- ==========================================  
INSERT INTO Train (Train_ID, Train_Name, Train_Type) VALUES   
('SE1', 'Reunification Express SE1', 'Express'),  
('SE2', 'Reunification Express SE2', 'Express'),  
('SE3', 'Reunification Express SE3', 'Express'),  
('SE4', 'Reunification Express SE4', 'Express'),  
('SE5', 'Reunification Express SE5', 'Express'),  
('SE6', 'Reunification Express SE6', 'Express'),  
('SE7', 'Reunification Express SE7', 'Express'),  
('SE8', 'Reunification Express SE8', 'Express'),  
('TN1', 'Thong Nhat Local 1', 'Local'),  
('TN2', 'Thong Nhat Local 2', 'Local'),  
('SNT1', 'Saigon - Nha Trang 1', 'Express'),  
('SNT2', 'Nha Trang - Saigon 2', 'Express'),  
('SPT1', 'Saigon - Phan Thiet 1', 'Local'),  
('SPT2', 'Phan Thiet - Saigon 2', 'Local'),  
('NA1', 'Hanoi - Vinh 1', 'Local'),  
('NA2', 'Vinh - Hanoi 2', 'Local'),  
('SP1', 'Hanoi - Lao Cai 1', 'Express'),  
('SP2', 'Lao Cai - Hanoi 2', 'Express'),  
('SP3', 'Hanoi - Lao Cai 3', 'Express'),  
('SP4', 'Lao Cai - Hanoi 4', 'Express'),  
('HP1', 'Hanoi - Hai Phong 1', 'Express'),  
('HP2', 'Hai Phong - Hanoi 2', 'Express'),  
('DD1', 'Hanoi - Dong Dang 1', 'Local'),  
('QT1', 'Hanoi - Quan Trieu 1', 'Local'),  
('VD1', 'Vinh - Da Nang 1', 'Local');  
   
-- ==========================================  
-- 3. Passenger  
-- ==========================================  
INSERT INTO Passenger (Passenger_ID, Passenger_Name) VALUES   
('P001', 'Nguyen Van An'),  
('P002', 'Tran Thi Binh'),  
('P003', 'Le Hoang Cuong'),  
('P004', 'Pham Thi Dung'),  
('P005', 'Hoang Van Em'),  
('P006', 'Vu Thi Phuong'),  
('P007', 'Vo Thanh Giang'),  
('P008', 'Ngo Thi Hang'),  
('P009', 'Ly Van Inh'),  
('P010', 'Bui Thi Khanh'),  
('P011', 'Doan Van Lam'),  
('P012', 'Truong Thi Mai'),  
('P013', 'Dang Van Nam'),  
('P014', 'Mai Thi Oanh'),  
('P015', 'Phan Van Phong'),  
('P016', 'Dinh Thi Quan'),  
('P017', 'Trinh Van Run'),  
('P018', 'Nguyen Thi Sang'),  
('P019', 'Tran Van Tien'),  
('P020', 'Le Thi Uyen'),  
('P021', 'Pham Van Vinh'),  
('P022', 'Hoang Thi Xuan'),  
('P023', 'Vu Van Yen'),  
('P024', 'Do Van Dai'),  
('P025', 'Ton Thi Bao');  
  
-- ==========================================  
-- 3b. Passenger Phone  
-- ==========================================  
INSERT INTO Passenger_Phone (Passenger_ID, Phone_Number) VALUES  
('P001', '0901234567'),  
('P001', '0909998881'),  
('P002', '0912345678'),  
('P003', '0923456789'),  
('P003', '0908887773'),  
('P004', '0934567890'),  
('P005', '0945678901'),  
('P006', '0956789012'),  
('P007', '0967890123'),  
('P007', '0933334447'),  
('P008', '0978901234'),  
('P009', '0989012345'),  
('P010', '0990123456'),  
('P011', '0801234567'),  
('P012', '0812345678'),  
('P012', '0822223332'),  
('P013', '0823456789'),  
('P014', '0834567890'),  
('P015', '0845678901'),  
('P016', '0856789012'),  
('P017', '0867890123'),  
('P018', '0878901234'),  
('P018', '0899990008'),  
('P019', '0889012345'),  
('P020', '0890123456'),  
('P021', '0701234567'),  
('P022', '0712345678'),  
('P023', '0723456789'),  
('P024', '0734567890'),  
('P025', '0745678901');  
   
-- ==========================================  
-- 4. Station_Connection  
-- ==========================================  
INSERT INTO Station_Connection (Track_ID, From_Station, To_Station, Length_km) VALUES   
('TS01', 'SGN', 'BHO', 29),  
('TS02', 'BHO', 'BTT', 146),  
('TS03', 'BTT', 'TCV', 143),  
('TS04', 'TCV', 'NTR', 93),  
('TS05', 'NTR', 'NHA', 34),  
('TS06', 'NHA', 'PYN', 85),  
('TS07', 'PYN', 'QNG', 205),  
('TS08', 'QNG', 'DNG', 131),  
('TS09', 'DNG', 'HUI', 103),  
('TS10', 'HUI', 'DHA', 166),  
('TS11', 'DHA', 'VIN', 193),  
('TS12', 'VIN', 'THA', 144),  
('TS13', 'THA', 'NAM', 105),  
('TS14', 'NAM', 'HNO', 87),  
('TS15', 'HNO', 'GLA', 5),  
('TS16', 'GLA', 'LBE', 2),  
('TS17', 'HNO', 'LBE', 3),  
('TS18', 'LBE', 'HPH', 102),  
('TS19', 'HNO', 'VTR', 73),  
('TS20', 'VTR', 'YBA', 82),  
('TS21', 'YBA', 'LCI', 139),  
('TS22', 'BTT', 'PTT', 12),  
('TS23', 'HNO', 'TNG', 75),  
('TS24', 'GLA', 'LSN', 149),  
('TS25', 'LSN', 'DDG', 14);  
   
-- ==========================================  
-- 5. Timetable  
-- ==========================================  
-- Train SE1 schedule (Southbound)  
INSERT INTO Timetable (Train_ID, Stop_No, Station_Code, Arrival, Departure) VALUES   
('SE1', 1, 'HNO', NULL, '06:00:00'),  
('SE1', 2, 'NAM', '07:30:00', '07:35:00'),  
('SE1', 3, 'THA', '09:00:00', '09:05:00'),  
('SE1', 4, 'VIN', '11:30:00', '11:40:00'),  
('SE1', 5, 'DNG', '18:00:00', '18:15:00');  
-- Train SE2 schedule (Northbound)  
INSERT INTO Timetable (Train_ID, Stop_No, Station_Code, Arrival, Departure) VALUES   
('SE2', 1, 'SGN', NULL, '06:00:00'),  
('SE2', 2, 'NTR', '13:00:00', '13:10:00'),  
('SE2', 3, 'DNG', '22:00:00', '22:15:00'),  
('SE2', 4, 'VIN', '05:00:00', '05:10:00'),  
('SE2', 5, 'HNO', '11:00:00', NULL);  
-- Train SNT1 schedule  
INSERT INTO Timetable (Train_ID, Stop_No, Station_Code, Arrival, Departure) VALUES   
('SNT1', 1, 'SGN', NULL, '20:00:00'),  
('SNT1', 2, 'BHO', '20:40:00', '20:45:00'),  
('SNT1', 3, 'BTT', '23:30:00', '23:35:00'),  
('SNT1', 4, 'TCV', '02:00:00', '02:05:00'),  
('SNT1', 5, 'NTR', '04:30:00', NULL);  
-- Train SPT1 schedule  
INSERT INTO Timetable (Train_ID, Stop_No, Station_Code, Arrival, Departure) VALUES   
('SPT1', 1, 'SGN', NULL, '06:40:00'),  
('SPT1', 2, 'BHO', '07:25:00', '07:30:00'),  
('SPT1', 3, 'PTT', '10:30:00', NULL);  
-- Train SP1 schedule  
INSERT INTO Timetable (Train_ID, Stop_No, Station_Code, Arrival, Departure) VALUES   
('SP1', 1, 'HNO', NULL, '21:30:00'),  
('SP1', 2, 'LBE', '21:40:00', '21:45:00'),  
('SP1', 3, 'VTR', '23:00:00', '23:05:00'),  
('SP1', 4, 'YBA', '01:00:00', '01:10:00'),  
('SP1', 5, 'LCI', '05:30:00', NULL),  
('HP1', 1, 'HNO', NULL, '06:00:00'),  
('HP1', 2, 'HPH', '08:30:00', NULL);  
 --Train SE7 schedule  
INSERT INTO Timetable (Train_ID, Stop_No, Station_Code, Arrival, Departure) VALUES  
('SE7', 1, 'HNO', NULL, '06:00:00'),  
('SE7', 2, 'NAM', '07:30:00', '07:35:00'),   
('SE7', 3, 'THA', '09:00:00', '09:05:00'),  
('SE7', 4, 'VIN', '11:30:00', '11:40:00'),  
('SE7', 5, 'DNG', '18:00:00', '18:15:00'),  
('SE7', 6, 'SGN', '08:00:00', NULL);  
   
-- ==========================================  
-- 6. Coach  
-- ==========================================  
-- Allocate 5 coaches for each of 5 trains  
INSERT INTO Coach (Train_ID, C_ID, Coach_Class) VALUES   
('SE1', 'C01', 'Soft Seat AC'),  
('SE1', 'C02', 'Soft Seat AC'),  
('SE1', 'C03', 'Sleeper 6-berth'),  
('SE1', 'C04', 'Sleeper 4-berth'),  
('SE1', 'C05', 'VIP Sleeper 2-berth'),  
   
('SE2', 'C01', 'Hard Seat'),  
('SE2', 'C02', 'Soft Seat AC'),  
('SE2', 'C03', 'Sleeper 6-berth'),  
('SE2', 'C04', 'Sleeper 4-berth'),  
('SE2', 'C05', 'VIP Sleeper 2-berth'),  
   
('SNT1', 'C01', 'Soft Seat AC'),  
('SNT1', 'C02', 'Soft Seat AC'),  
('SNT1', 'C03', 'Sleeper 4-berth'),  
('SNT1', 'C04', 'Sleeper 4-berth'),  
('SNT1', 'C05', 'Sleeper 4-berth'),  
   
('SPT1', 'C01', 'Hard Seat'),  
('SPT1', 'C02', 'Soft Seat AC'),  
('SPT1', 'C03', 'Soft Seat AC'),  
('SPT1', 'C04', 'Soft Seat AC'),  
('SPT1', 'C05', 'Soft Seat AC'),  
   
('SP1', 'C01', 'Soft Seat AC'),  
('SP1', 'C02', 'Sleeper 4-berth'),  
('SP1', 'C03', 'Sleeper 4-berth'),  
('SP1', 'C04', 'Sleeper 4-berth'),  
('SP1', 'C05', 'VIP Sleeper 2-berth');  
   
-- ==========================================  
-- 7. Seat  
-- ==========================================  
INSERT INTO Seat (Train_ID, C_ID, S_ID) VALUES   
('SE1', 'C01', 'S01'), ('SE1', 'C01', 'S02'), ('SE1', 'C01', 'S03'), ('SE1', 'C01', 'S04'), ('SE1', 'C01', 'S05'),  
('SE2', 'C02', 'S01'), ('SE2', 'C02', 'S02'), ('SE2', 'C02', 'S03'), ('SE2', 'C02', 'S04'), ('SE2', 'C02', 'S05'),  
('SNT1', 'C03', 'B01'), ('SNT1', 'C03', 'B02'), ('SNT1', 'C03', 'B03'), ('SNT1', 'C03', 'B04'), ('SNT1', 'C04', 'B01'),  
('SPT1', 'C02', 'S01'), ('SPT1', 'C02', 'S02'), ('SPT1', 'C02', 'S03'), ('SPT1', 'C02', 'S04'), ('SPT1', 'C02', 'S05'),  
('SP1', 'C02', 'B01'), ('SP1', 'C02', 'B02'), ('SP1', 'C02', 'B03'), ('SP1', 'C02', 'B04'), ('SP1', 'C05', 'V01');  
   
-- ==========================================  
-- 8. Booking  
-- ==========================================  
INSERT INTO Booking (Booking_ID, Passenger_ID, Train_ID, Travel_Date, C_ID, S_ID, Fare) VALUES   
('BKG0001', 'P001', 'SE1', '2026-10-15', 'C01', 'S01', 550000.00),  
('BKG0002', 'P001', 'SE1', '2026-10-15', 'C01', 'S02', 550000.00),  
('BKG0003', 'P002', 'SE1', '2026-10-15', 'C01', 'S03', 550000.00),   
('BKG0004', 'P003', 'SE1', '2026-10-15', 'C01', 'S04', 550000.00),   
('BKG0005', 'P004', 'SE1', '2026-10-15', 'C01', 'S05', 550000.00),   
    
('BKG0006', 'P005', 'SE2', '2026-10-16', 'C02', 'S01', 600000.00),   
('BKG0007', 'P006', 'SE2', '2026-10-16', 'C02', 'S02', 600000.00),   
('BKG0008', 'P007', 'SE2', '2026-10-16', 'C02', 'S03', 600000.00),   
('BKG0009', 'P008', 'SE2', '2026-10-16', 'C02', 'S04', 600000.00),   
('BKG0010', 'P009', 'SE2', '2026-10-16', 'C02', 'S05', 600000.00),   
    
('BKG0011', 'P010', 'SNT1', '2026-10-17', 'C03', 'B01', 450000.00),   
('BKG0012', 'P011', 'SNT1', '2026-10-17', 'C03', 'B02', 450000.00),   
('BKG0013', 'P012', 'SNT1', '2026-10-17', 'C03', 'B03', 400000.00),   
('BKG0014', 'P013', 'SNT1', '2026-10-17', 'C03', 'B04', 400000.00),   
('BKG0015', 'P014', 'SNT1', '2026-10-17', 'C04', 'B01', 450000.00),   
    
('BKG0016', 'P015', 'SPT1', '2026-10-18', 'C02', 'S01', 180000.00),   
('BKG0017', 'P016', 'SPT1', '2026-10-18', 'C02', 'S02', 180000.00),   
('BKG0018', 'P017', 'SPT1', '2026-10-18', 'C02', 'S03', 180000.00),   
('BKG0019', 'P018', 'SPT1', '2026-10-18', 'C02', 'S04', 180000.00),   
('BKG0020', 'P019', 'SPT1', '2026-10-18', 'C02', 'S05', 180000.00),   
    
('BKG0021', 'P020', 'SP1', '2026-10-19', 'C02', 'B01', 750000.00),   
('BKG0022', 'P021', 'SP1', '2026-10-19', 'C02', 'B02', 750000.00),   
('BKG0023', 'P022', 'SP1', '2026-10-19', 'C02', 'B03', 700000.00),   
('BKG0024', 'P023', 'SP1', '2026-10-19', 'C02', 'B04', 700000.00),   
('BKG0025', 'P024', 'SP1', '2026-10-19', 'C05', 'V01', 1200000.00);  
  
SELECT * FROM Station;  
SELECT * FROM Train;  
SELECT * FROM Passenger_Phone; 
 