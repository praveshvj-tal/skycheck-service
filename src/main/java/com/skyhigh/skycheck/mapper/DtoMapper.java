package com.skyhigh.skycheck.mapper;

import com.skyhigh.skycheck.dto.*;
import com.skyhigh.skycheck.entity.*;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class DtoMapper {

    public SeatDto toSeatDto(Seat seat) {
        return SeatDto.builder()
                .id(seat.getId())
                .seatNumber(seat.getSeatNumber())
                .seatType(seat.getSeatType().name())
                .seatClass(seat.getSeatClass().name())
                .state(seat.getState().name())
                .flightId(seat.getFlight().getId())
                .build();
    }

    public SeatMapResponse toSeatMapResponse(Flight flight, List<Seat> seats) {
        List<SeatDto> seatDtos = seats.stream()
                .map(this::toSeatDto)
                .collect(Collectors.toList());

        long availableCount = seats.stream()
                .filter(s -> s.getState() == Seat.SeatState.AVAILABLE)
                .count();

        long heldCount = seats.stream()
                .filter(s -> s.getState() == Seat.SeatState.HELD)
                .count();

        long confirmedCount = seats.stream()
                .filter(s -> s.getState() == Seat.SeatState.CONFIRMED)
                .count();

        return SeatMapResponse.builder()
                .flightId(flight.getId())
                .flightNumber(flight.getFlightNumber())
                .seats(seatDtos)
                .totalSeats(seats.size())
                .availableSeats((int) availableCount)
                .heldSeats((int) heldCount)
                .confirmedSeats((int) confirmedCount)
                .build();
    }

    public SeatHoldResponse toSeatHoldResponse(SeatReservation reservation, Long remainingSeconds) {
        Seat seat = reservation.getSeat();

        return SeatHoldResponse.builder()
                .reservationId(reservation.getId())
                .seatId(seat.getId())
                .seatNumber(seat.getSeatNumber())
                .state(seat.getState().name())
                .holdExpiresAt(reservation.getHoldExpiresAt())
                .remainingSeconds(remainingSeconds.intValue())
                .message("Seat held successfully. Complete check-in within " + remainingSeconds + " seconds.")
                .build();
    }

    public CheckInResponse toCheckInResponse(CheckIn checkIn) {
        return CheckInResponse.builder()
                .checkInId(checkIn.getId())
                .passengerId(checkIn.getPassenger().getId())
                .passengerName(checkIn.getPassenger().getFirstName() + " " + checkIn.getPassenger().getLastName())
                .flightId(checkIn.getFlight().getId())
                .flightNumber(checkIn.getFlight().getFlightNumber())
                .seatId(checkIn.getSeat() != null ? checkIn.getSeat().getId() : null)
                .seatNumber(checkIn.getSeat() != null ? checkIn.getSeat().getSeatNumber() : null)
                .status(checkIn.getStatus().name())
                .checkInTime(checkIn.getCheckInTime())
                .completedAt(checkIn.getCompletedAt())
                .message(getCheckInStatusMessage(checkIn))
                .build();
    }

    public BaggageResponse toBaggageResponse(Baggage baggage) {
        return BaggageResponse.builder()
                .baggageId(baggage.getId())
                .checkInId(baggage.getCheckIn().getId())
                .weightKg(baggage.getWeightKg())
                .excessWeightKg(baggage.getExcessWeightKg())
                .excessFeeAmount(baggage.getExcessFeeAmount())
                .paymentStatus(baggage.getPaymentStatus().name())
                .paymentRequired(baggage.isPaymentRequired())
                .message(getBaggageMessage(baggage))
                .build();
    }

    public PaymentResponse toPaymentResponse(Baggage baggage) {
        return PaymentResponse.builder()
                .paymentId(baggage.getPaymentId())
                .amount(baggage.getExcessFeeAmount())
                .status(baggage.getPaymentStatus().name())
                .paidAt(baggage.getPaidAt())
                .message("Payment processed successfully")
                .build();
    }

    private String getCheckInStatusMessage(CheckIn checkIn) {
        return switch (checkIn.getStatus()) {
            case IN_PROGRESS -> "Check-in in progress. Please select a seat.";
            case WAITING_FOR_PAYMENT -> "Payment required for excess baggage.";
            case COMPLETED -> "Check-in completed successfully.";
            case CANCELLED -> "Check-in has been cancelled.";
        };
    }

    private String getBaggageMessage(Baggage baggage) {
        if (baggage.isPaymentRequired()) {
            return String.format("Baggage exceeds limit by %.2f kg. Fee: $%.2f",
                    baggage.getExcessWeightKg(), baggage.getExcessFeeAmount());
        }
        return "Baggage within allowed limit. No additional fee required.";
    }
}

