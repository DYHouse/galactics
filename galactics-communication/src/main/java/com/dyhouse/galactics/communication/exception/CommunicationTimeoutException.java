package com.dyhouse.galactics.communication.exception;

/**
 * Created by aaron.pan on 2017/3/10.
 */
public class CommunicationTimeoutException extends CommunicationException {

    private static final long serialVersionUID = 4106899185095245979L;

    public CommunicationTimeoutException(String message) {
        super(message);
    }

    public CommunicationTimeoutException(String addr, long timeoutMillis) {
        this(addr, timeoutMillis, null);
    }

    public CommunicationTimeoutException(String addr, long timeoutMillis, Throwable cause) {
        super("wait response on the channel <" + addr + "> timeout, " + timeoutMillis + "(ms)", cause);
    }
}
