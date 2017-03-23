package com.dyhouse.galactics.communication;

import io.netty.channel.Channel;

/**
 * Created by aaron.pan on 2017/3/9.
 */
public interface ChannelEventListener {
    void onChannelConnect(final String remoteAddr, final Channel channel);

    void onChannelClose(final String remoteAddr, final Channel channel);

    void onChannelException(final String remoteAddr, final Channel channel);

    void onChannelIdle(final String remoteAddr, final Channel channel);
}
