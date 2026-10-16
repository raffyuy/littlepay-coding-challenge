package com.littlepay.transit.matching;

import com.littlepay.transit.fare.FareTable;
import com.littlepay.transit.model.Tap;
import com.littlepay.transit.model.Trip;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static com.littlepay.transit.TapFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

class TripMatcherTest {

    private final TripMatcher matcher = new TripMatcher(FareTable.standard(), Duration.ofHours(24));

    // S#1, S#3: an ON alone stays open
    @Test
    void tapOnAloneDoesNotProduceATrip() {
        assertThat(matcher.onTap(on("13:00", STOP1))).isEmpty();
    }

    // S#1: completed, all fields
    @Test
    void validTapsResultInCompletedTrip() {
        matcher.onTap(on("13:00", STOP1));

        assertThat(matcher.onTap(off("13:05", STOP2))).containsExactly(
                new Trip(at("13:00"), at("13:05"), STOP1, STOP2, FARE_STOP1_STOP2,
                        COMPANY, BUS, PAN, Trip.Status.COMPLETED));
    }

    // S#1: completed, fare
    @Test
    void tapOffAtADifferentStopIsChargedTheCorrectFare() {
        matcher.onTap(on("13:00", STOP3));

        List<Trip> trips = matcher.onTap(off("13:20", STOP1));

        assertThat(trips).singleElement()
                .returns(Trip.Status.COMPLETED, Trip::status)
                .returns(FARE_STOP1_STOP3, Trip::chargeAmount);
    }

    // S#2: same stop
    @Test
    void tapOffAtTheSameStopCancelsTheTrip() {
        matcher.onTap(on("08:00", STOP1));

        List<Trip> trips = matcher.onTap(off("08:02", STOP1));

        assertThat(trips).singleElement()
                .returns(Trip.Status.CANCELLED, Trip::status)
                .returns(BigDecimal.ZERO, Trip::chargeAmount);
    }

    // S#1: making sure matching doesn't get mixed up between PANs
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

    // S#4: second ON, closing the previous ON
    @Test
    void secondTapOnClosesTheOpenTripAsIncomplete() {
        matcher.onTap(on("13:00", STOP2));

        List<Trip> trips = matcher.onTap(on("14:00", STOP1));

        assertThat(trips).singleElement()
                .returns(Trip.Status.INCOMPLETE, Trip::status)
                .returns(STOP2, Trip::fromStopId)
                .returns(at("13:00"), Trip::started)
                .returns(null, Trip::toStopId)
                .returns(null, Trip::finished);
    }

    // S#4: incomplete charge
    @Test
    void incompleteTripIsChargedTheHighestFareFromItsStop() {
        matcher.onTap(on("13:00", STOP2));

        List<Trip> trips = matcher.onTap(on("14:00", STOP1));

        // from Stop2 the options are $3.25 to Stop1 or $5.50 to Stop3
        assertThat(trips).singleElement()
                .returns(FARE_STOP2_STOP3, Trip::chargeAmount);
    }

    // S#4: second ON starts a new trip
    @Test
    void secondTapOnStartsANewTrip() {
        matcher.onTap(on("13:00", STOP2));
        matcher.onTap(on("14:00", STOP1));

        List<Trip> trips = matcher.onTap(off("14:20", STOP3));

        assertThat(trips).singleElement()
                .returns(Trip.Status.COMPLETED, Trip::status)
                .returns(STOP1, Trip::fromStopId)
                .returns(FARE_STOP1_STOP3, Trip::chargeAmount);
    }

    // S#5: unmatched
    @Test
    void tapOffWithNoOpenTripIsUnmatched() {
        List<Trip> trips = matcher.onTap(off("16:30", STOP2));

        assertThat(trips).singleElement()
                .returns(Trip.Status.UNMATCHED, Trip::status)
                .returns(STOP2, Trip::toStopId)
                .returns(at("16:30"), Trip::finished)
                .returns(null, Trip::fromStopId)
                .returns(null, Trip::started)
                .returns(BigDecimal.ZERO, Trip::chargeAmount);
    }

