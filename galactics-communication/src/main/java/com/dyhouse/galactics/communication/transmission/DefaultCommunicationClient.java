package com.dyhouse.galactics.communication.transmission;

import com.dyhouse.galactics.communication.common.CommunicationHelper;
import com.dyhouse.galactics.communication.exception.CommunicationTooMuchRequestException;
import com.dyhouse.galactics.communication.ChannelEventListener;
import com.dyhouse.galactics.communication.CommunicationClient;
import com.dyhouse.galactics.communication.common.CommunicationUtil;
import com.dyhouse.galactics.communication.common.Pair;
import com.dyhouse.galactics.communication.exception.CommunicationConnectException;
import com.dyhouse.galactics.communication.exception.CommunicationSendRequestException;
import com.dyhouse.galactics.communication.exception.CommunicationTimeoutException;
import com.dyhouse.galactics.communication.protocol.CommunicationCommand;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by aaron.pan on 2017/3/9.
 */
public class DefaultCommunicationClient extends CommunicationAbstract implements CommunicationClient{

    private static final long LOCK_TIMEOUT_MILLIS = 3000;
    private final ClientConfig clientConfig;
    private final Bootstrap bootstrap = new Bootstrap();
    private final EventLoopGroup eventLoopGroupWorker;
    private final ExecutorService publicExecutor;
    private final ChannelEventListener channelEventListener;
    private DefaultEventExecutorGroup defaultEventExecutorGroup;
    private final Lock lockChannelTables = new ReentrantLock();
    private final ConcurrentHashMap<String, ChannelWrapper> channelTables = new ConcurrentHashMap<>();

    public DefaultCommunicationClient(final ClientConfig clientConfig) {
        this(clientConfig, null);
    }

