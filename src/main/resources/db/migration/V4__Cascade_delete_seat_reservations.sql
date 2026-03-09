-- V4__Cascade_delete_seat_reservations.sql
-- Ensure seat reservations are removed when the parent seat is deleted.

ALTER TABLE seat_reservations DROP CONSTRAINT fk_reservation_seat;
ALTER TABLE seat_reservations
    ADD CONSTRAINT fk_reservation_seat
    FOREIGN KEY (seat_id) REFERENCES seats(id) ON DELETE CASCADE;

