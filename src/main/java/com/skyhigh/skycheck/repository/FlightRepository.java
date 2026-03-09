package com.skyhigh.skycheck.repository;

import com.skyhigh.skycheck.entity.Flight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FlightRepository extends JpaRepository<Flight, Long> {

    Optional<Flight> findByFlightNumber(String flightNumber);

    @Query("SELECT f FROM Flight f WHERE f.flightNumber = :flightNumber AND f.status = 'SCHEDULED'")
    Optional<Flight> findScheduledFlightByNumber(String flightNumber);
}

