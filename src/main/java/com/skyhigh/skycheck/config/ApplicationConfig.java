package com.skyhigh.skycheck.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class ApplicationConfig {

    private SeatHold seatHold = new SeatHold();
    private Baggage baggage = new Baggage();
    private Cache cache = new Cache();

    @Getter
    @Setter
    public static class SeatHold {
        private int ttlSeconds = 120;
        private int cleanupIntervalSeconds = 30;
    }

    @Getter
    @Setter
    public static class Baggage {
        private double maxWeightKg = 25.0;
        private double excessFeePerKg = 10.0;
    }

    @Getter
    @Setter
    public static class Cache {
        private int seatMapTtlSeconds = 10;
    }
}

