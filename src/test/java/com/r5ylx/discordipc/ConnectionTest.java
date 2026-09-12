package com.r5ylx.discordipc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.r5ylx.discordipc.data.Packet;
import com.r5ylx.discordipc.data.User;
import com.r5ylx.discordipc.enums.Opcode;
import com.r5ylx.discordipc.enums.State;
import com.r5ylx.discordipc.exception.IPCProtocolException;
import com.r5ylx.discordipc.transport.FakeTransport;

/** Framing and the handshake. */
class ConnectionTest {
    private FakeTransport transport;
    private Connection connection;

    @BeforeEach
    void setUp() {
        transport = new FakeTransport();
        connection = new Connection(transport);
    }

    // ---- sending ----

    @Test
    @DisplayName("a frame carries a little-endian 8-byte header")
    void writesLittleEndianHeader() throws IOException {
        connection.sendPacket(Opcode.FRAME, new JSONObject().put("cmd", "SET_ACTIVITY"));

        byte[] frame = transport.written();
        ByteBuffer buffer = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(Opcode.FRAME.getValue(), buffer.getInt());
        assertEquals(frame.length - 8, buffer.getInt());
    }

    @Test
    @DisplayName("a nonce is added on send")
    void addsNonce() throws IOException {
        connection.sendPacket(Opcode.FRAME, new JSONObject().put("cmd", "SET_ACTIVITY"));

        JSONObject sent = Frames.decode(transport.written()).getJson();

        assertTrue(sent.has("nonce"));
        assertFalse(sent.getString("nonce").isEmpty());
    }

    @Test
    @DisplayName("the caller's JSON is not modified")
    void doesNotMutateCallerJson() throws IOException {
        JSONObject original = new JSONObject().put("cmd", "SET_ACTIVITY");

        connection.sendPacket(Opcode.FRAME, original);

        // In 1.0.0 the nonce went straight into the JSONObject that was passed in.
        assertFalse(original.has("nonce"));
    }

    @Test
    @DisplayName("an oversized frame is not sent")
    void rejectsOversizedFrame() {
        char[] filler = new char[Connection.MAX_FRAME_SIZE];
        Arrays.fill(filler, 'x');

        JSONObject huge = new JSONObject().put("cmd", new String(filler));

        assertThrows(IPCProtocolException.class, () -> connection.sendPacket(Opcode.FRAME, huge));
    }

    // ---- receiving ----

    @Test
    @DisplayName("a frame can be taken apart")
    void readsFrame() throws IOException {
        transport.supply(Frames.encode(Opcode.FRAME, new JSONObject().put("evt", "ACTIVITY_JOIN")));

        Packet packet = connection.readPacket();

        assertEquals(Opcode.FRAME, packet.getOpcode());
        assertEquals("ACTIVITY_JOIN", packet.getJson().getString("evt"));
    }

    @Test
    @DisplayName("a frame split across reads is reassembled")
    void reassemblesSplitFrames() throws IOException {
        byte[] frame = Frames.encode(Opcode.FRAME, new JSONObject().put("evt", "READY"));

        // Cut it in the middle of the header.
        transport.supply(Arrays.copyOfRange(frame, 0, 3));
        transport.supply(Arrays.copyOfRange(frame, 3, 10));
        transport.supply(Arrays.copyOfRange(frame, 10, frame.length));

        assertEquals("READY", connection.readPacket().getJson().getString("evt"));
    }

    @Test
    @DisplayName("a negative frame length is rejected")
    void rejectsNegativeLength() {
        transport.supply(Frames.encodeWithLength(Opcode.FRAME, -1, "{}"));

        assertThrows(IPCProtocolException.class, () -> connection.readPacket());
    }

    @Test
    @DisplayName("a frame length over the limit is rejected")
    void rejectsOversizedLength() {
        transport.supply(Frames.encodeWithLength(Opcode.FRAME, Connection.MAX_FRAME_SIZE + 1, "{}"));

        assertThrows(IPCProtocolException.class, () -> connection.readPacket());
    }

    @Test
    @DisplayName("an unknown opcode is rejected")
    void rejectsUnknownOpcode() {
        transport.supply(Frames.encode(99, "{}"));

        assertThrows(IPCProtocolException.class, () -> connection.readPacket());
    }

    @Test
    @DisplayName("a body that is not JSON is rejected")
    void rejectsMalformedJson() {
        transport.supply(Frames.encode(Opcode.FRAME.getValue(), "this is not JSON"));

        assertThrows(IPCProtocolException.class, () -> connection.readPacket());
    }

    @Test
    @DisplayName("receiving CLOSE counts as a disconnect")
    void closeOpcodeEndsConnection() {
        transport.supply(Frames.encode(Opcode.CLOSE, new JSONObject().put("message", "goodbye")));

        EOFException error = assertThrows(EOFException.class, () -> connection.readPacket());

        assertTrue(error.getMessage().contains("goodbye"));
        assertEquals(State.DISCONNECTED, connection.getState());
    }

    @Test
    @DisplayName("being closed mid-frame counts as a disconnect")
    void truncatedFrameEndsConnection() {
        transport.supply(new byte[] { 1, 0, 0, 0 });
        transport.close();

        assertThrows(EOFException.class, () -> connection.readPacket());
    }

    // ---- the handshake ----

    @Test
    @DisplayName("a completed handshake returns the user")
    void handshakeReturnsUser() throws IOException {
        transport.onWrite(frame -> transport.supply(Frames.readyResponse()));

        User user = connection.handshake(1234567890L);

        assertEquals("n4yxr", user.getUsername());
        // A multi-byte global_name, so that the UTF-8 round trip through the frame is covered.
        assertEquals("み", user.getGlobalName().orElseThrow());
        assertEquals(State.CONNECTED, connection.getState());
        assertTrue(connection.isConnected());

        JSONObject sent = Frames.decode(transport.written()).getJson();
        assertEquals(Connection.RPC_VERSION, sent.getInt("v"));
        assertEquals("1234567890", sent.getString("client_id"));
    }

    @Test
    @DisplayName("a reply that is not READY is rejected")
    void handshakeRejectsUnexpectedResponse() {
        transport.onWrite(frame -> transport.supply(
            Frames.encode(Opcode.FRAME, new JSONObject().put("cmd", "DISPATCH").put("evt", "ERROR"))));

        assertThrows(IPCProtocolException.class, () -> connection.handshake(1L));
    }

    @Test
    @DisplayName("a reply with no user is rejected")
    void handshakeRejectsMissingUser() {
        transport.onWrite(frame -> transport.supply(
            Frames.encode(Opcode.FRAME, new JSONObject()
                .put("cmd", "DISPATCH")
                .put("evt", "READY")
                .put("data", new JSONObject().put("v", 1)))));

        assertThrows(IPCProtocolException.class, () -> connection.handshake(1L));
    }

    @Test
    @DisplayName("closing resets the state")
    void closeResetsState() throws IOException {
        transport.onWrite(frame -> transport.supply(Frames.readyResponse()));
        connection.handshake(1L);

        connection.close();

        assertEquals(State.DISCONNECTED, connection.getState());
        assertFalse(connection.isConnected());
        assertFalse(transport.isOpen());
    }
}
