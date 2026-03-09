package com.skyhigh.skycheck.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "seat_reservations", indexes = {
    @Index(name = "idx_reservations_seat", columnList = "seat_id"),
    @Index(name = "idx_reservations_checkin", columnList = "check_in_id"),
    @Index(name = "idx_reservations_status", columnList = "status"),
    @Index(name = "idx_reservations_expires", columnList = "hold_expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_in_id", nullable = false)
    private CheckIn checkIn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passenger_id", nullable = false)
    private Passenger passenger;

    @Enumerated(EnumType.STRING)
    @Column(name = "reservation_type", nullable = false, length = 20)
    private ReservationType reservationType;

    @Column(name = "hold_expires_at")
    private LocalDateTime holdExpiresAt;

    @Column(name = "reserved_at", nullable = false)
    private LocalDateTime reservedAt = LocalDateTime.now();

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReservationStatus status = ReservationStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum ReservationType {
        HOLD,
        CONFIRM
    }

    public enum ReservationStatus {
        ACTIVE,
        EXPIRED,
        RELEASED,
        CONFIRMED
    }

    /**
     * Check if hold has expired
     */
    public boolean isExpired() {
        return this.reservationType == ReservationType.HOLD
            && this.holdExpiresAt != null
            && LocalDateTime.now().isAfter(this.holdExpiresAt)
            && this.status == ReservationStatus.ACTIVE;
    }

    /**
     * Mark as expired
     */
    public void expire() {
        this.status = ReservationStatus.EXPIRED;
        this.releasedAt = LocalDateTime.now();
    }

    /**
     * Confirm the reservation
     */
    public void confirm() {
        this.status = ReservationStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    /**
     * Release the reservation
     */
    public void release() {
        this.status = ReservationStatus.RELEASED;
        this.releasedAt = LocalDateTime.now();
    }
}

