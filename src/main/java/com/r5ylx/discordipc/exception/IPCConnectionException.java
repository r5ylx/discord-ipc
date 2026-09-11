package com.r5ylx.discordipc.exception;

/**
 * Signals that the Discord pipe could not be reached.
 * Most of the time Discord is simply not running.
 */
public class IPCConnectionException extends IPCException {
    private static final long serialVersionUID = 1L;

    public IPCConnectionException(String message) {
        super(message);
    }

    public IPCConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
