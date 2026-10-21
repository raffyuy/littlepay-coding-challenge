package com.littlepay.transit.fare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

class FareTableTest {

    private final FareTable fareTable = FareTable.standard();

    @ParameterizedTest
    @CsvSource({
            "Stop1, Stop2, 3.25",
            "Stop2, Stop1, 3.25",
            "Stop2, Stop3, 5.50",
            "Stop3, Stop2, 5.50",
            "Stop1, Stop3, 7.30",
            "Stop3, Stop1, 7.30",
            "Stop1, Stop4, 6.30",
            "Stop4, Stop1, 6.30",
            "Stop2, Stop4, 4.30",
            "Stop4, Stop2, 4.30",
            "Stop3, Stop4, 5.30",
            "Stop4, Stop3, 5.30",
    })
    void chargesTheSameFareInEitherDirection(String from, String to, String expected) {
        assertThat(fareTable.fareBetween(from, to)).isEqualByComparingTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "Stop1, 7.30",
            "Stop2, 5.50",
            "Stop3, 7.30",
            "Stop4, 6.30"
    })
    void maxFareIsTheHighestFareFromThatStop(String stop, String expected) {
        assertThat(fareTable.maxFareFrom(stop)).isEqualByComparingTo(expected);
    }

    @Test
    void rejectsUnknownStopPair() {
        assertThatThrownBy(() -> fareTable.fareBetween("Stop1", "Stop9"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnknownStopForMaxFare() {
        assertThatThrownBy(() -> fareTable.maxFareFrom("Stop9"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requireKnownStopAcceptsStopsInTheTable() {
        fareTable.requireKnownStop("Stop2");
    }

    @Test
    void requireKnownStopRejectsOthers() {
        assertThatThrownBy(() -> fareTable.requireKnownStop("Stop9"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
