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
        final boolean isOptional = type.getType() == Optional.class;
        final Argument finalArgument = isOptional ? type.getFirstTypeVariable().orElse(type) : type;

        if (mediaTypeCodecRegistry != null && contentType != null) {
            if (CharSequence.class.isAssignableFrom(finalArgument.getType())) {
                Charset charset = contentType.getCharset().orElse(StandardCharsets.UTF_8);
                var converted = Optional.of(new String(bytes, charset));
                return isOptional ? Optional.of(converted) : converted;
            } else if (finalArgument.getType() == byte[].class) {
                var converted = Optional.of(bytes);
                return isOptional ? Optional.of(converted) : converted;
            } else {
                Optional<MediaTypeCodec> foundCodec = mediaTypeCodecRegistry.findCodec(contentType);
                if (foundCodec.isPresent()) {
                    try {
                        var converted = foundCodec.map(codec -> codec.decode(finalArgument, bytes));
                        return isOptional ? Optional.of(converted) : converted;
                    } catch (CodecException e) {
                        if (LOG.isDebugEnabled()) {
                            var message = e.getMessage();
                            LOG.debug("Error decoding body for type [{}] from '{}'. Attempting fallback.", type, contentType);
                            LOG.debug("CodecException Message was: {}", message == null ? "null" : message.replace("\n", ""));
                        }
                    }
                }
            }
        }
        // last chance, try type conversion
        var converted = conversionService.convert(bytes, ConversionContext.of(finalArgument));
        if (isOptional) {
            return Optional.of(converted);
        } else {
            return converted;
        }
    }
}
