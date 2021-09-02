/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.jackson.core.tree;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.json.JsonStreamConfig;
import io.micronaut.json.tree.JsonNode;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Codec for transforming {@link JsonNode} from and to json streams.
 *
 * @author Jonas Konrad
 * @since 3.1
 */
@Experimental
public final class MicronautTreeCodec {
    private static final MicronautTreeCodec INSTANCE = new MicronautTreeCodec(JsonStreamConfig.DEFAULT);

    private final JsonStreamConfig config;

    private MicronautTreeCodec(JsonStreamConfig config) {
        this.config = config;
    }

    /**
     * @return The default instance, using {@link JsonStreamConfig#DEFAULT}.
     */
    public static MicronautTreeCodec getInstance() {
        return INSTANCE;
    }

    /**
     * @param config The stream config to use.
     * @return A new codec that will use the given stream config.
     */
    public MicronautTreeCodec withConfig(JsonStreamConfig config) {
        return new MicronautTreeCodec(config);
    }

    /**
     * Read a json node from a stream.
     *
     * @param p The stream to parse.
     * @return The parsed json node.
     */
    public JsonNode readTree(JsonParser p) throws IOException {
        switch (p.hasCurrentToken() ? p.currentToken() : p.nextToken()) {
            case START_OBJECT:
                Map<String, JsonNode> map = new LinkedHashMap<>();
                while (p.nextToken() != JsonToken.END_OBJECT) {
                    String name = p.currentName();
                    p.nextToken();
                    map.put(name, readTree(p));
                }
                return JsonNode.createObjectNode(map);
            case START_ARRAY:
                List<JsonNode> list = new ArrayList<>();
                while (p.nextToken() != JsonToken.END_ARRAY) {
                    list.add(readTree(p));
                }
                return JsonNode.createArrayNode(list);
            case VALUE_STRING:
                return JsonNode.createStringNode(p.getText());
            case VALUE_NUMBER_INT:
                if (config.useBigIntegerForInts()) {
                    return JsonNode.createNumberNode(p.getBigIntegerValue());
                } else {
                    // technically, we could get an unsupported number value here.
                    return JsonNode.createNumberNodeImpl(p.getNumberValue());
                }
            case VALUE_NUMBER_FLOAT:
                if (config.useBigDecimalForFloats()) {
                    return JsonNode.createNumberNode(p.getDecimalValue());
                } else {
                    // technically, we could get an unsupported number value here.
                    return JsonNode.createNumberNodeImpl(p.getNumberValue());
                }
            case VALUE_TRUE:
                return JsonNode.createBooleanNode(true);
            case VALUE_FALSE:
                return JsonNode.createBooleanNode(false);
            case VALUE_NULL:
                return JsonNode.nullNode();
            default:
                throw new UnsupportedOperationException("Unsupported token: " + p.currentToken());
        }
    }

    /**
     * Write a json node to a json stream.
     *
     * @param generator The output json stream.
     * @param tree      The node to write.
     */
    public void writeTree(JsonGenerator generator, JsonNode tree) throws IOException {
        if (tree.isObject()) {
            generator.writeStartObject();
            for (Map.Entry<String, JsonNode> entry : tree.entries()) {
                generator.writeFieldName(entry.getKey());
                writeTree(generator, entry.getValue());
            }
            generator.writeEndObject();
        } else if (tree.isArray()) {
            generator.writeStartArray();
            for (JsonNode value : tree.values()) {
                writeTree(generator, value);
            }
            generator.writeEndArray();
        } else if (tree.isBoolean()) {
            generator.writeBoolean(tree.getBooleanValue());
        } else if (tree.isNull()) {
            generator.writeNull();
        } else if (tree.isNumber()) {
            Number value = tree.getNumberValue();
            // integer, long, double are the most common. Check those first.
            if (value instanceof Integer) {
                generator.writeNumber(value.intValue());
            } else if (value instanceof Long) {
                generator.writeNumber(value.longValue());
            } else if (value instanceof Double) {
                generator.writeNumber(value.doubleValue());
            } else if (value instanceof Float) {
                generator.writeNumber(value.floatValue());
            } else if (value instanceof BigDecimal) {
                generator.writeNumber((BigDecimal) value);
            } else if (value instanceof Byte || value instanceof Short) {
                generator.writeNumber(value.shortValue());
            } else if (value instanceof BigInteger) {
                generator.writeNumber((BigInteger) value);
            } else {
                throw new IllegalStateException("Unknown number type " + value.getClass().getName());
            }
        } else if (tree.isString()) {
            generator.writeString(tree.getStringValue());
        } else {
            throw new AssertionError();
        }
    }

    /**
     * Create a new parser that traverses over the given json node.
     *
     * @param node The json node to traverse over.
     * @return The parser that will visit the json node.
     */
    public JsonParser treeAsTokens(JsonNode node) {
        return new TraversingParser(node);
    }

    /**
     * Create a {@link JsonGenerator} that will return a {@link JsonNode} when completed.
     *
     * @return The generator.
     */
    public TreeGenerator createTreeGenerator() {
        return new TreeGenerator();
    }
}
