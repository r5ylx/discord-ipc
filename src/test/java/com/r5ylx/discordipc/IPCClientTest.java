package com.r5ylx.discordipc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.r5ylx.discordipc.data.Packet;
import com.r5ylx.discordipc.data.RichPresence;
import com.r5ylx.discordipc.data.User;
import com.r5ylx.discordipc.enums.Event;
import com.r5ylx.discordipc.enums.Opcode;
import com.r5ylx.discordipc.exception.IPCNotConnectedException;
import com.r5ylx.discordipc.transport.FakeTransport;
import com.r5ylx.discordipc.transport.Transport;
import com.r5ylx.discordipc.transport.TransportFactory;

/** How the client behaves as a whole. No real pipe is used. */
@Timeout(20)
class IPCClientTest {
    private IPCClient client;

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    /** A fake Discord that answers the handshake on its own. */
    private static final class FakeDiscord implements TransportFactory {
        private final List<FakeTransport> opened = new CopyOnWriteArrayList<>();
        private volatile boolean available = true;

        @Override
        public Transport open(int index) throws IOException {
            if (!available || index != 0) {
                throw new IOException("discord-ipc-" + index + " is not available");
            }

            FakeTransport transport = new FakeTransport();

            transport.onWrite(frame -> {
                if (Frames.decode(frame).getOpcode() == Opcode.HANDSHAKE) {
                    transport.supply(Frames.readyResponse());
                }
            });

            opened.add(transport);
            return transport;
        }

        FakeTransport latest() {
            return opened.get(opened.size() - 1);
        }

        int openedCount() {
            return opened.size();
        }

        void setAvailable(boolean available) {
            this.available = available;
        }
    }

    private static List<Packet> sentPackets(FakeTransport transport) {
        List<Packet> packets = new ArrayList<>();
        byte[] all = transport.written();
        int offset = 0;

        while (offset + 8 <= all.length) {
            int length = (all[offset + 4] & 0xFF)
                | ((all[offset + 5] & 0xFF) << 8)
                | ((all[offset + 6] & 0xFF) << 16)
                | ((all[offset + 7] & 0xFF) << 24);

            if (offset + 8 + length > all.length) {
                break;
            }

            byte[] frame = new byte[8 + length];
            System.arraycopy(all, offset, frame, 0, frame.length);
            packets.add(Frames.decode(frame));
            offset += frame.length;
        }

        return packets;
    }

    private static boolean waitFor(CountDownLatch latch) throws InterruptedException {
        return latch.await(10, TimeUnit.SECONDS);
    }

    // ---- connecting ----

    @Test
    @DisplayName("connecting calls onReady")
    void notifiesReady() throws InterruptedException {
        FakeDiscord discord = new FakeDiscord();
        CountDownLatch ready = new CountDownLatch(1);
        List<User> users = new CopyOnWriteArrayList<>();

        client = new IPCClient(1234L, discord);
        client.setListener(new IPCListener() {
            @Override
            public void onReady(IPCClient source, User user) {
                users.add(user);
                ready.countDown();
            }
        });

        client.connect();

        assertTrue(waitFor(ready), "onReady was never called");
        assertEquals("n4yxr", users.get(0).getUsername());
        assertTrue(client.isConnected());
        assertEquals("n4yxr", client.getUser().orElseThrow().getUsername());
    }

    @Test
    @DisplayName("retries while Discord is missing and reports through onError")
    void retriesWhenDiscordIsMissing() throws InterruptedException {
        FakeDiscord discord = new FakeDiscord();
        discord.setAvailable(false);

        CountDownLatch failed = new CountDownLatch(1);

        client = new IPCClient(1234L, discord);
        client.setListener(new IPCListener() {
            @Override
            public void onError(IPCClient source, String message) {
                failed.countDown();
            }
        });

        client.connect();

        assertTrue(waitFor(failed), "onError was never called");
        assertFalse(client.isConnected());
    }

    @Test
    @DisplayName("connecting twice throws")
    void rejectsDoubleConnect() {
        client = new IPCClient(1234L, new FakeDiscord());
        client.connect();

        assertThrows(IllegalStateException.class, () -> client.connect());
    }

    @Test
    @DisplayName("closing before connecting does nothing")
    void closeBeforeConnectIsSafe() {
        // In 1.0.0 shutdown() threw a NullPointerException out of ioThread.join().
        IPCClient fresh = new IPCClient(1234L, new FakeDiscord());
        fresh.close();
        fresh.close();
    }

    @Test
    @DisplayName("sending before connecting throws")
    void rejectsSendBeforeConnect() {
        IPCClient fresh = new IPCClient(1234L, new FakeDiscord());

        assertThrows(IPCNotConnectedException.class,
            () -> fresh.setPresence(RichPresence.builder().build()));
        assertThrows(IPCNotConnectedException.class, () -> fresh.subscribe(Event.ACTIVITY_JOIN));
    }

    // ---- sending ----

    @Test
    @DisplayName("the presence goes out as SET_ACTIVITY")
    void sendsPresence() throws InterruptedException {
        FakeDiscord discord = new FakeDiscord();
        CountDownLatch sent = new CountDownLatch(1);

        client = new IPCClient(1234L, discord);
        client.setListener(new IPCListener() {
            @Override
            public void onPacketSent(IPCClient source, Packet packet) {
                if ("SET_ACTIVITY".equals(packet.getJson().optString("cmd"))) {
                    sent.countDown();
                }
            }
        });

        client.connect();
        client.setPresence(RichPresence.builder().setDetails("under test").build());

        assertTrue(waitFor(sent), "SET_ACTIVITY was never sent");

        JSONObject activity = sentPackets(discord.latest()).stream()
            .filter(packet -> "SET_ACTIVITY".equals(packet.getJson().optString("cmd")))
            .findFirst()
            .orElseThrow()
            .getJson()
            .getJSONObject("args")
            .getJSONObject("activity");

        assertEquals("under test", activity.getString("details"));
    }

