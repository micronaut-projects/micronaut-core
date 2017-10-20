package org.particleframework.http.server.netty;

import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpContent;
import org.particleframework.http.exceptions.ContentLengthExceededException;
import org.particleframework.http.server.HttpServerConfiguration;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * This class will handle subscribing to a stream of {@link HttpContent}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultHttpContentSubscriber implements HttpContentSubscriber<Object> {
    private static final Logger LOG = LoggerFactory.getLogger(NettyHttpServer.class);
    protected final NettyHttpRequest nettyHttpRequest;
    protected final ChannelHandlerContext ctx;
    protected final HttpServerConfiguration configuration;
    protected final long advertisedLength;
    protected Subscription subscription;
    protected AtomicLong receivedLength = new AtomicLong();
    protected Throwable error;
    protected Consumer<Object> completionHandler;

    public DefaultHttpContentSubscriber(NettyHttpRequest<?> nettyHttpRequest, HttpServerConfiguration configuration) {
        this.nettyHttpRequest = nettyHttpRequest;
        this.configuration = configuration;
        this.ctx = nettyHttpRequest.getChannelHandlerContext();
        this.advertisedLength = nettyHttpRequest.getContentLength();
        this.completionHandler = ( body -> {
            if(error == null) {
                nettyHttpRequest.getMatchedRoute().execute();
            }
        });
    }


    @Override
    public void onSubscribe(Subscription s) {
        s.request(Long.MAX_VALUE );
        this.subscription = s;
    }

    @Override
    public void onNext(ByteBufHolder httpContent) {
        if(error != null) {
            httpContent.release();
        }
        else {

            long receivedLength = this.receivedLength.addAndGet(httpContent.content().readableBytes());

            if((advertisedLength != -1 && receivedLength > advertisedLength)) {
                fireExceedsLength(receivedLength, this.advertisedLength);
            }
            else {
                long serverMax = configuration.getMultipart().getMaxFileSize();
                if( receivedLength > serverMax ) {
                    fireExceedsLength(receivedLength, serverMax);
                }
                else {
                    addContent(httpContent);
                }
            }
        }
    }

    protected void fireExceedsLength(long receivedLength, long expected) {
        ContentLengthExceededException exception = new ContentLengthExceededException(expected, receivedLength);
        fireException(exception);
    }

    protected void fireException(Throwable exception) {
        this.error = exception;
        if(subscription != null) {
            subscription.cancel();
        }
        ChannelPipeline pipeline = ctx.pipeline();
        if( pipeline.get("http-streams-codec-body-publisher") != null) {
            pipeline.remove("http-streams-codec-body-publisher");
        }
        pipeline.fireExceptionCaught(exception);
    }

    protected void addContent(ByteBufHolder httpContent) {
        nettyHttpRequest.addContent(httpContent);
    }

    @Override
    public void onError(Throwable t) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Error processing Request body: " + t.getMessage(), t);
        }
        fireException(t);
    }

    @Override
    public void onComplete() {
        this.completionHandler.accept(nettyHttpRequest.getBody());
    }

    @Override
    public HttpContentSubscriber onComplete(Consumer<Object> consumer) {
        this.completionHandler = consumer;
        return this;
    }
}
