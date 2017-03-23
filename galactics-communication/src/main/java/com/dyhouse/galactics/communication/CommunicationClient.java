package com.dyhouse.galactics.communication;

import com.dyhouse.galactics.communication.exception.CommunicationSendRequestException;
import com.dyhouse.galactics.communication.exception.CommunicationTimeoutException;
import com.dyhouse.galactics.communication.exception.CommunicationTooMuchRequestException;
import com.dyhouse.galactics.communication.protocol.CommunicationCommand;
import com.dyhouse.galactics.communication.exception.CommunicationConnectException;
import com.dyhouse.galactics.communication.transmission.RequestProcessor;

import java.util.concurrent.ExecutorService;

/**
 * Created by aaron.pan on 2017/3/9.
 */
public interface CommunicationClient extends CommunicationService {
    public void registerProcessor(final int requestCode, final RequestProcessor processor,
                                  final ExecutorService executor);


    public void invoke(final String addr, final CommunicationCommand request, final long timeoutMillis)
            throws InterruptedException, CommunicationConnectException, CommunicationTooMuchRequestException,
            CommunicationTimeoutException, CommunicationSendRequestException;
}
