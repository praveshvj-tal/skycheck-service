package com.skyhigh.skycheck.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "baggage", indexes = {
    @Index(name = "idx_baggage_checkin", columnList = "check_in_id"),
    @Index(name = "idx_baggage_payment_status", columnList = "payment_status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Baggage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_in_id", nullable = false)
    private CheckIn checkIn;

    @Column(name = "weight_kg", nullable = false, precision = 5, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "excess_weight_kg", precision = 5, scale = 2)
    private BigDecimal excessWeightKg = BigDecimal.ZERO;

    @Column(name = "excess_fee_amount", precision = 10, scale = 2)
    private BigDecimal excessFeeAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.NOT_REQUIRED;

    @Column(name = "payment_id", length = 100)
    private String paymentId;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum PaymentStatus {
        NOT_REQUIRED,
        PENDING,
        COMPLETED,
        FAILED
    }

    /**
     * Check if payment is required
     */
    public boolean isPaymentRequired() {
        return this.excessWeightKg.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Mark payment as completed
     */
    public void markPaymentCompleted(String paymentId) {
        this.paymentStatus = PaymentStatus.COMPLETED;
        this.paymentId = paymentId;
        this.paidAt = LocalDateTime.now();
    }

    /**
     * Mark payment as failed
     */
    public void markPaymentFailed() {
        this.paymentStatus = PaymentStatus.FAILED;
    }
}

