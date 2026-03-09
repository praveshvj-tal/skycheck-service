package com.skyhigh.skycheck.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatMapResponse {
    private Long flightId;
    private String flightNumber;
    private List<SeatDto> seats;
    private int totalSeats;
    private int availableSeats;
    private int heldSeats;
    private int confirmedSeats;
}

