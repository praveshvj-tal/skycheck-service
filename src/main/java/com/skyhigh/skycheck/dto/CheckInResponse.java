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
public class CheckInResponse {
    private Long checkInId;
    private Long passengerId;
    private String passengerName;
    private Long flightId;
    private String flightNumber;
    private Long seatId;
    private String seatNumber;
    private String status;
    private LocalDateTime checkInTime;
    private LocalDateTime completedAt;
    private String message;
}

