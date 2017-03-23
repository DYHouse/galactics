package com.dyhouse.galactics.communication.transmission;

import com.dyhouse.galactics.communication.common.CommunicationHelper;
import com.dyhouse.galactics.communication.common.Pair;
import com.dyhouse.galactics.communication.exception.CommunicationSendRequestException;
import com.dyhouse.galactics.communication.exception.CommunicationTooMuchRequestException;
import com.dyhouse.galactics.communication.protocol.CommunicationCommand;
import com.dyhouse.galactics.communication.ChannelEventListener;
import com.dyhouse.galactics.communication.common.SemaphoreReleaseOnlyOnce;
import com.dyhouse.galactics.communication.common.ServiceThread;
import com.dyhouse.galactics.communication.exception.CommunicationTimeoutException;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;

import java.util.HashMap;
import java.util.concurrent.*;

/**
 * Created by aaron.pan on 2017/3/9.
 */
public abstract class CommunicationAbstract {
    protected final Semaphore semaphoreOneway;
    protected Pair<RequestProcessor, ExecutorService> defaultRequestProcessor;
    protected final HashMap<Integer, Pair<RequestProcessor, ExecutorService>> processorTable =
            new HashMap<Integer, Pair<RequestProcessor, ExecutorService>>(64);
    protected final TransmissionEventExecuter transmissionEventExecuter = new TransmissionEventExecuter();
    public CommunicationAbstract(final int permitsOneway) {
        this.semaphoreOneway = new Semaphore(permitsOneway, true);
    }
    public void putNettyEvent(final TransmissionEvent event) {
        this.transmissionEventExecuter.putNettyEvent(event);
    }

    public void processMessageReceived(ChannelHandlerContext ctx, CommunicationCommand msg) throws Exception {
        final CommunicationCommand cmd = msg;
        if (cmd != null) {
            switch (cmd.getType()) {
                case REQUEST_COMMAND:
                    processRequestCommand(ctx, cmd);
                    break;
                case RESPONSE_COMMAND:
                    processResponseCommand(ctx, cmd);
                    break;
                default:
                    break;
            }
        }
    }
    public void processRequestCommand(final ChannelHandlerContext ctx, final CommunicationCommand cmd) {
        final Pair<RequestProcessor, ExecutorService> matched = this.processorTable.get(cmd.getCode());
        final Pair<RequestProcessor, ExecutorService> pair = null == matched ? this.defaultRequestProcessor : matched;

        if (pair != null) {
            Runnable run = new Runnable() {
                @Override
                public void run() {
                    try {

                        pair.getObject1().processRequest(ctx, cmd);

                    } catch (Throwable e) {

                    }
                }
            };
            try {
                final RequestTask requestTask = new RequestTask(run, ctx.channel(), cmd);
                pair.getObject2().submit(requestTask);
            } catch (RejectedExecutionException e) {

            }
        } else {

        }
    }

    public void processResponseCommand(ChannelHandlerContext ctx, CommunicationCommand cmd) {
        //// TODO: 2017/3/11
    }

    public void invokeImpl(final Channel channel, final CommunicationCommand request, final long timeoutMillis)
            throws InterruptedException, CommunicationTooMuchRequestException, CommunicationTimeoutException, CommunicationSendRequestException {
        request.markOnewayRPC();
        boolean acquired = this.semaphoreOneway.tryAcquire(timeoutMillis, TimeUnit.MILLISECONDS);
        if (acquired) {
            final SemaphoreReleaseOnlyOnce once = new SemaphoreReleaseOnlyOnce(this.semaphoreOneway);
            try {
                channel.writeAndFlush(request).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture f) throws Exception {
                        once.release();
                        if (!f.isSuccess()) {
                            System.out.println("send a request command to channel <" + channel.remoteAddress() + "> failed.");
                        }else{
                            System.out.println("send a request command to channel <" + channel.remoteAddress() + "> successed.");
                        }
                    }
                });
            } catch (Exception e) {
                once.release();
                throw new CommunicationSendRequestException(CommunicationHelper.parseChannelRemoteAddr(channel), e);
            }
        } else {
            if (timeoutMillis <= 0) {
                throw new CommunicationTooMuchRequestException("invokeImpl invoke too fast");
            } else {
                String info = String.format(
                        "invokeImpl tryAcquire semaphore timeout, %dms, waiting thread nums: %d semaphoreAsyncValue: %d", //
                        timeoutMillis, //
                        this.semaphoreOneway.getQueueLength(), //
                        this.semaphoreOneway.availablePermits()//
                );
                throw new CommunicationTimeoutException(info);
            }
        }
    }

    public abstract ChannelEventListener getChannelEventListener();
    class TransmissionEventExecuter extends ServiceThread {
        private final LinkedBlockingQueue<TransmissionEvent> eventQueue = new LinkedBlockingQueue<TransmissionEvent>();
        private final int maxSize = 10000;

        public void putNettyEvent(final TransmissionEvent event) {
            if (this.eventQueue.size() <= maxSize) {
                this.eventQueue.add(event);
            }
        }

        @Override
        public void run() {

            final ChannelEventListener listener = CommunicationAbstract.this.getChannelEventListener();

            while (!this.isStopped()) {
                try {
                    TransmissionEvent event = this.eventQueue.poll(3000, TimeUnit.MILLISECONDS);
                    if (event != null && listener != null) {
                        switch (event.getType()) {
                            case IDLE:
                                listener.onChannelIdle(event.getRemoteAddr(), event.getChannel());
                                break;
                            case CLOSE:
                                listener.onChannelClose(event.getRemoteAddr(), event.getChannel());
                                break;
                            case CONNECT:
                                listener.onChannelConnect(event.getRemoteAddr(), event.getChannel());
                                break;
                            case EXCEPTION:
                                listener.onChannelException(event.getRemoteAddr(), event.getChannel());
                                break;
                            default:
                                break;

                        }
                    }
                } catch (Exception e) {
                }
            }
        }

        @Override
        public String getServiceName() {
            return TransmissionEventExecuter.class.getSimpleName();
        }
    }
}
