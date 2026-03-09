package com.skyhigh.skycheck.service;

import com.skyhigh.skycheck.config.ApplicationConfig;
import com.skyhigh.skycheck.entity.*;
import com.skyhigh.skycheck.exception.InvalidSeatStateException;
import com.skyhigh.skycheck.exception.ResourceNotFoundException;
import com.skyhigh.skycheck.exception.SeatAlreadyReservedException;
import com.skyhigh.skycheck.exception.SeatHoldExpiredException;
import com.skyhigh.skycheck.repository.SeatRepository;
import com.skyhigh.skycheck.repository.SeatReservationRepository;
import com.skyhigh.skycheck.repository.SeatStateHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeatService {

    private final SeatRepository seatRepository;
    private final SeatReservationRepository reservationRepository;
    private final SeatStateHistoryRepository historyRepository;
    private final SeatHoldManager seatHoldManager;
    private final ApplicationConfig appConfig;

    /**
     * Get all seats for a flight (cached for performance)
     */
    @Cacheable(value = "seatMap", key = "#flightId")
    @Transactional(readOnly = true)
    public List<Seat> getSeatsByFlightId(Long flightId) {
        log.debug("Fetching seats for flight {}", flightId);
        return seatRepository.findByFlightId(flightId);
    }

    /**
     * Get available seats for a flight
     */
    @Transactional(readOnly = true)
    public List<Seat> getAvailableSeatsByFlightId(Long flightId) {
        log.debug("Fetching available seats for flight {}", flightId);
        return seatRepository.findAvailableSeatsByFlightId(flightId);
    }

    /**
     * Hold a seat with optimistic locking and Redis TTL
     * Retries on optimistic locking failure
     */
    @Transactional
    @Retryable(
        retryFor = ObjectOptimisticLockingFailureException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 100)
    )
    @CacheEvict(value = "seatMap", key = "#seat.flight.id")
    public SeatReservation holdSeat(Seat seat, CheckIn checkIn) {
        log.info("Attempting to hold seat {} for passenger {}", seat.getId(), checkIn.getPassenger().getId());

        // Refresh seat to get latest version
        Seat currentSeat = seatRepository.findByIdWithLock(seat.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found: " + seat.getId()));

        // Check if seat is available
        if (!currentSeat.canBeHeld()) {
            throw new SeatAlreadyReservedException(
                String.format("Seat %s is not available. Current state: %s",
                    currentSeat.getSeatNumber(), currentSeat.getState())
            );
        }

        // Try to create hold in Redis (atomic operation)
        boolean holdCreated = seatHoldManager.createHold(currentSeat.getId(), checkIn.getPassenger().getId());
        if (!holdCreated) {
            throw new SeatAlreadyReservedException(
                String.format("Seat %s is already held by another passenger", currentSeat.getSeatNumber())
            );
        }

        try {
            // Update seat state to HELD
            currentSeat.hold();
            seatRepository.save(currentSeat);

            // Create reservation record
            LocalDateTime expiresAt = seatHoldManager.calculateExpirationTime();
            SeatReservation reservation = SeatReservation.builder()
                    .seat(currentSeat)
                    .checkIn(checkIn)
                    .passenger(checkIn.getPassenger())
                    .reservationType(SeatReservation.ReservationType.HOLD)
                    .holdExpiresAt(expiresAt)
                    .reservedAt(LocalDateTime.now())
                    .status(SeatReservation.ReservationStatus.ACTIVE)
                    .build();

            reservation = reservationRepository.save(reservation);

            // Record state change in audit trail
            recordStateChange(currentSeat, Seat.SeatState.AVAILABLE, Seat.SeatState.HELD,
                    checkIn.getPassenger(), "Seat held for check-in");

            log.info("Successfully held seat {} for passenger {}. Expires at {}",
                    currentSeat.getSeatNumber(), checkIn.getPassenger().getId(), expiresAt);

            return reservation;

        } catch (Exception e) {
            // Rollback Redis hold if database operation fails
            seatHoldManager.releaseHold(currentSeat.getId());
            throw e;
        }
    }

    /**
     * Confirm a seat reservation
     */
    @Transactional
    @Retryable(
        retryFor = ObjectOptimisticLockingFailureException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 100)
    )
    @CacheEvict(value = "seatMap", key = "#seat.flight.id")
    public SeatReservation confirmSeat(Seat seat, CheckIn checkIn) {
        log.info("Attempting to confirm seat {} for passenger {}", seat.getId(), checkIn.getPassenger().getId());

        // Refresh seat to get latest version
        Seat currentSeat = seatRepository.findByIdWithLock(seat.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found: " + seat.getId()));

        // Verify hold ownership in Redis
        if (!seatHoldManager.verifyHoldOwnership(currentSeat.getId(), checkIn.getPassenger().getId())) {
            throw new SeatHoldExpiredException(
                String.format("Seat hold for %s has expired or belongs to another passenger",
                    currentSeat.getSeatNumber())
            );
        }

        // Check if seat can be confirmed
        if (!currentSeat.canBeConfirmed()) {
            throw new InvalidSeatStateException(
                String.format("Seat %s cannot be confirmed. Current state: %s",
                    currentSeat.getSeatNumber(), currentSeat.getState())
            );
        }

        // Get active reservation
        SeatReservation reservation = reservationRepository.findActiveBySeatId(currentSeat.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No active reservation found for seat"));

        // Verify reservation belongs to the passenger
        if (!reservation.getPassenger().getId().equals(checkIn.getPassenger().getId())) {
            throw new SeatAlreadyReservedException("Seat is held by another passenger");
        }

        // Confirm the seat
        currentSeat.confirm();
        seatRepository.save(currentSeat);

        // Update reservation to confirmed
        reservation.confirm();
        reservationRepository.save(reservation);

        // Release Redis hold (no longer needed)
        seatHoldManager.releaseHold(currentSeat.getId());

        // Record state change in audit trail
        recordStateChange(currentSeat, Seat.SeatState.HELD, Seat.SeatState.CONFIRMED,
                checkIn.getPassenger(), "Seat confirmed for check-in");

        log.info("Successfully confirmed seat {} for passenger {}",
                currentSeat.getSeatNumber(), checkIn.getPassenger().getId());

        return reservation;
    }

    /**
     * Release a seat hold (make it available again)
     */
    @Transactional
    @CacheEvict(value = "seatMap", allEntries = true)
    public void releaseSeat(Long seatId, Long passengerId, String reason) {
        log.info("Releasing seat {} for passenger {}. Reason: {}", seatId, passengerId, reason);

        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found: " + seatId));

        if (seat.getState() == Seat.SeatState.CONFIRMED) {
            throw new InvalidSeatStateException("Cannot release a confirmed seat");
        }

        Seat.SeatState previousState = seat.getState();

        // Release the seat
        seat.release();
        seatRepository.save(seat);

        // Update reservation status
        reservationRepository.findActiveBySeatId(seatId).ifPresent(reservation -> {
            if (reservation.getPassenger().getId().equals(passengerId)) {
                reservation.release();
                reservationRepository.save(reservation);
            }
        });

        // Release Redis hold
        seatHoldManager.releaseHold(seatId);

        // Record state change
        recordStateChange(seat, previousState, Seat.SeatState.AVAILABLE, null, reason);

        log.info("Successfully released seat {}", seat.getSeatNumber());
    }

    /**
     * Expire a seat hold (automated cleanup)
     */
    @Transactional
    @CacheEvict(value = "seatMap", allEntries = true)
    public void expireSeatHold(Long seatId) {
        log.info("Expiring hold for seat {}", seatId);

        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found: " + seatId));

        if (seat.getState() == Seat.SeatState.HELD) {
            seat.release();
            seatRepository.save(seat);

            // Update reservation to expired
            reservationRepository.findActiveBySeatId(seatId).ifPresent(reservation -> {
                reservation.expire();
                reservationRepository.save(reservation);
            });

            // Record state change
            recordStateChange(seat, Seat.SeatState.HELD, Seat.SeatState.AVAILABLE,
                    null, "Hold expired after timeout");

            log.info("Expired hold for seat {}", seat.getSeatNumber());
        }
    }

    /**
     * Record seat state change in audit trail
     */
    private void recordStateChange(Seat seat, Seat.SeatState previousState,
                                   Seat.SeatState newState, Passenger passenger, String reason) {
        SeatStateHistory history = SeatStateHistory.builder()
                .seat(seat)
                .passenger(passenger)
                .previousState(previousState != null ? previousState.name() : null)
                .newState(newState.name())
                .reason(reason)
                .changedAt(LocalDateTime.now())
                .build();

        historyRepository.save(history);
        log.debug("Recorded state change for seat {}: {} -> {}", seat.getId(), previousState, newState);
    }

    /**
     * Get seat by ID
     */
    @Transactional(readOnly = true)
    public Seat getSeatById(Long seatId) {
        return seatRepository.findById(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found: " + seatId));
    }

    /**
     * Get seat state history
     */
    @Transactional(readOnly = true)
    public List<SeatStateHistory> getSeatHistory(Long seatId) {
        return historyRepository.findBySeatIdOrderByChangedAtDesc(seatId);
    }
}

