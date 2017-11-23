package org.particleframework.configuration.jackson.server.http;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.ReferenceCountUtil;
import org.particleframework.configuration.jackson.parser.JacksonProcessor;
import org.particleframework.core.async.subscriber.CompletionAwareSubscriber;
import org.particleframework.http.server.HttpServerConfiguration;
import org.particleframework.http.server.netty.*;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Optional;

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
    protected void doOnSubscribe(Subscription subscription, Subscriber<? super JsonNode> subscriber) {
        if(parentSubscription == null) {
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
    protected void onData(ByteBufHolder message) {
        ByteBuf content = message.content();
        try {
            byte[] bytes = ByteBufUtil.getBytes(content);
            jacksonProcessor.onNext(bytes);
        } finally {
            ReferenceCountUtil.release(content);
        }
    }

    @Override
    protected void doAfterOnError(Throwable throwable) {
        jacksonProcessor.onError(throwable);
    }

    @Override
    protected void doOnComplete() {
        jacksonProcessor.onComplete();
        super.doOnComplete();
    }
}
