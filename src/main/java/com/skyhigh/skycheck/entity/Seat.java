package com.skyhigh.skycheck.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "seats",
    uniqueConstraints = @UniqueConstraint(columnNames = {"flight_id", "seat_number"}),
    indexes = {
        @Index(name = "idx_seats_flight_id", columnList = "flight_id"),
        @Index(name = "idx_seats_state", columnList = "state"),
        @Index(name = "idx_seats_flight_state", columnList = "flight_id, state")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_id", nullable = false)
    private Flight flight;

    @Column(name = "seat_number", nullable = false, length = 5)
    private String seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_type", nullable = false, length = 20)
    private SeatType seatType;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_class", nullable = false, length = 20)
    private SeatClass seatClass = SeatClass.ECONOMY;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private SeatState state = SeatState.AVAILABLE;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum SeatState {
        AVAILABLE,
        HELD,
        CONFIRMED
    }

    public enum SeatType {
        WINDOW,
        MIDDLE,
        AISLE
    }

    public enum SeatClass {
        ECONOMY,
        BUSINESS,
        FIRST_CLASS
    }

    /**
     * Business logic: Check if seat can be held
     */
    public boolean canBeHeld() {
        return this.state == SeatState.AVAILABLE;
    }

    /**
     * Business logic: Check if seat can be confirmed
     */
    public boolean canBeConfirmed() {
        return this.state == SeatState.HELD;
    }

    /**
     * Business logic: Hold the seat
     */
    public void hold() {
        if (!canBeHeld()) {
            throw new IllegalStateException(
                String.format("Seat %s cannot be held. Current state: %s", seatNumber, state)
            );
        }
        this.state = SeatState.HELD;
    }

    /**
     * Business logic: Confirm the seat
     */
    public void confirm() {
        if (!canBeConfirmed()) {
            throw new IllegalStateException(
                String.format("Seat %s cannot be confirmed. Current state: %s", seatNumber, state)
            );
        }
        this.state = SeatState.CONFIRMED;
    }

    /**
     * Business logic: Release the seat back to available
     */
    public void release() {
        if (this.state == SeatState.CONFIRMED) {
            throw new IllegalStateException(
                String.format("Seat %s is confirmed and cannot be released", seatNumber)
            );
        }
        this.state = SeatState.AVAILABLE;
    }
}

