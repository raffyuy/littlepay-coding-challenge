package com.littlepay.transit.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row of the output file.
 *
 * <p>{@code started} and {@code fromStopId} are null for an UNSTARTED trip.
 * <p>{@code finished} and {@code toStopId} are null for an INCOMPLETE trip.
 */
public record Trip(
        Instant started,
        Instant finished,
        String fromStopId,
        String toStopId,
        BigDecimal chargeAmount,
        String companyId,
        String busId,
        String pan,
        TripStatus status) {
}
