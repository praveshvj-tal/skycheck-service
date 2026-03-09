package com.skyhigh.skycheck.service;

import com.skyhigh.skycheck.entity.Passenger;
import com.skyhigh.skycheck.exception.ResourceNotFoundException;
import com.skyhigh.skycheck.repository.PassengerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PassengerService {

    private final PassengerRepository passengerRepository;

    @Transactional(readOnly = true)
    public Passenger getPassengerById(Long passengerId) {
        return passengerRepository.findById(passengerId)
                .orElseThrow(() -> new ResourceNotFoundException("Passenger not found: " + passengerId));
    }

    @Transactional(readOnly = true)
    public Passenger getPassengerByEmail(String email) {
        return passengerRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Passenger not found with email: " + email));
    }

    @Transactional
    public Passenger createPassenger(Passenger passenger) {
        return passengerRepository.save(passenger);
    }
}

