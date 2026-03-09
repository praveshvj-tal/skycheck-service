-- V3__Cascade_delete_seat_state_history.sql
-- Ensure seat state history rows are removed when the parent seat is deleted.

ALTER TABLE seat_state_history DROP CONSTRAINT fk_history_seat;
ALTER TABLE seat_state_history
    ADD CONSTRAINT fk_history_seat
    FOREIGN KEY (seat_id) REFERENCES seats(id) ON DELETE CASCADE;

