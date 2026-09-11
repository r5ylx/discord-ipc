package com.r5ylx.discordipc.enums;

import java.util.Optional;

/** The kind of frame, held in the first 4 bytes of an IPC frame. */
public enum Opcode {
    HANDSHAKE(0),
    FRAME(1),
    CLOSE(2),
    PING(3),
    PONG(4);

    private final int value;

    Opcode(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    /**
     * Looks up a kind by its number.
     * An unknown value returns empty rather than throwing, so that a new kind added by Discord
     * does not bring the client down.
     */
    public static Optional<Opcode> of(int value) {
        for (Opcode opcode : values()) {
            if (opcode.value == value) {
                return Optional.of(opcode);
            }
        }

        return Optional.empty();
    }
}
