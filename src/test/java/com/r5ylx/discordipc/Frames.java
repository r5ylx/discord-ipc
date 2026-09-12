package com.r5ylx.discordipc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import org.json.JSONObject;

import com.r5ylx.discordipc.data.Packet;
import com.r5ylx.discordipc.enums.Opcode;

/**
 * A tool for building and taking apart frames in tests.
 * It is written independently of the implementation, so a test fails if either side breaks.
 */
final class Frames {
    private Frames() {
    }

    /** Builds a frame, header included. */
    static byte[] encode(Opcode opcode, JSONObject json) {
        return encode(opcode.getValue(), json.toString());
    }

    /** Builds a frame with the opcode given as a number. Used for sending unknown values. */
    static byte[] encode(int opcode, String body) {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);

        return ByteBuffer.allocate(8 + payload.length)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(opcode)
            .putInt(payload.length)
            .put(payload)
            .array();
    }

    /** Builds a frame whose length field carries an arbitrary value. */
    static byte[] encodeWithLength(Opcode opcode, int declaredLength, String body) {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);

        return ByteBuffer.allocate(8 + payload.length)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(opcode.getValue())
            .putInt(declaredLength)
            .put(payload)
            .array();
    }

    /** Takes a frame apart. */
    static Packet decode(byte[] frame) {
        ByteBuffer buffer = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN);
        int opcode = buffer.getInt();
        int length = buffer.getInt();

        byte[] body = new byte[length];
        buffer.get(body);

        return new Packet(
            Opcode.of(opcode).orElseThrow(() -> new IllegalArgumentException("unknown opcode: " + opcode)),
            new JSONObject(new String(body, StandardCharsets.UTF_8)));
    }

    /** A well formed reply to the handshake. */
    static byte[] readyResponse() {
        JSONObject user = new JSONObject()
            .put("id", "1193242580113170596")
            .put("username", "n4yxr")
            // A multi-byte global_name, so the UTF-8 round trip through a frame is covered.
            .put("global_name", "み")
            .put("discriminator", "0")
            .put("avatar", "a1895cdb3039903e58df0d8b518456dd");

        JSONObject reply = new JSONObject()
            .put("cmd", "DISPATCH")
            .put("evt", "READY")
            .put("data", new JSONObject().put("v", 1).put("user", user));

        return encode(Opcode.FRAME, reply);
    }
}
