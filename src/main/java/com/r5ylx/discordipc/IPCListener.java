package com.r5ylx.discordipc;

import com.r5ylx.discordipc.data.Packet;
import com.r5ylx.discordipc.data.User;

/**
 * Receives the notifications Discord sends.
 *
 * <p>Every method does nothing by default, so only the ones you need have to be implemented.
 *
 * <p>Calls arrive on a thread owned by the library. Move anything heavy, and anything that belongs
 * to your own render thread, over to that thread yourself.
 */
public interface IPCListener {
    /** Called once the handshake is complete, and again after every reconnect. */
    default void onReady(IPCClient client, User user) {
    }

    /** Called when the connection drops. A reconnect is attempted afterwards. */
    default void onDisconnected(IPCClient client, String message) {
    }

    /** Called when something unexpected happens. */
    default void onError(IPCClient client, String message) {
    }

    /** Called when the join button is pressed. */
    default void onJoinGame(IPCClient client, String secret) {
    }

    /** Called when the spectate button is pressed. */
    default void onSpectateGame(IPCClient client, String secret) {
    }

    /** Called when someone asks to join. */
    default void onJoinRequest(IPCClient client, User user) {
    }

    default void onPacketReceived(IPCClient client, Packet packet) {
    }

    default void onPacketSent(IPCClient client, Packet packet) {
    }
}
