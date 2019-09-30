/*
 *
 *  * Copyright 2017-2019 original authors
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */
package io.micronaut.xml.client.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecConfiguration;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.jackson.JacksonConfiguration;
import io.micronaut.runtime.ApplicationConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A {@link MediaTypeCodec} for JSON and Jackson.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class AbstractJacksonMediaTypeCodec implements MediaTypeCodec {

    private final ObjectMapper objectMapper;
    private final ApplicationConfiguration applicationConfiguration;
    private final List<MediaType> additionalTypes;
    private final MediaType mediaType;

    /**
     * @param objectMapper             To read/write JSON
     * @param applicationConfiguration The common application configurations
     * @param codecConfiguration       The configuration for the codec
     * @param mediaType                Client request/response media type
     */
    public AbstractJacksonMediaTypeCodec(ObjectMapper objectMapper,
                                         ApplicationConfiguration applicationConfiguration,
                                         CodecConfiguration codecConfiguration,
                                         MediaType mediaType) {
        this.objectMapper = objectMapper;
        this.applicationConfiguration = applicationConfiguration;
        this.mediaType = mediaType;
        if (codecConfiguration != null) {
            this.additionalTypes = codecConfiguration.getAdditionalTypes();
        } else {
            this.additionalTypes = Collections.emptyList();
        }
    }

    /**
     * @return The object mapper
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    @Override
    public Collection<MediaType> getMediaTypes() {
        List<MediaType> mediaTypes = new ArrayList<>();
        mediaTypes.add(mediaType);
        mediaTypes.addAll(additionalTypes);
        return mediaTypes;
    }

    @Override
    public boolean supportsType(Class<?> type) {
        return !(CharSequence.class.isAssignableFrom(type));
    }

    @SuppressWarnings("Duplicates")
    @Override
    public <T> T decode(Argument<T> type, InputStream inputStream) throws CodecException {
        try {
            if (type.hasTypeVariables()) {
                JavaType javaType = constructJavaType(type);
                return objectMapper.readValue(inputStream, javaType);
            } else {
                return objectMapper.readValue(inputStream, type.getType());
            }
        } catch (IOException e) {
            throw new CodecException("Error decoding XML stream for type [" + type.getName() + "]: " + e.getMessage());
        }
    }

    /**
     * Decodes the given JSON node.
     *
     * @param type The type
     * @param node The Json Node
     * @param <T> The generic type
     * @return The decoded object
     * @throws CodecException When object cannot be decoded
     */
    public <T> T decode(Argument<T> type, JsonNode node) throws CodecException {
        try {
            return objectMapper.treeToValue(node, type.getType());
        } catch (IOException e) {
            throw new CodecException("Error decoding XML stream for type [" + type.getName() + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> T decode(Argument<T> type, ByteBuffer<?> buffer) throws CodecException {
        try {
            if (CharSequence.class.isAssignableFrom(type.getType())) {
                return (T) buffer.toString(applicationConfiguration.getDefaultCharset());
            } else if (type.hasTypeVariables()) {
                JavaType javaType = constructJavaType(type);
                return objectMapper.readValue(buffer.toByteArray(), javaType);
            } else {
                return objectMapper.readValue(buffer.toByteArray(), type.getType());
            }
        } catch (IOException e) {
            throw new CodecException("Error decoding stream for type [" + type.getType() + "]: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("Duplicates")
    @Override
    public <T> T decode(Argument<T> type, String data) throws CodecException {
        try {
            if (type.hasTypeVariables()) {
                JavaType javaType = constructJavaType(type);
                return objectMapper.readValue(data, javaType);
            } else {
                return objectMapper.readValue(data, type.getType());
            }
        } catch (IOException e) {
            throw new CodecException("Error decoding XML stream for type [" + type.getName() + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> void encode(T object, OutputStream outputStream) throws CodecException {
        try {
            objectMapper.writeValue(outputStream, object);
        } catch (IOException e) {
            throw new CodecException("Error encoding object [" + object + "] to XML: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> byte[] encode(T object) throws CodecException {
        try {
            if (object instanceof byte[]) {
                return (byte[]) object;
            } else {
                return objectMapper.writeValueAsBytes(object);
            }
        } catch (JsonProcessingException e) {
            throw new CodecException("Error encoding object [" + object + "] to XML: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> ByteBuffer encode(T object, ByteBufferFactory allocator) throws CodecException {
        byte[] bytes = encode(object);
        return allocator.copiedBuffer(bytes);
    }

    private <T> JavaType constructJavaType(Argument<T> type) {
        TypeFactory typeFactory = objectMapper.getTypeFactory();
        return JacksonConfiguration.constructType(type, typeFactory);
    }

}
