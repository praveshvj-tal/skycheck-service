package com.skyhigh.skycheck.repository;

import com.skyhigh.skycheck.entity.Seat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {

    /**
     * Find all seats for a flight
     */
    @Query("SELECT s FROM Seat s WHERE s.flight.id = :flightId ORDER BY s.seatNumber")
    List<Seat> findByFlightId(@Param("flightId") Long flightId);

    /**
     * Find available seats for a flight
     */
    @Query("SELECT s FROM Seat s WHERE s.flight.id = :flightId AND s.state = 'AVAILABLE' ORDER BY s.seatNumber")
    List<Seat> findAvailableSeatsByFlightId(@Param("flightId") Long flightId);

    /**
     * Find seat by ID with optimistic lock
     */
    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT s FROM Seat s WHERE s.id = :seatId")
    Optional<Seat> findByIdWithLock(@Param("seatId") Long seatId);

    /**
     * Find seat by flight and seat number
     */
    @Query("SELECT s FROM Seat s WHERE s.flight.id = :flightId AND s.seatNumber = :seatNumber")
    Optional<Seat> findByFlightIdAndSeatNumber(@Param("flightId") Long flightId, @Param("seatNumber") String seatNumber);

    /**
     * Count available seats for a flight
     */
    @Query("SELECT COUNT(s) FROM Seat s WHERE s.flight.id = :flightId AND s.state = 'AVAILABLE'")
    long countAvailableSeatsByFlightId(@Param("flightId") Long flightId);
}

