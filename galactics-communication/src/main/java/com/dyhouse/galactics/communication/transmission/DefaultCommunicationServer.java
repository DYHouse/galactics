package com.dyhouse.galactics.communication.transmission;

import com.dyhouse.galactics.communication.ChannelEventListener;
import com.dyhouse.galactics.communication.common.CommunicationHelper;
import com.dyhouse.galactics.communication.common.Pair;
import com.dyhouse.galactics.communication.exception.CommunicationSendRequestException;
import com.dyhouse.galactics.communication.exception.CommunicationTooMuchRequestException;
import com.dyhouse.galactics.communication.protocol.CommunicationCommand;
import com.dyhouse.galactics.communication.CommunicationServer;
import com.dyhouse.galactics.communication.common.CommunicationUtil;
import com.dyhouse.galactics.communication.exception.CommunicationTimeoutException;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by aaron.pan on 2017/3/10.
 */
public class DefaultCommunicationServer extends CommunicationAbstract implements CommunicationServer {
    private final ServerBootstrap serverBootstrap;
    private final EventLoopGroup eventLoopGroupSelector;
    private final EventLoopGroup eventLoopGroupBoss;
    private final ServerConfig serverConfig;

    private final ExecutorService publicExecutor;
    private final ChannelEventListener channelEventListener;
    private DefaultEventExecutorGroup defaultEventExecutorGroup;
    private int port = 0;

    public DefaultCommunicationServer(final ServerConfig serverConfig) {
        this(serverConfig, null);
    }

