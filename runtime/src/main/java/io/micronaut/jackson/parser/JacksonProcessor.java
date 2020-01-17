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
package io.micronaut.jackson.parser;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.async.ByteArrayFeeder;
import com.fasterxml.jackson.core.io.JsonEOFException;
import com.fasterxml.jackson.core.json.async.NonBlockingJsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.deser.DefaultDeserializationContext;
import com.fasterxml.jackson.databind.util.TokenBuffer;

import io.micronaut.core.async.processor.SingleThreadedBufferingProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * A Reactive streams publisher that publishes a {@link JsonNode} once the JSON
 * has been fully consumed. Uses
 * {@link com.fasterxml.jackson.core.json.async.NonBlockingJsonParser}
 * internally allowing the parsing of JSON from an incoming stream of bytes in a
 * non-blocking manner
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class JacksonProcessor extends SingleThreadedBufferingProcessor<byte[], JsonNode> {

    private static final Logger LOG = LoggerFactory.getLogger(JacksonProcessor.class);

    private NonBlockingJsonParser currentNonBlockingJsonParser;
    private final JsonFactory jsonFactory;
    private DeserializationContext deserializationContext;
    private final ObjectMapper objectMapper;
    private boolean streamArray;
    private TokenBuffer tokenBuffer;
    private int objectDepth;
    private int arrayDepth;

    private boolean rootArray;

    /**
     * Creates a new JacksonProcessor.
     *
     * @param jsonFactory  The JSON factory
     * @param streamArray  Whether arrays should be streamed
     * @param objectMapper The jackson object mapper
     */
    public JacksonProcessor(JsonFactory jsonFactory, boolean streamArray, ObjectMapper objectMapper) {
        try {
            this.jsonFactory = jsonFactory;
            this.currentNonBlockingJsonParser = (NonBlockingJsonParser) jsonFactory.createNonBlockingByteArrayParser();
            this.objectMapper = objectMapper.copy().disable(DeserializationFeature.UNWRAP_ROOT_VALUE).disable(SerializationFeature.WRAP_ROOT_VALUE);
            this.deserializationContext = objectMapper.getDeserializationContext();
            if (this.deserializationContext instanceof DefaultDeserializationContext) {
                this.deserializationContext = ((DefaultDeserializationContext) this.deserializationContext).createInstance(objectMapper.getDeserializationConfig(),
                        currentNonBlockingJsonParser, objectMapper.getInjectableValues());
            }
            this.tokenBuffer = new TokenBuffer(this.currentNonBlockingJsonParser, this.deserializationContext);
            this.streamArray = streamArray;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create non-blocking JSON parser: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a new JacksonProcessor.
     *
     * @param jsonFactory The JSON factory
     * @param streamArray Whether arrays should be streamed
     */
    public JacksonProcessor(JsonFactory jsonFactory, boolean streamArray) {
        this(jsonFactory, streamArray, null);
    }

    /**
     * Construct with given JSON factory.
     *
     * @param jsonFactory  To configure and construct reader (aka parser,
     *                     {@link JsonParser}) and writer (aka generator,
     *                     {@link JsonGenerator}) instances.
     * @param objectMapper The jackson object mapper
     */
    public JacksonProcessor(JsonFactory jsonFactory, ObjectMapper objectMapper) {
        this(jsonFactory, false, objectMapper);
    }

    /**
     * Construct with given JSON factory.
     *
     * @param jsonFactory To configure and construct reader (aka parser,
     *                    {@link JsonParser}) and writer (aka generator,
     *                    {@link JsonGenerator}) instances.
     */
    public JacksonProcessor(JsonFactory jsonFactory) {
        this(jsonFactory, false, null);
    }

    /**
     * Construct with default JSON factory.
     *
     * @param objectMapper The jackson object mapper
     */
    public JacksonProcessor(ObjectMapper objectMapper) {
        this(new JsonFactory(), objectMapper);
    }

    /**
     * Default constructor.
     */
    public JacksonProcessor() {
        this(new JsonFactory(), null);
    }

    /**
     * @return Whether more input is needed
     */
    public boolean needMoreInput() {
        return currentNonBlockingJsonParser.getNonBlockingInputFeeder().needMoreInput();
    }

    @Override
    protected void doOnComplete() {
        if (needMoreInput()) {
            doOnError(new JsonEOFException(currentNonBlockingJsonParser, JsonToken.NOT_AVAILABLE, "Unexpected end-of-input"));
        } else {
            super.doOnComplete();
        }
    }

    @Override
    protected void onUpstreamMessage(byte[] message) {
        try {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Received upstream bytes of length: " + message.length);
            }

            if (message.length == 0) {
                if (needMoreInput()) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("More input required to parse JSON. Demanding more.");
                    }
                    upstreamSubscription.request(1);
                    upstreamDemand++;
                }
                return;
            }

            ByteArrayFeeder byteFeeder = currentNonBlockingJsonParser.getNonBlockingInputFeeder();
            boolean needMoreInput = byteFeeder.needMoreInput();
            if (!needMoreInput) {
                currentNonBlockingJsonParser = (NonBlockingJsonParser) jsonFactory.createNonBlockingByteArrayParser();
                byteFeeder = currentNonBlockingJsonParser.getNonBlockingInputFeeder();
                this.tokenBuffer = new TokenBuffer(currentNonBlockingJsonParser, deserializationContext);
            }

            byteFeeder.feedInput(message, 0, message.length);

            JsonToken event = currentNonBlockingJsonParser.nextToken();
            if (event == JsonToken.START_ARRAY && objectDepth == 0 && arrayDepth == 0) {
                rootArray = true;
            }
            parseTokenBufferFlux(event);
            if (objectDepth == 0 && arrayDepth == 0) {
                byteFeeder.endOfInput();
                event = currentNonBlockingJsonParser.nextToken();
                parseTokenBufferFlux(event);
            }
            if (needMoreInput()) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("More input required to parse JSON. Demanding more.");
                }
                upstreamSubscription.request(1);
                upstreamDemand++;
            }
        } catch (IOException e) {
            onError(e);
        }
    }

    private void updateDepth(JsonToken token) {
        switch (token) {
        case START_OBJECT:
            this.objectDepth++;
            break;
        case END_OBJECT:
            this.objectDepth--;
            break;
        case START_ARRAY:
            this.arrayDepth++;
            break;
        case END_ARRAY:
            this.arrayDepth--;
            break;
        default:
            break;
        }
    }

    private void parseTokenBufferFlux(JsonToken token) throws IOException {
        boolean previousNull = false;
        while (!this.currentNonBlockingJsonParser.isClosed()) {
            if (token == JsonToken.NOT_AVAILABLE || token == null && previousNull) {
                break;
            } else if (token == null) { // !previousNull
                previousNull = true;
                token = this.currentNonBlockingJsonParser.nextToken();
                continue;
            }
            updateDepth(token);
            if (rootArray && this.streamArray) {
                processTokenArray(token);
            } else {
                processTokenNormal(token);
            }

            token = this.currentNonBlockingJsonParser.nextToken();
        }
    }

    private void submitJsonNode() {
        currentDownstreamSubscriber().ifPresent(subscriber -> {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Materialized new JsonNode call onNext...");
            }
            try {
                JsonNode node = this.objectMapper.readTree(tokenBuffer.asParser());
                subscriber.onNext(node);
            } catch (IOException e) {
                onError(e);
            }
        });
    }

    private void processTokenNormal(JsonToken token) throws IOException {
        this.tokenBuffer.copyCurrentEvent(this.currentNonBlockingJsonParser);
        if ((token.isStructEnd() || token.isScalarValue()) && this.objectDepth == 0 && this.arrayDepth == 0) {
            submitJsonNode();
            this.tokenBuffer = new TokenBuffer(this.currentNonBlockingJsonParser, this.deserializationContext);
        }
    }

    private void processTokenArray(JsonToken token) throws IOException {
        if (!isTopLevelArrayToken(token)) {
            this.tokenBuffer.copyCurrentEvent(this.currentNonBlockingJsonParser);
        }
        if (this.objectDepth == 0 && (this.arrayDepth == 0 || this.arrayDepth == 1) && (token.isStructEnd() || token.isScalarValue())) {
            if (!(token == JsonToken.END_ARRAY && arrayDepth == 0)) {
                submitJsonNode();
            }
            this.tokenBuffer = new TokenBuffer(this.currentNonBlockingJsonParser, this.deserializationContext);
        }
    }

    private boolean isTopLevelArrayToken(JsonToken token) {
        return this.objectDepth == 0 && ((token == JsonToken.START_ARRAY && this.arrayDepth == 1) || (token == JsonToken.END_ARRAY && this.arrayDepth == 0));
    }

}
