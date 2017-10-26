package org.particleframework.configuration.jackson.server.http;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.typesafe.netty.http.StreamedHttpMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.util.ReferenceCountUtil;
import org.particleframework.configuration.jackson.parser.JacksonProcessor;
import org.particleframework.core.async.CompletionAwareSubscriber;
import org.particleframework.http.exceptions.ContentLengthExceededException;
import org.particleframework.http.server.HttpServerConfiguration;
import org.particleframework.http.server.netty.*;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This class will handle subscribing to a JSON stream and binding once the events are complete in a non-blocking manner
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class JsonContentProcessor extends AbstractHttpContentProcessor<JsonNode> {


    private final JacksonProcessor jacksonProcessor;

    public JsonContentProcessor(NettyHttpRequest<?> nettyHttpRequest, HttpServerConfiguration configuration, Optional<JsonFactory> jsonFactory) {
        super(nettyHttpRequest, configuration);
        this.jacksonProcessor = new JacksonProcessor(jsonFactory.orElse(new JsonFactory()));
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        this.parentSubscription = subscription;
        Subscriber<? super JsonNode> subscriber = this.subscriber.get();

        if(!verifyState(subscriber)) {
            return;
        }

        this.jacksonProcessor.subscribe(new CompletionAwareSubscriber<JsonNode>() {

            @Override
            protected void doOnSubscribe(Subscription jsonSubscription) {
                Subscription childSubscription = new Subscription() {
                    @Override
                    public synchronized void request(long n) {
                        jsonSubscription.request(n);
                        parentSubscription.request(n);
                    }

                    @Override
                    public synchronized void cancel() {
                        jsonSubscription.cancel();
                        parentSubscription.cancel();
                    }
                };
                subscriber.onSubscribe(childSubscription);
            }

            @Override
            protected void doOnNext(JsonNode message) {
                subscriber.onNext(message);
            }

            @Override
            protected void doOnError(Throwable t) {
                subscriber.onError(t);
            }

            @Override
            protected void doOnComplete() {
                subscriber.onComplete();
            }
        });


        jacksonProcessor.onSubscribe(subscription);

    }

    @Override
    protected void publishMessage(ByteBufHolder message) {
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

    @Override
    public void onError(Throwable t) {
        jacksonProcessor.onError(t);
    }

    @Override
    public void onComplete() {
        jacksonProcessor.onComplete();
    }


}
