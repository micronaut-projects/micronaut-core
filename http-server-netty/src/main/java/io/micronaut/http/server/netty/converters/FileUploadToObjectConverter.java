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
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.http.multipart.FileUpload;

import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.Optional;

/**
 * Converts file uploads.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class FileUploadToObjectConverter implements TypeConverter<FileUpload, Object> {

    private final ConversionService conversionService;
    private final Provider<MediaTypeCodecRegistry> decoderRegistryProvider;

    /**
     * @param conversionService       The conversion service
     * @param decoderRegistryProvider The media type decoder registry provider
     */
    protected FileUploadToObjectConverter(ConversionService conversionService,
                                          //Prevent early initialization of the codecs
                                          Provider<MediaTypeCodecRegistry> decoderRegistryProvider) {
        this.conversionService = conversionService;
        this.decoderRegistryProvider = decoderRegistryProvider;
    }

    @Override
    public Optional<Object> convert(FileUpload object, Class<Object> targetType, ConversionContext context) {
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
    }
}
