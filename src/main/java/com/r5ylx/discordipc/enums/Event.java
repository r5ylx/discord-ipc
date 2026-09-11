package com.r5ylx.discordipc.enums;

import java.util.Locale;

/** An event arriving from Discord. */
public enum Event {
    READY(false),
    ERROR(false),
    ACTIVITY_JOIN(true),
    ACTIVITY_SPECTATE(true),
    ACTIVITY_JOIN_REQUEST(true),
    /** An unknown event, or a response that carries no evt field. */
    UNKNOWN(false);

    private final boolean subscribable;

    Event(boolean subscribable) {
        this.subscribable = subscribable;
    }

    /** Whether this event can be subscribed to. */
    public boolean isSubscribable() {
        return subscribable;
    }

    /** Looks up an event by name, ignoring case. An unknown value becomes {@link #UNKNOWN}. */
    public static Event of(String value) {
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
