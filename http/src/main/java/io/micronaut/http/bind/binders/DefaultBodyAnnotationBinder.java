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
package io.micronaut.http.bind.binders;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Binds a String body argument.
 *
 * @param <T> A type
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultBodyAnnotationBinder<T> implements BodyArgumentBinder<T> {

    protected final ConversionService conversionService;
    private final MediaTypeCodecRegistry codecRegistry;

    /**
     * @param conversionService The conversion service
     */
    public DefaultBodyAnnotationBinder(ConversionService conversionService) {
        this.conversionService = conversionService;
        this.codecRegistry = null;
    }

    /**
     * @param conversionService The conversion service
     */
    public DefaultBodyAnnotationBinder(ConversionService conversionService, MediaTypeCodecRegistry mediaTypeCodecRegistry) {
        this.conversionService = conversionService;
        this.codecRegistry = mediaTypeCodecRegistry;
    }

    @Override
    public Class<Body> getAnnotationType() {
        return Body.class;
    }

    @Override
    public BindingResult<T> bind(ArgumentConversionContext<T> context, HttpRequest<?> source) {
        Optional<String> bodyComponent = context.getAnnotationMetadata().stringValue(Body.class);
        if (bodyComponent.isPresent()) {
            Optional<ConvertibleValues> body = source.getBody(ConvertibleValues.class);
            if (body.isPresent()) {
                ConvertibleValues values = body.get();
                String component = bodyComponent.get();
                if (!values.contains(component)) {
                    component = NameUtils.hyphenate(component);
                }

                Optional<T> value = values.get(component, context);
                return newResult(value.orElse(null), context);
            }
            //noinspection unchecked
            return BindingResult.EMPTY;
        }
        Object body = source.getBody().orElse(null);
        if (body == null) {
            //noinspection unchecked
            return BindingResult.EMPTY;
        } else {
            Argument<T> bodyType = context.getArgument();
            T decoded;
            try {
                decoded = decodeBody(source, body, bodyType, codecRegistry);
            } catch (Exception e) {
                context.reject(e);
                return newResult(null, context);
            }
            if (decoded != null) {
                return newResult(decoded, context);
            } else {
                Optional<T> converted = conversionService.convert(body, context);
                return newResult(converted.orElse(null), context);
            }
        }
    }

    /**
     * Decodes the body given the source and the arguments.
     * @param source The request
     * @param body The body
     * @param targetType The body type
     * @param mediaTypeCodecRegistry The coded registry
     * @return The decoded body or null
     * @param <T1> The target type
     */
    @Nullable
    @Internal
    public static <T1> T1 decodeBody(HttpRequest<?> source, Object body, Argument<T1> targetType, MediaTypeCodecRegistry mediaTypeCodecRegistry) {
        try {
            T1 decoded = null;
            if (targetType.isInstance(body)) {
                decoded = (T1) body;
            } else if (body instanceof ByteBuffer<?> byteBuffer && byteBuffer.readableBytes() > 0) {
                if (targetType.isAssignableFrom(String.class)) {
                    decoded = (T1) byteBuffer.toString(StandardCharsets.UTF_8);
                } else if (targetType.isAssignableFrom(byte[].class)) {
                    decoded = (T1) byteBuffer.toByteArray();
                } else {
                    if (mediaTypeCodecRegistry != null) {
                        MediaTypeCodec codec = source.getContentType().flatMap(mediaTypeCodecRegistry::findCodec).orElse(null);
                        if (codec != null) {
                            decoded = codec.decode(targetType, byteBuffer);
                        }
                    }
                }
            }
            return decoded;
        } catch (CodecException e) {
            if(e.getCause() instanceof RuntimeException r) {
                throw r;
            } else {
                throw e;
            }
        }
    }

    @SuppressWarnings("java:S3655") // false positive
    private BindingResult<T> newResult(T converted, ArgumentConversionContext<T> context) {
        final Optional<ConversionError> lastError = context.getLastError();
        if (lastError.isPresent()) {
            return new BindingResult<T>() {
                @Override
                public Optional<T> getValue() {
                    return Optional.empty();
                }

                @Override
                public List<ConversionError> getConversionErrors() {
                    return Collections.singletonList(lastError.get());
                }
            };
        } else {
            return () -> Optional.ofNullable(converted);
        }
    }
}
