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
package io.micronaut.json.codec;

import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecConfiguration;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.json.JsonFeatures;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.runtime.ApplicationConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * A {@link MediaTypeCodec} for {@link JsonMapper} based implementations.
 *
 * @author Graeme Rocher
 * @author svishnyakov
 * @since 1.3.0
 * @deprecated Replaced with message body writers / readers API
 */
@Deprecated(since = "4.7")
@Experimental
public abstract class MapperMediaTypeCodec implements MediaTypeCodec {
    public static final String REGULAR_JSON_MEDIA_TYPE_CODEC_NAME = "json";

    protected final ApplicationConfiguration applicationConfiguration;
    protected final List<MediaType> additionalTypes;
    protected final CodecConfiguration codecConfiguration;
    protected final MediaType mediaType;

    private final BeanProvider<JsonMapper> mapperProvider;
    private volatile JsonMapper mapper;

    /**
     * @param mapperProvider To read/write JSON
     * @param applicationConfiguration The common application configurations
     * @param codecConfiguration The configuration for the codec
     * @param mediaType Client request/response media type
     */
    public MapperMediaTypeCodec(BeanProvider<JsonMapper> mapperProvider,
                                ApplicationConfiguration applicationConfiguration,
                                CodecConfiguration codecConfiguration,
                                MediaType mediaType) {
        this(mapperProvider,
            applicationConfiguration,
            codecConfiguration,
            mediaType,
            null);
    }

    /**
     * @param mapperProvider To read/write JSON
     * @param applicationConfiguration The common application configurations
     * @param codecConfiguration The configuration for the codec
     * @param mediaType Client request/response media type
     * @param additionalTypes Additional Media Types
     */
    public MapperMediaTypeCodec(BeanProvider<JsonMapper> mapperProvider,
                                ApplicationConfiguration applicationConfiguration,
                                CodecConfiguration codecConfiguration,
                                MediaType mediaType,
                                @Nullable List<MediaType> additionalTypes) {
        this.mapperProvider = mapperProvider;
        this.applicationConfiguration = applicationConfiguration;
        this.codecConfiguration = codecConfiguration;
        this.mediaType = mediaType;

        var mediaTypes = new HashSet<MediaType>();
        if (codecConfiguration != null) {
            mediaTypes.addAll(codecConfiguration.getAdditionalTypes());
        }
        if (additionalTypes != null) {
            mediaTypes.addAll(additionalTypes);
        }
        this.additionalTypes = new ArrayList<>(mediaTypes);
    }

    /**
     * @param mapper To read/write JSON
     * @param applicationConfiguration The common application configurations
     * @param codecConfiguration The configuration for the codec
     * @param mediaType Client request/response media type
     */
    public MapperMediaTypeCodec(JsonMapper mapper,
                                ApplicationConfiguration applicationConfiguration,
                                CodecConfiguration codecConfiguration,
                                MediaType mediaType) {
        this(() -> mapper, applicationConfiguration, codecConfiguration, mediaType);
        ArgumentUtils.requireNonNull("objectMapper", mapper);
        this.mapper = mapper;
    }

    /**
     * @return The object mapper
     */
    public JsonMapper getJsonMapper() {
        JsonMapper mapper = this.mapper;
        if (mapper == null) {
            synchronized (this) { // double check
                mapper = this.mapper;
                if (mapper == null) {
                    mapper = mapperProvider.get();
                    this.mapper = mapper;
                }
            }
        }
        return mapper;
    }

    /**
     * Create a copy of this codec with the given features. Should not be extended, extend {@link #cloneWithMapper}
     * instead.
     *
     * @param features The features to apply.
     * @return A new codec with the features applied, leaving this codec unchanged.
     */
    public MapperMediaTypeCodec cloneWithFeatures(JsonFeatures features) {
        return cloneWithMapper(getJsonMapper().cloneWithFeatures(features));
    }

    public final MapperMediaTypeCodec cloneWithViewClass(Class<?> viewClass) {
        return cloneWithMapper(getJsonMapper().cloneWithViewClass(viewClass));
    }

