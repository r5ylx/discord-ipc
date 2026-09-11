package com.r5ylx.discordipc.enums;

import java.util.Locale;

/** A command sent to, or arriving from, Discord. */
public enum Command {
    DISPATCH,
    SUBSCRIBE,
    UNSUBSCRIBE,
    SET_ACTIVITY,
    SEND_ACTIVITY_JOIN_INVITE,
    CLOSE_ACTIVITY_REQUEST,
    /** An unknown command. */
    UNKNOWN;

    /** Looks up a command by name, ignoring case. An unknown value becomes {@link #UNKNOWN}. */
    public static Command of(String value) {
        if (value == null || value.isEmpty()) {
            return UNKNOWN;
        }

        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
