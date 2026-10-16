package com.littlepay.transit;

import com.littlepay.transit.matching.TripMatcher;
import com.littlepay.transit.model.Tap;
import com.littlepay.transit.model.Trip;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Does the sorting required by the TripMatcher, the matching, and closing anything that's left open.
 * Currently sorts everything, but optionally can have a reordering buffer to mimic a production event streaming env.
 */
public class TripProcessor {

    private static final Comparator<Tap> TIME_ORDER =
            Comparator.comparing(Tap::dateTimeUtc).thenComparingLong(Tap::id);

    private final TripMatcher matcher;

    public TripProcessor(TripMatcher matcher) {
        this.matcher = matcher;
    }

    public Stream<Trip> process(Stream<Tap> taps) {
        Stream<Trip> closedTripsByTaps = taps.sorted(TIME_ORDER).flatMap(tap -> matcher.onTap(tap).stream());
        // lazy. flush MUST run only once AFTER every tap is processed. only applicable for a finite file stream
        Stream<Trip> leftoverIncompleteTrips = Stream.of(matcher).flatMap(m -> m.flush().stream());
        return Stream.concat(closedTripsByTaps, leftoverIncompleteTrips);
    }
}
