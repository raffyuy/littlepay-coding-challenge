package com.littlepay.transit.model;

import java.time.Instant;

/** One row of the input file signifying a tap event */
public record Tap(
        long id,
        Instant dateTimeUtc,
        TapType tapType,
        String stopId,
        String companyId,
        String busId,
        String pan) {
}
