package com.dyhouse.galactics.communication.exception;

/**
 * Created by aaron.pan on 2017/3/10.
 */
public class CommunicationException extends Exception {
    private static final long serialVersionUID = -5690687334570505110L;

    public CommunicationException(String message) {
        super(message);
    }

    public CommunicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
