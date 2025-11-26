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
package io.micronaut.jackson.core.parser;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NonNull;
import io.micronaut.core.async.processor.SingleThreadedBufferingProcessor;
import io.micronaut.jackson.core.tree.JsonNodeTreeCodec;
import io.micronaut.jackson.core.tree.JsonStreamTransfer;
import io.micronaut.jackson.core.tree.TreeGenerator;
import io.micronaut.json.JsonStreamConfig;
import io.micronaut.json.tree.JsonNode;
import org.reactivestreams.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.TokenStreamFactory;
import tools.jackson.core.async.ByteArrayFeeder;
import tools.jackson.core.exc.UnexpectedEndOfInputException;
import tools.jackson.core.json.async.NonBlockingByteArrayJsonParser;

import java.io.IOException;
import java.util.Optional;

/**
 * A Reactive streams publisher that publishes a {@link JsonNode} once the JSON has been fully consumed.
 * Uses {@link NonBlockingByteArrayJsonParser} internally allowing the parsing of
 * JSON from an incoming stream of bytes in a non-blocking manner
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class JacksonCoreProcessor extends SingleThreadedBufferingProcessor<byte[], JsonNode> {

    private static final Logger LOG = LoggerFactory.getLogger(JacksonCoreProcessor.class);

    private NonBlockingByteArrayJsonParser currentNonBlockingByteArrayJsonParser;
    private TreeGenerator currentGenerator = null;

    private final TokenStreamFactory jsonFactory;
    private final JsonStreamConfig deserializationConfig;
    private final JsonNodeTreeCodec treeCodec;

    private final boolean streamArray;

    private boolean started;
    private boolean rootIsArray;
    private boolean jsonStream;

    /**
     * Creates a new JacksonProcessor.
     *
     * @param streamArray Whether arrays should be streamed
     * @param jsonFactory Factory to use for creating the parser
     * @param deserializationConfig The deserialization configuration (in particular bignum handling)
     */
    public JacksonCoreProcessor(boolean streamArray, TokenStreamFactory jsonFactory, @NonNull JsonStreamConfig deserializationConfig) {
        this.jsonFactory = jsonFactory;
        this.streamArray = streamArray;
        this.treeCodec = JsonNodeTreeCodec.getInstance().withConfig(deserializationConfig);
        this.jsonStream = true;
        this.deserializationConfig = deserializationConfig;
        this.currentNonBlockingByteArrayJsonParser = jsonFactory.createNonBlockingByteArrayParser(ObjectReadContext.empty());
    }

    /**
     * @return Whether more input is needed
     */
    public boolean needMoreInput() {
        return currentNonBlockingByteArrayJsonParser.nonBlockingInputFeeder().needMoreInput();
    }

    @Override
    protected void doOnComplete() {
        if (jsonStream && currentGenerator == null) {
            super.doOnComplete();
        } else if (needMoreInput()) {
            doOnError(new UnexpectedEndOfInputException(currentNonBlockingByteArrayJsonParser, JsonToken.NOT_AVAILABLE, "Unexpected end-of-input"));
        } else {
            super.doOnComplete();
        }
    }

    @Override
    protected void onUpstreamMessage(byte[] message) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Received upstream bytes of length: {}", message.length);
        }

        try {
            if (message.length == 0) {
                if (needMoreInput()) {
                    requestMoreInput();
                }
                return;
            }

            final ByteArrayFeeder byteFeeder = byteFeeder(message);

            JsonToken event;

            while ((event = currentNonBlockingByteArrayJsonParser.nextToken()) != JsonToken.NOT_AVAILABLE) {
                if (!started) {
                    started = true;
                    if (streamArray && event == JsonToken.START_ARRAY) {
                        rootIsArray = true;
                        jsonStream = false;
                        continue;
                    }
                }

                if (currentGenerator == null) {
                    if (event == JsonToken.END_ARRAY && rootIsArray) {
                        byteFeeder.endOfInput();
                        break;
                    }

                    currentGenerator = treeCodec.createTreeGenerator();
                }

                JsonStreamTransfer.transferCurrentToken(currentNonBlockingByteArrayJsonParser, currentGenerator, deserializationConfig);

                if (currentGenerator.isComplete()) {
                    publishNode(currentGenerator.getCompletedValue());
                    currentGenerator = null;
                }
            }
            if (jsonStream) {
                if (currentGenerator == null) {
                    byteFeeder.endOfInput();
                }
                requestMoreInput();
            } else {
                if (needMoreInput()) {
                    requestMoreInput();
                }
            }
        } catch (IOException e) {
            onError(e);
        }
    }

    private void publishNode(final JsonNode root) {
        final Optional<Subscriber<? super JsonNode>> opt = currentDownstreamSubscriber();
        if (opt.isPresent()) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Materialized new JsonNode call onNext...");
            }
            opt.get().onNext(root);
        }
    }

    private void requestMoreInput() {
        if (LOG.isTraceEnabled()) {
            LOG.trace("More input required to parse JSON. Demanding more.");
        }
        upstreamSubscription.request(1);
        upstreamDemand++;
    }

    private ByteArrayFeeder byteFeeder(byte[] message) throws IOException {
        ByteArrayFeeder byteFeeder = currentNonBlockingByteArrayJsonParser.nonBlockingInputFeeder();
        final boolean needMoreInput = byteFeeder.needMoreInput();
        if (!needMoreInput) {
            currentNonBlockingByteArrayJsonParser = (NonBlockingByteArrayJsonParser) jsonFactory.createNonBlockingByteArrayParser(ObjectReadContext.empty());
            byteFeeder = currentNonBlockingByteArrayJsonParser.nonBlockingInputFeeder();
        }

        byteFeeder.feedInput(message, 0, message.length);
        return byteFeeder;
    }
}
