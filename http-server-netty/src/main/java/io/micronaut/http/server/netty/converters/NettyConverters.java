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

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.multipart.PartData;
import io.micronaut.http.netty.channel.converters.ChannelOptionFactory;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelOption;
import io.netty.util.ReferenceCounted;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Optional;

/**
 * Factory for bytebuf related converters.
 *
 * @author graemerocher
 * @since 1.0
 */
@Prototype
@Internal
public final class NettyConverters implements TypeConverterRegistrar {

    private final ConversionService conversionService;
    private final BeanProvider<MediaTypeCodecRegistry> decoderRegistryProvider;
    private final BeanProvider<MessageBodyHandlerRegistry> messageBodyHandlerRegistries;
    private final BeanProvider<ChannelOptionFactory> channelOptionFactory;

    /**
     * Default constructor.
     *
     * @param conversionService            The conversion service
     * @param decoderRegistryProvider      The decoder registry provider
     * @param messageBodyHandlerRegistries The message body handlers
     * @param channelOptionFactory         The decoder channel option factory
     */
    public NettyConverters(ConversionService conversionService,
                           //Prevent early initialization of the codecs
                           BeanProvider<MediaTypeCodecRegistry> decoderRegistryProvider,
                           BeanProvider<MessageBodyHandlerRegistry> messageBodyHandlerRegistries,
                           BeanProvider<ChannelOptionFactory> channelOptionFactory) {
        this.conversionService = conversionService;
        this.decoderRegistryProvider = decoderRegistryProvider;
        this.messageBodyHandlerRegistries = messageBodyHandlerRegistries;
        this.channelOptionFactory = channelOptionFactory;
    }

    @Override
    public void register(MutableConversionService conversionService) {
        conversionService.addConverter(
                CharSequence.class,
                ChannelOption.class,
                (object, targetType, context) -> {
                    String str = object.toString();
                    String name = NameUtils.underscoreSeparate(str).toUpperCase(Locale.ENGLISH);
                    return Optional.of(channelOptionFactory.get().channelOption(name));
                }
        );

        conversionService.addConverter(
                ByteBuf.class,
                Object.class,
                (object, targetType, context) -> conversionService.convert(object.toString(context.getCharset()), targetType, context)
        );

        conversionService.addConverter(
                PartData.class,
                Object.class,
                (object, targetType, context) -> {
                    try {
                        if (targetType.isAssignableFrom(ByteBuffer.class)) {
                            return Optional.of(object.getByteBuffer());
                        } else if (targetType.isAssignableFrom(InputStream.class)) {
                            return Optional.of(object.getInputStream());
                        } else {
                            try (ReadBuffer byteBuf = object.readBuffer()) {
                                return this.conversionService.convert(byteBuf, targetType, context);
                            }
                        }
                    } catch (IOException e) {
                        context.reject(e);
                        return Optional.empty();
                    }
                }
        );

        conversionService.addConverter(
                String.class,
                ChannelOption.class,
                s -> channelOptionFactory.get().channelOption(NameUtils.environmentName(s))
        );
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static <T> void postProcess(ReferenceCounted input, Optional<T> converted) {
        if (converted.isPresent()) {
            input.touch();
            T item = converted.get();
            // this is not great, but what can we do?
            boolean targetRefCounted = item instanceof ReferenceCounted || item instanceof io.micronaut.core.io.buffer.ReferenceCounted;
            if (!targetRefCounted) {
                input.release();
            }
        } else {
            input.release();
        }
    }
}
