-- V1__Create_initial_schema.sql
-- SkyCheck Digital Check-In System Database Schema

-- Flights Table
CREATE TABLE flights (
    id BIGSERIAL PRIMARY KEY,
    flight_number VARCHAR(10) NOT NULL UNIQUE,
    departure_time TIMESTAMP NOT NULL,
    arrival_time TIMESTAMP NOT NULL,
    origin VARCHAR(3) NOT NULL,
    destination VARCHAR(3) NOT NULL,
    aircraft_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_flights_flight_number ON flights(flight_number);
CREATE INDEX idx_flights_departure_time ON flights(departure_time);

-- Seats Table
CREATE TABLE seats (
    id BIGSERIAL PRIMARY KEY,
    flight_id BIGINT NOT NULL,
    seat_number VARCHAR(5) NOT NULL,
    seat_type VARCHAR(20) NOT NULL,
    seat_class VARCHAR(20) NOT NULL DEFAULT 'ECONOMY',
    state VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_seats_flight FOREIGN KEY (flight_id) REFERENCES flights(id) ON DELETE CASCADE,
    CONSTRAINT uq_flight_seat UNIQUE (flight_id, seat_number),
    CONSTRAINT chk_seat_state CHECK (state IN ('AVAILABLE', 'HELD', 'CONFIRMED'))
);

CREATE INDEX idx_seats_flight_id ON seats(flight_id);
CREATE INDEX idx_seats_state ON seats(state);
CREATE INDEX idx_seats_flight_state ON seats(flight_id, state);

-- Passengers Table
CREATE TABLE passengers (
    id BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(20),
    passport_number VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_passengers_email ON passengers(email);

-- Check-Ins Table
CREATE TABLE check_ins (
    id BIGSERIAL PRIMARY KEY,
    passenger_id BIGINT NOT NULL,
    flight_id BIGINT NOT NULL,
    seat_id BIGINT,
    status VARCHAR(30) NOT NULL DEFAULT 'IN_PROGRESS',
    check_in_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_checkin_passenger FOREIGN KEY (passenger_id) REFERENCES passengers(id),
    CONSTRAINT fk_checkin_flight FOREIGN KEY (flight_id) REFERENCES flights(id),
    CONSTRAINT fk_checkin_seat FOREIGN KEY (seat_id) REFERENCES seats(id),
    CONSTRAINT chk_checkin_status CHECK (status IN ('IN_PROGRESS', 'WAITING_FOR_PAYMENT', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_checkins_passenger ON check_ins(passenger_id);
CREATE INDEX idx_checkins_flight ON check_ins(flight_id);
CREATE INDEX idx_checkins_status ON check_ins(status);

-- Seat Reservations Table (tracks hold/confirm)
CREATE TABLE seat_reservations (
    id BIGSERIAL PRIMARY KEY,
    seat_id BIGINT NOT NULL,
    check_in_id BIGINT NOT NULL,
    passenger_id BIGINT NOT NULL,
    reservation_type VARCHAR(20) NOT NULL,
    hold_expires_at TIMESTAMP,
    reserved_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMP,
    released_at TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reservation_seat FOREIGN KEY (seat_id) REFERENCES seats(id),
    CONSTRAINT fk_reservation_checkin FOREIGN KEY (check_in_id) REFERENCES check_ins(id),
    CONSTRAINT fk_reservation_passenger FOREIGN KEY (passenger_id) REFERENCES passengers(id),
    CONSTRAINT chk_reservation_type CHECK (reservation_type IN ('HOLD', 'CONFIRM')),
    CONSTRAINT chk_reservation_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'RELEASED', 'CONFIRMED'))
);

CREATE INDEX idx_reservations_seat ON seat_reservations(seat_id);
CREATE INDEX idx_reservations_checkin ON seat_reservations(check_in_id);
CREATE INDEX idx_reservations_status ON seat_reservations(status);
CREATE INDEX idx_reservations_expires ON seat_reservations(hold_expires_at) WHERE hold_expires_at IS NOT NULL;

-- Baggage Table
CREATE TABLE baggage (
    id BIGSERIAL PRIMARY KEY,
    check_in_id BIGINT NOT NULL,
    weight_kg DECIMAL(5,2) NOT NULL,
    excess_weight_kg DECIMAL(5,2) DEFAULT 0,
    excess_fee_amount DECIMAL(10,2) DEFAULT 0,
    payment_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUIRED',
    payment_id VARCHAR(100),
    paid_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_baggage_checkin FOREIGN KEY (check_in_id) REFERENCES check_ins(id) ON DELETE CASCADE,
    CONSTRAINT chk_payment_status CHECK (payment_status IN ('NOT_REQUIRED', 'PENDING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_baggage_checkin ON baggage(check_in_id);
CREATE INDEX idx_baggage_payment_status ON baggage(payment_status);

-- Seat State History Table (Audit Trail)
CREATE TABLE seat_state_history (
    id BIGSERIAL PRIMARY KEY,
    seat_id BIGINT NOT NULL,
    passenger_id BIGINT,
    previous_state VARCHAR(20),
    new_state VARCHAR(20) NOT NULL,
    reason VARCHAR(255),
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_history_seat FOREIGN KEY (seat_id) REFERENCES seats(id),
    CONSTRAINT fk_history_passenger FOREIGN KEY (passenger_id) REFERENCES passengers(id)
);

CREATE INDEX idx_history_seat ON seat_state_history(seat_id);
CREATE INDEX idx_history_changed_at ON seat_state_history(changed_at);

-- Comments for documentation
COMMENT ON TABLE flights IS 'Stores flight information';
COMMENT ON TABLE seats IS 'Stores seat configuration and current state with optimistic locking';
COMMENT ON TABLE passengers IS 'Stores passenger information';
COMMENT ON TABLE check_ins IS 'Stores check-in sessions and their status';
COMMENT ON TABLE seat_reservations IS 'Tracks seat hold and confirmation with expiration';
COMMENT ON TABLE baggage IS 'Stores baggage information and payment status';
COMMENT ON TABLE seat_state_history IS 'Audit trail for all seat state changes';

COMMENT ON COLUMN seats.version IS 'Optimistic locking version for concurrent seat assignment';
COMMENT ON COLUMN seat_reservations.hold_expires_at IS 'Expiration time for seat hold (120 seconds from reserved_at)';

