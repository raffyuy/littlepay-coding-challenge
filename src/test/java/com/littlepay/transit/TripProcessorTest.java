package com.littlepay.transit;

import com.littlepay.transit.csv.CsvUtils;
import com.littlepay.transit.fare.FareTable;
import com.littlepay.transit.matching.TripMatcher;
import com.littlepay.transit.model.Tap;
import com.littlepay.transit.model.Trip;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.littlepay.transit.TapFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class TripProcessorTest {

    private final TripProcessor processor =
            new TripProcessor(new TripMatcher(FareTable.standard(), Duration.ofHours(24)));

    @Test
    void offThatComesBeforeItsOnInTheFileIsStillMatched() {
        List<Tap> taps = List.of(off("13:05", STOP2), on("13:00", STOP1));

        assertThat(processAll(processor, taps)).singleElement()
                .returns(Trip.Status.COMPLETED, Trip::status)
                .returns(FARE_STOP1_STOP2, Trip::chargeAmount);
    }

    @Test
    void tapAtAnUnknownStopIsSkippedAndTheRestStillProcessed() {
        List<Tap> taps = List.of(on("13:00", STOP1), on("13:02", "Stop9"), off("13:05", STOP2));

        assertThat(processAll(processor, taps)).singleElement()
                .returns(Trip.Status.COMPLETED, Trip::status);
    }

    @Test
    void openTripsAreClosedAsIncompleteAtTheEnd() {
        List<Tap> taps = List.of(on("13:00", STOP1));

        assertThat(processAll(processor, taps)).singleElement()
                .returns(Trip.Status.INCOMPLETE, Trip::status);
    }

    // ordering of lines in file must not matter
    @ParameterizedTest
    @ValueSource(longs = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
    void exampleFileGivesTheSameTripsInAnyOrder(long seed) throws IOException {
        List<Tap> taps;
        try (Stream<Tap> lines = CsvUtils.readTaps(Path.of("src/test/resources/example/taps.csv"))) {
            taps = lines.toList();
        }

        assertThat(processAll(freshProcessor(), shuffled(taps, seed)))
                .isEqualTo(processAll(freshProcessor(), taps));
    }

    // includes the other edge cases not in base example
    @ParameterizedTest
    @ValueSource(longs = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
    void mixedDaysAndCardsGiveTheSameTripsInAnyOrder(long seed) {
        List<Trip> trips = processAll(freshProcessor(), shuffled(mixedScenario(), seed));

        assertThat(trips).extracting(Trip::pan, Trip::status, Trip::chargeAmount).containsExactly(
                tuple(PAN2, Trip.Status.UNMATCHED, BigDecimal.ZERO),        // day 1, OFF with no ON
                tuple(PAN, Trip.Status.COMPLETED, FARE_STOP1_STOP2),       // day 1 morning
                tuple(PAN, Trip.Status.CANCELLED, BigDecimal.ZERO),        // day 1 evening, same stop
                tuple(PAN, Trip.Status.INCOMPLETE, FARE_STOP1_STOP3),      // day 2, tapped on again (max from Stop3)
                tuple(PAN, Trip.Status.COMPLETED, FARE_STOP1_STOP3),       // day 2, the second ON's trip
                tuple(PAN2, Trip.Status.INCOMPLETE, FARE_STOP2_STOP3),     // day 1 ON, OFF over 24h later (max from Stop2)
                tuple(PAN2, Trip.Status.UNMATCHED, BigDecimal.ZERO),       // ...and that late OFF
                tuple(PAN2, Trip.Status.INCOMPLETE, FARE_STOP1_STOP3));    // day 3, still open at the end
    }


    // Verifies all taps are accounted for in the output.
    @Test
    void tapsUsedByTripsAddUpToTapsRead() {
        List<Tap> taps = mixedScenario();

        List<Trip> trips = processAll(freshProcessor(), taps);

        int tapsUsed = trips.stream()
                .mapToInt(trip -> (trip.started() != null ? 1 : 0) + (trip.finished() != null ? 1 : 0))
                .sum();
        assertThat(tapsUsed).isEqualTo(taps.size());
    }

    // tie-break scenario in case of events logged in the same second.
    @Test
    void tapOnComesBeforeTapOffAtTheSameSecond() {
        Tap off = withId(1, off("13:00", STOP1));
        Tap on = withId(2, on("13:00", STOP1));

        // even with the OFF's id first, it must not be UNMATCHED + INCOMPLETE (charging the max fare)
        assertThat(processAll(freshProcessor(), List.of(off, on))).singleElement()
                .returns(Trip.Status.CANCELLED, Trip::status);
    }

    private static List<Trip> processAll(TripProcessor processor, List<Tap> taps) {
        return processor.process(taps.stream()).toList();
    }

    private static TripProcessor freshProcessor() {
        return new TripProcessor(new TripMatcher(FareTable.standard(), Duration.ofHours(24)));
    }

    // Two cards over three days covering every outcome, listed in time order and numbered as tap ids.
    private static List<Tap> mixedScenario() {
        List<Tap> taps = List.of(
                tap(Tap.Type.OFF, at(1, "07:00"), STOP1, COMPANY, BUS, PAN2),
                on(1, "08:00", STOP1),
                off(1, "08:10", STOP2),
                tap(Tap.Type.ON, at(1, "12:00"), STOP2, COMPANY, BUS, PAN2),
                on(1, "17:00", STOP2),
                off(1, "17:05", STOP2),
                on(2, "09:00", STOP3),
                on(2, "10:00", STOP1),
                off(2, "10:30", STOP3),
                tap(Tap.Type.OFF, at(3, "12:30"), STOP3, COMPANY, BUS, PAN2),
                tap(Tap.Type.ON, at(3, "18:00"), STOP1, COMPANY, BUS, PAN2));
        return IntStream.range(0, taps.size()).mapToObj(i -> withId(i + 1, taps.get(i))).toList();
    }

    private static List<Tap> shuffled(List<Tap> taps, long seed) {
        List<Tap> copy = new ArrayList<>(taps);
        Collections.shuffle(copy, new Random(seed));
        return copy;
    }

    private static Tap withId(long id, Tap tap) {
        return new Tap(id, tap.dateTimeUtc(), tap.tapType(), tap.stopId(), tap.companyId(), tap.busId(), tap.pan());
    }
}
