/*
 * Copyright 2017 original authors
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
package org.particleframework.http.server.netty.converters;

import io.netty.handler.codec.http.multipart.FileUpload;
import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.convert.TypeConverter;
import org.particleframework.http.MediaType;

import javax.inject.Singleton;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Converts file uploads
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class FileUploadConverter implements TypeConverter<FileUpload, Object> {

    private final Map<MediaType, MediaTypeReader> javaTypeMappings;
    private final ConversionService conversionService;

    protected FileUploadConverter(ConversionService conversionService, MediaTypeReader... mediaTypeMappings) {
        this.javaTypeMappings = new LinkedHashMap<>();
        for (MediaTypeReader mediaTypeMapping : mediaTypeMappings) {
            javaTypeMappings.put(mediaTypeMapping.getMediaType(), mediaTypeMapping);
        }
        this.conversionService = conversionService;
    }

    @Override
    public Optional<Object> convert(FileUpload object, Class<Object> targetType, ConversionContext context) {
        try {
            String contentType = object.getContentType();
            if (contentType != null) {
                MediaType mediaType = new MediaType(contentType);
                Charset charset = object.getCharset();
                if (charset == null) {
                    charset = context.getCharset();
                }
                MediaTypeReader reader = javaTypeMappings.get(mediaType);
                Object val = reader.read(targetType, object.getByteBuf(), charset);
                return Optional.of(val);
            }
            return conversionService.convert(object.getByteBuf(), targetType, context);
        } catch (IOException e) {
            // TODO: conversion context errors
            return Optional.empty();
        }
    }
}
