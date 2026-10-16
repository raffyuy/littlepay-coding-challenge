package com.littlepay.transit.csv;

import com.littlepay.transit.model.Tap;
import com.littlepay.transit.model.Trip;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

/**
 * CSV Reader and writer utils for the input and output of this challenge.
 */
public final class CsvUtils {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private static final String SEPARATOR = ", ";
    private static final String TRIPS_HEADER = String.join(SEPARATOR,
            "Started", "Finished", "DurationSecs", "FromStopId", "ToStopId", "ChargeAmount",
            "CompanyId", "BusID", "PAN", "Status");

    private CsvUtils() {
    }

    /**
     * Returns a stream of taps from a file, to mimic production-like event streaming. The caller must close it.
     * Input is assumed well-formed, so no parsing validation required.
     */
    public static Stream<Tap> readTaps(Path file) throws IOException {
        return Files.lines(file)
                .skip(1) // header
                .filter(line -> !line.isBlank())
                .map(CsvUtils::parseTap);
    }

    public static void writeTrips(Path file, Stream<Trip> trips) throws IOException {
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file))) {
            out.println(TRIPS_HEADER);
            trips.forEach(trip -> out.println(formatTrip(trip)));
        }
    }

    private static Tap parseTap(String line) {
        String[] fields = line.split(",");
        return new Tap(
                Long.parseLong(fields[0].trim()),
                LocalDateTime.parse(fields[1].trim(), DATE_TIME).toInstant(ZoneOffset.UTC),
                Tap.Type.valueOf(fields[2].trim()),
                fields[3].trim(),
                fields[4].trim(),
                fields[5].trim(),
                fields[6].trim());
    }

    // Fields a trip doesn't have (e.g. no OFF for INCOMPLETE) are left empty.
    private static String formatTrip(Trip trip) {
        return String.join(SEPARATOR,
                dateTime(trip.started()),
                dateTime(trip.finished()),
                durationSecs(trip),
                orEmpty(trip.fromStopId()),
                orEmpty(trip.toStopId()),
                "$" + trip.chargeAmount().setScale(2).toPlainString(),
                trip.companyId(),
                trip.busId(),
                trip.pan(),
                trip.status().name());
    }

    private static String dateTime(Instant time) {
        return time == null ? "" : DATE_TIME.format(time.atOffset(ZoneOffset.UTC));
    }

    private static String durationSecs(Trip trip) {
        if (trip.started() == null || trip.finished() == null) {
            return "";
        }
        return String.valueOf(Duration.between(trip.started(), trip.finished()).toSeconds());
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
