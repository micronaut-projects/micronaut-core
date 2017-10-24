package org.particleframework.configuration.jackson.server.http;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.typesafe.netty.http.StreamedHttpMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import io.netty.util.ReferenceCountUtil;
import org.particleframework.configuration.jackson.parser.JacksonProcessor;
import org.particleframework.http.HttpResponse;
import org.particleframework.http.exceptions.ContentLengthExceededException;
import org.particleframework.http.server.HttpServerConfiguration;
import org.particleframework.http.server.netty.DefaultHttpContentProcessor;
import org.particleframework.http.server.netty.HttpContentProcessor;
import org.particleframework.http.server.netty.NettyHttpRequest;
import org.particleframework.http.server.netty.NettyHttpServer;
import org.particleframework.reactive.AbstractSingleSubscriberProcessor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * This class will handle subscribing to a JSON stream and binding once the events are complete in a non-blocking manner
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class JsonContentProcessor<T> implements HttpContentProcessor<T> {


    private static final Subscription EMPTY_SUBSCRIPTION = new Subscription() {
        @Override
        public void request(long n) {
        }

        @Override
        public void cancel() {

        }
    };

    private final JacksonProcessor jacksonProcessor;
    protected final AtomicReference<Subscriber<? super T>> subscriber = new AtomicReference<>();
    private final NettyHttpRequest nettyHttpRequest;
    private final ChannelHandlerContext context;
    private final HttpServerConfiguration configuration;
    private Subscription parentSubscription;
    protected final long advertisedLength;
    protected final long requestMaxSize;
    protected final AtomicLong receivedLength = new AtomicLong();


    public JsonContentProcessor(NettyHttpRequest<?> nettyHttpRequest, HttpServerConfiguration configuration, Optional<JsonFactory> jsonFactory) {
        this.jacksonProcessor = new JacksonProcessor(jsonFactory.orElse(new JsonFactory()));
        this.nettyHttpRequest = nettyHttpRequest;
        this.requestMaxSize = configuration.getMaxRequestSize();
        this.advertisedLength = nettyHttpRequest.getContentLength();
        this.context = nettyHttpRequest.getChannelHandlerContext();
        this.configuration = configuration;
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        this.parentSubscription = subscription;
        Subscriber<? super T> subscriber = this.subscriber.get();

        if(parentSubscription == null) {
            subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
            subscriber.onError(new IllegalStateException("Upstream publisher must be subscribed to first"));
            return;
        }

        this.jacksonProcessor.subscribe(new Subscriber<JsonNode>() {
            @Override
            public void onSubscribe(Subscription jsonSubscription) {
                Subscription childSubscription = new Subscription() {
                    @Override
                    public void request(long n) {
                        jsonSubscription.request(n);
                        parentSubscription.request(n);
                    }

                    @Override
                    public void cancel() {
                        jsonSubscription.cancel();
                        parentSubscription.cancel();
                    }
                };
                subscriber.onSubscribe(childSubscription);
            }

            @Override
            public void onNext(JsonNode jsonNode) {
                subscriber.onNext((T)jsonNode);
            }

            @Override
            public void onError(Throwable t) {
                subscriber.onError(t);
            }

            @Override
            public void onComplete() {
                subscriber.onComplete();
            }
        });


        jacksonProcessor.onSubscribe(subscription);

    }

    @Override
    public void onNext(ByteBufHolder message) {
        long receivedLength = this.receivedLength.addAndGet(message.content().readableBytes());

        if((advertisedLength != -1 && receivedLength > advertisedLength) || (receivedLength > requestMaxSize)) {
            fireExceedsLength(receivedLength, advertisedLength == -1 ? requestMaxSize : advertisedLength);
        }
        else {
            long serverMax = configuration.getMultipart().getMaxFileSize();
            if( receivedLength > serverMax ) {
                fireExceedsLength(receivedLength, serverMax);
            }
            else {
                ByteBuf content = message.content();
                byte[] bytes;
                try {
                    if (content.hasArray()) {
                        bytes = content.array();
                    } else {
                        bytes = new byte[content.readableBytes()];
                        content.readBytes(bytes);
                    }
                } finally {
                    ReferenceCountUtil.release(content);
                }
                jacksonProcessor.onNext(bytes);
            }
        }
    }

    @Override
    public void onError(Throwable t) {
        jacksonProcessor.onError(t);
    }

    @Override
    public void onComplete() {
        jacksonProcessor.onComplete();
    }


    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        StreamedHttpMessage message = (StreamedHttpMessage) nettyHttpRequest.getNativeRequest();
        message.subscribe(this);

        if(!this.subscriber.compareAndSet(null, subscriber)) {
            subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
            subscriber.onError(new IllegalStateException("Only one subscriber allowed"));
        }
    }

    protected void fireExceedsLength(long receivedLength, long expected) {
        parentSubscription.cancel();
        onError(new ContentLengthExceededException(expected, receivedLength));
    }
}
