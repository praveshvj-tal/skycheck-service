package com.skyhigh.skycheck.service;

import com.skyhigh.skycheck.entity.*;
import com.skyhigh.skycheck.exception.InvalidSeatStateException;
import com.skyhigh.skycheck.exception.PaymentRequiredException;
import com.skyhigh.skycheck.exception.ResourceNotFoundException;
import com.skyhigh.skycheck.repository.CheckInRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckInService {

    private final CheckInRepository checkInRepository;
    private final FlightService flightService;
    private final PassengerService passengerService;
    private final SeatService seatService;
    private final BaggageService baggageService;

    /**
     * Initiate check-in for a passenger
     */
    @Transactional
    public CheckIn initiateCheckIn(Long passengerId, String flightNumber) {
        log.info("Initiating check-in for passenger {} on flight {}", passengerId, flightNumber);

        Passenger passenger = passengerService.getPassengerById(passengerId);
        Flight flight = flightService.getFlightByNumber(flightNumber);

        // Check if passenger already has a check-in for this flight
        checkInRepository.findByPassengerIdAndFlightId(passengerId, flight.getId())
                .ifPresent(existing -> {
                    if (existing.getStatus() == CheckIn.CheckInStatus.COMPLETED) {
                        throw new InvalidSeatStateException(
                            "Passenger has already completed check-in for this flight"
                        );
                    }
                });

        CheckIn checkIn = CheckIn.builder()
                .passenger(passenger)
                .flight(flight)
                .status(CheckIn.CheckInStatus.IN_PROGRESS)
                .checkInTime(java.time.LocalDateTime.now())
                .build();

        checkIn = checkInRepository.save(checkIn);
        log.info("Check-in initiated with ID: {}", checkIn.getId());

        return checkIn;
    }

    /**
     * Hold a seat during check-in
     */
    @Transactional
    public SeatReservation holdSeat(Long checkInId, Long seatId) {
        log.info("Holding seat {} for check-in {}", seatId, checkInId);

        CheckIn checkIn = getCheckInById(checkInId);

        if (checkIn.getStatus() != CheckIn.CheckInStatus.IN_PROGRESS) {
            throw new InvalidSeatStateException(
                "Cannot hold seat. Check-in status: " + checkIn.getStatus()
            );
        }

        // Release previous seat if exists
        if (checkIn.getSeat() != null) {
            seatService.releaseSeat(checkIn.getSeat().getId(),
                    checkIn.getPassenger().getId(), "Passenger selected different seat");
        }

        Seat seat = seatService.getSeatById(seatId);

        // Verify seat belongs to same flight
        if (!seat.getFlight().getId().equals(checkIn.getFlight().getId())) {
            throw new InvalidSeatStateException("Seat does not belong to the check-in flight");
        }

        // Hold the seat
        SeatReservation reservation = seatService.holdSeat(seat, checkIn);

        // Update check-in with seat
        checkIn.setSeat(seat);
        checkInRepository.save(checkIn);

        return reservation;
    }

    /**
     * Confirm seat reservation for check-in
     */
    @Transactional
    public SeatReservation confirmSeat(Long checkInId, Long seatId) {
        log.info("Confirming seat {} for check-in {}", seatId, checkInId);

        CheckIn checkIn = getCheckInById(checkInId);

        if (checkIn.getSeat() == null || !checkIn.getSeat().getId().equals(seatId)) {
            throw new InvalidSeatStateException("Seat is not held for this check-in");
        }

        Seat seat = seatService.getSeatById(seatId);

        // Confirm the seat
        return seatService.confirmSeat(seat, checkIn);
    }

    /**
     * Add baggage to check-in
     */
    @Transactional
    public Baggage addBaggage(Long checkInId, java.math.BigDecimal weightKg) {
        log.info("Adding baggage to check-in {}", checkInId);

        CheckIn checkIn = getCheckInById(checkInId);

        if (checkIn.getStatus() == CheckIn.CheckInStatus.COMPLETED) {
            throw new InvalidSeatStateException("Check-in already completed");
        }

        Baggage baggage = baggageService.addBaggage(checkIn, weightKg);

        // If payment required, update check-in status
        if (baggage.isPaymentRequired()) {
            checkIn.waitForPayment();
            checkInRepository.save(checkIn);
            log.info("Check-in {} moved to WAITING_FOR_PAYMENT status", checkInId);
        }

        return baggage;
    }

    /**
     * Process payment for excess baggage
     */
    @Transactional
    public Baggage processPayment(Long checkInId, java.math.BigDecimal amount, String paymentMethod) {
        log.info("Processing payment for check-in {}", checkInId);

        CheckIn checkIn = getCheckInById(checkInId);

        if (checkIn.getStatus() != CheckIn.CheckInStatus.WAITING_FOR_PAYMENT) {
            throw new InvalidSeatStateException(
                "Check-in is not waiting for payment. Current status: " + checkIn.getStatus()
            );
        }

        Baggage baggage = baggageService.getBaggageByCheckInId(checkInId);

        // Verify payment amount
        if (amount.compareTo(baggage.getExcessFeeAmount()) < 0) {
            throw new IllegalArgumentException(
                "Payment amount insufficient. Required: " + baggage.getExcessFeeAmount()
            );
        }

        // Process payment (simulated)
        String paymentId = generatePaymentId();
        baggage = baggageService.processPayment(baggage.getId(), paymentId);

        // Resume check-in
        checkIn.setStatus(CheckIn.CheckInStatus.IN_PROGRESS);
        checkInRepository.save(checkIn);

        log.info("Payment processed successfully for check-in {}", checkInId);
        return baggage;
    }

    /**
     * Complete check-in
     */
    @Transactional
    public CheckIn completeCheckIn(Long checkInId) {
        log.info("Completing check-in {}", checkInId);

        CheckIn checkIn = getCheckInById(checkInId);

        // Validate check-in can be completed
        if (checkIn.getStatus() == CheckIn.CheckInStatus.COMPLETED) {
            throw new InvalidSeatStateException("Check-in already completed");
        }

        if (checkIn.getStatus() == CheckIn.CheckInStatus.WAITING_FOR_PAYMENT) {
            throw new PaymentRequiredException("Payment required before completing check-in");
        }

        if (checkIn.getSeat() == null) {
            throw new InvalidSeatStateException("Seat must be selected before completing check-in");
        }

        // Verify seat is confirmed
        if (checkIn.getSeat().getState() != Seat.SeatState.CONFIRMED) {
            throw new InvalidSeatStateException("Seat must be confirmed before completing check-in");
        }

        // Complete check-in
        checkIn.complete();
        checkIn = checkInRepository.save(checkIn);

        log.info("Check-in {} completed successfully", checkInId);
        return checkIn;
    }

    /**
     * Get check-in by ID
     */
    @Transactional(readOnly = true)
    public CheckIn getCheckInById(Long checkInId) {
        return checkInRepository.findById(checkInId)
                .orElseThrow(() -> new ResourceNotFoundException("Check-in not found: " + checkInId));
    }

    /**
     * Get all check-ins for a passenger
     */
    @Transactional(readOnly = true)
    public List<CheckIn> getCheckInsByPassengerId(Long passengerId) {
        return checkInRepository.findByPassengerId(passengerId);
    }

    /**
     * Cancel check-in
     */
    @Transactional
    public void cancelCheckIn(Long checkInId) {
        log.info("Cancelling check-in {}", checkInId);

        CheckIn checkIn = getCheckInById(checkInId);

        // Release seat if held
        if (checkIn.getSeat() != null && checkIn.getSeat().getState() != Seat.SeatState.CONFIRMED) {
            seatService.releaseSeat(checkIn.getSeat().getId(),
                    checkIn.getPassenger().getId(), "Check-in cancelled");
        }

        checkIn.cancel();
        checkInRepository.save(checkIn);
    }

    /**
     * Generate simulated payment ID
     */
    private String generatePaymentId() {
        return "PAY-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 1000);
    }
}

