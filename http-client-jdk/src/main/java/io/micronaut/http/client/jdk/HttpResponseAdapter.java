/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.client.jdk;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.convert.value.MutableConvertibleValuesMap;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Adapter from {@link java.net.http.HttpResponse} to {@link HttpResponse}.
 * @author Sergio del Amo
 * @since 4.0.0
 * @param <O> Body Type
 */
@Internal
@Experimental
public class HttpResponseAdapter<O> implements HttpResponse<O> {

    private static final Logger LOG = LoggerFactory.getLogger(HttpResponseAdapter.class);

    private final java.net.http.HttpResponse<byte[]> httpResponse;
    @NonNull
    private final Argument<O> bodyType;
    private final ConversionService conversionService;
    private final MutableConvertibleValues<Object> attributes = new MutableConvertibleValuesMap<>();

    private final MediaTypeCodecRegistry mediaTypeCodecRegistry;

    public HttpResponseAdapter(java.net.http.HttpResponse<byte[]> httpResponse,
                               @NonNull Argument<O> bodyType,
                               ConversionService conversionService,
                               MediaTypeCodecRegistry mediaTypeCodecRegistry) {
        this.httpResponse = httpResponse;
        this.bodyType = bodyType;
        this.conversionService = conversionService;
        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.valueOf(httpResponse.statusCode());
    }

    @Override
    public int code() {
        return httpResponse.statusCode();
    }

    @Override
    public String reason() {
        return getStatus().getReason();
    }

    @Override
    public HttpHeaders getHeaders() {
        return new HttpHeadersAdapter(httpResponse.headers(), conversionService);
    }

    @Override
    public MutableConvertibleValues<Object> getAttributes() {
        return attributes;
    }

    @Override
    public Optional<O> getBody() {
        return convertBytes(getContentType().orElse(null), httpResponse.body(), bodyType);
    }

    @Override
    public <T> Optional<T> getBody(Argument<T> type) {
        return convertBytes(getContentType().orElse(null), httpResponse.body(), type);
    }

    private <T> Optional convertBytes(@Nullable MediaType contentType, byte[] bytes, Argument<T> type) {
        if (type != null && mediaTypeCodecRegistry != null && contentType != null) {
            if (isCharSequence(type)) {
                Charset charset = contentType.getCharset().orElse(StandardCharsets.UTF_8);
                return maybeWrap(Optional.of(new String(bytes, charset)), isOptional(type));
            } else if (isByteArray(type)) {
                return maybeWrap(Optional.of(bytes), isOptional(type));
            } else {
                Optional<MediaTypeCodec> foundCodec = mediaTypeCodecRegistry.findCodec(contentType);
                if (foundCodec.isPresent()) {
                    try {
                        return foundCodec.map(codec -> codec.decode(type, bytes));
                    } catch (CodecException e) {
                        if (LOG.isDebugEnabled()) {
                            var message = e.getMessage();
                            LOG.debug("Error decoding body for type [{}] from '{}'. Attempting fallback.", type, contentType);
                            LOG.debug("CodecException Message was: {}", message == null ? "null" : message.replace("\n", ""));
                        }
                        return fallback(bytes, type);
                    }
                }
            }
        }
        // last chance, try type conversion
        return fallback(bytes, type);
    }

    private boolean isOptional(Argument<?> type) {
        return type != null && Optional.class.isAssignableFrom(type.getType());
    }

    private boolean isCharSequence(Argument<?> type) {
        return type != null &&
            (CharSequence.class.isAssignableFrom(type.getType())
                || (isOptional(type) && CharSequence.class.isAssignableFrom(type.getWrappedType().getType())));
    }

    private boolean isByteArray(Argument<?> type) {
        return type != null &&
            (type.getType() == byte[].class
                || (isOptional(type) && type.getWrappedType().getType() == byte[].class));
    }

    @SuppressWarnings({"rawtypes", "OptionalUsedAsFieldOrParameterType"})
    private Optional maybeWrap(@NonNull Optional value, boolean wrap) {
        return wrap ? Optional.of(value) : value;
    }

    @SuppressWarnings("rawtypes")
    private <T> Optional fallback(byte[] bytes, Argument<T> type) {
        if (type != null && Optional.class.isAssignableFrom(type.getType())) {
            return Optional.of(conversionService.convert(bytes, ConversionContext.of(type.getWrappedType())));
        } else {
            return type != null ? conversionService.convert(bytes, ConversionContext.of(type)) : Optional.empty();
        }
    }
}
