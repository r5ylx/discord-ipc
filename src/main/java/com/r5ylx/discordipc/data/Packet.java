package com.r5ylx.discordipc.data;

import java.util.Objects;

import org.json.JSONObject;

import com.r5ylx.discordipc.enums.Opcode;

/** One frame of an IPC exchange. */
public final class Packet {
    private final Opcode opcode;
    private final JSONObject json;

    public Packet(Opcode opcode, JSONObject json) {
        this.opcode = Objects.requireNonNull(opcode, "opcode");
        this.json = Objects.requireNonNull(json, "json");
    }

    public Opcode getOpcode() {
        return opcode;
    }

    /**
     * The JSON body.
     * Treat it as read-only: changing it changes what is sent or what was received.
     */
    public JSONObject getJson() {
        return json;
    }

    @Override
    public String toString() {
        return "Packet[" + opcode + " " + json + "]";
    }
}