    @Test
    @DisplayName("a presence set before connecting is sent once connected")
    void presenceSetBeforeConnectIsSentLater() throws InterruptedException {
        FakeDiscord discord = new FakeDiscord();
        CountDownLatch sent = new CountDownLatch(1);

        client = new IPCClient(1234L, discord);
        client.setListener(new IPCListener() {
            @Override
            public void onPacketSent(IPCClient source, Packet packet) {
                if ("SET_ACTIVITY".equals(packet.getJson().optString("cmd"))) {
                    sent.countDown();
                }
            }
        });

        client.connect();
        client.setPresence(RichPresence.builder().setDetails("set first").build());

        assertTrue(waitFor(sent));
    }

    // ---- reconnecting ----

    @Test
    @DisplayName("a drop calls onDisconnected, then reconnects and sends the state again")
    void reconnectsAndRestoresState() throws InterruptedException {
        FakeDiscord discord = new FakeDiscord();
        CountDownLatch firstReady = new CountDownLatch(1);
        CountDownLatch disconnected = new CountDownLatch(1);
        CountDownLatch secondReady = new CountDownLatch(2);

        client = new IPCClient(1234L, discord);
        client.setListener(new IPCListener() {
            @Override
            public void onReady(IPCClient source, User user) {
                firstReady.countDown();
                secondReady.countDown();
            }

            @Override
            public void onDisconnected(IPCClient source, String message) {
                disconnected.countDown();
            }
        });

        client.connect();
        assertTrue(waitFor(firstReady), "the first connection was never made");

        client.subscribe(Event.ACTIVITY_JOIN);
        client.setPresence(RichPresence.builder().setDetails("still going").build());

        // Act as though the Discord side went down.
        discord.latest().close();

        // In 1.0.0 a failed read left the state connected, so this was never reached.
        assertTrue(waitFor(disconnected), "onDisconnected was never called");
        assertTrue(waitFor(secondReady), "the reconnect never happened");

        assertTrue(discord.openedCount() >= 2);

        // The subscription and the presence are sent again over the new connection.
        Thread.sleep(300);
        List<Packet> resent = sentPackets(discord.latest());

        assertTrue(resent.stream().anyMatch(p -> "SUBSCRIBE".equals(p.getJson().optString("cmd"))),
            "the subscription was not sent again");
        assertTrue(resent.stream().anyMatch(p -> "SET_ACTIVITY".equals(p.getJson().optString("cmd"))),
            "the presence was not sent again");
    }

    // ---- receiving ----

    @Test
    @DisplayName("the secret is taken out of ACTIVITY_JOIN")
    void extractsJoinSecret() throws InterruptedException {
        FakeDiscord discord = new FakeDiscord();
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch joined = new CountDownLatch(1);
        List<String> secrets = new CopyOnWriteArrayList<>();

        client = new IPCClient(1234L, discord);
        client.setListener(new IPCListener() {
            @Override
            public void onReady(IPCClient source, User user) {
                ready.countDown();
            }

            @Override
            public void onJoinGame(IPCClient source, String secret) {
                if (secret != null) {
                    secrets.add(secret);
                }

                joined.countDown();
            }
        });

        client.connect();
        assertTrue(waitFor(ready));

        discord.latest().supply(Frames.encode(Opcode.FRAME, new JSONObject()
            .put("cmd", "DISPATCH")
            .put("evt", "ACTIVITY_JOIN")
            .put("data", new JSONObject().put("secret", "join-secret-1"))));

        assertTrue(waitFor(joined), "onJoinGame was never called");

        // In 1.0.0 this was optString("data"), so it was always an empty string.
        assertEquals(List.of("join-secret-1"), secrets);
    }

    @Test
    @DisplayName("the other user is taken out of ACTIVITY_JOIN_REQUEST")
    void extractsJoinRequestUser() throws InterruptedException {
        FakeDiscord discord = new FakeDiscord();
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch requested = new CountDownLatch(1);
        List<User> users = new CopyOnWriteArrayList<>();

        client = new IPCClient(1234L, discord);
        client.setListener(new IPCListener() {
            @Override
            public void onReady(IPCClient source, User user) {
                ready.countDown();
            }

            @Override
            public void onJoinRequest(IPCClient source, User user) {
                users.add(user);
                requested.countDown();
            }
        });

        client.connect();
        assertTrue(waitFor(ready));

        discord.latest().supply(Frames.encode(Opcode.FRAME, new JSONObject()
            .put("cmd", "DISPATCH")
            .put("evt", "ACTIVITY_JOIN_REQUEST")
            .put("data", new JSONObject().put("user", new JSONObject()
                .put("id", "12345678901234567")
                .put("username", "someone")))));

        assertTrue(waitFor(requested));
        assertEquals("someone", users.get(0).getUsername());
    }

    @Test
    @DisplayName("an exception from a listener does not kill the threads")
    void listenerFailureDoesNotKillThreads() throws InterruptedException {
        FakeDiscord discord = new FakeDiscord();
        CountDownLatch errored = new CountDownLatch(1);

        client = new IPCClient(1234L, discord);
        client.setListener(new IPCListener() {
            @Override
            public void onReady(IPCClient source, User user) {
                throw new IllegalStateException("a bug on the listener side");
            }

            @Override
            public void onError(IPCClient source, String message) {
                errored.countDown();
            }
        });

        client.connect();

        assertTrue(waitFor(errored), "it was never routed to onError");
        assertTrue(client.isConnected(), "the connection is kept");
    }
}
