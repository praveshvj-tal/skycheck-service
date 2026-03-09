package com.skyhigh.skycheck.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckInRequest {
    @NotNull(message = "Passenger ID is required")
    private Long passengerId;

    @NotBlank(message = "Flight number is required")
    private String flightNumber;
}

