package com.r5ylx.discordipc.exception;

/** The base of every exception this library throws. */
public class IPCException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public IPCException(String message) {
        super(message);
    }

    public IPCException(String message, Throwable cause) {
        super(message, cause);
    }
}
