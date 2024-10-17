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
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyReader;
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
public class HttpResponseAdapter<O> extends BaseHttpResponseAdapter<byte[], O> {

    private static final Logger LOG = LoggerFactory.getLogger(HttpResponseAdapter.class);

    @NonNull
    private final Argument<O> bodyType;

    private final MediaTypeCodecRegistry mediaTypeCodecRegistry;
    private final MessageBodyHandlerRegistry messageBodyHandlerRegistry;

    public HttpResponseAdapter(java.net.http.HttpResponse<byte[]> httpResponse,
                               @Nullable Argument<O> bodyType,
                               ConversionService conversionService,
                               MediaTypeCodecRegistry mediaTypeCodecRegistry,
                               MessageBodyHandlerRegistry messageBodyHandlerRegistry) {
        super(httpResponse, conversionService);
        this.bodyType = bodyType;
        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
        this.messageBodyHandlerRegistry = messageBodyHandlerRegistry;
    }

    @Override
    public Optional<O> getBody() {
        return getBody(bodyType);
    }

    @Override
    public <T> Optional<T> getBody(Argument<T> type) {
        final boolean isOptional = type.getType() == Optional.class;
        final Argument<Object> theArgument = (Argument<Object>) (isOptional ? type.getFirstTypeVariable().orElse(type) : type);
        Optional<?> optional = convertBytes(getContentType().orElse(null), httpResponse.body(), theArgument);
        if (isOptional) {
            // If the requested type is an Optional, then we need to wrap the result again
            return Optional.of((T) optional);
        }
        return (Optional<T>) optional;
    }

    private <T> Optional<T> convertBytes(@Nullable MediaType contentType, byte[] bytes, Argument<T> type) {
        if (bytes.length == 0) {
            return Optional.empty();
        }
        if (type.getType().equals(byte[].class)) {
            return Optional.of((T) bytes);
        }
        if (contentType != null) {
            if (CharSequence.class.isAssignableFrom(type.getType())) {
                Charset charset = contentType.getCharset().orElse(StandardCharsets.UTF_8);
                return Optional.of((T) new String(bytes, charset));
            }
        }
        if (mediaTypeCodecRegistry != null) {
            Optional<MediaTypeCodec> foundCodec = mediaTypeCodecRegistry.findCodec(contentType);
            if (foundCodec.isPresent()) {
                try {
                    return foundCodec.map(codec -> codec.decode(type, bytes));
                } catch (CodecException e) {
                    logCodecError(contentType, type, e);
                }
            }
        }
        if (messageBodyHandlerRegistry != null) {
            MessageBodyReader<T> reader = messageBodyHandlerRegistry.findReader(type, contentType).orElse(null);
            if (reader != null) {
                try {
                    T value = reader.read(
                        type,
                        contentType,
                        getHeaders(),
                        ByteArrayBufferFactory.INSTANCE.wrap(bytes)
                    );
                    return Optional.of(value);
                } catch (CodecException e) {
                    logCodecError(contentType, type, e);
                }
            }
        }
        // last chance, try type conversion
       return conversionService.convert(bytes, ConversionContext.of(type));
    }

    private <T> void logCodecError(MediaType contentType, Argument<T> type, CodecException e) {
        if (LOG.isDebugEnabled()) {
            var message = e.getMessage();
            LOG.debug("Error decoding body for type [{}] from '{}'. Attempting fallback.", type, contentType);
            LOG.debug("CodecException Message was: {}", message == null ? "null" : message.replace("\n", ""));
        }
    }
}
