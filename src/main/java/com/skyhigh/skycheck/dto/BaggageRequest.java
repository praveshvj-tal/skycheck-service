package com.skyhigh.skycheck.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaggageRequest {
    @NotNull(message = "Check-in ID is required")
    private Long checkInId;

    @NotNull(message = "Weight is required")
    @DecimalMin(value = "0.1", message = "Weight must be greater than 0")
    private BigDecimal weightKg;
}

