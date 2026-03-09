package com.skyhigh.skycheck.repository;

import com.skyhigh.skycheck.entity.SeatReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SeatReservationRepository extends JpaRepository<SeatReservation, Long> {

    @Query("SELECT sr FROM SeatReservation sr WHERE sr.seat.id = :seatId AND sr.status = 'ACTIVE'")
    Optional<SeatReservation> findActiveBySeatId(@Param("seatId") Long seatId);

    @Query("SELECT sr FROM SeatReservation sr WHERE sr.checkIn.id = :checkInId")
    List<SeatReservation> findByCheckInId(@Param("checkInId") Long checkInId);

    @Query("SELECT sr FROM SeatReservation sr WHERE sr.status = 'ACTIVE' AND sr.reservationType = 'HOLD' AND sr.holdExpiresAt < :now")
    List<SeatReservation> findExpiredHolds(@Param("now") LocalDateTime now);

    @Query("SELECT sr FROM SeatReservation sr WHERE sr.seat.id = :seatId AND sr.passenger.id = :passengerId AND sr.status = 'ACTIVE'")
    Optional<SeatReservation> findActiveBySeatIdAndPassengerId(@Param("seatId") Long seatId, @Param("passengerId") Long passengerId);
}

