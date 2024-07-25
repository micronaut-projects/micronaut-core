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

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
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
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.codec.CodecException;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Body handler for JSON.
 *
 * @param <T> The type to read/write
 * @author Jonas Konrad
 * @since 4.0.0
 */
@Experimental
@Singleton
@JsonMessageHandler.ProducesJson
@JsonMessageHandler.ConsumesJson
@BootstrapContextCompatible
public final class JsonMessageHandler<T> implements MessageBodyHandler<T> {

    private final JsonMapper jsonMapper;

    public JsonMessageHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * Get the json mapper used by this handler.
     *
     * @return The mapper
     */
    @NonNull
    public JsonMapper getJsonMapper() {
        return jsonMapper;
    }

    @Override
    public boolean isReadable(Argument<T> type, MediaType mediaType) {
        return mediaType != null && mediaType.getExtension().equals(MediaType.EXTENSION_JSON);
    }

    private static CodecException decorateRead(Argument<?> type, IOException e) {
        return new CodecException("Error decoding JSON stream for type [" + type.getName() + "]: " + e.getMessage(), e);
    }

    @Override
    public JsonMessageHandler<T> createSpecific(Argument<T> type) {
        return new JsonMessageHandler<>(jsonMapper.createSpecific(type));
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
            if (type.getType() == Object.class && object instanceof CharSequence charSequence) {
                outputStream.write(charSequence.toString().getBytes(MessageBodyWriter.findCharset(mediaType, outgoingHeaders).orElse(StandardCharsets.UTF_8)));
                return;
            }
            jsonMapper.writeValue(outputStream, type, object);
        } catch (IOException e) {
            throw decorateWrite(object, e);
        }
    }

    /**
     * A {@link Produces} with JSON supported types.
     */
    @Documented
    @Retention(RUNTIME)
    @Target(ElementType.TYPE)
    @Inherited
    @Produces({
        MediaType.APPLICATION_JSON,
        MediaType.TEXT_JSON,
        MediaType.APPLICATION_HAL_JSON,
        MediaType.APPLICATION_JSON_GITHUB,
        MediaType.APPLICATION_JSON_FEED,
        MediaType.APPLICATION_JSON_PROBLEM,
        MediaType.APPLICATION_JSON_PATCH,
        MediaType.APPLICATION_JSON_MERGE_PATCH,
        MediaType.APPLICATION_JSON_SCHEMA
    })
    public @interface ProducesJson {
    }

    /**
     * A {@link Consumes} with JSON supported types.
     */
    @Documented
    @Retention(RUNTIME)
    @Target(ElementType.TYPE)
    @Inherited
    @Consumes({
        MediaType.APPLICATION_JSON,
        MediaType.TEXT_JSON,
        MediaType.APPLICATION_HAL_JSON,
        MediaType.APPLICATION_JSON_GITHUB,
        MediaType.APPLICATION_JSON_FEED,
        MediaType.APPLICATION_JSON_PROBLEM,
        MediaType.APPLICATION_JSON_PATCH,
        MediaType.APPLICATION_JSON_MERGE_PATCH,
        MediaType.APPLICATION_JSON_SCHEMA
    })
    public @interface ConsumesJson {
    }


}
