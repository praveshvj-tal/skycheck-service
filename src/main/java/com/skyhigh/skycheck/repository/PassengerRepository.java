package com.skyhigh.skycheck.repository;

import com.skyhigh.skycheck.entity.Passenger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PassengerRepository extends JpaRepository<Passenger, Long> {

    Optional<Passenger> findByEmail(String email);

    Optional<Passenger> findByPassportNumber(String passportNumber);
}

