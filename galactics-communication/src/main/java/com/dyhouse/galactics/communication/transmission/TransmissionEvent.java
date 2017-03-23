package com.dyhouse.galactics.communication.transmission;

import io.netty.channel.Channel;

/**
 * Created by aaron.pan on 2017/3/9.
 */
public class TransmissionEvent  {
    private final TransmissionEventType type;
    private final String remoteAddr;
    private final Channel channel;

    public TransmissionEvent(TransmissionEventType type, String remoteAddr, Channel channel) {
        this.type = type;
        this.remoteAddr = remoteAddr;
        this.channel = channel;
    }

    public TransmissionEventType getType() {
        return type;
    }

    public String getRemoteAddr() {
        return remoteAddr;
    }

    public Channel getChannel() {
        return channel;
    }

    @Override
    public String toString() {
        return "TransmissionEvent [type=" + type + ", remoteAddr=" + remoteAddr + ", channel=" + channel + "]";
    }
}
