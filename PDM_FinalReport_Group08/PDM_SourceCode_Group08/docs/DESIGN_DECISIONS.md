# Design decisions: business rules vs. ERD vs. SQL

The project follows the order **business rules → ERD → SQL → Java**.
`database/01_schema_and_seed.sql` is a byte-for-byte copy of the team's `SQL.sql` and is **not modified**.
Where the given schema or seed data conflicted with the business rules (project report, section III) or the ERD, the conflict was put to the team as a multiple-choice question on 2026-11-18. This file records each conflict and the option chosen.

| # | Conflict | Evidence | Decision | Where implemented |
|---|----------|----------|----------|-------------------|
| 1 | **Payment** is in the ERD, the relational schema, Normalization.docx and the report's in-scope list, but SQL.sql has no Payment table. | ERD: `Payment(Pay_ID, Pay_Date, Method, B_ID FK UNIQUE)` | Add the table in a **separate file**; booking = Booking + Payment in one transaction. Seed tickets have no payment row and show "Not recorded". | `02_payment_table.sql`, `sp_booking_create` |
| 2 | **Cancelling a ticket**: the rules need cancel + seat release + "cancelled tickets cannot be used". Booking has no status column, and `uq_seat_per_trip` blocks resale while the row exists. | Report 4 and 5 | Cancel = delete Payment + Booking in one transaction (matches the "Cancellation & Refund" workflow in Normalization.docx). The question was not answered, so the recommended default was applied. | `sp_booking_cancel` |
| 3 | **Boarding / alighting stations** on a ticket and per-segment seat resale are required by the rules, but Booking (SQL and ERD) has no such columns and `uq_seat_per_trip` allows one sale per seat per trip. | Report 5 and 8 | A ticket holds its seat for the **whole route** of the train. Trip search still lets staff search by from/to station. | `sp_trip_search`, `sp_check_trip_seat` |
| 4 | **Overnight trains**: the brief says a train finishes within one day, but the seed has SE2 (~29 h), SE7 (~26 h), SNT1 and SP1 running past midnight in a TIME column with no date. | Assignment brief, Timetable seed | A time **earlier than the previous time means the next day**; the app shows "+1 day". New timetables must still move forward in time; a stop may last up to 3 hours. | `fn_roll_forward`, `fn_stop_minutes`, `fn_timetable_problem` |
| 5 | **Route distance**: `Station_Connection` is one-way (all sections stored south to north), trains run both ways and express trains skip stations, so Query 3 gave wrong results (SE1, SE2, SE7, HP1 none; SPT1 29 km; SP1 224 km). | `queries_reference.sql` Query 3 | Tracks are **two-way**; the distance between consecutive stops is the **shortest path** through the track network (SE1 = 798 km). A stop can only be added if a chain of tracks reaches it, and a track cannot be removed if a route would be cut. | `fn_track_distance`, `fn_track_path`, `fn_train_segment_km`, `sp_track_delete` |
| 6 | **Currency**: seed fares look like VND (550000.00), the test row B001 is 500.00, and Query 11 says "$50". | SQL.sql test block, Queries.sql | Show **VND** ("550,000 ₫"). B001 is left unchanged (500 ₫). The "fare above" report takes the amount as a parameter. | UI formatting, `sp_report_passengers_fare_above` |
| 7 | **Fare**: "the system records the fare at booking", but there is no fare table and the seed fares follow no formula. | Report 5 | Staff enter the fare. The app **suggests** the fare most often charged for the same coach class on the same train. | `fn_suggest_fare` |
| 8 | **Sample data gaps**: 18 of 25 trains have no timetable; only 5 trains have seats (5 each); SE7/HP1 have a timetable but no seats; Query 9 (SE3) is empty. | Seed | The seed is **left as it is**. Coaches, seats and stops can be added from the app during the demo. | Trains & Timetables screen |

## Smaller observations (no decision needed)

- The ERD/report mentions a track **Status** and passenger **email**; neither exists in the ERD-to-SQL mapping, so they are not used.
- The brief lists train types *express, local, night*; the seed only uses Express and Local. The app offers all three.
- SE1's final stop (Da Nang) has a departure time; a departure written on a final stop is ignored.
- Seat codes repeat across coaches (SNT1 C03-B01 and C04-B01); a place is therefore identified by coach + seat.
- One passenger may hold several tickets on the same train and date (seed BKG0001/BKG0002); this is allowed.
- Tickets for trips that have already departed cannot be changed or cancelled.

## Rules enforced by the routines (summary)

Unique codes; non-blank names; phone numbers with 10 digits starting with 0 (`+84` is converted); distance > 0; track joins two different stations, at most one track per pair; first stop needs a departure, middle stops need both times, last stop needs an arrival; consecutive stops linked by track; seat capacity per coach class (Soft Seat AC 64, Hard Seat 80, Sleeper 6-berth 42, Sleeper 4-berth 28, VIP 2-berth 14); booking only for trains "On sale", for a trip that has not departed, on an existing free seat, fare ≥ 0; seat locked with `SELECT ... FOR UPDATE` inside a transaction so two staff members can never sell the same seat; trains, coaches, seats and passengers with tickets on record cannot be deleted.
