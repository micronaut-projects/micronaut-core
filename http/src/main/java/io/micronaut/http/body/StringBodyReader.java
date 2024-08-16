/*
 * Copyright 2017-2024 original authors
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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecException;
import io.micronaut.runtime.ApplicationConfiguration;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * The body reader for {@link String}.
 *
 * @author Denis Stepanov
 * @since 4.6
 */
@Internal
@Singleton
@BootstrapContextCompatible
public final class StringBodyReader implements TypedMessageBodyReader<String>, ChunkedMessageBodyReader<String> {
    private final Charset defaultCharset;

    StringBodyReader(ApplicationConfiguration applicationConfiguration) {
        this.defaultCharset = applicationConfiguration.getDefaultCharset();
    }

    @Override
    public Argument<String> getType() {
        return Argument.STRING;
    }

    @Override
    public String read(Argument<String> type, MediaType mediaType, Headers httpHeaders, ByteBuffer<?> byteBuffer) throws CodecException {
        return read0(byteBuffer, getCharset(mediaType, httpHeaders));
    }

    private String read0(ByteBuffer<?> byteBuffer, Charset charset) {
        String s = byteBuffer.toString(charset);
        if (byteBuffer instanceof ReferenceCounted rc) {
            rc.release();
        }
        return s;
    }

    @Override
    public String read(Argument<String> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
        try {
            return new String(inputStream.readAllBytes(), getCharset(mediaType, httpHeaders));
        } catch (IOException e) {
            throw new CodecException("Failed to read InputStream", e);
        }
    }

    @Override
    public Publisher<String> readChunked(Argument<String> type, MediaType mediaType, Headers httpHeaders, Publisher<ByteBuffer<?>> input) {
        return Flux.from(input).map(byteBuffer -> read0(byteBuffer, getCharset(mediaType, httpHeaders)));
    }

    private Charset getCharset(MediaType mediaType, Headers httpHeaders) {
        return MessageBodyWriter.findCharset(mediaType, httpHeaders).orElse(defaultCharset);
    }
}
