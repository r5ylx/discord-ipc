package com.r5ylx.discordipc.enums;

/** The state of the connection. */
public enum State {
    /** Not connected. */
    DISCONNECTED,
    /** The handshake has been sent and the reply is being awaited. */
    HANDSHAKING,
    /** The handshake is done and messages can be exchanged. */
    CONNECTED;
}
