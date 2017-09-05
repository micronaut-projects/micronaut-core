package org.particleframework.http.server.netty;

import com.fasterxml.jackson.databind.JsonNode;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import org.particleframework.configuration.jackson.parser.JacksonProcessor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

/**
 * This class will handle subscribing to a JSON stream and binding once the events are complete in a non-blocking manner
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class JsonContentSubscriber implements Subscriber<HttpContent> {
    private static final Logger LOG = LoggerFactory.getLogger(ParticleNettyHttpServer.class);


    private final JacksonProcessor jacksonProcessor = new JacksonProcessor();
    private final AtomicReference<JsonNode> nodeRef = new AtomicReference<>();
    private final AtomicReference<Throwable> error = new AtomicReference<>();
    private final ChannelHandlerContext ctx;
    private final NettyHttpRequest nettyHttpRequest;
    private final NettyHttpRequestContext requestContext;

    protected JsonContentSubscriber(NettyHttpRequestContext requestContext) {
        this.requestContext = requestContext;
        this.ctx = requestContext.getContext();
        this.nettyHttpRequest = requestContext.getRequest();
    }

    protected JsonContentSubscriber(NettyHttpRequest request) {
        this.requestContext = request.getRequestContext();
        this.ctx = requestContext.getContext();
        this.nettyHttpRequest = requestContext.getRequest();
    }

    @Override
    public void onSubscribe(Subscription s) {
        s.request(Long.MAX_VALUE);

        jacksonProcessor.subscribe(new Subscriber<JsonNode>() {
            @Override
            public void onSubscribe(Subscription s) {
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
                requestContext
                        .getResponseTransmitter()
                        .sendBadRequest(ctx);
            }

            @Override
            public void onComplete() {
                JsonNode jsonNode = nodeRef.get();
                if (jsonNode != null) {
                    JsonContentSubscriber.this.onComplete(jsonNode);
                } else {
                    requestContext
                            .getResponseTransmitter()
                            .sendBadRequest(ctx);
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
        requestContext.processRequestBody();
    }

    @Override
    public void onNext(HttpContent httpContent) {
        // TODO: content length checks
        try {
            ByteBuf content = httpContent.content();
            int len = content.readableBytes();
            if (len > 0) {
                byte[] bytes;
                if (content.hasArray()) {
                    bytes = content.array();
                } else {
                    bytes = new byte[len];
                    content.readBytes(bytes);
                }

                jacksonProcessor.onNext(bytes);
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
        requestContext.getResponseTransmitter()
                .sendBadRequest(ctx);
    }

    @Override
    public void onComplete() {
        if (error.get() == null) {
            jacksonProcessor.onComplete();
        }
    }
}
