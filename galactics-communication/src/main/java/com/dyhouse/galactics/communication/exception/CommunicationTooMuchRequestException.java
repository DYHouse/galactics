package com.dyhouse.galactics.communication.exception;

/**
 * Created by aaron.pan on 2017/3/10.
 */
public class CommunicationTooMuchRequestException extends CommunicationException {
    private static final long serialVersionUID = 4326919581254519654L;

    public CommunicationTooMuchRequestException(String message) {
        super(message);
    }
}