    protected abstract MapperMediaTypeCodec cloneWithMapper(JsonMapper mapper);

    @Override
    public Collection<MediaType> getMediaTypes() {
        var mediaTypes = new ArrayList<MediaType>();
        mediaTypes.add(mediaType);
        mediaTypes.addAll(additionalTypes);
        return mediaTypes;
    }

    @Override
    public boolean supportsType(Class<?> type) {
        return !(CharSequence.class.isAssignableFrom(type));
    }

    @Override
    public <T> T decode(Argument<T> type, InputStream inputStream) throws CodecException {
        try {
            return getJsonMapper().readValue(inputStream, type);
        } catch (IOException e) {
            throw new CodecException("Error decoding JSON stream for type [" + type.getName() + "]: " + e.getMessage(), e);
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
            JsonMapper om = getJsonMapper();
            return om.readValueFromTree(node, type);
        } catch (IOException e) {
            throw new CodecException("Error decoding JSON stream for type [" + type.getName() + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> T decode(Argument<T> type, ByteBuffer<?> buffer) throws CodecException {
        try {
            if (CharSequence.class.isAssignableFrom(type.getType())) {
                return (T) buffer.toString(applicationConfiguration.getDefaultCharset());
            } else {
                return getJsonMapper().readValue(buffer, type);
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
            } else {
                return getJsonMapper().readValue(bytes, type);
            }
        } catch (IOException e) {
            throw new CodecException("Error decoding stream for type [" + type.getType() + "]: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("Duplicates")
    @Override
    public <T> T decode(Argument<T> type, String data) throws CodecException {
        try {
            return getJsonMapper().readValue(data, type);
        } catch (IOException e) {
            throw new CodecException("Error decoding JSON stream for type [" + type.getName() + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> void encode(T object, OutputStream outputStream) throws CodecException {
        try {
            getJsonMapper().writeValue(outputStream, object);
        } catch (IOException e) {
            throw new CodecException("Error encoding object [" + object + "] to JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> void encode(@NonNull Argument<T> type, @NonNull T object, @NonNull OutputStream outputStream) throws CodecException {
        try {
            getJsonMapper().writeValue(outputStream, type, object);
        } catch (IOException e) {
            throw new CodecException("Error encoding object [" + object + "] to JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> byte[] encode(T object) throws CodecException {
        try {
            if (object instanceof byte[] bytes) {
                return bytes;
            } else {
                return getJsonMapper().writeValueAsBytes(object);
            }
        } catch (IOException e) {
            throw new CodecException("Error encoding object [" + object + "] to JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> byte[] encode(@NonNull Argument<T> type, T object) throws CodecException {
        try {
            if (object instanceof byte[] bytes) {
                return bytes;
            } else {
                return getJsonMapper().writeValueAsBytes(type, object);
            }
        } catch (IOException e) {
            throw new CodecException("Error encoding object [" + object + "] to JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public <T, B> ByteBuffer<B> encode(T object, ByteBufferFactory<?, B> allocator) throws CodecException {
        if (object instanceof byte[] bytes) {
            return allocator.copiedBuffer(bytes);
        }
        ByteBuffer<B> buffer = allocator.buffer();
        try {
            OutputStream outputStream = buffer.toOutputStream();
            encode(object, outputStream);
        } catch (Throwable t) {
            if (buffer instanceof ReferenceCounted counted) {
                counted.release();
            }
            throw t;
        }
        return buffer;
    }

    @Override
    public <T, B> @NonNull ByteBuffer<B> encode(@NonNull Argument<T> type, T object, @NonNull ByteBufferFactory<?, B> allocator) throws CodecException {
        if (object instanceof byte[] bytes) {
            return allocator.copiedBuffer(bytes);
        }
        ByteBuffer<B> buffer = allocator.buffer();
        try {
            OutputStream outputStream = buffer.toOutputStream();
            encode(type, object, outputStream);
            return buffer;
        } catch (Throwable t) {
            if (buffer instanceof ReferenceCounted counted) {
                counted.release();
            }
            throw t;
        }
    }
}
