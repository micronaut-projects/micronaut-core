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
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.body.MessageBodyHandler;
import io.micronaut.http.codec.CodecException;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@SuppressWarnings("DefaultAnnotationParam")
@Singleton
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public final class JsonMessageHandler<T> implements MessageBodyHandler<T> {
    private final JsonMapper jsonMapper;

    public JsonMessageHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public boolean isReadable(Argument<T> type, MediaType mediaType) {
        return mediaType != null && mediaType.getExtension().equals(MediaType.EXTENSION_JSON);
    }

    private static CodecException decorateRead(Argument<?> type, IOException e) {
        return new CodecException("Error decoding JSON stream for type [" + type.getName() + "]: " + e.getMessage(), e);
    }

    @Override
    public T read(Argument<T> type, MediaType mediaType, Headers httpHeaders, ByteBuffer<?> byteBuffer) throws CodecException {
        T decoded;
        try {
            decoded = jsonMapper.readValue(byteBuffer, type);
        } catch (IOException e) {
            throw decorateRead(type, e);
        }
        if (byteBuffer instanceof ReferenceCounted rc) {
            rc.release();
        }
        return decoded;
    }

    @Override
    public T read(Argument<T> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
        try {
            return jsonMapper.readValue(inputStream, type);
        } catch (IOException e) {
            throw decorateRead(type, e);
        }
    }

    @Override
    public boolean isWriteable(Argument<T> type, MediaType mediaType) {
        return mediaType != null && mediaType.getExtension().equals(MediaType.EXTENSION_JSON);
    }

    private static CodecException decorateWrite(Object object, IOException e) {
        return new CodecException("Error encoding object [" + object + "] to JSON: " + e.getMessage(), e);
    }

    @Override
    public void writeTo(Argument<T> type, MediaType mediaType, T object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
        outgoingHeaders.set(HttpHeaders.CONTENT_TYPE, mediaType != null ? mediaType : MediaType.APPLICATION_JSON_TYPE);
        try {
            jsonMapper.writeValue(outputStream, type, object);
        } catch (IOException e) {
            throw decorateWrite(object, e);
        }
    }
}
