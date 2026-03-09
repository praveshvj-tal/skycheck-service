-- V2__Insert_sample_data.sql
-- Sample data for testing and demonstration

-- Insert sample flights
INSERT INTO flights (flight_number, departure_time, arrival_time, origin, destination, aircraft_type, status)
VALUES
    ('SH101', CURRENT_TIMESTAMP + INTERVAL '6 hours', CURRENT_TIMESTAMP + INTERVAL '9 hours', 'JFK', 'LAX', 'Boeing 737', 'SCHEDULED'),
    ('SH202', CURRENT_TIMESTAMP + INTERVAL '8 hours', CURRENT_TIMESTAMP + INTERVAL '11 hours', 'LAX', 'SFO', 'Airbus A320', 'SCHEDULED'),
    ('SH303', CURRENT_TIMESTAMP + INTERVAL '12 hours', CURRENT_TIMESTAMP + INTERVAL '16 hours', 'SFO', 'JFK', 'Boeing 777', 'SCHEDULED');

-- Insert seats for Flight SH101 (Boeing 737 - 30 seats for demo)
INSERT INTO seats (flight_id, seat_number, seat_type, seat_class, state)
SELECT
    (SELECT id FROM flights WHERE flight_number = 'SH101'),
    seat_number,
    CASE
        WHEN seat_number LIKE '%A' OR seat_number LIKE '%F' THEN 'WINDOW'
        WHEN seat_number LIKE '%C' OR seat_number LIKE '%D' THEN 'AISLE'
        ELSE 'MIDDLE'
    END,
    CASE
        WHEN CAST(SUBSTRING(seat_number FROM 1 FOR LENGTH(seat_number)-1) AS INTEGER) <= 3 THEN 'BUSINESS'
        ELSE 'ECONOMY'
    END,
    'AVAILABLE'
FROM (
    SELECT ROW_NUMBER() OVER () AS row_num,
           CAST(((ROW_NUMBER() OVER () - 1) / 6 + 1) AS VARCHAR) ||
           SUBSTRING('ABCDEF' FROM ((ROW_NUMBER() OVER () - 1) % 6 + 1) FOR 1) AS seat_number
    FROM generate_series(1, 30)
) AS seat_gen;

-- Insert seats for Flight SH202 (Airbus A320 - 30 seats for demo)
INSERT INTO seats (flight_id, seat_number, seat_type, seat_class, state)
SELECT
    (SELECT id FROM flights WHERE flight_number = 'SH202'),
    seat_number,
    CASE
        WHEN seat_number LIKE '%A' OR seat_number LIKE '%F' THEN 'WINDOW'
        WHEN seat_number LIKE '%C' OR seat_number LIKE '%D' THEN 'AISLE'
        ELSE 'MIDDLE'
    END,
    CASE
        WHEN CAST(SUBSTRING(seat_number FROM 1 FOR LENGTH(seat_number)-1) AS INTEGER) <= 2 THEN 'BUSINESS'
        ELSE 'ECONOMY'
    END,
    'AVAILABLE'
FROM (
    SELECT ROW_NUMBER() OVER () AS row_num,
           CAST(((ROW_NUMBER() OVER () - 1) / 6 + 1) AS VARCHAR) ||
           SUBSTRING('ABCDEF' FROM ((ROW_NUMBER() OVER () - 1) % 6 + 1) FOR 1) AS seat_number
    FROM generate_series(1, 30)
) AS seat_gen;

-- Insert seats for Flight SH303 (Boeing 777 - 30 seats for demo)
INSERT INTO seats (flight_id, seat_number, seat_type, seat_class, state)
SELECT
    (SELECT id FROM flights WHERE flight_number = 'SH303'),
    seat_number,
    CASE
        WHEN seat_number LIKE '%A' OR seat_number LIKE '%F' THEN 'WINDOW'
        WHEN seat_number LIKE '%C' OR seat_number LIKE '%D' THEN 'AISLE'
        ELSE 'MIDDLE'
    END,
    CASE
        WHEN CAST(SUBSTRING(seat_number FROM 1 FOR LENGTH(seat_number)-1) AS INTEGER) <= 4 THEN 'BUSINESS'
        ELSE 'ECONOMY'
    END,
    'AVAILABLE'
FROM (
    SELECT ROW_NUMBER() OVER () AS row_num,
           CAST(((ROW_NUMBER() OVER () - 1) / 6 + 1) AS VARCHAR) ||
           SUBSTRING('ABCDEF' FROM ((ROW_NUMBER() OVER () - 1) % 6 + 1) FOR 1) AS seat_number
    FROM generate_series(1, 30)
) AS seat_gen;

-- Insert sample passengers
INSERT INTO passengers (first_name, last_name, email, phone, passport_number)
VALUES
    ('John', 'Doe', 'john.doe@example.com', '+1-555-0101', 'P12345678'),
    ('Jane', 'Smith', 'jane.smith@example.com', '+1-555-0102', 'P23456789'),
    ('Bob', 'Johnson', 'bob.johnson@example.com', '+1-555-0103', 'P34567890'),
    ('Alice', 'Williams', 'alice.williams@example.com', '+1-555-0104', 'P45678901'),
    ('Charlie', 'Brown', 'charlie.brown@example.com', '+1-555-0105', 'P56789012');

