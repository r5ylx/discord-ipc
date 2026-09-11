package com.r5ylx.discordipc;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.json.JSONObject;

import com.r5ylx.discordipc.data.Packet;
import com.r5ylx.discordipc.data.RichPresence;
import com.r5ylx.discordipc.data.User;
import com.r5ylx.discordipc.enums.Command;
import com.r5ylx.discordipc.enums.Event;
import com.r5ylx.discordipc.enums.Opcode;
import com.r5ylx.discordipc.exception.IPCConnectionException;
import com.r5ylx.discordipc.exception.IPCNotConnectedException;
import com.r5ylx.discordipc.transport.Transport;
import com.r5ylx.discordipc.transport.TransportFactory;

/**
 * A client for Discord's local IPC socket, with Rich Presence support.
 *
 * <h2>How connecting works</h2>
 * <p>{@link #connect()} returns immediately. A background thread does the connecting, and it
 * keeps retrying with a growing delay while Discord is not running. A drop recovers the same way.
 *
 * <p>So there is no need to wait for a connection before sending. What you pass to
 * {@link #setPresence(RichPresence)} and {@link #subscribe(Event)} is kept, and sent again
 * as soon as a connection is made and after every reconnect.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * try (IPCClient client = new IPCClient(1234567890L)) {
 *     client.setListener(new IPCListener() {
 *         @Override
 *         public void onReady(IPCClient client, User user) {
 *             System.out.println("connected as " + user.getDisplayName());
 *         }
 *     });
 *
 *     client.connect();
 *     client.setPresence(RichPresence.builder()
 *         .setDetails("In the lobby")
 *         .setStartTimestamp(Instant.now())
 *         .build());
 *     ...
 * }
 * }</pre>
 *
 * <h2>Threads</h2>
 * <p>Two daemon threads are used. One connects and reads, the other writes.
 * {@link IPCListener} is called from those threads.
 * An exception from a listener goes to {@link IPCListener#onError}; the thread keeps running.
 */
public final class IPCClient implements AutoCloseable {
    /** The first delay before a reconnect is attempted. */
    private static final Duration INITIAL_RETRY_DELAY = Duration.ofSeconds(1);
    /** The longest a reconnect delay may grow to. */
    private static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(30);
    /** How often the write queue is checked. Also the bound on noticing a stop request. */
    private static final long WRITE_POLL_MILLIS = 200;
    /** How long a stop waits for the threads to finish. */
    private static final long SHUTDOWN_TIMEOUT_MILLIS = 2000;

    private final long clientId;
    private final TransportFactory transportFactory;

    private final BlockingQueue<Packet> outgoing = new LinkedBlockingQueue<>();
    private final Set<Event> subscriptions = ConcurrentHashMap.newKeySet();

    private volatile IPCListener listener;
    private volatile Connection connection;
    private volatile User user;
    private volatile RichPresence presence;
    private volatile boolean running;

    /** Keeps an exception from a listener from looping forever by coming back through onError. */
    private final ThreadLocal<Boolean> notifyingError = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private Thread connectionThread;
    private Thread writerThread;

    /**
     * Creates a client that connects the way the running operating system expects.
     *
     * @param clientId the application id from the Discord developer portal
     */
    public IPCClient(long clientId) {
        this(clientId, TransportFactory.forCurrentPlatform());
    }

    /**
     * Creates a client with a given way of connecting. This is the seam for tests.
     *
     * @param clientId         the application id from the Discord developer portal
     * @param transportFactory what opens the connection
     */
    public IPCClient(long clientId, TransportFactory transportFactory) {
        this.clientId = clientId;
        this.transportFactory = Objects.requireNonNull(transportFactory, "transportFactory");
    }

    // ------------------------------------------------------------------ start and stop

    /**
     * Starts connecting. Returns immediately.
     *
     * @throws IllegalStateException if it has already been started
     */
    public synchronized void connect() {
        if (running) {
            throw new IllegalStateException("already started");
        }

        running = true;

        connectionThread = new Thread(this::runConnectionLoop, "Discord-IPC-Connection");
        writerThread = new Thread(this::runWriterLoop, "Discord-IPC-Writer");

        connectionThread.setDaemon(true);
        writerThread.setDaemon(true);

        connectionThread.start();
        writerThread.start();
    }

