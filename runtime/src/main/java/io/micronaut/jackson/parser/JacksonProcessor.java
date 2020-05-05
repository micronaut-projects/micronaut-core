/*
 * Copyright 2017-2020 original authors
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
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micronaut.core.async.processor.SingleThreadedBufferingProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
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
        try {
            this.jsonFactory = jsonFactory;
            this.deserializationConfig = deserializationConfig;
            this.currentNonBlockingJsonParser = (NonBlockingJsonParser) jsonFactory.createNonBlockingByteArrayParser();
            this.streamArray = streamArray;
            this.jsonStream = true;
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
            }

            byteFeeder.feedInput(message, 0, message.length);

            JsonToken event = currentNonBlockingJsonParser.nextToken();
            if (event == JsonToken.START_ARRAY && nodeStack.peekFirst() == null) {
                rootIsArray = true;
                jsonStream = false;
            }

            while (event != JsonToken.NOT_AVAILABLE) {
                JsonNode root = asJsonNode(event);
                if (root != null) {

                    boolean isLast = nodeStack.isEmpty() && !jsonStream;
                    if (isLast) {
                        byteFeeder.endOfInput();
                    }

                    if (isLast && streamArray && root instanceof ArrayNode) {
                        break;
                    } else {
                        currentDownstreamSubscriber()
                                .ifPresent(subscriber -> {
                                        if (LOG.isTraceEnabled()) {
                                            LOG.trace("Materialized new JsonNode call onNext...");
                                        }
                                        subscriber.onNext(root);
                                    }
                                );
                    }
                    if (isLast) {
                        break;
                    }
                }
                event = currentNonBlockingJsonParser.nextToken();
            }
            if (jsonStream && nodeStack.isEmpty()) {
                byteFeeder.endOfInput();
            }
            if (jsonStream || needMoreInput()) {
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

    /**
     * @return The root node when the whole tree is built.
     **/
    private JsonNode asJsonNode(JsonToken event) throws IOException {
        switch (event) {
            case START_OBJECT:
                nodeStack.push(node(nodeStack.peekFirst()));
                break;

            case START_ARRAY:
                JsonNode node = nodeStack.peekFirst();
                if (node == null) {
                    rootIsArray = true;
                }
                nodeStack.push(array(node));
                break;

            case END_OBJECT:
            case END_ARRAY:
                if (nodeStack.isEmpty()) {
                    throw new JsonParseException(currentNonBlockingJsonParser, "Unexpected array end literal");
                }
                JsonNode current = nodeStack.pop();
                if (nodeStack.isEmpty()) {
                    return current;
                } else {
                    if (streamArray && nodeStack.size() == 1) {
                        JsonNode jsonNode = nodeStack.peekFirst();
                        if (jsonNode instanceof ArrayNode) {
                            return current;
                        } else {
                            return null;
                        }
                    } else {
                        return null;
                    }
                }

            case FIELD_NAME:
                if (nodeStack.isEmpty()) {
                    throw new JsonParseException(currentNonBlockingJsonParser, "Unexpected field literal");
                }
                currentFieldName = currentNonBlockingJsonParser.getCurrentName();
                break;

            case VALUE_NUMBER_INT:
                if (nodeStack.isEmpty()) {
                    throw new JsonParseException(currentNonBlockingJsonParser, "Unexpected integer literal");
                }
                final JsonParser.NumberType numberIntType = currentNonBlockingJsonParser.getNumberType();
                JsonNode integerNode = nodeStack.peekFirst();
                switch (numberIntType) {
                    case BIG_INTEGER:
                        if (integerNode instanceof ObjectNode) {
                            ((ObjectNode) integerNode).put(currentFieldName, currentNonBlockingJsonParser.getBigIntegerValue());
                        } else {
                            ((ArrayNode) integerNode).add(currentNonBlockingJsonParser.getBigIntegerValue());
                        }
                        break;
                    case LONG:
                        if (deserializationConfig != null && deserializationConfig.isEnabled(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)) {
                            if (integerNode instanceof ObjectNode) {
                                ((ObjectNode) integerNode).put(currentFieldName, currentNonBlockingJsonParser.getBigIntegerValue());
                            } else {
                                ((ArrayNode) integerNode).add(currentNonBlockingJsonParser.getBigIntegerValue());
                            }
                        } else if (integerNode instanceof ObjectNode) {
                            ((ObjectNode) integerNode).put(currentFieldName, currentNonBlockingJsonParser.getLongValue());
                        } else {
                            ((ArrayNode) integerNode).add(currentNonBlockingJsonParser.getLongValue());
                        }
                        break;
                    case INT:
                        if (deserializationConfig != null && deserializationConfig.isEnabled(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)) {
                            if (integerNode instanceof ObjectNode) {
                                ((ObjectNode) integerNode).put(currentFieldName, currentNonBlockingJsonParser.getBigIntegerValue());
                            } else {
                                ((ArrayNode) integerNode).add(currentNonBlockingJsonParser.getBigIntegerValue());
                            }
                        } else if (integerNode instanceof ObjectNode) {
                            ((ObjectNode) integerNode).put(currentFieldName, currentNonBlockingJsonParser.getIntValue());
                        } else {
                            ((ArrayNode) integerNode).add(currentNonBlockingJsonParser.getIntValue());
                        }
                        break;
                    default:
                        throw new IllegalStateException("Unsupported number type: " + numberIntType);
                }
                break;

            case VALUE_STRING:
                if (nodeStack.isEmpty()) {
                    throw new JsonParseException(currentNonBlockingJsonParser, "Unexpected string literal");
                }
                JsonNode stringNode = nodeStack.peekFirst();
                if (stringNode instanceof ObjectNode) {
                    ((ObjectNode) stringNode).put(currentFieldName, currentNonBlockingJsonParser.getValueAsString());
                } else {
                    ((ArrayNode) stringNode).add(currentNonBlockingJsonParser.getValueAsString());
                }
                break;

            case VALUE_NUMBER_FLOAT:
                if (nodeStack.isEmpty()) {
                    throw new JsonParseException(currentNonBlockingJsonParser, "Unexpected float literal");
                }

                final JsonParser.NumberType numberDecimalType = currentNonBlockingJsonParser.getNumberType();
                JsonNode decimalNode = nodeStack.peekFirst();
                switch (numberDecimalType) {
                    case FLOAT:
                        if (deserializationConfig != null && deserializationConfig.isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)) {
                            if (decimalNode instanceof ObjectNode) {
                                ((ObjectNode) decimalNode).put(currentFieldName, currentNonBlockingJsonParser.getDecimalValue());
                            } else {
                                ((ArrayNode) decimalNode).add(currentNonBlockingJsonParser.getDecimalValue());
                            }
                        } else if (decimalNode instanceof ObjectNode) {
                            ((ObjectNode) decimalNode).put(currentFieldName, currentNonBlockingJsonParser.getFloatValue());
                        } else {
                            ((ArrayNode) decimalNode).add(currentNonBlockingJsonParser.getFloatValue());
                        }
                        break;
                    case DOUBLE:
                        if (deserializationConfig != null && deserializationConfig.isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)) {
                            if (decimalNode instanceof ObjectNode) {
                                ((ObjectNode) decimalNode).put(currentFieldName, currentNonBlockingJsonParser.getDecimalValue());
                            } else {
                                ((ArrayNode) decimalNode).add(currentNonBlockingJsonParser.getDecimalValue());
                            }
                        } else if (decimalNode instanceof ObjectNode) {
                            ((ObjectNode) decimalNode).put(currentFieldName, currentNonBlockingJsonParser.getDoubleValue());
                        } else {
                            ((ArrayNode) decimalNode).add(currentNonBlockingJsonParser.getDoubleValue());
                        }
                        break;
                    case BIG_DECIMAL:
                        if (decimalNode instanceof ObjectNode) {
                            ((ObjectNode) decimalNode).put(currentFieldName, currentNonBlockingJsonParser.getDecimalValue());
                        } else {
                            ((ArrayNode) decimalNode).add(currentNonBlockingJsonParser.getDecimalValue());
                        }
                        break;
                    default:
                        // shouldn't get here
                        throw new IllegalStateException("Unsupported number type: " + numberDecimalType);
                }
                break;

            case VALUE_NULL:
                if (nodeStack.isEmpty()) {
                    throw new JsonParseException(currentNonBlockingJsonParser, "Unexpected null literal");
                }
                JsonNode nullNode = nodeStack.peekFirst();
                if (nullNode instanceof ObjectNode) {
                    ((ObjectNode) nullNode).putNull(currentFieldName);
                } else {
                    ((ArrayNode) nullNode).addNull();
                }
                break;

            case VALUE_TRUE:
            case VALUE_FALSE:
                if (nodeStack.isEmpty()) {
                    throw new JsonParseException(currentNonBlockingJsonParser, "Unexpected boolean literal");
                }
                JsonNode booleanNode = nodeStack.peekFirst();
                if (booleanNode instanceof ObjectNode) {
                    ((ObjectNode) booleanNode).put(currentFieldName, currentNonBlockingJsonParser.getBooleanValue());
                } else {
                    ((ArrayNode) booleanNode).add(currentNonBlockingJsonParser.getBooleanValue());
                }
                break;

            default:
                throw new IllegalStateException("Unsupported JSON event: " + event);
        }

        //its an array and the stack size is 1 which means the value is scalar
        if (rootIsArray && streamArray && nodeStack.size() == 1) {
            ArrayNode arrayNode = (ArrayNode) nodeStack.peekFirst();
            if (arrayNode.size() > 0) {
                return arrayNode.remove(arrayNode.size() - 1);
            }
        }

        return null;
    }

    private JsonNode array(JsonNode node) {
        if (node instanceof ObjectNode) {
            return ((ObjectNode) node).putArray(currentFieldName);
        } else if (node instanceof ArrayNode) {
            return ((ArrayNode) node).addArray();
        } else {
            return deserializationConfig.getNodeFactory().arrayNode();
        }
    }

    private JsonNode node(JsonNode node) {
        if (node instanceof ObjectNode) {
            return ((ObjectNode) node).putObject(currentFieldName);
        } else if (node instanceof ArrayNode && !(streamArray && nodeStack.size() == 1)) {
            return ((ArrayNode) node).addObject();
        } else {
            return deserializationConfig.getNodeFactory().objectNode();
        }
    }
}
