package com.littlepay.transit;

import com.littlepay.transit.model.Tap;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;

/**
 * Improves readability of Tap data mostly simplifying how we write time -"HH:mm" on a fixed test day in UTC.
 */
public final class TapFixtures {

    public static final LocalDate DAY = LocalDate.of(2023, 1, 22);
    public static final String PAN = "1234123412341234";
    public static final String PAN2 = "4111111111111111";
    public static final String PAN3 = "5500005555555559";
    public static final String COMPANY = "Company1";
    public static final String COMPANY2 = "Company2";
    public static final String BUS = "Bus21";
    public static final String BUS2 = "Bus36";

    public static final String STOP1 = "Stop1";
    public static final String STOP2 = "Stop2";
    public static final String STOP3 = "Stop3";

    // Fares from the brief, written out independently of FareTable so tests catch a wrong table
    public static final BigDecimal FARE_STOP1_STOP2 = new BigDecimal("3.25");
    public static final BigDecimal FARE_STOP2_STOP3 = new BigDecimal("5.50");
    public static final BigDecimal FARE_STOP1_STOP3 = new BigDecimal("7.30");

    private TapFixtures() {
    }

    public static Instant at(String time) {
        return at(1, time);
    }

    // day 1 is DAY, day 2 is the day after, and so on
    public static Instant at(int day, String time) {
        return DAY.plusDays(day - 1).atTime(LocalTime.parse(time)).toInstant(ZoneOffset.UTC);
    }

    public static Tap on(String time, String stop) {
        return on(1, time, stop);
    }

    public static Tap on(int day, String time, String stop) {
        return tap(Tap.Type.ON, at(day, time), stop, COMPANY, BUS, PAN);
    }

    public static Tap off(String time, String stop) {
        return off(1, time, stop);
    }

    public static Tap off(int day, String time, String stop) {
        return tap(Tap.Type.OFF, at(day, time), stop, COMPANY, BUS, PAN);
    }

    // same as on/off, but for a different card
    public static Tap onCard(String pan, String time, String stop) {
        return tap(Tap.Type.ON, at(time), stop, COMPANY, BUS, pan);
    }

    public static Tap offCard(String pan, String time, String stop) {
        return tap(Tap.Type.OFF, at(time), stop, COMPANY, BUS, pan);
    }

    public static Tap tap(Tap.Type type, Instant time, String stop, String company, String bus, String pan) {
        return new Tap(0, time, type, stop, company, bus, pan);
    }
}
