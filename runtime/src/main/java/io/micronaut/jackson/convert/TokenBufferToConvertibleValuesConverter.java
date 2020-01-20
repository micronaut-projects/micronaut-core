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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.TokenBuffer;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.convert.value.ConvertibleValues;

import javax.inject.Provider;
import javax.inject.Singleton;

import java.io.IOException;
import java.util.Optional;

/**
 * Converts a {@link TokenBuffer} to a {@link ConvertibleValues} instance.
 *
 * @author Christophe Roudet
 * @since 1.3
 */
@Singleton
public class TokenBufferToConvertibleValuesConverter implements TypeConverter<TokenBuffer, ConvertibleValues> {

    private final Provider<ObjectMapper> objectMapper;
    private final ConversionService<?> conversionService;

    /**
     * @param objectMapper To read/write JSON
     * @param conversionService To convert from JSON node to a {@link ConvertibleValues} instance
     */
    public TokenBufferToConvertibleValuesConverter(Provider<ObjectMapper> objectMapper, ConversionService<?> conversionService) {
        this.objectMapper = objectMapper;
        this.conversionService = conversionService;
    }

    @Override
    public Optional<ConvertibleValues> convert(TokenBuffer tokenBuffer, Class<ConvertibleValues> targetType, ConversionContext context) {
        try {
            return Optional.of(new TokenBufferConvertibleValues(tokenBuffer, conversionService, objectMapper.get()));
        } catch (IOException e) {
            context.reject(e);
            return Optional.empty();
        }
    }
}
