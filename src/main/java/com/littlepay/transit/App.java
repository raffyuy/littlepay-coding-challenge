package com.littlepay.transit;

/**
 * Entry point: reads taps from an input CSV and writes trips to an output CSV.
 */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: <input taps.csv> <output trips.csv>");
            System.exit(1);
        }
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
