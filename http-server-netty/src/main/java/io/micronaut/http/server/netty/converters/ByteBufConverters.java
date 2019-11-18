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
package io.micronaut.http.server.netty.converters;

import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * Factory for bytebuf related converters.
 *
 * @author graemerocher
 * @since 1.0
 */
@Factory
@Internal
public class ByteBufConverters {


    /**
     * @return A converter that converts bytebufs to strings
     */
    @Singleton
    TypeConverter<ByteBuf, CharSequence> byteBufCharSequenceTypeConverter() {
        return (object, targetType, context) -> Optional.of(object.toString(context.getCharset()));
    }

    /**
     * @return A converter that converts composite bytebufs to strings
     */
    @Singleton
    TypeConverter<CompositeByteBuf, CharSequence> compositeByteBufCharSequenceTypeConverter() {
        return (object, targetType, context) -> Optional.of(object.toString(context.getCharset()));
    }

    /**
     * @return A converter that converts bytebufs to byte arrays
     */
    @Singleton
    TypeConverter<ByteBuf, byte[]> byteBufToArrayTypeConverter() {
        return (object, targetType, context) -> Optional.of(ByteBufUtil.getBytes(object));
    }

    /**
     * @return A converter that converts bytebufs to byte arrays
     */
    @Singleton
    TypeConverter<byte[], ByteBuf> byteArrayToByteBuffTypeConverter() {
        return (object, targetType, context) -> Optional.of(Unpooled.wrappedBuffer(object));
    }

    /**
     * @return A converter that converts composite bytebufs to byte arrays
     */
    @Singleton
    TypeConverter<CompositeByteBuf, byte[]> compositeByteBufTypeConverter() {
        return (object, targetType, context) -> Optional.of(ByteBufUtil.getBytes(object));
    }

    /**
     * @param conversionService The conversion service
     * @return A converter that converts composite bytebufs to object
     */
    @Singleton
    TypeConverter<CompositeByteBuf, Object> compositeByteBufToObjectTypeConverter(ConversionService conversionService) {
        return (object, targetType, context) -> conversionService
                    .convert(object, String.class, context)
                    .flatMap(val -> conversionService.convert(val, targetType, context));
    }

}
