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
import io.micronaut.http.server.netty.multipart.NettyPartData;
import io.netty.buffer.ByteBuf;

import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * @author James Kleeh
 * @since 1.0
 */
@Singleton
public class NettyPartDataToObjectConverter implements TypeConverter<NettyPartData, Object> {

    private final ConversionService conversionService;

    /**
     * @param conversionService The conversion service
     */
    protected NettyPartDataToObjectConverter(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public Optional<Object> convert(NettyPartData object, Class<Object> targetType, ConversionContext context) {
        try {
            if (targetType.isAssignableFrom(ByteBuffer.class)) {
                return Optional.of(object.getByteBuffer());
            } else if (targetType.isAssignableFrom(InputStream.class)) {
                return Optional.of(object.getInputStream());
            } else {
                ByteBuf byteBuf = object.getByteBuf();
                try {
                    return conversionService.convert(byteBuf, targetType, context);
                } finally {
                    byteBuf.release();
                }
            }
        } catch (IOException e) {
            context.reject(e);
            return Optional.empty();
        }
    }
}
