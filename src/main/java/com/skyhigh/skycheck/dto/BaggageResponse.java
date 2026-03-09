package com.skyhigh.skycheck.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaggageResponse {
    private Long baggageId;
    private Long checkInId;
    private BigDecimal weightKg;
    private BigDecimal excessWeightKg;
    private BigDecimal excessFeeAmount;
    private String paymentStatus;
    private boolean paymentRequired;
    private String message;
}

