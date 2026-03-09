package com.skyhigh.skycheck.service;

import com.skyhigh.skycheck.entity.Flight;
import com.skyhigh.skycheck.exception.ResourceNotFoundException;
import com.skyhigh.skycheck.repository.FlightRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlightService {

    private final FlightRepository flightRepository;

    @Transactional(readOnly = true)
    public Flight getFlightById(Long flightId) {
        return flightRepository.findById(flightId)
                .orElseThrow(() -> new ResourceNotFoundException("Flight not found: " + flightId));
    }

    @Transactional(readOnly = true)
    public Flight getFlightByNumber(String flightNumber) {
        return flightRepository.findByFlightNumber(flightNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Flight not found: " + flightNumber));
    }

    @Transactional(readOnly = true)
    public List<Flight> getAllFlights() {
        return flightRepository.findAll();
    }
}

