package com.skyhigh.skycheck.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "check_ins", indexes = {
    @Index(name = "idx_checkins_passenger", columnList = "passenger_id"),
    @Index(name = "idx_checkins_flight", columnList = "flight_id"),
    @Index(name = "idx_checkins_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckIn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passenger_id", nullable = false)
    private Passenger passenger;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_id", nullable = false)
    private Flight flight;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id")
    private Seat seat;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CheckInStatus status = CheckInStatus.IN_PROGRESS;

    @Column(name = "check_in_time", nullable = false)
    private LocalDateTime checkInTime = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum CheckInStatus {
        IN_PROGRESS,
        WAITING_FOR_PAYMENT,
        COMPLETED,
        CANCELLED
    }

    /**
     * Business logic: Check if check-in can be completed
     */
    public boolean canBeCompleted() {
        return this.status == CheckInStatus.IN_PROGRESS && this.seat != null;
    }

    /**
     * Business logic: Mark as waiting for payment
     */
    public void waitForPayment() {
        this.status = CheckInStatus.WAITING_FOR_PAYMENT;
    }

    /**
     * Business logic: Complete the check-in
     */
    public void complete() {
        if (!canBeCompleted() && this.status != CheckInStatus.WAITING_FOR_PAYMENT) {
            throw new IllegalStateException(
                String.format("Check-in %d cannot be completed. Current status: %s", id, status)
            );
        }
        this.status = CheckInStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Business logic: Cancel the check-in
     */
    public void cancel() {
        if (this.status == CheckInStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel a completed check-in");
        }
        this.status = CheckInStatus.CANCELLED;
    }
}

