package com.dyhouse.galactics.communication.transmission;

import com.dyhouse.galactics.communication.common.CommunicationHelper;
import com.dyhouse.galactics.communication.common.CommunicationUtil;
import com.dyhouse.galactics.communication.protocol.CommunicationCommand;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.nio.ByteBuffer;

/**
 * Created by aaron.pan on 2017/3/9.
 */
public class MessageEncoder extends MessageToByteEncoder<CommunicationCommand> {
    @Override
    public void encode(ChannelHandlerContext ctx, CommunicationCommand remotingCommand, ByteBuf out)
            throws Exception {
        try {
            ByteBuffer header = remotingCommand.encodeHeader();
            out.writeBytes(header);
            byte[] body = remotingCommand.getBody();
            if (body != null) {
                out.writeBytes(body);
            }
        } catch (Exception e) {
            System.out.println("encode exception, " + CommunicationHelper.parseChannelRemoteAddr(ctx.channel()));
            if (remotingCommand != null) {
                System.out.println(remotingCommand.toString());
            }
            CommunicationUtil.closeChannel(ctx.channel());
        }
    }
}