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
package io.micronaut.http.server.netty.converters;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.multipart.HttpData;

import java.util.Optional;

/**
 * @author James Kleeh
 * @since 1.0
 * @deprecated Registered by {@link NettyConverters} instead
 */
@Deprecated
public class HttpDataToStringConverter implements TypeConverter<HttpData, CharSequence> {

    private final ConversionService conversionService;

    /**
     * @param conversionService The conversion service
     */
    protected HttpDataToStringConverter(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public Optional<CharSequence> convert(HttpData upload, Class<CharSequence> targetType, ConversionContext context) {
        try {
            if (!upload.isCompleted()) {
                return Optional.empty();
            }
            ByteBuf byteBuf = upload.getByteBuf();
            return conversionService.convert(byteBuf, targetType, context);
        } catch (Exception e) {
            context.reject(e);
            return Optional.empty();
        }
    }
}
