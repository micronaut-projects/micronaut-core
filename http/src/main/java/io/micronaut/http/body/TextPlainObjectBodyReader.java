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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.codec.CodecException;
import io.micronaut.runtime.ApplicationConfiguration;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * The body reader that reads a text/plain string converting it into the argument type.
 *
 * @param <T> The body type
 * @author Denis Stepanov
 * @since 4.6
 */
@Produces(MediaType.TEXT_PLAIN)
@Consumes(MediaType.TEXT_PLAIN)
@Singleton
@Internal
public final class TextPlainObjectBodyReader<T> implements TypedMessageBodyReader<T>, ChunkedMessageBodyReader<T> {

    private final Charset defaultCharset;
    private final ConversionService conversionService;

    TextPlainObjectBodyReader(ApplicationConfiguration applicationConfiguration, ConversionService conversionService) {
        this.defaultCharset = applicationConfiguration.getDefaultCharset();
        this.conversionService = conversionService;
    }

    @Override
    public Argument<T> getType() {
        return (Argument<T>) Argument.OBJECT_ARGUMENT;
    }

    @Override
    public boolean isReadable(Argument<T> type, MediaType mediaType) {
        return mediaType != null && mediaType.matches(MediaType.TEXT_PLAIN_TYPE);
    }

    @Override
    public T read(Argument<T> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
        try {
            String string = new String(inputStream.readAllBytes(), getCharset(mediaType, httpHeaders));
            return conversionService.convertRequired(string, type);
        } catch (IOException e) {
            throw new CodecException("Failed to read InputStream", e);
        }
    }

    @Override
    public T read(Argument<T> type, MediaType mediaType, Headers httpHeaders, ByteBuffer<?> byteBuffer) throws CodecException {
        return read0(type, byteBuffer, getCharset(mediaType, httpHeaders));
    }

    private T read0(Argument<T> type, ByteBuffer<?> byteBuffer, Charset charset) {
        String string = byteBuffer.toString(charset);
        T conv = conversionService.convertRequired(string, type);
        if (byteBuffer instanceof ReferenceCounted rc) {
            rc.release();
        }
        return conv;
    }

    @Override
    public Publisher<T> readChunked(Argument<T> type, MediaType mediaType, Headers httpHeaders, Publisher<ByteBuffer<?>> input) {
        return Flux.from(input).map(byteBuffer -> read0(type, byteBuffer, getCharset(mediaType, httpHeaders)));
    }

    private Charset getCharset(MediaType mediaType, Headers httpHeaders) {
        return MessageBodyWriter.findCharset(mediaType, httpHeaders).orElse(defaultCharset);
    }
}
