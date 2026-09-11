package com.r5ylx.discordipc.transport;

import java.io.File;
import java.io.IOException;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The implementation that uses the Unix domain socket {@code $XDG_RUNTIME_DIR/discord-ipc-N} on
 * Linux and macOS.
 *
 * <p>A Discord installed through Flatpak or Snap puts its socket one level down, so the common
 * nested locations are searched as well.
 */
public final class UnixSocketTransport implements Transport {
    private static final String[] NESTED_DIRECTORIES = {
        "",
        "app/com.discordapp.Discord",
        "snap.discord",
        "app/com.discordapp.DiscordCanary",
    };

    private final SocketChannel channel;
    private final Object writeLock = new Object();

    private volatile boolean closed;

    private UnixSocketTransport(SocketChannel channel) {
        this.channel = channel;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (closed) {
            throw new IOException("the transport is already closed");
        }

        return channel.read(ByteBuffer.wrap(buffer, offset, length));
    }

    @Override
    public void write(byte[] data) throws IOException {
        if (closed) {
            throw new IOException("the transport is already closed");
        }

        synchronized (writeLock) {
            ByteBuffer buffer = ByteBuffer.wrap(data);

            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
        }
    }

    @Override
    public boolean isOpen() {
        return !closed && channel.isOpen();
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }

        closed = true;
        channel.close();
    }

    /** Opens a {@link UnixSocketTransport}. */
    public static final class Factory implements TransportFactory {
        @Override
        public Transport open(int index) throws IOException {
            IOException last = null;

            for (Path candidate : candidates(index)) {
                try {
                    SocketChannel channel = SocketChannel.open(UnixDomainSocketAddress.of(candidate));
                    channel.configureBlocking(true);

                    return new UnixSocketTransport(channel);
                } catch (IOException e) {
                    last = e;
                }
            }

            throw last != null ? last : new IOException("discord-ipc-" + index + " was not found");
        }

        private static List<Path> candidates(int index) throws IOException {
            Path base = runtimeDirectory();
            List<Path> paths = new ArrayList<>();

            for (String nested : NESTED_DIRECTORIES) {
                Path directory = nested.isEmpty() ? base : base.resolve(nested);
                paths.add(directory.resolve("discord-ipc-" + index));
            }

            return paths;
        }

        /**
         * Finds the directory the socket sits under.
         * The result is not cached, because the environment can change while the process runs.
         */
        private static Path runtimeDirectory() throws IOException {
            String[] variables = { "XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP" };

            for (String variable : variables) {
                String value = System.getenv(variable);

                if (value != null && !value.isBlank() && new File(value).isDirectory()) {
                    return Path.of(value);
                }
            }

            File fallback = new File("/tmp");

            if (fallback.isDirectory()) {
                return fallback.toPath();
            }

            throw new IOException("no temporary directory was found to hold the Discord socket");
        }
    }
}
