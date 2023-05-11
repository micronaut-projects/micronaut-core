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
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.codec.CodecException;
import jakarta.inject.Singleton;

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
public final class TextPlainHandler implements MessageBodyHandler<CharSequence> {
    @Override
    public boolean isReadable(Argument<CharSequence> type, MediaType mediaType) {
        return true;
    }

    @Override
    public void writeTo(Argument<CharSequence> type, MediaType mediaType, CharSequence object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
        if (!outgoingHeaders.contains(HttpHeaders.CONTENT_TYPE)) {
            outgoingHeaders.set(HttpHeaders.CONTENT_TYPE, mediaType);
        }
        try {
            outputStream.write(object.toString().getBytes(MessageBodyWriter.getCharset(outgoingHeaders)));
        } catch (IOException e) {
            throw new CodecException("Error writing body text: " + e.getMessage(), e);
        }
    }

    @Override
    public String read(Argument<CharSequence> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
        try {
            return IOUtils.readText(new BufferedReader(new InputStreamReader(inputStream, MessageBodyWriter.getCharset(httpHeaders))));
        } catch (IOException e) {
            throw new CodecException("Error reading body text: " + e.getMessage(), e);
        }
    }

    @Override
    public String read(Argument<CharSequence> type, MediaType mediaType, Headers httpHeaders, ByteBuffer<?> byteBuffer) throws CodecException {
        return byteBuffer.toString(MessageBodyWriter.getCharset(httpHeaders));
    }
}
