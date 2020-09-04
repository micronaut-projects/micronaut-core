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
package io.micronaut.jackson.parser;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.async.ByteArrayFeeder;
import com.fasterxml.jackson.core.io.JsonEOFException;
import com.fasterxml.jackson.core.json.async.NonBlockingJsonParser;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micronaut.core.async.processor.SingleThreadedBufferingProcessor;

import org.reactivestreams.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * A Reactive streams publisher that publishes a {@link JsonNode} once the JSON has been fully consumed.
 * Uses {@link com.fasterxml.jackson.core.json.async.NonBlockingJsonParser} internally allowing the parsing of
 * JSON from an incoming stream of bytes in a non-blocking manner
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class JacksonProcessor extends SingleThreadedBufferingProcessor<byte[], JsonNode> {

    private static final Logger LOG = LoggerFactory.getLogger(JacksonProcessor.class);

    private NonBlockingJsonParser currentNonBlockingJsonParser;
    private final ConcurrentLinkedDeque<JsonNode> nodeStack = new ConcurrentLinkedDeque<>();
    private final JsonFactory jsonFactory;
    private final @Nullable DeserializationConfig deserializationConfig;
    private final JsonNodeFactory nodeFactory;
    private String currentFieldName;
    private boolean streamArray;
    private boolean rootIsArray;
    private boolean jsonStream;

    /**
     * Creates a new JacksonProcessor.
     *
     * @param jsonFactory The JSON factory
     * @param streamArray Whether arrays should be streamed
     * @param deserializationConfig The jackson deserialization configuration
     */
    public JacksonProcessor(JsonFactory jsonFactory, boolean streamArray, @Nullable DeserializationConfig deserializationConfig) {
        this.jsonFactory = jsonFactory;
        this.deserializationConfig = deserializationConfig;
        this.streamArray = streamArray;
        this.jsonStream = true;
        this.nodeFactory = deserializationConfig == null ? JsonNodeFactory.instance : deserializationConfig.getNodeFactory();
        try {
            this.currentNonBlockingJsonParser = (NonBlockingJsonParser) jsonFactory.createNonBlockingByteArrayParser();
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
     * @param jsonFactory To configure and construct reader (aka parser, {@link JsonParser})
     *                    and writer (aka generator, {@link JsonGenerator}) instances.
     * @param deserializationConfig The jackson deserialization configuration
     */
    public JacksonProcessor(JsonFactory jsonFactory, DeserializationConfig deserializationConfig) {
        this(jsonFactory, false, deserializationConfig);
    }

    /**
     * Construct with given JSON factory.
     *
     * @param jsonFactory To configure and construct reader (aka parser, {@link JsonParser})
     *                    and writer (aka generator, {@link JsonGenerator}) instances.
     */
    public JacksonProcessor(JsonFactory jsonFactory) {
        this(jsonFactory, false, null);
    }

    /**
     * Construct with default JSON factory.
     * @param deserializationConfig The jackson deserialization configuration
     */
    public JacksonProcessor(DeserializationConfig deserializationConfig) {
        this(new JsonFactory(), deserializationConfig);
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
        if (jsonStream && nodeStack.isEmpty()) {
            super.doOnComplete();
        } else if (needMoreInput()) {
            doOnError(new JsonEOFException(currentNonBlockingJsonParser, JsonToken.NOT_AVAILABLE, "Unexpected end-of-input"));
        } else {
            super.doOnComplete();
        }
    }

    @Override
    protected void onUpstreamMessage(byte[] message) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Received upstream bytes of length: " + message.length);
        }

        try {
            if (message.length == 0) {
                if (needMoreInput()) {
                    requestMoreInput();
                }
                return;
            }

            final ByteArrayFeeder byteFeeder = byteFeeder(message);

            JsonToken event = currentNonBlockingJsonParser.nextToken();
            checkForStreaming(event);

            while (event != JsonToken.NOT_AVAILABLE) {
                final JsonNode root = asJsonNode(event);
                if (root != null) {

                    final boolean isLast = nodeStack.isEmpty() && !jsonStream;
                    if (isLast) {
                        byteFeeder.endOfInput();
                        if (streamArray && root.isArray()) {
                            break;
                        }
                    }

                    publishNode(root);
                    if (isLast) {
                        break;
                    }
                }
                event = currentNonBlockingJsonParser.nextToken();
            }
            if (jsonStream) {
                if (nodeStack.isEmpty()) {
                    byteFeeder.endOfInput();
                }
                requestMoreInput();
            } else if (needMoreInput()) {
                requestMoreInput();
            }
        } catch (IOException e) {
            onError(e);
        }
    }

    private void checkForStreaming(JsonToken event) {
        if (event == JsonToken.START_ARRAY && nodeStack.peekFirst() == null) {
            rootIsArray = true;
            jsonStream = false;
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
        ByteArrayFeeder byteFeeder = currentNonBlockingJsonParser.getNonBlockingInputFeeder();
        final boolean needMoreInput = byteFeeder.needMoreInput();
        if (!needMoreInput) {
            currentNonBlockingJsonParser = (NonBlockingJsonParser) jsonFactory.createNonBlockingByteArrayParser();
            byteFeeder = currentNonBlockingJsonParser.getNonBlockingInputFeeder();
        }

        byteFeeder.feedInput(message, 0, message.length);
        return byteFeeder;
    }

    /**
     * @return The root node when the whole tree is built.
     **/
    private JsonNode asJsonNode(JsonToken event) throws IOException {
        switch (event) {
            case START_OBJECT:
                nodeStack.push(node(nodeStack.peekFirst()));
                break;

            case START_ARRAY:
                final JsonNode node = nodeStack.peekFirst();
                if (node == null) {
                    rootIsArray = true;
                }
                nodeStack.push(array(node));
                break;

            case END_OBJECT:
            case END_ARRAY:
                checkEmptyNodeStack(event);
                final JsonNode current = nodeStack.pop();
                if (nodeStack.isEmpty()) {
                    return current;
                }
                if (streamArray && nodeStack.size() == 1) {
                    return nodeStack.peekFirst().isArray() ? current : null;
                }
                return null;

            case FIELD_NAME:
                checkEmptyNodeStack(event);
                currentFieldName = currentNonBlockingJsonParser.getCurrentName();
                break;

            case VALUE_NUMBER_INT:
                checkEmptyNodeStack(event);
                addIntegerNumber(nodeStack.peekFirst());
                break;

            case VALUE_STRING:
                checkEmptyNodeStack(event);
                addJsonNode(nodeStack.peekFirst(), nodeFactory.textNode(currentNonBlockingJsonParser.getValueAsString()));
                break;

            case VALUE_NUMBER_FLOAT:
                checkEmptyNodeStack(event);
                addFloatNumber(nodeStack.peekFirst());
                break;

            case VALUE_NULL:
                checkEmptyNodeStack(event);
                addJsonNode(nodeStack.peekFirst(), nodeFactory.nullNode());
                break;

            case VALUE_TRUE:
            case VALUE_FALSE:
                checkEmptyNodeStack(event);
                addJsonNode(nodeStack.peekFirst(), nodeFactory.booleanNode(currentNonBlockingJsonParser.getBooleanValue()));
                break;

            default:
                throw new IllegalStateException("Unsupported JSON event: " + event);
        }

        // it is an array and the stack size is 1 which means the value is scalar
        if (rootIsArray && streamArray && nodeStack.size() == 1) {
            final ArrayNode arrayNode = (ArrayNode) nodeStack.peekFirst();
            if (arrayNode.size() > 0) {
                return arrayNode.remove(arrayNode.size() - 1);
            }
        }

        return null;
    }

    private static String tokenType(JsonToken token) {
        switch (token) {
        case END_OBJECT:
        case END_ARRAY:
            return "container end";
        case FIELD_NAME:
            return "field";
        case VALUE_NUMBER_INT:
            return "integer";
        case VALUE_STRING:
            return "string";
        case VALUE_NUMBER_FLOAT:
            return "float";
        case VALUE_NULL:
            return "null";
        case VALUE_TRUE:
        case VALUE_FALSE:
            return "boolean";
        default:
            return "";
        }
    }

    private void addIntegerNumber(final JsonNode integerNode) throws IOException {
        if (useBigIntegerForInts()) {
            addJsonNode(integerNode, nodeFactory.numberNode(currentNonBlockingJsonParser.getBigIntegerValue()));
        } else {
            final JsonParser.NumberType numberIntType = currentNonBlockingJsonParser.getNumberType();
            switch (numberIntType) {
            case BIG_INTEGER:
                addJsonNode(integerNode, nodeFactory.numberNode(currentNonBlockingJsonParser.getBigIntegerValue()));
                break;
            case LONG:
                addJsonNode(integerNode, nodeFactory.numberNode(currentNonBlockingJsonParser.getLongValue()));
                break;
            case INT:
                addJsonNode(integerNode, nodeFactory.numberNode(currentNonBlockingJsonParser.getIntValue()));
                break;
            default:
                throw new IllegalStateException("Unsupported number type: " + numberIntType);
            }
        }
    }

    private void addFloatNumber(final JsonNode decimalNode) throws IOException {
        if (useBigDecimalForFloats()) {
            addJsonNode(decimalNode, nodeFactory.numberNode(currentNonBlockingJsonParser.getDecimalValue()));
        } else {
            final JsonParser.NumberType numberDecimalType = currentNonBlockingJsonParser.getNumberType();
            switch (numberDecimalType) {
            case FLOAT:
                addJsonNode(decimalNode, nodeFactory.numberNode(currentNonBlockingJsonParser.getFloatValue()));
                break;
            case DOUBLE:
                addJsonNode(decimalNode, nodeFactory.numberNode(currentNonBlockingJsonParser.getDoubleValue()));
                break;
            case BIG_DECIMAL:
                addJsonNode(decimalNode, nodeFactory.numberNode(currentNonBlockingJsonParser.getDecimalValue()));
                break;
            default:
                // shouldn't get here
                throw new IllegalStateException("Unsupported number type: " + numberDecimalType);
            }
        }
    }

    private void checkEmptyNodeStack(JsonToken token) throws JsonParseException {
        if (nodeStack.isEmpty()) {
            throw new JsonParseException(currentNonBlockingJsonParser, "Unexpected " + tokenType(token) + " literal");
        }
    }

    private boolean useBigDecimalForFloats() {
        return deserializationConfig != null && deserializationConfig.isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    private boolean useBigIntegerForInts() {
        return deserializationConfig != null && deserializationConfig.isEnabled(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS);
    }

    private void addJsonNode(JsonNode node, JsonNode value) {
        if (node.isObject()) {
            ((ObjectNode) node).set(currentFieldName, value);
        } else {
            ((ArrayNode) node).add(value);
        }
    }

    private JsonNode array(JsonNode node) {
        if (node == null) {
            return nodeFactory.arrayNode();
        }
        if (node.isObject()) {
            return ((ObjectNode) node).putArray(currentFieldName);
        }
        return ((ArrayNode) node).addArray();
    }

    private JsonNode node(JsonNode node) {
        if (node == null) {
            return nodeFactory.objectNode();
        }
        if (node.isObject()) {
            return ((ObjectNode) node).putObject(currentFieldName);
        }
        if (node.isArray() && !(streamArray && nodeStack.size() == 1)) {
            return ((ArrayNode) node).addObject();
        }
        return nodeFactory.objectNode();
    }
}
