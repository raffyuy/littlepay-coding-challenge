package com.littlepay.transit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// End to end test from the challenge brief example
class AppTest {

    private static final Path EXAMPLE = Path.of("src/test/resources/example");

    @Test
    void exampleInputProducesExpectedTrips(@TempDir Path tempDir) throws IOException {
        Path output = tempDir.resolve("trips.csv");

        App.run(EXAMPLE.resolve("taps.csv"), output);

        assertThat(Files.readAllLines(output)).isEqualTo(Files.readAllLines(EXAMPLE.resolve("trips.csv")));
    }

    @Test
    void tapAtAnUnknownStopIsSkippedAndTheRestStillProcessed(@TempDir Path tempDir) throws IOException {
        Path input = tempDir.resolve("taps.csv");
        Files.writeString(input, """
                ID, DateTimeUTC, TapType, StopId, CompanyId, BusID, PAN
                1, 22-01-2023 08:00:00, ON, Stop1, Company1, Bus37, 4111111111111111
                2, 22-01-2023 08:02:00, ON, Stop4, Company1, Bus37, 5500005555555559
                3, 22-01-2023 08:05:00, OFF, Stop2, Company1, Bus37, 4111111111111111
                """);
        Path output = tempDir.resolve("trips.csv");

        App.run(input, output);

        assertThat(Files.readAllLines(output)).containsExactly(
                "Started, Finished, DurationSecs, FromStopId, ToStopId, ChargeAmount, CompanyId, BusID, PAN, Status",
                "22-01-2023 08:00:00, 22-01-2023 08:05:00, 300, Stop1, Stop2, $3.25, Company1, Bus37, 4111111111111111, COMPLETED");
    }
}
