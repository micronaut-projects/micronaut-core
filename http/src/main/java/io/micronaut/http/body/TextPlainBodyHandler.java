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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.io.IOUtils;
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
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

/**
 * Body handler for content type {@value MediaType#TEXT_PLAIN}.
 *
 * @since 4.0.0
 * @author Graeme Rocher
 */
@Produces(MediaType.TEXT_PLAIN)
@Consumes(MediaType.TEXT_PLAIN)
@Singleton
@Experimental
public final class TextPlainBodyHandler implements MessageBodyWriter<CharSequence>, MessageBodyReader<String>, ChunkedMessageBodyReader<String> {

    @Override
    public void writeTo(Argument<CharSequence> type, MediaType mediaType, CharSequence object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
        outgoingHeaders.setIfMissing(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN);
        try {
            outputStream.write(object.toString().getBytes(MessageBodyWriter.getCharset(mediaType, outgoingHeaders)));
        } catch (IOException e) {
            throw new CodecException("Error writing body text: " + e.getMessage(), e);
        }
    }

    @Override
    public String read(Argument<String> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
        try {
            return IOUtils.readText(new BufferedReader(new InputStreamReader(inputStream, MessageBodyWriter.getCharset(mediaType, httpHeaders))));
        } catch (IOException e) {
            throw new CodecException("Error reading body text: " + e.getMessage(), e);
        }
    }

    @Override
    public String read(Argument<String> type, MediaType mediaType, Headers httpHeaders, ByteBuffer<?> byteBuffer) throws CodecException {
        return byteBuffer.toString(MessageBodyWriter.getCharset(mediaType, httpHeaders));
    }

    @Override
    public Publisher<String> readChunked(Argument<String> type, MediaType mediaType, Headers httpHeaders, Publisher<ByteBuffer<?>> input) {
        return Flux.from(input).map(byteBuffer -> {
            String s = byteBuffer.toString(MessageBodyWriter.getCharset(mediaType, httpHeaders));
            if (byteBuffer instanceof ReferenceCounted rc) {
                rc.release();
            }
            return s;
        });
    }
}
