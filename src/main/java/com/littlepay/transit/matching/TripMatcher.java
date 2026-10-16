package com.littlepay.transit.matching;

import com.littlepay.transit.fare.FareTable;
import com.littlepay.transit.model.Tap;
import com.littlepay.transit.model.Trip;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for taps, matching them to create Trip records.
 * This requires that the taps are coming in sequential time order, because it will hold at most one open Tap.Type.ON
 * per PAN.
 */
public class TripMatcher {

    private final FareTable fareTable;
    private final Duration maxTripLength;

    private final Map<String, Tap> openTapsByPan = new LinkedHashMap<>();

    /**
     * @param fareTable - mapping of stops to fares
     * @param maxTripLength - longest allowed gap between an ON and its OFF to consider it a trip
     */
    public TripMatcher(FareTable fareTable, Duration maxTripLength) {
        this.fareTable = fareTable;
        this.maxTripLength = maxTripLength;
    }

    /**
     * Main entry for tap events.
     * @return list of max 2 trips (2 when an OFF comes after the max trip length)
     */
    public List<Trip> onTap(Tap tap) {
        return switch (tap.tapType()) {
            case ON -> handleOn(tap);
            case OFF -> handleOff(tap);
        };
    }

    private List<Trip> handleOn(Tap on) {
        Tap previousOn = openTapsByPan.put(on.pan(), on);
        if (previousOn == null) {
            return List.of();
        }
        // S#4: has active ON, so the second ON closes the previous one.
        return List.of(incomplete(previousOn));
    }

    private List<Trip> handleOff(Tap off) {
        Tap on = openTapsByPan.remove(off.pan());

        // S#5: Unmatched
        if (on == null) {
            return List.of(unmatched(off));
        }

        // S#6: exceeded max trip length
        if (!withinMaxTripLength(on, off)) {
            return List.of(incomplete(on), unmatched(off));
        }

        // S#2: same stop
        if (on.stopId().equals(off.stopId())) {
            return List.of(cancelled(on, off));
        }

        // S#1, S#7 - normal completion
        return List.of(completed(on, off));
    }

    // Inclusive boundary
    private boolean withinMaxTripLength(Tap on, Tap off) {
        return !off.dateTimeUtc().isAfter(on.dateTimeUtc().plus(maxTripLength));
    }

    private Trip completed(Tap on, Tap off) {
        BigDecimal fare = fareTable.fareBetween(on.stopId(), off.stopId());
        return closedTrip(on, off, fare, Trip.Status.COMPLETED);
    }

    private Trip cancelled(Tap on, Tap off) {
        return closedTrip(on, off, BigDecimal.ZERO, Trip.Status.CANCELLED);
    }

    private Trip incomplete(Tap on) {
        return new Trip(on.dateTimeUtc(), null, on.stopId(), null,
                fareTable.maxFareFrom(on.stopId()), on.companyId(), on.busId(), on.pan(), Trip.Status.INCOMPLETE);
    }

    private Trip unmatched(Tap off) {
        return new Trip(null, off.dateTimeUtc(), null, off.stopId(),
                BigDecimal.ZERO, off.companyId(), off.busId(), off.pan(), Trip.Status.UNMATCHED);
    }

    private Trip closedTrip(Tap on, Tap off, BigDecimal charge, Trip.Status status) {
        return new Trip(on.dateTimeUtc(), off.dateTimeUtc(), on.stopId(), off.stopId(),
                charge, on.companyId(), on.busId(), on.pan(), status);
    }

    // S#3: End of input: any trip still open was never finished.
    public List<Trip> flush() {
        List<Trip> trips = openTapsByPan.values().stream().map(this::incomplete).toList();
        openTapsByPan.clear();
        return trips;
    }
}
