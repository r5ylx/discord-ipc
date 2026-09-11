package com.r5ylx.discordipc;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

import org.json.JSONException;
import org.json.JSONObject;

import com.r5ylx.discordipc.data.Packet;
import com.r5ylx.discordipc.data.User;
import com.r5ylx.discordipc.enums.Command;
import com.r5ylx.discordipc.enums.Event;
import com.r5ylx.discordipc.enums.Opcode;
import com.r5ylx.discordipc.enums.State;
import com.r5ylx.discordipc.exception.IPCProtocolException;
import com.r5ylx.discordipc.transport.Transport;

/**
 * Does the framing and the handshake on top of a single {@link Transport}.
 *
 * <p>Reconnecting is not handled here. A broken connection is thrown away and a new one built.
 * Not public API.
 */
final class Connection implements AutoCloseable {
    /** The version of the IPC protocol. */
    static final int RPC_VERSION = 1;

    /** The largest a single frame may be, including the 8-byte header. */
    static final int MAX_FRAME_SIZE = 64 * 1024;

    /** Four bytes of opcode and four bytes of length. */
    private static final int HEADER_SIZE = 8;

    private final Transport transport;

    private volatile State state = State.DISCONNECTED;

    Connection(Transport transport) {
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    State getState() {
        return state;
    }

    boolean isConnected() {
        return state == State.CONNECTED && transport.isOpen();
    }

    /**
     * Performs the handshake and returns the user on the other end.
     *
     * @throws IPCProtocolException if the reply differs from what was expected
     * @throws IOException          if the exchange fails
     */
    User handshake(long clientId) throws IOException {
        state = State.HANDSHAKING;

        sendPacket(Opcode.HANDSHAKE, new JSONObject()
            .put("v", RPC_VERSION)
            .put("client_id", String.valueOf(clientId)));

        Packet response = readPacket();
        JSONObject json = response.getJson();

        if (Command.of(json.optString("cmd")) != Command.DISPATCH
            || Event.of(json.optString("evt")) != Event.READY) {
            throw new IPCProtocolException("unexpected handshake reply: " + json);
        }

        JSONObject data = json.optJSONObject("data");
        JSONObject user = data == null ? null : data.optJSONObject("user");

        if (user == null) {
            throw new IPCProtocolException("the handshake reply carries no user: " + json);
        }

        state = State.CONNECTED;
        return User.fromJson(user);
    }

    /**
     * Sends a single frame.
     *
     * <p>The given JSON is not modified. The nonce goes into a copy.
     */
    void sendPacket(Opcode opcode, JSONObject json) throws IOException {
        Objects.requireNonNull(opcode, "opcode");
        Objects.requireNonNull(json, "json");

        JSONObject payload = new JSONObject(json.toString());
        payload.put("nonce", UUID.randomUUID().toString());

        byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);

        if (body.length + HEADER_SIZE > MAX_FRAME_SIZE) {
            throw new IPCProtocolException("frame too large: " + (body.length + HEADER_SIZE) + " bytes");
        }

        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + body.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(opcode.getValue());
        buffer.putInt(body.length);
        buffer.put(body);

        transport.write(buffer.array());
    }

    /**
     * Reads a single frame. Blocks until the far end sends something readable.
     *
     * @throws EOFException         if the far end closed the connection
     * @throws IPCProtocolException if the shape of the frame does not match the protocol
     */
    Packet readPacket() throws IOException {
        byte[] header = new byte[HEADER_SIZE];
        readFully(header, 0, HEADER_SIZE);

        ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int rawOpcode = buffer.getInt();
        int length = buffer.getInt();

        if (length < 0 || length + HEADER_SIZE > MAX_FRAME_SIZE) {
            throw new IPCProtocolException("frame length out of range: " + length);
        }

        byte[] body = new byte[length];
        readFully(body, 0, length);

        Opcode opcode = Opcode.of(rawOpcode)
            .orElseThrow(() -> new IPCProtocolException("unknown opcode: " + rawOpcode));

        JSONObject json;

        try {
            json = new JSONObject(new String(body, StandardCharsets.UTF_8));
        } catch (JSONException e) {
            throw new IPCProtocolException("the frame body could not be read as JSON", e);
        }

        if (opcode == Opcode.CLOSE) {
            state = State.DISCONNECTED;
            throw new EOFException(
                "Discord closed the connection: " + json.optString("message", json.toString()));
        }

        return new Packet(opcode, json);
    }

    private void readFully(byte[] buffer, int offset, int length) throws IOException {
        int total = 0;

        while (total < length) {
            int read = transport.read(buffer, offset + total, length - total);

            if (read == -1) {
                throw new EOFException("the connection closed in the middle of a frame");
            }

            total += read;
        }
    }

    @Override
    public void close() throws IOException {
        state = State.DISCONNECTED;
        transport.close();
    }
}
