package com.littlepay.transit;

import com.littlepay.transit.csv.CsvUtils;
import com.littlepay.transit.fare.FareTable;
import com.littlepay.transit.matching.TripMatcher;
import com.littlepay.transit.model.Tap;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.stream.Stream;

public class App {

    private static final Duration MAX_TRIP_LENGTH = Duration.ofHours(24);

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("Usage: <input taps.csv> <output trips.csv>");
            System.exit(1);
        }
        run(Path.of(args[0]), Path.of(args[1]));
    }

    static void run(Path input, Path output) throws IOException {
        TripProcessor processor = new TripProcessor(new TripMatcher(FareTable.standard(), MAX_TRIP_LENGTH));
        try (Stream<Tap> taps = CsvUtils.readTaps(input)) {
            CsvUtils.writeTrips(output, processor.process(taps));
        }
    }
}
