package com.littlepay.transit.matching;

import com.littlepay.transit.fare.FareTable;
import com.littlepay.transit.model.Tap;
import com.littlepay.transit.model.Trip;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for taps, matching them to create Trip records.
 * This assumes that the taps are coming in sequential time order, because it will hold at most one open Tap.Type.ON
 * per PAN.
 */
public class TripMatcher {

    private final FareTable fareTable;
    private final Map<String, Tap> openTapByPan = new HashMap<>();

    public TripMatcher(FareTable fareTable) {
        this.fareTable = fareTable;
    }

    /**
     * Tap Handler.
     * @return list of max 2 trips (2 trips in edge cases involving late unmatched OFF or in a different bus)
     */
    public List<Trip> onTap(Tap tap) {
        return switch (tap.tapType()) {
            case ON -> handleOn(tap);
            case OFF -> handleOff(tap);
        };
    }

    private List<Trip> handleOn(Tap on) {
        // TODO: other ON cases (duplicates, or closing a previous ON)
        openTapByPan.put(on.pan(), on);
        return List.of();
    }

    private List<Trip> handleOff(Tap off) {
        Tap on = openTapByPan.remove(off.pan());
        // TODO: handle edge cases around unmatched, differing busses, or timing cases
        if (on.stopId().equals(off.stopId())) {
            return List.of(cancelled(on, off));
        }
        return List.of(completed(on, off));
    }

    private Trip completed(Tap on, Tap off) {
        BigDecimal fare = fareTable.fareBetween(on.stopId(), off.stopId());
        return closedTrip(on, off, fare, Trip.Status.COMPLETED);
    }

    private Trip cancelled(Tap on, Tap off) {
        return closedTrip(on, off, BigDecimal.ZERO, Trip.Status.CANCELLED);
    }

    private Trip closedTrip(Tap on, Tap off, BigDecimal charge, Trip.Status status) {
        return new Trip(on.dateTimeUtc(), off.dateTimeUtc(), on.stopId(), off.stopId(),
                charge, on.companyId(), on.busId(), on.pan(), status);
    }
}
