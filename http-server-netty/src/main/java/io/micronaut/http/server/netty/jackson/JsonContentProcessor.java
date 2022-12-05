/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.netty.jackson;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.async.subscriber.CompletionAwareSubscriber;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.netty.AbstractHttpContentProcessor;
import io.micronaut.http.server.netty.HttpContentProcessor;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.ReferenceCountUtil;
import org.reactivestreams.Processor;
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
public class JsonContentProcessor extends AbstractHttpContentProcessor {

    private final JsonMapper jsonMapper;
    private Processor<byte[], JsonNode> jacksonProcessor;
    private boolean inFlight = false;
    private Throwable failure = null;

    /**
     * @param nettyHttpRequest The Netty Http request
     * @param configuration    The Http server configuration
     * @param jsonMapper        The json codec
     */
    public JsonContentProcessor(
            NettyHttpRequest<?> nettyHttpRequest,
            HttpServerConfiguration configuration,
            JsonMapper jsonMapper) {
        super(nettyHttpRequest, configuration);
        this.jsonMapper = jsonMapper;
    }

    @Override
    public HttpContentProcessor resultType(Argument<?> type) {
        boolean streamArray = false;

        boolean isJsonStream = nettyHttpRequest.getContentType()
            .map(mediaType -> mediaType.equals(MediaType.APPLICATION_JSON_STREAM_TYPE))
            .orElse(false);

        if (type != null) {
            Class<?> targetType = type.getType();
            if (Publishers.isConvertibleToPublisher(targetType) && !Publishers.isSingle(targetType)) {
                Optional<Argument<?>> genericArgument = type.getFirstTypeVariable();
                if (genericArgument.isPresent() && !Iterable.class.isAssignableFrom(genericArgument.get().getType()) && !isJsonStream) {
                    // if the generic argument is not a iterable type them stream the array into the publisher
                    streamArray = true;
                }
            }
        }

        this.jacksonProcessor = jsonMapper.createReactiveParser(p -> {
        }, streamArray);
        this.jacksonProcessor.subscribe(new CompletionAwareSubscriber<>() {

            @Override
            protected void doOnSubscribe(Subscription jsonSubscription) {
                jsonSubscription.request(Long.MAX_VALUE);
            }

            @Override
            protected void doOnNext(JsonNode message) {
                if (!inFlight) {
                    throw new IllegalStateException("Concurrent access not allowed");
                }
                offer(message);
            }

            @Override
            protected void doOnError(Throwable t) {
                if (!inFlight) {
                    throw new IllegalStateException("Concurrent access not allowed");
                }
                failure = t;
            }

            @Override
            protected void doOnComplete() {
                if (!inFlight) {
                    throw new IllegalStateException("Concurrent access not allowed");
                }
            }
        });
        this.jacksonProcessor.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
            }

            @Override
            public void cancel() {
                // happens on error, ignore
            }
        });
        return this;
    }

    @Override
    protected void onData(ByteBufHolder message) throws Throwable {
        if (jacksonProcessor == null) {
            resultType(null);
        }

        inFlight = true;
        ByteBuf content = message.content();
        try {
            byte[] bytes = ByteBufUtil.getBytes(content);
            jacksonProcessor.onNext(bytes);
        } finally {
            ReferenceCountUtil.release(content);
            inFlight = false;
        }
        Throwable f = failure;
        if (f != null) {
            failure = null;
            throw f;
        }
    }

    @Override
    public void complete() throws Throwable {
        if (jacksonProcessor == null) {
            resultType(null);
        }

        inFlight = true;
        jacksonProcessor.onComplete();
        inFlight = false;
        Throwable f = failure;
        if (f != null) {
            failure = null;
            throw f;
        }
    }
}
