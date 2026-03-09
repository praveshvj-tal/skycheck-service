package com.skyhigh.skycheck.schedule;

import com.skyhigh.skycheck.entity.SeatReservation;
import com.skyhigh.skycheck.repository.SeatReservationRepository;
import com.skyhigh.skycheck.service.SeatHoldManager;
import com.skyhigh.skycheck.service.SeatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Background job to clean up expired seat holds.
 * Runs every 30 seconds to ensure database consistency with Redis TTL.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SeatHoldCleanupJob {

    private final SeatReservationRepository reservationRepository;
    private final SeatService seatService;
    private final SeatHoldManager seatHoldManager;

    /**
     * Find and expire seat holds that have passed their expiration time
     */
    @Scheduled(fixedDelayString = "${app.seat-hold.cleanup-interval-seconds}000")
    @Transactional
    public void cleanupExpiredHolds() {
        log.debug("Running seat hold cleanup job");

        try {
            LocalDateTime now = LocalDateTime.now();
            List<SeatReservation> expiredReservations = reservationRepository.findExpiredHolds(now);

            if (expiredReservations.isEmpty()) {
                log.debug("No expired holds found");
                return;
            }

            log.info("Found {} expired seat holds to clean up", expiredReservations.size());

            for (SeatReservation reservation : expiredReservations) {
                try {
                    Long seatId = reservation.getSeat().getId();

                    // Check if Redis hold still exists (might have expired naturally)
                    if (!seatHoldManager.isHeld(seatId)) {
                        // Redis already expired, just update database
                        seatService.expireSeatHold(seatId);
                    } else {
                        // Redis still has hold, expire both
                        seatHoldManager.releaseHold(seatId);
                        seatService.expireSeatHold(seatId);
                    }

                    log.info("Cleaned up expired hold for seat {}", seatId);
                } catch (Exception e) {
                    log.error("Failed to cleanup expired hold for reservation {}: {}",
                            reservation.getId(), e.getMessage());
                }
            }

            log.info("Completed seat hold cleanup. Processed {} reservations", expiredReservations.size());

        } catch (Exception e) {
            log.error("Error during seat hold cleanup job", e);
        }
    }
}

