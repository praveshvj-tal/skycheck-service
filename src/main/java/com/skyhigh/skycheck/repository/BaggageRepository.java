package com.skyhigh.skycheck.repository;

import com.skyhigh.skycheck.entity.Baggage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BaggageRepository extends JpaRepository<Baggage, Long> {

    @Query("SELECT b FROM Baggage b WHERE b.checkIn.id = :checkInId")
    Optional<Baggage> findByCheckInId(@Param("checkInId") Long checkInId);
}

