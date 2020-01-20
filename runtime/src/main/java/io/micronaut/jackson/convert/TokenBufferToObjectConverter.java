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

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.TokenBuffer;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import java.io.IOException;
import java.util.Optional;

/**
 * A {@link TypeConverter} that leverages Jackson {@link ObjectMapper} to convert from {@link TokenBuffer} instances to
 * objects.
 *
 * @author Christophe Roudet
 * @since 1.3
 */
@Singleton
public class TokenBufferToObjectConverter implements TypeConverter<TokenBuffer, Object> {
    private final Provider<ObjectMapper> objectMapper;

    /**
     * @param objectMapper To read/write JSON
     * @deprecated Use {@link #TokenBufferToObjectConverter(Provider)} instead
     */
    @Deprecated
    public TokenBufferToObjectConverter(ObjectMapper objectMapper) {
        this(() -> objectMapper);
    }

    /**
     * @param objectMapper To read/write JSON
     */
    @Inject
    public TokenBufferToObjectConverter(Provider<ObjectMapper> objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<Object> convert(TokenBuffer tokenBuffer, Class<Object> targetType, ConversionContext context) {
        try {
            if (CharSequence.class.isAssignableFrom(targetType) && tokenBuffer.firstToken().equals(JsonToken.START_OBJECT)) {
                return Optional.of(objectMapper.get().readTree(tokenBuffer.asParser()).toString());
            } else {
                Object result = objectMapper.get().readValue(tokenBuffer.asParser(), targetType);
                return Optional.ofNullable(result);
            }
        } catch (IOException e) {
            context.reject(e);
            return Optional.empty();
        }
    }

}
