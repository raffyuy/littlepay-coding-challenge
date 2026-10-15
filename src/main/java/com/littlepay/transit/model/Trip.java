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
        Status status) {

    public enum Status {
        /** Tapped on and off at different stops. */
        COMPLETED,
        /** Tapped on with no matching tap off. */
        INCOMPLETE,
        /** Tapped on and off at the same stop. */
        CANCELLED,
        /** Tapped off with no matching tap on. Not charged, needs review. */
        UNSTARTED
    }
}
