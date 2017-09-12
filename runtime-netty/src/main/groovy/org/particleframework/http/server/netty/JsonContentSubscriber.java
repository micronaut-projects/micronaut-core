package org.particleframework.http.server.netty;

import com.fasterxml.jackson.databind.JsonNode;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import org.particleframework.configuration.jackson.parser.JacksonProcessor;
import org.particleframework.http.HttpResponse;
import org.particleframework.http.MediaType;
import org.particleframework.http.exceptions.ContentLengthExceededException;
import org.particleframework.web.router.RouteMatch;
import org.particleframework.web.router.annotation.Consumes;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This class will handle subscribing to a JSON stream and binding once the events are complete in a non-blocking manner
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class JsonContentSubscriber implements Subscriber<HttpContent> {
    private static final Logger LOG = LoggerFactory.getLogger(NettyHttpServer.class);


    private final JacksonProcessor jacksonProcessor = new JacksonProcessor();
    private final AtomicReference<JsonNode> nodeRef = new AtomicReference<>();
    private final AtomicReference<Throwable> error = new AtomicReference<>();
    private final ChannelHandlerContext ctx;
    private final NettyHttpRequest nettyHttpRequest;
    private final RouteMatch<Object> route;
    private final long advertisedLength;
    private long accumulatedLength = 0;
    private Subscription subscription;

    public JsonContentSubscriber(NettyHttpRequest request) {
        this.nettyHttpRequest = request;
        this.advertisedLength = request.getContentLength();
        this.route = request.getMatchedRoute();
        this.ctx = request.getChannelHandlerContext();
    }

    @Override
    public void onSubscribe(Subscription s) {
        this.subscription = s;
        this.subscription.request(1L); // start with 1

        jacksonProcessor.subscribe(new Subscriber<JsonNode>() {
            @Override
            public void onSubscribe(Subscription s) {
                // downstream subscriber controlled by previous subscription, so just request everything
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(JsonNode jsonNode) {
                nodeRef.set(jsonNode);
            }

            @Override
            public void onError(Throwable t) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Error processing JSON body: " + t.getMessage(), t);
                }
                error.set(t);
                ctx.pipeline().fireExceptionCaught(t);
            }

            @Override
            public void onComplete() {
                JsonNode jsonNode = nodeRef.get();
                if (jsonNode != null) {
                    JsonContentSubscriber.this.onComplete(jsonNode);
                } else {
                    ctx.writeAndFlush(HttpResponse.badRequest())
                            .addListener(ChannelFutureListener.CLOSE);
                }
            }
        });
    }

    /**
     * Called when the materialized JSON node has been built
     *
     * @param jsonNode The {@link JsonNode} instance
     */
    protected void onComplete(JsonNode jsonNode) {
        nettyHttpRequest.setBody(jsonNode);
        route.execute();
    }

    @Override
    public void onNext(HttpContent httpContent) {
        try {
            ByteBuf content = httpContent.content();
            int len = content.readableBytes();
            if (len > 0) {
                byte[] bytes;
                accumulatedLength += (long) len;
                if(advertisedLength != -1 && accumulatedLength > advertisedLength) {
                    if(subscription != null) {
                        subscription.cancel();
                    }
                    error.set(new ContentLengthExceededException(advertisedLength, accumulatedLength));
                    onError(error.get());
                }
                if (content.hasArray()) {
                    bytes = content.array();
                } else {
                    bytes = new byte[len];
                    content.readBytes(bytes);
                }

                jacksonProcessor.onNext(bytes);
                if(jacksonProcessor.needMoreInput()) {
                    // request more input
                    subscription.request(1L);
                }
            }
        } finally {
            httpContent.release();
        }
    }

    @Override
    public void onError(Throwable t) {
        if (LOG.isErrorEnabled()) {
            LOG.error("Error processing JSON body: " + t.getMessage(), t);
        }
        error.set(t);
        ctx.pipeline().fireExceptionCaught(t);
    }

    @Override
    public void onComplete() {
        if (error.get() == null) {
            jacksonProcessor.onComplete();
        }
    }
}
