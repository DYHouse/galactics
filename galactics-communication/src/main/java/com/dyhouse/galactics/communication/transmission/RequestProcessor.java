package com.dyhouse.galactics.communication.transmission;

import com.dyhouse.galactics.communication.protocol.CommunicationCommand;
import io.netty.channel.ChannelHandlerContext;

/**
 * Created by aaron.pan on 2017/3/9.
 */
public interface RequestProcessor {
    CommunicationCommand processRequest(ChannelHandlerContext ctx, CommunicationCommand request)
            throws Exception;
}
