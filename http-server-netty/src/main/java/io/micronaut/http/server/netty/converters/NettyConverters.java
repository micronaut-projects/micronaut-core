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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.netty.channel.converters.ChannelOptionFactory;
import io.micronaut.http.server.netty.multipart.NettyCompletedFileUpload;
import io.micronaut.http.server.netty.multipart.NettyPartData;
import io.netty.buffer.*;
import io.netty.channel.ChannelOption;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpData;

import javax.inject.Provider;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Factory for bytebuf related converters.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Internal
public class NettyConverters implements TypeConverterRegistrar {

    private final ConversionService<?> conversionService;
    private final Provider<MediaTypeCodecRegistry> decoderRegistryProvider;
    private final ChannelOptionFactory channelOptionFactory;

    /**
     * Default constructor.
     * @param conversionService The conversion service
     * @param decoderRegistryProvider The decoder registry provider
     * @param channelOptionFactory The decoder channel option factory
     */
    public NettyConverters(ConversionService<?> conversionService,
                           //Prevent early initialization of the codecs
                           Provider<MediaTypeCodecRegistry> decoderRegistryProvider,
                           ChannelOptionFactory channelOptionFactory) {
        this.conversionService = conversionService;
        this.decoderRegistryProvider = decoderRegistryProvider;
        this.channelOptionFactory = channelOptionFactory;
    }

    @Override
    public void register(ConversionService<?> conversionService) {
        conversionService.addConverter(
                CharSequence.class,
                ChannelOption.class,
                (TypeConverter<CharSequence, ChannelOption>) (object, targetType, context) -> {
                    String str = object.toString();
                    String name = NameUtils.underscoreSeparate(str).toUpperCase(Locale.ENGLISH);
                    return Optional.of(channelOptionFactory.channelOption(name));
                }
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
                ByteBuf.class,
                Object.class,
                byteBufToObjectConverter()
        );

        conversionService.addConverter(
                FileUpload.class,
                CompletedFileUpload.class,
                fileUploadToCompletedFileUploadConverter()
        );

        conversionService.addConverter(
                FileUpload.class,
                Object.class,
                fileUploadToObjectConverter()
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

        conversionService.addConverter(
                NettyPartData.class,
                byte[].class,
                nettyPartDataToByteArrayConverter()
        );

        conversionService.addConverter(
                NettyPartData.class,
                Object.class,
                nettyPartDataToObjectConverter()
        );

        conversionService.addConverter(
                Attribute.class,
                Object.class,
                nettyAttributeToObjectConverter()
        );

        conversionService.addConverter(
                String.class,
                ChannelOption.class,
                s -> channelOptionFactory.channelOption(NameUtils.environmentName(s))
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
    }

    private TypeConverter<Attribute, Object> nettyAttributeToObjectConverter() {
        return (object, targetType, context) -> {
            try {
                final String value = object.getValue();
                if (targetType.isInstance(value)) {
                    return Optional.of(value);
                } else {
                    return conversionService.convert(value, targetType, context);
                }
            } catch (IOException e) {
                context.reject(e);
                return Optional.empty();
            }
        };
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

    private TypeConverter<NettyPartData, Object> nettyPartDataToObjectConverter() {
        return (object, targetType, context) -> {
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
        };
    }

    /**
     * @return The HTTP data to string converter.
     */
    protected TypeConverter<HttpData, CharSequence> httpDataToStringConverter() {
        return (upload, targetType, context) -> {
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
        };
    }

    /**
     * @return The HTTP data to byte array converter
     */
    protected TypeConverter<HttpData, byte[]> httpDataToByteArrayConverter() {
        return (upload, targetType, context) -> {
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
        };
    }


    /**
     * @return A FileUpload to CompletedFileUpload converter
     */
    protected TypeConverter<FileUpload, CompletedFileUpload> fileUploadToCompletedFileUploadConverter() {
        return (object, targetType, context) -> {
            try {
                if (!object.isCompleted()) {
                    return Optional.empty();
                }

                return Optional.of(new NettyCompletedFileUpload(object));
            } catch (Exception e) {
                context.reject(e);
                return Optional.empty();
            }
        };
    }

    /**
     * @return A FileUpload to CompletedFileUpload converter
     */
    protected TypeConverter<FileUpload, Object> fileUploadToObjectConverter() {
        return (object, targetType, context) -> {
            try {
                if (!object.isCompleted()) {
                    return Optional.empty();
                }

                String contentType = object.getContentType();
                ByteBuf byteBuf = object.getByteBuf();
                if (StringUtils.isNotEmpty(contentType)) {
                    MediaType mediaType = new MediaType(contentType);
                    Optional<MediaTypeCodec> registered = decoderRegistryProvider.get().findCodec(mediaType);
                    if (registered.isPresent()) {
                        MediaTypeCodec decoder = registered.get();
                        Object val = decoder.decode(targetType, new ByteBufInputStream(byteBuf));
                        return Optional.of(val);
                    } else {
                        return conversionService.convert(byteBuf, targetType, context);
                    }
                }
                return conversionService.convert(byteBuf, targetType, context);
            } catch (Exception e) {
                context.reject(e);
                return Optional.empty();
            }
        };
    }

    /**
     * @return A converter that returns bytebufs to objects
     */
    protected TypeConverter<ByteBuf, Object> byteBufToObjectConverter() {
        return (object, targetType, context) -> conversionService.convert(object.toString(context.getCharset()), targetType, context);
    }

    /**
     * @return A converter that converts bytebufs to strings
     */
    protected TypeConverter<ByteBuf, CharSequence> byteBufCharSequenceTypeConverter() {
        return (object, targetType, context) -> Optional.of(object.toString(context.getCharset()));
    }

    /**
     * @return A converter that converts composite bytebufs to strings
     */
    protected TypeConverter<CompositeByteBuf, CharSequence> compositeByteBufCharSequenceTypeConverter() {
        return (object, targetType, context) -> Optional.of(object.toString(context.getCharset()));
    }

    /**
     * @return A converter that converts bytebufs to byte arrays
     */
    protected TypeConverter<ByteBuf, byte[]> byteBufToArrayTypeConverter() {
        return (object, targetType, context) -> Optional.of(ByteBufUtil.getBytes(object));
    }

    /**
     * @return A converter that converts bytebufs to byte arrays
     */
    protected TypeConverter<byte[], ByteBuf> byteArrayToByteBuffTypeConverter() {
        return (object, targetType, context) -> Optional.of(Unpooled.wrappedBuffer(object));
    }
}