    // S#5: unmatched after a closed trip
    @Test
    void secondTapOffAfterACompletedTripIsUnmatched() {
        matcher.onTap(on("13:00", STOP1));
        matcher.onTap(off("13:05", STOP2));

        List<Trip> trips = matcher.onTap(off(3, "16:30", STOP2));

        assertThat(trips).singleElement()
                .returns(Trip.Status.UNMATCHED, Trip::status);
    }

    // S#6: exceeded max trip length
    @Test
    void tapOffMoreThan24HoursLaterDoesNotCloseTheTrip() {
        matcher.onTap(on("13:00", STOP1));

        List<Trip> trips = matcher.onTap(off(2, "13:01", STOP2));

        assertThat(trips).extracting(Trip::status)
                .containsExactly(Trip.Status.INCOMPLETE, Trip.Status.UNMATCHED);
    }

    // S#7: exactly the max trip length
    @Test
    void tapOffExactly24HoursLaterStillCompletesTheTrip() {
        matcher.onTap(on("13:00", STOP1));

        List<Trip> trips = matcher.onTap(off(2, "13:00", STOP2));

        assertThat(trips).singleElement()
                .returns(Trip.Status.COMPLETED, Trip::status);
    }

    // S#3: no matching OFF by end of input
    @Test
    void flushClosesOpenTripsAsIncomplete() {
        matcher.onTap(on("13:00", STOP1));
        matcher.onTap(onCard(PAN2, "13:01", STOP2));

        List<Trip> trips = matcher.flush();

        assertThat(trips)
                .extracting(Trip::pan, Trip::status)
                .containsExactly(
                        tuple(PAN, Trip.Status.INCOMPLETE),
                        tuple(PAN2, Trip.Status.INCOMPLETE)
                );
    }

    @Test
    void flushLeavesNothingOpen() {
        matcher.onTap(on("13:00", STOP1));
        matcher.flush();

        assertThat(matcher.flush()).isEmpty();
    }

    // S#8: multi trip
    @Test
    void severalTripsInADayAreMatchedSeparately() {
        matcher.onTap(on("08:00", STOP1));
        List<Trip> morning = matcher.onTap(off("08:10", STOP2));
        matcher.onTap(on("17:00", STOP2));
        List<Trip> evening = matcher.onTap(off("17:20", STOP1));

        assertThat(morning).singleElement().returns(Trip.Status.COMPLETED, Trip::status);
        assertThat(evening).singleElement().returns(Trip.Status.COMPLETED, Trip::status);
    }

    // S#9: different bus
    @Test
    void tapOffOnADifferentBusDoesNotCloseTheTrip() {
        matcher.onTap(on("13:00", STOP1));

        List<Trip> trips = matcher.onTap(tap(Tap.Type.OFF, at("13:05"), STOP2, COMPANY, BUS2, PAN));

        assertThat(trips).extracting(Trip::status)
                .containsExactly(Trip.Status.INCOMPLETE, Trip.Status.UNMATCHED);
    }

    // S#9: different company
    @Test
    void tapOffWithADifferentCompanyDoesNotCloseTheTrip() {
        matcher.onTap(on("13:00", STOP1));

        List<Trip> trips = matcher.onTap(tap(Tap.Type.OFF, at("13:05"), STOP2, COMPANY2, BUS, PAN));

        assertThat(trips).extracting(Trip::status)
                .containsExactly(Trip.Status.INCOMPLETE, Trip.Status.UNMATCHED);
    }

    // S#10: duplicate ON
    @Test
    void duplicateTapOnWithinAMinuteIsIgnored() {
        matcher.onTap(on("13:00", STOP1));

        assertThat(matcher.onTap(on("13:00:30", STOP1))).isEmpty();
        assertThat(matcher.onTap(off("13:05", STOP2))).singleElement()
                .returns(Trip.Status.COMPLETED, Trip::status)
                .returns(at("13:00"), Trip::started);
    }

    // S#10: a minute or more apart is a new trip (S#4)
    @Test
    void tapOnAgainAMinuteLaterIsANewTrip() {
        matcher.onTap(on("13:00", STOP1));

        assertThat(matcher.onTap(on("13:01", STOP1))).singleElement()
                .returns(Trip.Status.INCOMPLETE, Trip::status);
    }

