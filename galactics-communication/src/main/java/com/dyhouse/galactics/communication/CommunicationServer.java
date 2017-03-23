package com.dyhouse.galactics.communication;

import com.dyhouse.galactics.communication.exception.CommunicationSendRequestException;
import com.dyhouse.galactics.communication.exception.CommunicationTooMuchRequestException;
import com.dyhouse.galactics.communication.protocol.CommunicationCommand;
import com.dyhouse.galactics.communication.exception.CommunicationTimeoutException;
import com.dyhouse.galactics.communication.transmission.RequestProcessor;
import io.netty.channel.Channel;

import java.util.concurrent.ExecutorService;

/**
 * Created by aaron.pan on 2017/3/9.
 */
public interface CommunicationServer extends CommunicationService {
    void registerProcessor(final int requestCode, final RequestProcessor processor,
                                  final ExecutorService executor);
    void registerDefaultProcessor(final RequestProcessor processor, final ExecutorService executor);
    void invoke(final Channel channel, final CommunicationCommand request, final long timeoutMillis)
            throws InterruptedException, CommunicationTooMuchRequestException, CommunicationTimeoutException,
            CommunicationSendRequestException;
}
