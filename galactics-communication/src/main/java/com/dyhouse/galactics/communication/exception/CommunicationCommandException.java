package com.dyhouse.galactics.communication.exception;

/**
 * Created by aaron.pan on 2017/3/10.
 */
public class CommunicationCommandException extends CommunicationException {
    private static final long serialVersionUID = -6061365915274953096L;

    public CommunicationCommandException(String message) {
        super(message, null);
    }

    public CommunicationCommandException(String message, Throwable cause) {
        super(message, cause);
    }
}

