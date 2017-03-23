package com.dyhouse.galactics.communication.exception;

/**
 * Created by aaron.pan on 2017/3/10.
 */
public class CommunicationConnectException extends CommunicationException {
    private static final long serialVersionUID = -5565366231695911316L;

    public CommunicationConnectException(String addr) {
        this(addr, null);
    }

    public CommunicationConnectException(String addr, Throwable cause) {
        super("connect to <" + addr + "> failed", cause);
    }
}