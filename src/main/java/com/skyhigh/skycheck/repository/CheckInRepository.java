package com.skyhigh.skycheck.repository;

import com.skyhigh.skycheck.entity.CheckIn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    @Query("SELECT c FROM CheckIn c WHERE c.passenger.id = :passengerId")
    List<CheckIn> findByPassengerId(@Param("passengerId") Long passengerId);

    @Query("SELECT c FROM CheckIn c WHERE c.flight.id = :flightId")
    List<CheckIn> findByFlightId(@Param("flightId") Long flightId);

    @Query("SELECT c FROM CheckIn c WHERE c.passenger.id = :passengerId AND c.flight.id = :flightId")
    Optional<CheckIn> findByPassengerIdAndFlightId(@Param("passengerId") Long passengerId, @Param("flightId") Long flightId);

    @Query("SELECT c FROM CheckIn c WHERE c.status = :status")
    List<CheckIn> findByStatus(@Param("status") CheckIn.CheckInStatus status);
}

