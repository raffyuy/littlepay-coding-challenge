package com.littlepay.transit.fare;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class FareTable {

    // sorted so (Stop2, Stop1) == (Stop1, Stop2)
    private record StopPair(String stopA, String stopB) {
        StopPair {
            if (stopA.compareTo(stopB) > 0) {
                String swap = stopA;
                stopA = stopB;
                stopB = swap;
            }
        }
    }

    private final Map<StopPair, BigDecimal> fares;
    private final Map<String, BigDecimal> maxFareByStop = new HashMap<>();

    private FareTable(Map<StopPair, BigDecimal> fares) {
        this.fares = fares;
        fares.forEach((pair, fare) -> {
            maxFareByStop.merge(pair.stopA(), fare, BigDecimal::max);
            maxFareByStop.merge(pair.stopB(), fare, BigDecimal::max);
        });
    }

    // One table for all companies and buses. Hard-coded, not configurable.
    public static FareTable standard() {
        return new FareTable(Map.of(
                new StopPair("Stop1", "Stop2"), new BigDecimal("3.25"),
                new StopPair("Stop2", "Stop3"), new BigDecimal("5.50"),
                new StopPair("Stop1", "Stop3"), new BigDecimal("7.30")));
    }

    public BigDecimal fareBetween(String fromStop, String toStop) {
        BigDecimal fare = fares.get(new StopPair(fromStop, toStop));
        if (fare == null) {
            throw new IllegalArgumentException("No fare between " + fromStop + " and " + toStop);
        }
        return fare;
    }

    // used to charge incomplete trips
    public BigDecimal maxFareFrom(String stop) {
        BigDecimal fare = maxFareByStop.get(stop);
        if (fare == null) {
            throw new IllegalArgumentException("No fares from " + stop);
        }
        return fare;
    }
}
