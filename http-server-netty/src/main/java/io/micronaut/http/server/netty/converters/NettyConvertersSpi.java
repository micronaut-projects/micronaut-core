/*
 * Copyright 2017-2022 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.CompletedPart;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.http.server.netty.multipart.NettyCompletedAttribute;
import io.micronaut.http.server.netty.multipart.NettyCompletedFileUpload;
import io.micronaut.http.server.netty.multipart.NettyPartData;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpData;
import io.netty.handler.logging.LogLevel;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * Factory for bytebuf related converters that do not need the application context (can be
 * registered with SPI).
 *
 * @author Jonas Konrad
 * @since 3.6.3
 */
@Internal
public final class NettyConvertersSpi implements TypeConverterRegistrar {
    @Override
    public void register(MutableConversionService conversionService) {
        conversionService.addConverter(String.class, LogLevel.class, LogLevel::valueOf);
        conversionService.addConverter(
                String.class,
                NettyHttpServerConfiguration.NettyListenerConfiguration.Family.class,
                NettyHttpServerConfiguration.NettyListenerConfiguration.Family::valueOf
        );
        conversionService.addConverter(
            ByteBuf.class,
            CharSequence.class,
            byteBufCharSequenceTypeConverter()
        );

        conversionService.addConverter(
            CompositeByteBuf.class,
            CharSequence.class,
            compositeByteBufCharSequenceTypeConverter()
        );

        conversionService.addConverter(
            ByteBuf.class,
            byte[].class,
            byteBufToArrayTypeConverter()
        );

        conversionService.addConverter(
            byte[].class,
            ByteBuf.class,
            byteArrayToByteBuffTypeConverter()
        );

        conversionService.addConverter(
            FileUpload.class,
            CompletedFileUpload.class,
            fileUploadToCompletedFileUploadConverter()
        );

        conversionService.addConverter(
            Attribute.class,
            CompletedPart.class,
            attributeToCompletedPartConverter()
        );

        conversionService.addConverter(
            NettyPartData.class,
            byte[].class,
            nettyPartDataToByteArrayConverter()
        );

        conversionService.addConverter(
            Map.class,
            WriteBufferWaterMark.class,
            (map, targetType, context) -> {
                Object h = map.get("high");
                Object l = map.get("low");
                if (h != null && l != null) {
                    try {
                        int high = Integer.parseInt(h.toString());
                        int low = Integer.parseInt(l.toString());
                        return Optional.of(new WriteBufferWaterMark(low, high));
                    } catch (NumberFormatException e) {
                        context.reject(e);
                        return Optional.empty();
                    }
                }
                return Optional.empty();
            }
        );

        conversionService.addConverter(
            HttpData.class,
            byte[].class,
            httpDataToByteArrayConverter()
        );

        conversionService.addConverter(
            HttpData.class,
            CharSequence.class,
            httpDataToStringConverter()
        );
    }

    private TypeConverter<NettyPartData, byte[]> nettyPartDataToByteArrayConverter() {
        return (upload, targetType, context) -> {
            try {
                return Optional.of(upload.getBytes());
            } catch (IOException e) {
                context.reject(e);
                return Optional.empty();
            }
        };
    }

    /**
     * @return A FileUpload to CompletedFileUpload converter
     */
    private TypeConverter<FileUpload, CompletedFileUpload> fileUploadToCompletedFileUploadConverter() {
        return (object, targetType, context) -> {
            try {
                if (!object.isCompleted()) {
                    return Optional.empty();
                }

                // unlike NettyCompletedAttribute, NettyCompletedFileUpload does a `retain` on
                // construct, so we don't need one here
                return Optional.of(new NettyCompletedFileUpload(object));
            } catch (Exception e) {
                context.reject(e);
                return Optional.empty();
            }
        };
    }

    /**
     * @return An Attribute to CompletedPart converter
     */
    private TypeConverter<Attribute, CompletedPart> attributeToCompletedPartConverter() {
        return (object, targetType, context) -> {
            try {
                if (!object.isCompleted() || !targetType.isAssignableFrom(NettyCompletedAttribute.class)) {
                    return Optional.empty();
                }

                // converter does not claim the input object, so we need to retain it here. it's
                // released by NettyCompletedAttribute.get*
                return Optional.of(new NettyCompletedAttribute(object.retain()));
            } catch (Exception e) {
                context.reject(e);
                return Optional.empty();
            }
        };
    }

    /**
     * @return A converter that converts bytebufs to strings
     */
    private TypeConverter<ByteBuf, CharSequence> byteBufCharSequenceTypeConverter() {
        return (object, targetType, context) -> Optional.of(object.toString(context.getCharset()));
    }

    /**
     * @return A converter that converts composite bytebufs to strings
     */
    private TypeConverter<CompositeByteBuf, CharSequence> compositeByteBufCharSequenceTypeConverter() {
        return (object, targetType, context) -> Optional.of(object.toString(context.getCharset()));
    }

    /**
     * @return A converter that converts bytebufs to byte arrays
     */
    private TypeConverter<ByteBuf, byte[]> byteBufToArrayTypeConverter() {
        return (object, targetType, context) -> Optional.of(ByteBufUtil.getBytes(object));
    }

    /**
     * @return A converter that converts bytebufs to byte arrays
     */
    private TypeConverter<byte[], ByteBuf> byteArrayToByteBuffTypeConverter() {
        return (object, targetType, context) -> Optional.of(Unpooled.wrappedBuffer(object));
    }

    /**
     * @return The HTTP data to string converter.
     */
    private TypeConverter<HttpData, CharSequence> httpDataToStringConverter() {
        return (upload, targetType, context) -> {
            try {
                if (!upload.isCompleted()) {
                    return Optional.empty();
                }
                ByteBuf byteBuf = upload.getByteBuf();
                return Optional.of(byteBuf.toString(context.getCharset()));
            } catch (Exception e) {
                context.reject(e);
                return Optional.empty();
            }
        };
    }

    /**
     * @return The HTTP data to byte array converter
     */
    private TypeConverter<HttpData, byte[]> httpDataToByteArrayConverter() {
        return (upload, targetType, context) -> {
            try {
                if (!upload.isCompleted()) {
                    return Optional.empty();
                }
                ByteBuf byteBuf = upload.getByteBuf();
                return Optional.of(ByteBufUtil.getBytes(byteBuf));
            } catch (Exception e) {
                context.reject(e);
                return Optional.empty();
            }
        };
    }
}
