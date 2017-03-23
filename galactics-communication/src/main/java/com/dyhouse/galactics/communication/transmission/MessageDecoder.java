package com.dyhouse.galactics.communication.transmission;

import com.dyhouse.galactics.communication.common.CommunicationUtil;
import com.dyhouse.galactics.communication.protocol.CommunicationCommand;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

import java.nio.ByteBuffer;

/**
 * Created by aaron.pan on 2017/3/9.
 */
public class MessageDecoder extends LengthFieldBasedFrameDecoder {
    private static final int FRAME_MAX_LENGTH =
            Integer.parseInt(System.getProperty("com.galactics.communication.frameMaxLength", "16777216"));

    public MessageDecoder() {
        super(FRAME_MAX_LENGTH, 0, 4, 0, 4);
    }

    @Override
    public Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        ByteBuf frame = null;
        try {
            frame = (ByteBuf) super.decode(ctx, in);
            if (null == frame) {
                return null;
            }

            ByteBuffer byteBuffer = frame.nioBuffer();

            return CommunicationCommand.decode(byteBuffer);
        } catch (Exception e) {
            CommunicationUtil.closeChannel(ctx.channel());
        } finally {
            if (null != frame) {
                frame.release();
            }
        }

        return null;
    }
}
