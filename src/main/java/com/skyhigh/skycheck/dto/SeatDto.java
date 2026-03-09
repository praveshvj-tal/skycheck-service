package com.skyhigh.skycheck.dto;

import com.skyhigh.skycheck.entity.Seat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatDto {
    private Long id;
    private String seatNumber;
    private String seatType;
    private String seatClass;
    private String state;
    private Long flightId;
}

