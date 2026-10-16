package com.littlepay.transit.matching;

import com.littlepay.transit.fare.FareTable;
import com.littlepay.transit.model.Tap;
import com.littlepay.transit.model.Trip;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;

/**
 * Handler for taps, matching them to create Trip records.
 * This requires that the taps are coming in sequential time order, because it will hold at most one open Tap.Type.ON
 * per PAN.
 */
public class TripMatcher {

    // a repeated ON at the same stop, company, and bus, within this window is treated as one tap
    private static final Duration DUPLICATE_WINDOW = Duration.ofMinutes(1);

    // determines when cancellations can happen. later than that it's a round trip
    private static final Duration CANCELLATION_WINDOW = Duration.ofMinutes(10);

    private final FareTable fareTable;
    private final Duration maxTripLength;

    // tracks the open trips in the order of ingestion. the ordering + maxTripLength allows us to expireOpenTrips()
    private final SequencedMap<String, Tap> openTapsByPan = new LinkedHashMap<>();

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
     * @return trips closed by this tap, plus any open trips (from any PAN) that should expire based off this tap time
     * @throws IllegalArgumentException if the tap's stop isn't in the fare table
     */
    public List<Trip> onTap(Tap tap) {
        // an unknown stop is bad data. reject the tap rather than guess a fare
        fareTable.requireKnownStop(tap.stopId());

        List<Trip> trips = expireOpenTrips(tap.dateTimeUtc());
        trips.addAll(switch (tap.tapType()) {
            case ON -> handleOn(tap);
            case OFF -> handleOff(tap);
        });
        return trips;
    }

    // S#6, S#11: a Tap OFF after the maxTripLength can't be used to close a Tap ON, so it's INCOMPLETE now.
    // Relies on tap time as the clock. Attempt to reduce memory use by only keeping open trips within the max trip length.
    private List<Trip> expireOpenTrips(Instant now) {
        List<Trip> expired = new ArrayList<>();
        while (!openTapsByPan.isEmpty() && hasExpired(openTapsByPan.firstEntry().getValue(), now)) {
            expired.add(incomplete(openTapsByPan.pollFirstEntry().getValue()));
        }
        return expired;
    }

    private boolean hasExpired(Tap on, Instant now) {
        return now.isAfter(on.dateTimeUtc().plus(maxTripLength));
    }

    private List<Trip> handleOn(Tap on) {
        Tap openOn = openTapsByPan.get(on.pan());

        // S#10: duplicate ON, e.g. the card was read twice
        if (isDuplicate(openOn, on)) {
            return List.of();
        }

        openTapsByPan.putLast(on.pan(), on);
        if (openOn == null) {
            return List.of();
        }
        // S#4: has active ON, so the second ON closes the previous one.
        return List.of(incomplete(openOn));
    }

    private boolean isDuplicate(Tap openOn, Tap on) {
        return openOn != null
                && openOn.stopId().equals(on.stopId())
                && sameCompanyAndBus(openOn, on)
                && Duration.between(openOn.dateTimeUtc(), on.dateTimeUtc()).compareTo(DUPLICATE_WINDOW) < 0;
    }

    private List<Trip> handleOff(Tap off) {
        Tap on = openTapsByPan.remove(off.pan());

        // S#5: Unmatched
        if (on == null) {
            return List.of(unmatched(off));
        }

        // S#9: OFF on a different company or bus isn't the same trip
        // (S#6, an OFF past the max trip length, never gets here: expireOpenTrips() already closed that ON)
        if (!sameCompanyAndBus(on, off)) {
            return List.of(incomplete(on), unmatched(off));
        }

        if (on.stopId().equals(off.stopId())) {
            // S#2: same stop, soon after the ON
            if (withinCancellationWindow(on, off)) {
                return List.of(cancelled(on, off));
            }
            // S#12: same stop but much later, so they rode somewhere and came back. Not a free cancellation.
            return List.of(incomplete(on), unmatched(off));
        }

        // S#1, S#7 - normal completion
        return List.of(completed(on, off));
    }

    // boundary inclusive: exactly the window still counts as a cancellation
    private boolean withinCancellationWindow(Tap on, Tap off) {
        return !off.dateTimeUtc().isAfter(on.dateTimeUtc().plus(CANCELLATION_WINDOW));
    }

    private boolean sameCompanyAndBus(Tap on, Tap off) {
        return on.companyId().equals(off.companyId()) && on.busId().equals(off.busId());
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
