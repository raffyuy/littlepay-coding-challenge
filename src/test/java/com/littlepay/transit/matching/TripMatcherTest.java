package com.littlepay.transit.matching;

import com.littlepay.transit.fare.FareTable;
import com.littlepay.transit.model.Trip;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.littlepay.transit.TapFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class TripMatcherTest {

    private final TripMatcher matcher = new TripMatcher(FareTable.standard());

    @Test
    void tapOnAloneDoesNotProduceATrip() {
        assertThat(matcher.onTap(on("13:00", STOP1))).isEmpty();
    }

    @Test
    void validTapsResultInCompletedTrip() {
        matcher.onTap(on("13:00", STOP1));

        assertThat(matcher.onTap(off("13:05", STOP2))).containsExactly(
                new Trip(at("13:00"), at("13:05"), STOP1, STOP2, FARE_STOP1_STOP2,
                        COMPANY, BUS, PAN, Trip.Status.COMPLETED));
    }

    @Test
    void tapOffAtADifferentStopIsChargedTheCorrectFare() {
        matcher.onTap(on("13:00", STOP3));

        List<Trip> trips = matcher.onTap(off("13:20", STOP1));

        assertThat(trips).singleElement()
                .returns(Trip.Status.COMPLETED, Trip::status)
                .returns(FARE_STOP1_STOP3, Trip::chargeAmount);
    }

    @Test
    void tapOffAtTheSameStopCancelsTheTrip() {
        matcher.onTap(on("08:00", STOP1));

        List<Trip> trips = matcher.onTap(off("08:02", STOP1));

        assertThat(trips).singleElement()
                .returns(Trip.Status.CANCELLED, Trip::status)
                .returns(BigDecimal.ZERO, Trip::chargeAmount);
    }

    @Test
    void tapsFromDifferentCardsAreMatchedSeparately() {
        matcher.onTap(on("13:00", STOP1));

        // must not close the first card's open trip
        assertThat(matcher.onTap(onCard(PAN2, "13:01", STOP2))).isEmpty();

        List<Trip> firstCardTrips = matcher.onTap(off("13:05", STOP2));
        List<Trip> otherCardTrips = matcher.onTap(offCard(PAN2, "13:10", STOP3));

        // match the right trips
        assertThat(firstCardTrips).singleElement()
                .returns(PAN, Trip::pan)
                .returns(FARE_STOP1_STOP2, Trip::chargeAmount);
        assertThat(otherCardTrips).singleElement()
                .returns(PAN2, Trip::pan)
                .returns(FARE_STOP2_STOP3, Trip::chargeAmount);
    }
}