    public DefaultCommunicationServer(final ServerConfig serverConfig, final ChannelEventListener channelEventListener) {
        super(serverConfig.getServerOnewaySemaphoreValue());
        this.serverBootstrap = new ServerBootstrap();
        this.serverConfig = serverConfig;
        this.channelEventListener = channelEventListener;

        int publicThreadNums = serverConfig.getServerCallbackExecutorThreads();
        if (publicThreadNums <= 0) {
            publicThreadNums = 4;
        }

        this.publicExecutor = Executors.newFixedThreadPool(publicThreadNums, new ThreadFactory() {
            private AtomicInteger threadIndex = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "ServerPublicExecutor_" + this.threadIndex.incrementAndGet());
            }
        });

        this.eventLoopGroupBoss = new NioEventLoopGroup(1, new ThreadFactory() {
            private AtomicInteger threadIndex = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, String.format("Boss_%d", this.threadIndex.incrementAndGet()));
            }
        });

        if (CommunicationUtil.isLinuxPlatform()
                && serverConfig.isUseEpollNativeSelector()) {
            this.eventLoopGroupSelector = new EpollEventLoopGroup(serverConfig.getServerSelectorThreads(), new ThreadFactory() {
                private AtomicInteger threadIndex = new AtomicInteger(0);
                private int threadTotal = serverConfig.getServerSelectorThreads();

                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, String.format("ServerEPOLLSelector_%d_%d", threadTotal, this.threadIndex.incrementAndGet()));
                }
            });
        } else {
            this.eventLoopGroupSelector = new NioEventLoopGroup(serverConfig.getServerSelectorThreads(), new ThreadFactory() {
                private AtomicInteger threadIndex = new AtomicInteger(0);
                private int threadTotal = serverConfig.getServerSelectorThreads();

                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, String.format("ServerNIOSelector_%d_%d", threadTotal, this.threadIndex.incrementAndGet()));
                }
            });
        }
    }

    @Override
    public void start() {
        this.defaultEventExecutorGroup = new DefaultEventExecutorGroup(//
                serverConfig.getServerWorkerThreads(), //
                new ThreadFactory() {

                    private AtomicInteger threadIndex = new AtomicInteger(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "ServerCodecThread_" + this.threadIndex.incrementAndGet());
                    }
                });

        ServerBootstrap childHandler =
                this.serverBootstrap.group(this.eventLoopGroupBoss, this.eventLoopGroupSelector)
                        .channel(NioServerSocketChannel.class)
                        .option(ChannelOption.SO_BACKLOG, 1024)
                        .option(ChannelOption.SO_REUSEADDR, true)
                        .option(ChannelOption.SO_KEEPALIVE, false)
                        .childOption(ChannelOption.TCP_NODELAY, true)
                        .option(ChannelOption.SO_SNDBUF, serverConfig.getServerSocketSndBufSize())
                        .option(ChannelOption.SO_RCVBUF, serverConfig.getServerSocketRcvBufSize())
                        .localAddress(new InetSocketAddress(this.serverConfig.getListenPort()))
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            public void initChannel(SocketChannel ch) throws Exception {
                                ch.pipeline().addLast(
                                        defaultEventExecutorGroup,
                                        new MessageEncoder(),
                                        new MessageDecoder(),
                                        new IdleStateHandler(0, 0, serverConfig.getServerChannelMaxIdleTimeSeconds()),
                                        new ConnetManageHandler(),
                                        new ServerHandler());
                            }
                        });

        if (serverConfig.isServerPooledByteBufAllocatorEnable()) {
            childHandler.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        }

        try {
            ChannelFuture sync = this.serverBootstrap.bind().sync();
            InetSocketAddress addr = (InetSocketAddress) sync.channel().localAddress();
            this.port = addr.getPort();
        } catch (InterruptedException e1) {
            throw new RuntimeException("this.serverBootstrap.bind().sync() InterruptedException", e1);
        }

        if (this.channelEventListener != null) {
            this.transmissionEventExecuter.start();
        }
    }

    @Override
    public void shutdown() {
        try {
            this.eventLoopGroupBoss.shutdownGracefully();

            this.eventLoopGroupSelector.shutdownGracefully();

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
    public void registerDefaultProcessor(RequestProcessor processor, ExecutorService executor) {
        this.defaultRequestProcessor = new Pair<RequestProcessor, ExecutorService>(processor, executor);
    }

    @Override
    public void invoke(Channel channel, CommunicationCommand request, long timeoutMillis) throws InterruptedException, CommunicationTooMuchRequestException, CommunicationTimeoutException, CommunicationSendRequestException {
        //// TODO: 2017/3/11 ;
    }


    @Override
    public ChannelEventListener getChannelEventListener() {
        return channelEventListener;
    }

    class ServerHandler extends SimpleChannelInboundHandler<CommunicationCommand> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, CommunicationCommand msg) throws Exception {
            processMessageReceived(ctx, msg);
        }
    }

    class ConnetManageHandler extends ChannelDuplexHandler {
        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            final String remoteAddress = CommunicationHelper.parseChannelRemoteAddr(ctx.channel());
            super.channelRegistered(ctx);
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
            final String remoteAddress = CommunicationHelper.parseChannelRemoteAddr(ctx.channel());
            super.channelUnregistered(ctx);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            final String remoteAddress = CommunicationHelper.parseChannelRemoteAddr(ctx.channel());
            super.channelActive(ctx);

            if (DefaultCommunicationServer.this.channelEventListener != null) {
                DefaultCommunicationServer.this.putNettyEvent(new TransmissionEvent(TransmissionEventType.CONNECT, remoteAddress.toString(), ctx.channel()));
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            final String remoteAddress = CommunicationHelper.parseChannelRemoteAddr(ctx.channel());
            super.channelInactive(ctx);

            if (DefaultCommunicationServer.this.channelEventListener != null) {
                DefaultCommunicationServer.this.putNettyEvent(new TransmissionEvent(TransmissionEventType.CLOSE, remoteAddress.toString(), ctx.channel()));
            }
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent evnet = (IdleStateEvent) evt;
                if (evnet.state().equals(IdleState.ALL_IDLE)) {
                    final String remoteAddress = CommunicationHelper.parseChannelRemoteAddr(ctx.channel());
                    CommunicationUtil.closeChannel(ctx.channel());
                    if (DefaultCommunicationServer.this.channelEventListener != null) {
                        DefaultCommunicationServer.this
                                .putNettyEvent(new TransmissionEvent(TransmissionEventType.IDLE, remoteAddress.toString(), ctx.channel()));
                    }
                }
            }

            ctx.fireUserEventTriggered(evt);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            final String remoteAddress = CommunicationHelper.parseChannelRemoteAddr(ctx.channel());
            if (DefaultCommunicationServer.this.channelEventListener != null) {
                DefaultCommunicationServer.this.putNettyEvent(new TransmissionEvent(TransmissionEventType.EXCEPTION, remoteAddress.toString(), ctx.channel()));
            }

            CommunicationUtil.closeChannel(ctx.channel());
        }
    }
}
