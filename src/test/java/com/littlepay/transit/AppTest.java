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
}
