package com.railway.app.data;

/**
 * A problem that can be shown to the user. The message is always written
 * in plain language; technical details only go to the log file.
 */
public class AppException extends Exception {

    public enum Kind {
        /** A business rule said no (message comes from the routine). */
        RULE,
        /** The booking system cannot be reached. */
        CONNECTION,
        /** The booking system is reachable but not set up completely. */
        SETUP,
        /** Anything else. */
        UNEXPECTED
    }

    private final Kind kind;

    public AppException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public String title() {
        return switch (kind) {
            case RULE -> "Please check";
            case CONNECTION -> "Can't reach the booking system";
            case SETUP -> "The booking system isn't ready";
            case UNEXPECTED -> "Something went wrong";
        };
    }
}
