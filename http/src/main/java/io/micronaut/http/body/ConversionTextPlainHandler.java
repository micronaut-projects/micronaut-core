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
package io.micronaut.http.body;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.codec.CodecException;
import io.micronaut.runtime.ApplicationConfiguration;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Handler for text/plain that will read and write any type, through {@link ConversionService} and
 * {@link Object#toString()} respectively. This is the behavior of
 * {@link io.micronaut.runtime.http.codec.TextPlainCodec} and can be enabled for legacy support.
 *
 * @since 4.0.0
 * @author Jonas Konrad
 */
@Produces(MediaType.TEXT_PLAIN)
@Consumes(MediaType.TEXT_PLAIN)
@Singleton
@Internal
@BootstrapContextCompatible
@Requires(property = "micronaut.http.legacy-text-conversion", value = "true")
final class ConversionTextPlainHandler<T> implements MessageBodyHandler<T> {
    private final ApplicationConfiguration configuration;
    private final ConversionService conversionService;

    ConversionTextPlainHandler(ApplicationConfiguration configuration, ConversionService conversionService) {
        this.configuration = configuration;
        this.conversionService = conversionService;
    }

    @Override
    public T read(Argument<T> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
        String text;
        try {
            text = new String(inputStream.readAllBytes(), configuration.getDefaultCharset());
        } catch (IOException e) {
            throw new CodecException("Error reading body text: " + e.getMessage(), e);
        }
        return convert(type, text);
    }

    @Override
    public T read(Argument<T> type, MediaType mediaType, Headers httpHeaders, ByteBuffer<?> byteBuffer) throws CodecException {
        T r = convert(type, byteBuffer.toString(configuration.getDefaultCharset()));
        if (byteBuffer instanceof ReferenceCounted rc) {
            rc.release();
        }
        return r;
    }

    private T convert(Argument<T> type, String text) {
        return conversionService.convert(text, type)
            .orElseThrow(() -> new CodecException("Cannot decode message with value [" + text + "] to type: " + type));
    }

    @Override
    public void writeTo(Argument<T> type, MediaType mediaType, T object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
        if (!outgoingHeaders.contains(HttpHeaders.CONTENT_TYPE)) {
            outgoingHeaders.set(HttpHeaders.CONTENT_TYPE, mediaType);
        }
        try {
            outputStream.write(object.toString().getBytes(MessageBodyWriter.getCharset(outgoingHeaders)));
        } catch (IOException e) {
            throw new CodecException("Error writing body text: " + e.getMessage(), e);
        }
    }
}
