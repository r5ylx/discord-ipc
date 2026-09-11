package com.r5ylx.discordipc.exception;

/**
 * Signals that a received frame does not match the protocol.
 * For example a frame length out of range, an unknown opcode, or a handshake reply that differs
 * from what was expected.
 */
public class IPCProtocolException extends IPCException {
    private static final long serialVersionUID = 1L;

    public IPCProtocolException(String message) {
        super(message);
    }

    public IPCProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
