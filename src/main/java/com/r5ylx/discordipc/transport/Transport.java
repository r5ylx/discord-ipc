package com.r5ylx.discordipc.transport;

import java.io.Closeable;
import java.io.IOException;

/**
 * The layer that does nothing but move bytes to and from Discord.
 *
 * <p>Framing and the handshake belong to {@code Connection}. This is replaceable so that the
 * protocol can be exercised without a real pipe.
 *
 * <p>An implementation must survive being called from one reader thread and one writer thread at
 * the same time. Two reads, or two writes, never overlap.
 */
public interface Transport extends Closeable {
    /**
     * Reads up to {@code length} bytes.
     *
     * @return how many bytes were actually read, or -1 once the far end has closed
     */
    int read(byte[] buffer, int offset, int length) throws IOException;

    /** Writes every byte of the given array. */
    void write(byte[] data) throws IOException;

    /** Returns true while messages can still be exchanged. */
    boolean isOpen();
}
