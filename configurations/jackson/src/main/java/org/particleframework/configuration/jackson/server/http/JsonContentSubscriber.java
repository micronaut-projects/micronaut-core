package org.particleframework.configuration.jackson.server.http;

import com.fasterxml.jackson.databind.JsonNode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import org.particleframework.configuration.jackson.parser.JacksonProcessor;
import org.particleframework.http.HttpResponse;
import org.particleframework.http.exceptions.ContentLengthExceededException;
import org.particleframework.http.server.HttpServerConfiguration;
import org.particleframework.http.server.netty.HttpContentSubscriber;
import org.particleframework.http.server.netty.NettyHttpRequest;
import org.particleframework.http.server.netty.NettyHttpServer;
import org.particleframework.web.router.RouteMatch;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * This class will handle subscribing to a JSON stream and binding once the events are complete in a non-blocking manner
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class JsonContentSubscriber implements HttpContentSubscriber<JsonNode> {
    private static final Logger LOG = LoggerFactory.getLogger(NettyHttpServer.class);


    private final JacksonProcessor jacksonProcessor = new JacksonProcessor();
    private final AtomicReference<JsonNode> nodeRef = new AtomicReference<>();
    private final AtomicReference<Throwable> error = new AtomicReference<>();
    private final ChannelHandlerContext ctx;
    private final NettyHttpRequest nettyHttpRequest;
    private final long advertisedLength;
    private final long requestMaxSize;
    private final AtomicLong accumulatedLength = new AtomicLong();
    private Subscription subscription;
    private Consumer<JsonNode> completionHandler;

    public JsonContentSubscriber(NettyHttpRequest request, HttpServerConfiguration httpServerConfiguration) {
        this.nettyHttpRequest = request;
        this.requestMaxSize = httpServerConfiguration.getMaxRequestSize();
        this.advertisedLength = request.getContentLength();
        this.ctx = request.getChannelHandlerContext();
        this.completionHandler = (jsonNode -> {
            nettyHttpRequest.setBody(jsonNode);
            nettyHttpRequest.getMatchedRoute().execute();
        });
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
        this.completionHandler.accept(jsonNode);
    }

    @Override
    public void onNext(ByteBufHolder httpContent) {
        try {
            ByteBuf content = httpContent.content();
            int len = content.readableBytes();
            if (len > 0) {
                byte[] bytes;
                long accumulatedLength = this.accumulatedLength.addAndGet(len);
                if((advertisedLength != -1 && accumulatedLength > advertisedLength) || (accumulatedLength > requestMaxSize)) {
                    if(subscription != null) {
                        subscription.cancel();
                    }
                    error.set(new ContentLengthExceededException(advertisedLength == -1 ? requestMaxSize : advertisedLength, accumulatedLength));
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
        error.set(t);
        ctx.pipeline().fireExceptionCaught(t);
    }

    @Override
    public void onComplete() {
        if (error.get() == null) {
            jacksonProcessor.onComplete();
        }
    }

    @Override
    public HttpContentSubscriber onComplete(Consumer<JsonNode> consumer) {
        this.completionHandler = consumer;
        return this;
    }
}
