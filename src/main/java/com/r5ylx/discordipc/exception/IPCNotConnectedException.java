package com.r5ylx.discordipc.exception;

/** Signals that something was sent while the connection was not established. */
public class IPCNotConnectedException extends IPCException {
    private static final long serialVersionUID = 1L;

    public IPCNotConnectedException(long clientId) {
        super("IPCClient (ID: " + clientId + ") is not connected");
    }
}