    // S#10: a different stop is never a duplicate
    @Test
    void tapOnAtAnotherStopWithinAMinuteIsANewTrip() {
        matcher.onTap(on("13:00", STOP1));

        assertThat(matcher.onTap(on("13:00:30", STOP2))).singleElement()
                .returns(Trip.Status.INCOMPLETE, Trip::status);
    }

    // S#11: abandoned ON expires once any later tap is past the max trip length
    @Test
    void abandonedTapOnIsClosedOnceTapsPassTheMaxTripLength() {
        matcher.onTap(on("13:00", STOP1));

        List<Trip> trips = matcher.onTap(tap(Tap.Type.ON, at(2, "13:01"), STOP2, COMPANY, BUS, PAN2));

        assertThat(trips).singleElement()
                .returns(PAN, Trip::pan)
                .returns(Trip.Status.INCOMPLETE, Trip::status);
    }

    // S#11: exactly the max trip length isn't expired yet (same boundary as S#7)
    @Test
    void abandonedTapOnIsNotClosedAtExactlyTheMaxTripLength() {
        matcher.onTap(on("13:00", STOP1));

        assertThat(matcher.onTap(tap(Tap.Type.ON, at(2, "13:00"), STOP2, COMPANY, BUS, PAN2))).isEmpty();
    }

    // S#11: a second ON moves to the back, so an older trip from another card still expires first
    @Test
    void reopenedTripMovesBehindOlderOpenTrips() {
        matcher.onTap(on("13:00", STOP1));
        matcher.onTap(onCard(PAN2, "14:00", STOP2));
        matcher.onTap(on("15:00", STOP3)); // PAN's 13:00 trip closes as INCOMPLETE, its 15:00 ON is now newest

        List<Trip> trips = matcher.onTap(tap(Tap.Type.ON, at(2, "14:30"), STOP1, COMPANY, BUS, PAN3));

        assertThat(trips).singleElement().returns(PAN2, Trip::pan);
        assertThat(matcher.flush()).extracting(Trip::pan).contains(PAN);
    }

    // S#2: exactly the cancellation window still cancels
    @Test
    void tapOffAtTheSameStopTenMinutesLaterIsStillCancelled() {
        matcher.onTap(on("08:00", STOP1));

        assertThat(matcher.onTap(off("08:10", STOP1))).singleElement()
                .returns(Trip.Status.CANCELLED, Trip::status);
    }

    // S#12: same stop after the cancellation window is a round trip, not a free cancellation
    @Test
    void tapOffAtTheSameStopAfterTheCancellationWindowIsNotCancelled() {
        matcher.onTap(on("08:00", STOP1));

        List<Trip> trips = matcher.onTap(off("08:10:01", STOP1));

        assertThat(trips).extracting(Trip::status)
                .containsExactly(Trip.Status.INCOMPLETE, Trip.Status.UNMATCHED);
        assertThat(trips.getFirst().chargeAmount()).isEqualByComparingTo(FARE_STOP1_STOP3);
    }

    // S#10: an ON on a different bus is a real new trip, not a duplicate
    @Test
    void tapOnOnAnotherBusWithinAMinuteIsNotADuplicate() {
        matcher.onTap(on("13:00", STOP1));

        assertThat(matcher.onTap(tap(Tap.Type.ON, at("13:00:30"), STOP1, COMPANY, BUS2, PAN))).singleElement()
                .returns(Trip.Status.INCOMPLETE, Trip::status);
        assertThat(matcher.onTap(tap(Tap.Type.OFF, at("13:05"), STOP2, COMPANY, BUS2, PAN))).singleElement()
                .returns(Trip.Status.COMPLETED, Trip::status)
                .returns(BUS2, Trip::busId);
    }

    @Test
    void tapAtAnUnknownStopIsRejected() {
        matcher.onTap(on("13:00", STOP1));

        assertThatThrownBy(() -> matcher.onTap(on("13:02", "Stop9")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Stop9");

        // the open trip from Stop1 is untouched by the rejected tap
        assertThat(matcher.onTap(off("13:05", STOP2))).singleElement()
                .returns(Trip.Status.COMPLETED, Trip::status);
    }
}
