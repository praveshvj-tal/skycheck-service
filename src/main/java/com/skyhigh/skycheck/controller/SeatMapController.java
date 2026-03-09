package com.skyhigh.skycheck.controller;

import com.skyhigh.skycheck.dto.SeatDto;
import com.skyhigh.skycheck.dto.SeatMapResponse;
import com.skyhigh.skycheck.entity.Flight;
import com.skyhigh.skycheck.entity.Seat;
import com.skyhigh.skycheck.mapper.DtoMapper;
import com.skyhigh.skycheck.service.FlightService;
import com.skyhigh.skycheck.service.SeatService;
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
@Tag(name = "Seat Map", description = "APIs for retrieving flight seat maps and availability")
public class SeatMapController {

    private final FlightService flightService;
    private final SeatService seatService;
    private final DtoMapper mapper;

    @GetMapping("/{flightId}/seats")
    @Operation(
        summary = "Get seat map for a flight",
        description = "Retrieves all seats for a flight with their current availability status. " +
                     "This endpoint is optimized for high performance with caching (P95 < 1s)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Seat map retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Flight not found")
    })
    public ResponseEntity<SeatMapResponse> getSeatMap(
            @Parameter(description = "Flight ID", required = true)
            @PathVariable Long flightId) {

        log.info("Fetching seat map for flight {}", flightId);

        Flight flight = flightService.getFlightById(flightId);
        List<Seat> seats = seatService.getSeatsByFlightId(flightId);

        SeatMapResponse response = mapper.toSeatMapResponse(flight, seats);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{flightId}/seats/available")
    @Operation(
        summary = "Get available seats for a flight",
        description = "Retrieves only the available seats for a flight"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Available seats retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Flight not found")
    })
    public ResponseEntity<List<SeatDto>> getAvailableSeats(
            @Parameter(description = "Flight ID", required = true)
            @PathVariable Long flightId) {

        log.info("Fetching available seats for flight {}", flightId);

        List<Seat> seats = seatService.getAvailableSeatsByFlightId(flightId);
        List<SeatDto> seatDtos = seats.stream()
                .map(mapper::toSeatDto)
                .toList();

        return ResponseEntity.ok(seatDtos);
    }
}
