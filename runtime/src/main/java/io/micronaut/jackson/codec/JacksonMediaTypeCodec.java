/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.jackson.codec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecConfiguration;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.jackson.JacksonConfiguration;
import io.micronaut.runtime.ApplicationConfiguration;

import javax.inject.Provider;

/**
 * A {@link MediaTypeCodec} Jackson based implementations.
 *
 * @author Graeme Rocher
 * @author svishnyakov
 * @since 1.3.0
 */
public abstract class JacksonMediaTypeCodec implements MediaTypeCodec {

    protected final ApplicationConfiguration applicationConfiguration;
    protected final List<MediaType> additionalTypes;
    protected final CodecConfiguration codecConfiguration;
    protected final MediaType mediaType;
    private final Provider<ObjectMapper> objectMapperProvider;
    private ObjectMapper objectMapper;

    /**
     * @param objectMapperProvider     To read/write JSON
     * @param applicationConfiguration The common application configurations
     * @param codecConfiguration       The configuration for the codec
     * @param mediaType                Client request/response media type
     */
    public JacksonMediaTypeCodec(Provider<ObjectMapper> objectMapperProvider,
                                 ApplicationConfiguration applicationConfiguration,
                                 CodecConfiguration codecConfiguration,
                                 MediaType mediaType) {
        this.objectMapperProvider = objectMapperProvider;
        this.applicationConfiguration = applicationConfiguration;
        this.codecConfiguration = codecConfiguration;
        this.mediaType = mediaType;
        if (codecConfiguration != null) {
            this.additionalTypes = codecConfiguration.getAdditionalTypes();
        } else {
            this.additionalTypes = Collections.emptyList();
        }
    }

    /**
     * @param objectMapper             To read/write JSON
     * @param applicationConfiguration The common application configurations
     * @param codecConfiguration       The configuration for the codec
     * @param mediaType                Client request/response media type
     */
    public JacksonMediaTypeCodec(ObjectMapper objectMapper,
                                 ApplicationConfiguration applicationConfiguration,
                                 CodecConfiguration codecConfiguration,
                                 MediaType mediaType) {
        this(() -> objectMapper, applicationConfiguration, codecConfiguration, mediaType);
        ArgumentUtils.requireNonNull("objectMapper", objectMapper);
        this.objectMapper = objectMapper;
    }

    /**
     * @return The object mapper
     */
    public ObjectMapper getObjectMapper() {
        ObjectMapper objectMapper = this.objectMapper;
        if (objectMapper == null) {
            synchronized (this) { // double check
                objectMapper = this.objectMapper;
                if (objectMapper == null) {
                    objectMapper = objectMapperProvider.get();
                    this.objectMapper = objectMapper;
                }
            }
        }
        return objectMapper;
    }

    /**
     * Create a new codec with the provided features.
     *
     * @param jacksonFeatures The jackson features
     * @return The new codec
     */
    public abstract JacksonMediaTypeCodec cloneWithFeatures(JacksonFeatures jacksonFeatures);

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
                return getObjectMapper().readValue(inputStream, javaType);
            } else {
                return getObjectMapper().readValue(inputStream, type.getType());
            }
        } catch (IOException e) {
            throw new CodecException("Error decoding JSON stream for type [" + type.getName() + "]: " + e.getMessage(), e);
        }
    }

    /**
     * Decodes the given JSON node.
     *
     * @param type The type
     * @param node The Json Node
     * @param <T>  The generic type
     * @return The decoded object
     * @throws CodecException When object cannot be decoded
     */
    public <T> T decode(Argument<T> type, JsonNode node) throws CodecException {
        try {
            return getObjectMapper().treeToValue(node, type.getType());
        } catch (IOException e) {
            throw new CodecException("Error decoding JSON stream for type [" + type.getName() + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> T decode(Argument<T> type, ByteBuffer<?> buffer) throws CodecException {
        try {
            if (CharSequence.class.isAssignableFrom(type.getType())) {
                return (T) buffer.toString(applicationConfiguration.getDefaultCharset());
            } else if (type.hasTypeVariables()) {
                JavaType javaType = constructJavaType(type);
                return getObjectMapper().readValue(buffer.toByteArray(), javaType);
            } else {
                return getObjectMapper().readValue(buffer.toByteArray(), type.getType());
            }
        } catch (IOException e) {
            throw new CodecException("Error decoding stream for type [" + type.getType() + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> T decode(Argument<T> type, byte[] bytes) throws CodecException {
        try {
            if (CharSequence.class.isAssignableFrom(type.getType())) {
                return (T) new String(bytes, applicationConfiguration.getDefaultCharset());
            } else if (type.hasTypeVariables()) {
                JavaType javaType = constructJavaType(type);
                return getObjectMapper().readValue(bytes, javaType);
            } else {
                return getObjectMapper().readValue(bytes, type.getType());
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
                return getObjectMapper().readValue(data, javaType);
            } else {
                return getObjectMapper().readValue(data, type.getType());
            }
        } catch (IOException e) {
            throw new CodecException("Error decoding JSON stream for type [" + type.getName() + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> void encode(T object, OutputStream outputStream) throws CodecException {
        try {
            getObjectMapper().writeValue(outputStream, object);
        } catch (IOException e) {
            throw new CodecException("Error encoding object [" + object + "] to JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> byte[] encode(T object) throws CodecException {
        try {
            if (object instanceof byte[]) {
                return (byte[]) object;
            } else {
                return getObjectMapper().writeValueAsBytes(object);
            }
        } catch (JsonProcessingException e) {
            throw new CodecException("Error encoding object [" + object + "] to JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public <T, B> ByteBuffer<B> encode(T object, ByteBufferFactory<?, B> allocator) throws CodecException {
        byte[] bytes = encode(object);
        return allocator.copiedBuffer(bytes);
    }

    private <T> JavaType constructJavaType(Argument<T> type) {
        TypeFactory typeFactory = getObjectMapper().getTypeFactory();
        return JacksonConfiguration.constructType(type, typeFactory);
    }

}
