package com.skyhigh.skycheck.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatConfirmRequest {
    @NotNull(message = "Seat ID is required")
    private Long seatId;

    @NotNull(message = "Passenger ID is required")
    private Long passengerId;

    @NotNull(message = "Check-in ID is required")
    private Long checkInId;
}