    public DefaultCommunicationClient(final ClientConfig clientConfig,
                                      final ChannelEventListener channelEventListener) {
        super(clientConfig.getClientOnewaySemaphoreValue());
        this.clientConfig = clientConfig;
        this.channelEventListener = channelEventListener;
        int publicThreadNums = clientConfig.getClientCallbackExecutorThreads();
        if (publicThreadNums <= 0) {
            publicThreadNums = 4;
        }

        this.publicExecutor = Executors.newFixedThreadPool(publicThreadNums, new ThreadFactory() {
            private AtomicInteger threadIndex = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "ClientPublicExecutor_" + this.threadIndex.incrementAndGet());
            }
        });

        this.eventLoopGroupWorker = new NioEventLoopGroup(1, new ThreadFactory() {
            private AtomicInteger threadIndex = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, String.format("ClientSelector_%d", this.threadIndex.incrementAndGet()));
            }
        });
    }
    @Override
    public void start() {
        this.defaultEventExecutorGroup = new DefaultEventExecutorGroup(
                clientConfig.getClientWorkerThreads(),
                new ThreadFactory() {

                    private AtomicInteger threadIndex = new AtomicInteger(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "ClientWorkerThread_" + this.threadIndex.incrementAndGet());
                    }
                });

        Bootstrap handler = this.bootstrap.group(this.eventLoopGroupWorker).channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, false)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, clientConfig.getConnectTimeoutMillis())
                .option(ChannelOption.SO_SNDBUF, clientConfig.getClientSocketSndBufSize())
                .option(ChannelOption.SO_RCVBUF, clientConfig.getClientSocketRcvBufSize())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(
                                defaultEventExecutorGroup,
                                new MessageEncoder(),
                                new MessageDecoder(),
                                new IdleStateHandler(0, 0, clientConfig.getClientChannelMaxIdleTimeSeconds()),
                                new ConnectManageHandler(),
                                new ClientHandler());
                    }
                });

        if (this.channelEventListener != null) {
            this.transmissionEventExecuter.start();
        }
    }

    @Override
    public void shutdown() {
        try {
            for (ChannelWrapper cw : this.channelTables.values()) {
                this.closeChannel(null, cw.getChannel());
            }

            this.channelTables.clear();

            this.eventLoopGroupWorker.shutdownGracefully();

            if (this.transmissionEventExecuter != null) {
                this.transmissionEventExecuter.shutdown();
            }

            if (this.defaultEventExecutorGroup != null) {
                this.defaultEventExecutorGroup.shutdownGracefully();
            }
        } catch (Exception e) {
        }

        if (this.publicExecutor != null) {
            try {
                this.publicExecutor.shutdown();
            } catch (Exception e) {
            }
        }
    }

    @Override
    public void registerProcessor(int requestCode, RequestProcessor processor, ExecutorService executor) {
        ExecutorService executorThis = executor;
        if (null == executor) {
            executorThis = this.publicExecutor;
        }

        Pair<RequestProcessor, ExecutorService> pair = new Pair<>(processor, executorThis);
        this.processorTable.put(requestCode, pair);
    }

    @Override
    public void invoke(String addr, CommunicationCommand request, long timeoutMillis) throws InterruptedException, CommunicationConnectException, CommunicationTooMuchRequestException, CommunicationTimeoutException, CommunicationSendRequestException {
        final Channel channel = this.getAndCreateChannel(addr);
        if (channel != null && channel.isActive()) {
            try {
                this.invokeImpl(channel, request, timeoutMillis);
            } catch (CommunicationSendRequestException e) {
                this.closeChannel(addr, channel);
                throw e;
            }
        } else {
            this.closeChannel(addr, channel);
            throw new CommunicationConnectException(addr);
        }
    }

    private Channel getAndCreateChannel(final String addr) throws InterruptedException {
        ChannelWrapper cw = this.channelTables.get(addr);
        if (cw != null && cw.isOK()) {
            return cw.getChannel();
        }

        return this.createChannel(addr);
    }


    private Channel createChannel(final String addr) throws InterruptedException {
        ChannelWrapper cw = this.channelTables.get(addr);
        if (cw != null && cw.isOK()) {
            return cw.getChannel();
        }

        if (this.lockChannelTables.tryLock(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            try {
                boolean createNewConnection = false;
                cw = this.channelTables.get(addr);
                if (cw != null) {

                    if (cw.isOK()) {
                        return cw.getChannel();
                    } else if (!cw.getChannelFuture().isDone()) {
                        createNewConnection = false;
                    } else {
                        this.channelTables.remove(addr);
                        createNewConnection = true;
                    }
                } else {
                    createNewConnection = true;
                }

                if (createNewConnection) {
                    ChannelFuture channelFuture = this.bootstrap.connect(CommunicationHelper.string2SocketAddress(addr));

                    cw = new ChannelWrapper(channelFuture);
                    this.channelTables.put(addr, cw);
                }
            } catch (Exception e) {
            } finally {
                this.lockChannelTables.unlock();
            }
        } else {
        }

        if (cw != null) {
            ChannelFuture channelFuture = cw.getChannelFuture();
            if (channelFuture.awaitUninterruptibly(this.clientConfig.getConnectTimeoutMillis())) {
                if (cw.isOK()) {
                    return cw.getChannel();
                } else {
                }
            } else {
            }
        }

        return null;
    }

    public void closeChannel(final String addr, final Channel channel) {
        if (null == channel)
            return;

        final String addrRemote = null == addr ? CommunicationHelper.parseChannelRemoteAddr(channel) : addr;

        try {
            if (this.lockChannelTables.tryLock(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                try {
                    boolean removeItemFromTable = true;
                    final ChannelWrapper prevCW = this.channelTables.get(addrRemote);

                    if (null == prevCW) {
                        removeItemFromTable = false;
                    } else if (prevCW.getChannel() != channel) {
                        removeItemFromTable = false;
                    }

                    if (removeItemFromTable) {
                        this.channelTables.remove(addrRemote);
                    }

                    CommunicationUtil.closeChannel(channel);
                } catch (Exception e) {
                } finally {
                    this.lockChannelTables.unlock();
                }
            } else {
            }
        } catch (InterruptedException e) {
        }
    }

    public void closeChannel(final Channel channel) {
        if (null == channel)
            return;

        try {
            if (this.lockChannelTables.tryLock(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                try {
                    boolean removeItemFromTable = true;
                    ChannelWrapper prevCW = null;
                    String addrRemote = null;
                    for (Map.Entry<String, ChannelWrapper> entry : channelTables.entrySet()) {
                        String key = entry.getKey();
                        ChannelWrapper prev = entry.getValue();
                        if (prev.getChannel() != null) {
                            if (prev.getChannel() == channel) {
                                prevCW = prev;
                                addrRemote = key;
                                break;
                            }
                        }
                    }

                    if (null == prevCW) {
                       removeItemFromTable = false;
                    }

                    if (removeItemFromTable) {
                        this.channelTables.remove(addrRemote);
                        CommunicationUtil.closeChannel(channel);
                    }
                } catch (Exception e) {
                } finally {
                    this.lockChannelTables.unlock();
                }
            } else {
            }
        } catch (InterruptedException e) {
        }
    }

    @Override
    public ChannelEventListener getChannelEventListener() {
        return channelEventListener;
    }

    static class ChannelWrapper {
        private final ChannelFuture channelFuture;

        public ChannelWrapper(ChannelFuture channelFuture) {
            this.channelFuture = channelFuture;
        }

        public boolean isOK() {
            return this.channelFuture.channel() != null && this.channelFuture.channel().isActive();
        }

        public boolean isWriteable() {
            return this.channelFuture.channel().isWritable();
        }

        private Channel getChannel() {
            return this.channelFuture.channel();
        }

        public ChannelFuture getChannelFuture() {
            return channelFuture;
        }
    }

    class ConnectManageHandler extends ChannelDuplexHandler {
        @Override
        public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress,
                            ChannelPromise promise) throws Exception {
            final String local = localAddress == null ? "UNKNOW" : localAddress.toString();
            final String remote = remoteAddress == null ? "UNKNOW" : remoteAddress.toString();

            super.connect(ctx, remoteAddress, localAddress, promise);

            if (DefaultCommunicationClient.this.channelEventListener != null) {
                DefaultCommunicationClient.this.putNettyEvent(new TransmissionEvent(TransmissionEventType.CONNECT, remote, ctx.channel()));
            }
        }

        @Override
        public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            final String remoteAddress = CommunicationHelper.parseChannelRemoteAddr(ctx.channel());
            closeChannel(ctx.channel());
            super.disconnect(ctx, promise);

            if (DefaultCommunicationClient.this.channelEventListener != null) {
                DefaultCommunicationClient.this.putNettyEvent(new TransmissionEvent(TransmissionEventType.CLOSE, remoteAddress.toString(), ctx.channel()));
            }
        }

        @Override
        public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            final String remoteAddress = CommunicationHelper.parseChannelRemoteAddr(ctx.channel());
            closeChannel(ctx.channel());
            super.close(ctx, promise);

            if (DefaultCommunicationClient.this.channelEventListener != null) {
                DefaultCommunicationClient.this.putNettyEvent(new TransmissionEvent(TransmissionEventType.CLOSE, remoteAddress.toString(), ctx.channel()));
            }
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent evnet = (IdleStateEvent) evt;
                if (evnet.state().equals(IdleState.ALL_IDLE)) {
                    final String remoteAddress = CommunicationHelper.parseChannelRemoteAddr(ctx.channel());
                    closeChannel(ctx.channel());
                    if (DefaultCommunicationClient.this.channelEventListener != null) {
                        DefaultCommunicationClient.this
                                .putNettyEvent(new TransmissionEvent(TransmissionEventType.IDLE, remoteAddress.toString(), ctx.channel()));
                    }
                }
            }

            ctx.fireUserEventTriggered(evt);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            final String remoteAddress = CommunicationHelper.parseChannelRemoteAddr(ctx.channel());
            closeChannel(ctx.channel());
            if (DefaultCommunicationClient.this.channelEventListener != null) {
                DefaultCommunicationClient.this.putNettyEvent(new TransmissionEvent(TransmissionEventType.EXCEPTION, remoteAddress.toString(), ctx.channel()));
            }
        }
    }

    class ClientHandler extends SimpleChannelInboundHandler<CommunicationCommand> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, CommunicationCommand msg) throws Exception {
            processMessageReceived(ctx, msg);
        }
    }
}
