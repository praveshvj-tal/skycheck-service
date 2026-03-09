package com.skyhigh.skycheck.service;

import com.skyhigh.skycheck.config.ApplicationConfig;
import com.skyhigh.skycheck.entity.Baggage;
import com.skyhigh.skycheck.entity.CheckIn;
import com.skyhigh.skycheck.exception.InvalidBaggageWeightException;
import com.skyhigh.skycheck.exception.ResourceNotFoundException;
import com.skyhigh.skycheck.repository.BaggageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class BaggageService {

    private final BaggageRepository baggageRepository;
    private final ApplicationConfig appConfig;
    private final WeightService weightService;

    /**
     * Add baggage to check-in and calculate excess fees
     */
    @Transactional
    public Baggage addBaggage(CheckIn checkIn, BigDecimal weightKg) {
        log.info("Adding baggage of weight {} kg to check-in {}", weightKg, checkIn.getId());

        BigDecimal measuredWeight = weightService.measureWeight(weightKg);

        if (measuredWeight.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidBaggageWeightException("Baggage weight must be greater than 0");
        }

        BigDecimal maxWeight = BigDecimal.valueOf(appConfig.getBaggage().getMaxWeightKg());
        BigDecimal excessWeight = measuredWeight.subtract(maxWeight);

        Baggage baggage = Baggage.builder()
                .checkIn(checkIn)
                .weightKg(measuredWeight)
                .build();

        // Check if weight exceeds limit
        if (excessWeight.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal feePerKg = BigDecimal.valueOf(appConfig.getBaggage().getExcessFeePerKg());
            BigDecimal totalFee = excessWeight.multiply(feePerKg);

            baggage.setExcessWeightKg(excessWeight);
            baggage.setExcessFeeAmount(totalFee);
            baggage.setPaymentStatus(Baggage.PaymentStatus.PENDING);

            log.info("Baggage exceeds limit by {} kg. Fee: ${}", excessWeight, totalFee);
        } else {
            baggage.setExcessWeightKg(BigDecimal.ZERO);
            baggage.setExcessFeeAmount(BigDecimal.ZERO);
            baggage.setPaymentStatus(Baggage.PaymentStatus.NOT_REQUIRED);

            log.info("Baggage within allowed limit. No fee required.");
        }

        return baggageRepository.save(baggage);
    }

    /**
     * Process payment for excess baggage
     */
    @Transactional
    public Baggage processPayment(Long baggageId, String paymentId) {
        log.info("Processing payment {} for baggage {}", paymentId, baggageId);

        Baggage baggage = baggageRepository.findById(baggageId)
                .orElseThrow(() -> new ResourceNotFoundException("Baggage not found: " + baggageId));

        if (baggage.getPaymentStatus() == Baggage.PaymentStatus.COMPLETED) {
            log.warn("Payment already completed for baggage {}", baggageId);
            return baggage;
        }

        // Simulate payment processing (in real system, would call payment gateway)
        baggage.markPaymentCompleted(paymentId);
        baggageRepository.save(baggage);

        log.info("Payment completed for baggage {}", baggageId);
        return baggage;
    }

    /**
     * Get baggage by check-in ID
     */
    @Transactional(readOnly = true)
    public Baggage getBaggageByCheckInId(Long checkInId) {
        return baggageRepository.findByCheckInId(checkInId)
                .orElseThrow(() -> new ResourceNotFoundException("No baggage found for check-in: " + checkInId));
    }
}
