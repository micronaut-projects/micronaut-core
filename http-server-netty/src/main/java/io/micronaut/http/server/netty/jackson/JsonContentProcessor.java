/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.netty.jackson;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JsonNode;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.async.subscriber.CompletionAwareSubscriber;
import io.micronaut.core.async.subscriber.TypedSubscriber;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.netty.AbstractHttpContentProcessor;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.jackson.parser.JacksonProcessor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.ReferenceCountUtil;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Optional;

/**
 * This class will handle subscribing to a JSON stream and binding once the events are complete in a non-blocking
 * manner.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class JsonContentProcessor extends AbstractHttpContentProcessor<JsonNode> {

    private final JsonFactory jsonFactory;
    private final DeserializationConfig deserializationConfig;
    private JacksonProcessor jacksonProcessor;

    /**
     * @param nettyHttpRequest The Netty Http request
     * @param configuration    The Http server configuration
     * @param jsonFactory      The json factory
     * @param deserializationConfig The jackson deserialization configuration
     */
    public JsonContentProcessor(NettyHttpRequest<?> nettyHttpRequest, HttpServerConfiguration configuration, Optional<JsonFactory> jsonFactory, DeserializationConfig deserializationConfig) {
        super(nettyHttpRequest, configuration);
        this.jsonFactory = jsonFactory.orElse(new JsonFactory());
        this.deserializationConfig = deserializationConfig;
    }

    @Override
    protected void doOnSubscribe(Subscription subscription, Subscriber<? super JsonNode> subscriber) {
        if (parentSubscription == null) {
            return;
        }

        boolean streamArray = false;

        boolean isJsonStream = nettyHttpRequest.getContentType()
                .map(mediaType -> mediaType.equals(MediaType.APPLICATION_JSON_STREAM_TYPE))
                .orElse(false);

        if (subscriber instanceof TypedSubscriber) {
            TypedSubscriber typedSubscriber = (TypedSubscriber) subscriber;
            Argument typeArgument = typedSubscriber.getTypeArgument();

            Class targetType = typeArgument.getType();
            if (Publishers.isConvertibleToPublisher(targetType) && !Publishers.isSingle(targetType)) {
                Optional<Argument<?>> genericArgument = typeArgument.getFirstTypeVariable();
                if (genericArgument.isPresent() && !Iterable.class.isAssignableFrom(genericArgument.get().getType()) && !isJsonStream) {
                    // if the generic argument is not a iterable type them stream the array into the publisher
                    streamArray = true;
                }
            }
        }

        this.jacksonProcessor = new JacksonProcessor(jsonFactory, streamArray, deserializationConfig);
        this.jacksonProcessor.subscribe(new CompletionAwareSubscriber<JsonNode>() {

            @Override
            protected void doOnSubscribe(Subscription jsonSubscription) {

                Subscription childSubscription = new Subscription() {
                    boolean first = true;
                    @Override
                    public synchronized void request(long n) {
                        // this is a hack. The first item emitted for arrays is already in the buffer
                        // and not part of the demand, so we have to demand 1 extra
                        // find a better way in the future
                        if (first) {
                            jsonSubscription.request(n < Long.MAX_VALUE ? n + 1 : n);
                            parentSubscription.request(n < Long.MAX_VALUE ? n + 1 : n);
                        } else {
                            jsonSubscription.request(n);
                            parentSubscription.request(n);
                        }
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
