
USE RailwayManagement;

DROP TABLE IF EXISTS Payment;

CREATE TABLE Payment (
    Pay_ID      VARCHAR(10) PRIMARY KEY,
    Pay_Date    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    Method      VARCHAR(30) NOT NULL,
    Booking_ID  VARCHAR(10) NOT NULL,
    FOREIGN KEY (Booking_ID) REFERENCES Booking(Booking_ID)
        ON DELETE NO ACTION
        ON UPDATE CASCADE,
    CONSTRAINT uq_payment_booking UNIQUE (Booking_ID),
    CONSTRAINT chk_payment_method CHECK (Method IN ('Cash', 'Card', 'Bank Transfer', 'E-Wallet'))
);
