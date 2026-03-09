package com.skyhigh.skycheck.controller;

import com.skyhigh.skycheck.entity.Flight;
import com.skyhigh.skycheck.service.FlightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/flights")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Flights", description = "APIs for flight information")
public class FlightController {

    private final FlightService flightService;

    @GetMapping
    @Operation(
        summary = "Get all flights",
        description = "Retrieve all available flights"
    )
    @ApiResponse(responseCode = "200", description = "Flights retrieved successfully")
    public ResponseEntity<List<Flight>> getAllFlights() {
        log.info("Fetching all flights");
        List<Flight> flights = flightService.getAllFlights();
        return ResponseEntity.ok(flights);
    }

    @GetMapping("/{flightId}")
    @Operation(
        summary = "Get flight by ID",
        description = "Retrieve flight details by ID"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Flight retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Flight not found")
    })
    public ResponseEntity<Flight> getFlightById(
            @Parameter(description = "Flight ID", required = true)
            @PathVariable Long flightId) {

        log.info("Fetching flight {}", flightId);
        Flight flight = flightService.getFlightById(flightId);
        return ResponseEntity.ok(flight);
    }

    @GetMapping("/by-number/{flightNumber}")
    @Operation(
        summary = "Get flight by number",
        description = "Retrieve flight details by flight number"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Flight retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Flight not found")
    })
    public ResponseEntity<Flight> getFlightByNumber(
            @Parameter(description = "Flight number (e.g., SH101)", required = true)
            @PathVariable String flightNumber) {

        log.info("Fetching flight by number: {}", flightNumber);
        Flight flight = flightService.getFlightByNumber(flightNumber);
        return ResponseEntity.ok(flight);
    }
}

