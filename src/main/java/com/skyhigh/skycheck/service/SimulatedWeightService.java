package com.skyhigh.skycheck.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Simulated external weight service.
 * Returns the provided weight to keep behavior deterministic in tests.
 */
@Service
@Slf4j
public class SimulatedWeightService implements WeightService {

    @Override
    public BigDecimal measureWeight(BigDecimal declaredWeightKg) {
        log.debug("Simulated weight service measured {} kg", declaredWeightKg);
        return declaredWeightKg;
    }
}

