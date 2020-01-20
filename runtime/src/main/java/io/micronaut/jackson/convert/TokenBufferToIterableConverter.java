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

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.type.Argument;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Christophe Roudet
 * @since 1.3
 */
@Singleton
public class TokenBufferToIterableConverter implements TypeConverter<TokenBuffer, Iterable> {

    private final Provider<ObjectMapper> objectMapper;
    private final ConversionService conversionService;

    /**
     * Create a new converter to convert from json to given type iteratively.
     *
     * @param objectMapper  To convert from Json
     * @param conversionService Convert the given json node to the given target type.
     * @deprecated Use {@link #TokenBufferToIterableConverter(ConversionService)} instead
     */
    @Deprecated
    public TokenBufferToIterableConverter(ObjectMapper objectMapper, ConversionService conversionService) {
        this(() -> objectMapper, conversionService);
    }

    /**
     * Create a new converter to convert from json to given type iteratively.
     *
     * @param objectMapper  To convert from Json
     * @param conversionService Convert the given json node to the given target type.
     */
    @Inject
    public TokenBufferToIterableConverter(Provider<ObjectMapper> objectMapper, ConversionService conversionService) {
        this.objectMapper = objectMapper;
        this.conversionService = conversionService;
    }

    @Override
    public Optional<Iterable> convert(TokenBuffer tokenBuffer, Class<Iterable> targetType, ConversionContext context) {
        Map<String, Argument<?>> typeVariables = context.getTypeVariables();
        Class elementType = typeVariables.isEmpty() ? Map.class : typeVariables.values().iterator().next().getType();
        try {
            return Optional.of(splitArray(tokenBuffer, objectMapper.get().getDeserializationContext())
                    .stream()
                    .map(tb -> conversionService.convert(tb, elementType, context))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList()));
        } catch (IOException e) {
            context.reject(e);
            return Optional.empty();
        }
    }

    private static List<TokenBuffer> splitArray(TokenBuffer tokenBuffer, DeserializationContext context) throws IOException {
        JsonParser p = tokenBuffer.asParser();
        JsonToken current = p.nextToken();
        if (current != JsonToken.START_ARRAY) {
            throw new IOException("Expecting START_ARRAY, got " + current);
        }
        List<TokenBuffer> result = new ArrayList<>();
        int depth = 1;
        TokenBuffer tb = new TokenBuffer(p, context);
        current = p.nextToken();
        while (current != JsonToken.END_ARRAY && depth >= 1) {
            if (tb == null) {
                tb = new TokenBuffer(p, context);
            }
            if (current.isScalarValue() && depth == 1) {
                tb.copyCurrentEvent(p);
                result.add(tb);
                tb = null;
            } else if (current.isStructStart()) {
                ++depth;
                tb.copyCurrentEvent(p);
            } else if (current.isStructEnd()) {
                --depth;
                tb.copyCurrentEvent(p);
                if (depth == 1) {
                    result.add(tb);
                    tb = null;
                }
            } else {
                tb.copyCurrentEvent(p);
            }
            current = p.nextToken();
        }
        return result;
    }
}
