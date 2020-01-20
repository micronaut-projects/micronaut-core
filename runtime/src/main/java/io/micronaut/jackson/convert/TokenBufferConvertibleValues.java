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
package io.micronaut.jackson.convert;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.TokenBuffer;

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Simple facade over a Jackson {@link TokenBuffer} to make it a {@link ConvertibleValues}.
 *
 * @param <V> The generic type for values
 *
 * @author Christophe Roudet
 * @since 1.3
 */
public class TokenBufferConvertibleValues<V> implements ConvertibleValues<V> {

    private final Map<String, TokenBuffer> tokenBuffers;
    private final ConversionService<?> conversionService;

    /**
     * @param tokenBuffer        The node that maps to JSON object structure
     * @param conversionService To convert the JSON node into given type
     * @param objectMapper To read/write JSON
     * @throws IOException If json is not valid
     */
    public TokenBufferConvertibleValues(TokenBuffer tokenBuffer, ConversionService<?> conversionService, ObjectMapper objectMapper) throws IOException {
        this.tokenBuffers = toMap(tokenBuffer, objectMapper.getDeserializationContext());
        this.conversionService = conversionService;
    }

    private static Map<String, TokenBuffer> toMap(TokenBuffer tokenBuffer, DeserializationContext context) throws IOException {
        JsonParser p = tokenBuffer.asParser();
        JsonToken current = p.nextToken();
        if (current != JsonToken.START_OBJECT) {
            throw new IOException("Expecting START_OBJECT, got " + current);
        }
        Map<String, TokenBuffer> result = new HashMap<>();
        int depth = 1;
        TokenBuffer tb = null;
        current = p.nextToken();
        while (current != JsonToken.END_OBJECT && depth >= 1) {
            if (current == JsonToken.FIELD_NAME && depth == 1) {
                final String fieldName = p.getCurrentName();
                current = p.nextToken();
                tb = new TokenBuffer(p, context);
                result.put(fieldName, tb);
                continue;
            } else if (current.isStructStart()) {
                ++depth;
            } else if (current.isStructEnd()) {
                --depth;
            }
            tb.copyCurrentEvent(p);
            current = p.nextToken();
        }
        return result;
    }

    @Override
    public Set<String> names() {
        return tokenBuffers.keySet();
    }

    @Override
    public Collection<V> values() {
        return (Collection<V>) Collections.unmodifiableCollection(tokenBuffers.values());
    }

    @Override
    public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
        String fieldName = name.toString();
        TokenBuffer tokenBuffer = tokenBuffers.get(fieldName);
        if (tokenBuffer == null) {
            return Optional.empty();
        } else {
            return conversionService.convert(tokenBuffer, conversionContext);
        }
    }
}
