package com.r5ylx.discordipc.transport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.function.Consumer;

/**
 * A {@link Transport} that reproduces an exchange without a real pipe.
 *
 * <p>Stack up the bytes to be read with {@link #supply(byte[])}, catch writes with
 * {@link #onWrite(Consumer)} and answer them, and you can act out the Discord side.
 */
public final class FakeTransport implements Transport {
    private final Object lock = new Object();
    private final ByteArrayOutputStream written = new ByteArrayOutputStream();

    private byte[] readable = new byte[0];
    private int readPosition;
    private boolean closed;
    private IOException writeFailure;
    private Consumer<byte[]> writeHook;

    /** Stacks up bytes for the client to read. */
    public void supply(byte[] data) {
        synchronized (lock) {
            byte[] merged = new byte[readable.length - readPosition + data.length];
            System.arraycopy(readable, readPosition, merged, 0, readable.length - readPosition);
            System.arraycopy(data, 0, merged, readable.length - readPosition, data.length);

            readable = merged;
            readPosition = 0;
            lock.notifyAll();
        }
    }

    /** Sets what receives each frame as it is written. */
    public void onWrite(Consumer<byte[]> hook) {
        synchronized (lock) {
            this.writeHook = hook;
        }
    }

    /** Makes the next write fail. Used to reproduce a disconnect. */
    public void failNextWrite(IOException failure) {
        synchronized (lock) {
            this.writeFailure = failure;
        }
    }

    /** Every byte written so far. */
    public byte[] written() {
        synchronized (lock) {
            return written.toByteArray();
        }
    }

    /** Whether anything is left unread. */
    public boolean hasUnreadData() {
        synchronized (lock) {
            return readPosition < readable.length;
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        synchronized (lock) {
            while (readPosition >= readable.length && !closed) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException("interrupted while reading");
                }
            }

            if (readPosition >= readable.length) {
                return -1;
            }

            int count = Math.min(length, readable.length - readPosition);
            System.arraycopy(readable, readPosition, buffer, offset, count);
            readPosition += count;
            return count;
        }
    }

    @Override
    public void write(byte[] data) throws IOException {
        Consumer<byte[]> hook;

        synchronized (lock) {
            if (closed) {
                throw new IOException("the transport is already closed");
            }

            if (writeFailure != null) {
                IOException failure = writeFailure;
                writeFailure = null;
                throw failure;
            }

            written.writeBytes(data);
            hook = writeHook;
        }

        // The hook calls supply to stack up a reply, so it runs outside the lock.
        if (hook != null) {
            hook.accept(data);
        }
    }

    @Override
    public boolean isOpen() {
        synchronized (lock) {
            return !closed;
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            closed = true;
            lock.notifyAll();
        }
    }
}
