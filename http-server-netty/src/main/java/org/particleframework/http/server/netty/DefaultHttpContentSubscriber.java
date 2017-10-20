package org.particleframework.http.server.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import org.particleframework.http.exceptions.ContentLengthExceededException;
import org.particleframework.web.router.RouteMatch;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    protected final long advertisedLength;
    protected Subscription subscription;
    protected long receivedLength;
    protected Throwable error;
    protected Consumer<Object> completionHandler;

    public DefaultHttpContentSubscriber(NettyHttpRequest<?> nettyHttpRequest) {
        this.nettyHttpRequest = nettyHttpRequest;
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
        receivedLength += httpContent.content().readableBytes();

        if(advertisedLength != -1 && receivedLength > advertisedLength) {
            if(subscription != null) {
                subscription.cancel();
            }
            onError(new ContentLengthExceededException(advertisedLength, receivedLength));
        }
        else {
            addContent(httpContent);
        }
    }

    protected void addContent(ByteBufHolder httpContent) {
        nettyHttpRequest.addContent(httpContent);
    }

    @Override
    public void onError(Throwable t) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Error processing Request body: " + t.getMessage(), t);
        }
        error = t;
        subscription.cancel();
        ctx.pipeline().fireExceptionCaught(t);
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
