package com.r5ylx.discordipc.transport;

import java.io.IOException;
import java.io.RandomAccessFile;

/** The implementation that uses the Windows named pipe {@code \\.\pipe\discord-ipc-N}. */
public final class WindowsPipeTransport implements Transport {
    private static final String PIPE_PREFIX = "\\\\.\\pipe\\discord-ipc-";

    private final RandomAccessFile pipe;

    /** Keeps writes from interleaving. Reads arrive on a single separate thread. */
    private final Object writeLock = new Object();

    private volatile boolean closed;

    private WindowsPipeTransport(RandomAccessFile pipe) {
        this.pipe = pipe;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (closed) {
            throw new IOException("the transport is already closed");
        }

        return pipe.read(buffer, offset, length);
    }

    @Override
    public void write(byte[] data) throws IOException {
        if (closed) {
            throw new IOException("the transport is already closed");
        }

        synchronized (writeLock) {
            pipe.write(data);
        }
    }

    @Override
    public boolean isOpen() {
        return !closed && pipe.getChannel().isOpen();
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }

        closed = true;
        pipe.close();
    }

    /** Opens a {@link WindowsPipeTransport}. */
    public static final class Factory implements TransportFactory {
        @Override
        public Transport open(int index) throws IOException {
            return new WindowsPipeTransport(new RandomAccessFile(PIPE_PREFIX + index, "rw"));
        }
    }
}
