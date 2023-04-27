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
package io.micronaut.json.body;

import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.body.MessageBodyHandler;
import io.micronaut.http.codec.CodecException;
import io.micronaut.json.codec.JsonMediaTypeCodec;
import jakarta.inject.Singleton;

import java.io.InputStream;
import java.io.OutputStream;

@Singleton
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public final class JsonMessageHandler<T> implements MessageBodyHandler<T> {
    private final JsonMediaTypeCodec jsonMediaTypeCodec;

    public JsonMessageHandler(JsonMediaTypeCodec jsonMediaTypeCodec) {
        this.jsonMediaTypeCodec = jsonMediaTypeCodec;
    }

    @Override
    public boolean isReadable(Argument<T> type, MediaType mediaType) {
        return jsonMediaTypeCodec.supportsType(type.getType()) && mediaType != null && mediaType.getExtension().equals(MediaType.EXTENSION_JSON);
    }

    @Override
    public T read(Argument<T> type, MediaType mediaType, Headers httpHeaders, ByteBuffer<?> byteBuffer) throws CodecException {
        return jsonMediaTypeCodec.decode(
            type,
            byteBuffer
        );
    }

    @Override
    public T read(Argument<T> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
        return jsonMediaTypeCodec.decode(
            type,
            inputStream
        );
    }

    @Override
    public boolean isWriteable(Argument<T> type, MediaType mediaType) {
        return jsonMediaTypeCodec.supportsType(type.getType()) && mediaType != null && mediaType.getExtension().equals(MediaType.EXTENSION_JSON);
    }

    @Override
    public void writeTo(Argument<T> type, MediaType mediaType, T object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
        outgoingHeaders.set(HttpHeaders.CONTENT_TYPE, mediaType != null ? mediaType : MediaType.APPLICATION_JSON_TYPE);
        jsonMediaTypeCodec.encode(
            type,
            outputStream
        );
    }

    @Override
    public ByteBuffer<?> writeTo(Argument<T> type, MediaType mediaType, T object, MutableHeaders outgoingHeaders, ByteBufferFactory<?, ?> bufferFactory) throws CodecException {
        outgoingHeaders.set(HttpHeaders.CONTENT_TYPE, mediaType != null ? mediaType : MediaType.APPLICATION_JSON_TYPE);
        return jsonMediaTypeCodec.encode(
            type,
            object,
            bufferFactory
        );
    }
}
