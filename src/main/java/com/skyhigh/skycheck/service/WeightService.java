package com.skyhigh.skycheck.service;

import java.math.BigDecimal;

/**
 * Simulated external weight service interface.
 */
public interface WeightService {
    BigDecimal measureWeight(BigDecimal declaredWeightKg);
}

