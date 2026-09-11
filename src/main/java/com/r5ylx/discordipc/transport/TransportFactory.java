package com.r5ylx.discordipc.transport;

import java.io.IOException;
import java.util.Locale;

/**
 * Opens a {@link Transport}.
 *
 * <p>Discord listens on whichever of {@code discord-ipc-0} through {@code discord-ipc-9} is free.
 * Which one it is depends on what else is running, so the caller tries them in order.
 *
 * <p>Replacing this implementation lets the whole client run in a test without a real pipe.
 */
@FunctionalInterface
public interface TransportFactory {
    /** How many pipes Discord may use. */
    int MAX_PIPE_INDEX = 9;

    /**
     * Connects to the pipe with the given number.
     *
     * @param index a number from 0 to {@link #MAX_PIPE_INDEX}
     * @throws IOException if that number is not available
     */
    Transport open(int index) throws IOException;

    /** Returns the implementation that matches the running operating system. */
    static TransportFactory forCurrentPlatform() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
            ? new WindowsPipeTransport.Factory()
            : new UnixSocketTransport.Factory();
    }
}
