package com.skyhigh.skycheck.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "seat_state_history", indexes = {
    @Index(name = "idx_history_seat", columnList = "seat_id"),
    @Index(name = "idx_history_changed_at", columnList = "changed_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatStateHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passenger_id")
    private Passenger passenger;

    @Column(name = "previous_state", length = 20)
    private String previousState;

    @Column(name = "new_state", nullable = false, length = 20)
    private String newState;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt = LocalDateTime.now();
}

