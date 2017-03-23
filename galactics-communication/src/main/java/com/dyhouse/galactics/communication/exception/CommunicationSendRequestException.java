package com.dyhouse.galactics.communication.exception;

/**
 * Created by aaron.pan on 2017/3/10.
 */
public class CommunicationSendRequestException extends CommunicationException {
    private static final long serialVersionUID = 5391285827332471674L;

    public CommunicationSendRequestException(String addr) {
        this(addr, null);
    }

    public CommunicationSendRequestException(String addr, Throwable cause) {
        super("send request to <" + addr + "> failed", cause);
    }
}
