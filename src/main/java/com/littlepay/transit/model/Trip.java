package com.littlepay.transit.model;

import java.math.BigDecimal;
import java.time.Instant;

/** One row of the output file.
 * UNSTARTED trips have no started/fromStopId,
 * INCOMPLETE trips have no finished/toStopId.
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
        COMPLETED,
        INCOMPLETE,
        CANCELLED,
        // tapped off with no matching tap on (needs review)
        UNSTARTED
    }
}
