package com.skyhigh.skycheck.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatHoldResponse {
    private Long reservationId;
    private Long seatId;
    private String seatNumber;
    private String state;
    private LocalDateTime holdExpiresAt;
    private int remainingSeconds;
    private String message;
}