    /**
     * Closes the connection and stops the threads.
     * Safe to call before starting, and safe to call twice.
     */
    @Override
    public synchronized void close() {
        if (!running) {
            return;
        }

        running = false;

        // The reading thread is parked in a read, so the transport is closed to release it.
        closeConnection();

        Thread connectionWorker = connectionThread;
        Thread writerWorker = writerThread;

        if (connectionWorker != null) {
            connectionWorker.interrupt();
        }

        if (writerWorker != null) {
            writerWorker.interrupt();
        }

        join(connectionWorker);
        join(writerWorker);

        connectionThread = null;
        writerThread = null;

        outgoing.clear();
        user = null;
    }

    private static void join(Thread thread) {
        if (thread == null) {
            return;
        }

        try {
            thread.join(SHUTDOWN_TIMEOUT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------ sending

    /**
     * Sets what is displayed.
     *
     * <p>Can be called before connecting. It is kept and sent again on connect and reconnect.
     *
     * @throws IPCNotConnectedException if {@link #connect()} was never called, or it is closed
     */
    public void setPresence(RichPresence presence) {
        Objects.requireNonNull(presence, "presence");
        requireStarted();

        this.presence = presence;
        enqueue(activityPacket(presence));
    }

    /**
     * Clears what is displayed.
     *
     * @throws IPCNotConnectedException if {@link #connect()} was never called, or it is closed
     */
    public void clearPresence() {
        requireStarted();

        this.presence = null;
        enqueue(activityPacket(null));
    }

    /**
     * Subscribes to an event.
     *
     * <p>Can be called before connecting. Subscriptions are kept and asked for again after every
     * reconnect. Passing an event that cannot be subscribed to does nothing.
     */
    public void subscribe(Event event) {
        Objects.requireNonNull(event, "event");
        requireStarted();

        if (!event.isSubscribable() || !subscriptions.add(event)) {
            return;
        }

        enqueue(eventPacket(Command.SUBSCRIBE, event));
    }

    /** Stops subscribing to an event. */
    public void unsubscribe(Event event) {
        Objects.requireNonNull(event, "event");
        requireStarted();

        if (!subscriptions.remove(event)) {
            return;
        }

        enqueue(eventPacket(Command.UNSUBSCRIBE, event));
    }

    private void requireStarted() {
        if (!running) {
            throw new IPCNotConnectedException(clientId);
        }
    }

    private void enqueue(Packet packet) {
        outgoing.add(packet);
    }

    private Packet activityPacket(RichPresence value) {
        JSONObject args = new JSONObject()
            .put("pid", getPid())
            .put("activity", value == null ? JSONObject.NULL : value.toJson());

        return new Packet(Opcode.FRAME, new JSONObject()
            .put("cmd", Command.SET_ACTIVITY.name())
            .put("args", args));
    }

    private static Packet eventPacket(Command command, Event event) {
        return new Packet(Opcode.FRAME, new JSONObject()
            .put("cmd", command.name())
            .put("evt", event.name()));
    }

    // ------------------------------------------------------------------ state

    /** Returns true once the handshake has completed. */
    public boolean isConnected() {
        Connection current = connection;
        return current != null && current.isConnected();
    }

    /** The connected user. Returns empty while not connected. */
    public Optional<User> getUser() {
        return Optional.ofNullable(user);
    }

    /** The presence that is currently set. */
    public Optional<RichPresence> getPresence() {
        return Optional.ofNullable(presence);
    }

    public long getClientId() {
        return clientId;
    }

    public IPCListener getListener() {
        return listener;
    }

    /** Sets where notifications go. Passing null stops them. */
    public void setListener(IPCListener listener) {
        this.listener = listener;
    }

    /** The id of this process. Discord uses it to tell whose activity this is. */
    public static long getPid() {
        return ProcessHandle.current().pid();
    }

    // ------------------------------------------------------------------ connection thread

    private void runConnectionLoop() {
        Duration delay = INITIAL_RETRY_DELAY;

        while (running) {
            Connection current = connection;

            if (current == null) {
                try {
                    current = establish();
                } catch (IPCConnectionException e) {
                    notifyError(e.getMessage());

                    if (!sleep(delay)) {
                        return;
                    }

                    delay = nextDelay(delay);
                    continue;
                }

                delay = INITIAL_RETRY_DELAY;
            }

            try {
                Packet packet = current.readPacket();
                dispatch(packet);
            } catch (IOException e) {
                handleDisconnected(e.getMessage());
            }
        }
    }

    /** Tries each pipe in turn and takes the first that gets through the handshake. */
    private Connection establish() {
        for (int index = 0; index <= TransportFactory.MAX_PIPE_INDEX; index++) {
            Transport transport = null;

            try {
                transport = transportFactory.open(index);
                Connection candidate = new Connection(transport);
                User connected = candidate.handshake(clientId);

                connection = candidate;
                user = connected;

                handleConnected(connected);
                return candidate;
            } catch (IOException | RuntimeException e) {
                closeQuietly(transport);
            }
        }

        throw new IPCConnectionException(
            "no Discord IPC pipe was found. Check that Discord is running");
    }

    /** Right after connecting, sends the kept subscriptions and presence again. */
    private void handleConnected(User connected) {
        for (Event event : subscriptions) {
            enqueue(eventPacket(Command.SUBSCRIBE, event));
        }

        RichPresence current = presence;

        if (current != null) {
            enqueue(activityPacket(current));
        }

        notify(target -> target.onReady(this, connected));
    }

    private void handleDisconnected(String message) {
        Connection current = connection;

        connection = null;
        user = null;

        closeQuietly(current);

        if (running) {
            notify(target -> target.onDisconnected(this, message));
        }
    }

    private void closeConnection() {
        Connection current = connection;
        connection = null;
        closeQuietly(current);
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }

        try {
            closeable.close();
        } catch (Exception ignored) {
            // A failure while closing only means it was already gone, so it is ignored.
        }
    }

    private static Duration nextDelay(Duration current) {
        Duration doubled = current.multipliedBy(2);
        return doubled.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : doubled;
    }

    /** @return true if the wait finished without being interrupted */
    private boolean sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
            return running;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // ------------------------------------------------------------------ writer thread

    private void runWriterLoop() {
        while (running) {
            Packet packet;

            try {
                packet = outgoing.poll(WRITE_POLL_MILLIS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            if (packet == null) {
                continue;
            }

            Connection current = connection;

            if (current == null || !current.isConnected()) {
                // Anything queued while disconnected is dropped; the kept state is re-sent.
                continue;
            }

            try {
                current.sendPacket(packet.getOpcode(), packet.getJson());
                notify(target -> target.onPacketSent(this, packet));
            } catch (IOException e) {
                handleDisconnected(e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------ receiving

    private void dispatch(Packet packet) {
        notify(target -> target.onPacketReceived(this, packet));

        JSONObject json = packet.getJson();
        JSONObject data = json.optJSONObject("data");

        switch (Event.of(json.optString("evt"))) {
            case ACTIVITY_JOIN -> notify(target -> target.onJoinGame(this, secretOf(data)));
            case ACTIVITY_SPECTATE -> notify(target -> target.onSpectateGame(this, secretOf(data)));
            case ACTIVITY_JOIN_REQUEST -> {
                JSONObject requester = data == null ? null : firstNonNull(data.optJSONObject("user"), data);

                if (requester != null) {
                    User parsed = User.fromJson(requester);
                    notify(target -> target.onJoinRequest(this, parsed));
                }
            }
            case ERROR -> notifyError(errorMessageOf(data, json));
            default -> {
                // READY is handled by the handshake. For the rest, onPacketReceived is enough.
            }
        }
    }

    private static JSONObject firstNonNull(JSONObject first, JSONObject second) {
        return first != null ? first : second;
    }

    /** The join and spectate secrets live inside the data object. */
    private static String secretOf(JSONObject data) {
        return data == null ? null : data.optString("secret", null);
    }

    private static String errorMessageOf(JSONObject data, JSONObject json) {
        if (data == null) {
            return json.toString();
        }

        String message = data.optString("message", null);
        int code = data.optInt("code", -1);

        if (message == null) {
            return json.toString();
        }

        return code < 0 ? message : message + " (code " + code + ")";
    }

    // ------------------------------------------------------------------ listener dispatch

    /**
     * Calls the listener.
     * An exception from a listener is caught so the thread survives, and sent to onError.
     */
    private void notify(Consumer<IPCListener> action) {
        IPCListener target = listener;

        if (target == null) {
            return;
        }

        try {
            action.accept(target);
        } catch (Exception e) {
            notifyError("the listener threw: " + e);
        }
    }

    private void notifyError(String message) {
        IPCListener target = listener;

        if (target == null || Boolean.TRUE.equals(notifyingError.get())) {
            return;
        }

        notifyingError.set(Boolean.TRUE);

        try {
            target.onError(this, message);
        } catch (Exception ignored) {
            // If onError itself fails, there is nowhere left to report to.
        } finally {
            notifyingError.set(Boolean.FALSE);
        }
    }
}
