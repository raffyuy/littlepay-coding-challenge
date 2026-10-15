package com.littlepay.transit.model;

public enum TripStatus {
    /** Valid trip with tap on and off at different stops. */
    COMPLETED,
    /** Tapped on with no matching tap off. */
    INCOMPLETE,
    /** Tapped on and off at the same stop. */
    CANCELLED,
    /** Tapped off with no matching tap on. Not charged, needs review. */
    UNSTARTED
}
