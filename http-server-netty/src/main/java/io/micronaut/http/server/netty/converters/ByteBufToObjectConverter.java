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
package io.micronaut.http.server.netty.converters;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.netty.buffer.ByteBuf;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * A byte buf to object converter.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class ByteBufToObjectConverter implements TypeConverter<ByteBuf, Object> {

    private final ConversionService<?> conversionService;

    /**
     * @param conversionService The conversion service
     */
    public ByteBufToObjectConverter(ConversionService<?> conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public Optional<Object> convert(ByteBuf object, Class<Object> targetType, ConversionContext context) {
        return conversionService
            .convert(object, String.class, context)
            .flatMap(val -> conversionService.convert(val, targetType, context));
    }
}
