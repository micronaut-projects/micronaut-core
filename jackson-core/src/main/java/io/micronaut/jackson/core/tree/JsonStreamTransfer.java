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
import io.micronaut.core.annotation.Internal;
import io.micronaut.json.JsonStreamConfig;

import java.io.IOException;

/**
 * Utility functions for transferring from a {@link JsonParser} to a {@link JsonGenerator}.
 *
 * @author Jonas Konrad
 * @since 3.1
 */
@Internal
public final class JsonStreamTransfer {
    private JsonStreamTransfer() {
    }

    /**
     * Transfer tokens, starting with the next token.
     *
     * @param from   Parser to transfer data from.
     * @param to     Generator to transfer data to.
     * @param config Configuration to use for copying.
     */
    public static void transferNext(JsonParser from, JsonGenerator to, JsonStreamConfig config) throws IOException {
        from.nextToken();
        transfer(from, to, config);
    }

    /**
     * Transfer tokens, starting with the current token.
     *
     * @param from   Parser to transfer data from.
     * @param to     Generator to transfer data to.
     * @param config Configuration to use for copying.
     */
    public static void transfer(JsonParser from, JsonGenerator to, JsonStreamConfig config) throws IOException {
        if (!from.hasCurrentToken()) {
            throw new IllegalArgumentException("Parser not positioned at token. Try transferNext");
        }
        do {
            transferCurrentToken(from, to, config);
        } while (from.nextToken() != null);
    }

    /**
     * Transfer a single token.
     *
     * @param from   Parser to transfer data from.
     * @param to     Generator to transfer data to.
     * @param config Configuration to use for copying.
     */
    public static void transferCurrentToken(
            JsonParser from,
            JsonGenerator to,
            JsonStreamConfig config
    ) throws IOException {
        switch (from.currentToken()) {
            case START_OBJECT:
                to.writeStartObject();
                break;
            case END_OBJECT:
                to.writeEndObject();
                break;
            case START_ARRAY:
                to.writeStartArray();
                break;
            case END_ARRAY:
                to.writeEndArray();
                break;
            case FIELD_NAME:
                to.writeFieldName(from.currentName());
                break;
            case VALUE_EMBEDDED_OBJECT:
                to.writeObject(from.getEmbeddedObject());
                break;
            case VALUE_STRING:
                to.writeString(from.getText());
                break;
            case VALUE_NUMBER_INT:
                if (config.useBigIntegerForInts()) {
                    to.writeNumber(from.getBigIntegerValue());
                } else {
                    final JsonParser.NumberType numberIntType = from.getNumberType();
                    switch (numberIntType) {
                        case BIG_INTEGER:
                            to.writeNumber(from.getBigIntegerValue());
                            break;
                        case LONG:
                            to.writeNumber(from.getLongValue());
                            break;
                        case INT:
                            to.writeNumber(from.getIntValue());
                            break;
                        default:
                            throw new IllegalStateException("Unsupported number type: " + numberIntType);
                    }
                }
                break;
            case VALUE_NUMBER_FLOAT:
                if (config.useBigDecimalForFloats()) {
                    to.writeNumber(from.getDecimalValue());
                } else {
                    final JsonParser.NumberType numberDecimalType = from.getNumberType();
                    switch (numberDecimalType) {
                        case FLOAT:
                            to.writeNumber(from.getFloatValue());
                            break;
                        case DOUBLE:
                            to.writeNumber(from.getDoubleValue());
                            break;
                        case BIG_DECIMAL:
                            to.writeNumber(from.getDecimalValue());
                            break;
                        default:
                            throw new IllegalStateException("Unsupported number type: " + numberDecimalType);
                    }
                }
                break;
            case VALUE_TRUE:
                to.writeBoolean(true);
                break;
            case VALUE_FALSE:
                to.writeBoolean(false);
                break;
            case VALUE_NULL:
                to.writeNull();
                break;
            default:
                throw new IllegalStateException("Unsupported JSON token: " + from.currentToken());
        }
    }
}
