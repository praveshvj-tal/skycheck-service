-- Optional local-dev seed data.
-- This file is intentionally minimal and idempotent-ish for H2 dev runs.
-- In production-like runs, Flyway migrations should be used instead.

INSERT INTO flights (flight_number, departure_time, arrival_time, origin, destination, aircraft_type, status, created_at, updated_at)
SELECT 'SH101', CURRENT_TIMESTAMP + INTERVAL '6' HOUR, CURRENT_TIMESTAMP + INTERVAL '9' HOUR, 'JFK', 'LAX', 'Boeing 737', 'SCHEDULED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM flights WHERE flight_number = 'SH101');

INSERT INTO flights (flight_number, departure_time, arrival_time, origin, destination, aircraft_type, status, created_at, updated_at)
SELECT 'SH202', CURRENT_TIMESTAMP + INTERVAL '8' HOUR, CURRENT_TIMESTAMP + INTERVAL '11' HOUR, 'LAX', 'SFO', 'Airbus A320', 'SCHEDULED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM flights WHERE flight_number = 'SH202');

INSERT INTO flights (flight_number, departure_time, arrival_time, origin, destination, aircraft_type, status, created_at, updated_at)
SELECT 'SH303', CURRENT_TIMESTAMP + INTERVAL '12' HOUR, CURRENT_TIMESTAMP + INTERVAL '16' HOUR, 'SFO', 'JFK', 'Boeing 777', 'SCHEDULED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM flights WHERE flight_number = 'SH303');

