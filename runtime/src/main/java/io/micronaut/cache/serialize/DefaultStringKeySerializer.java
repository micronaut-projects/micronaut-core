/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.cache.serialize;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.serialize.ObjectSerializer;
import io.micronaut.core.serialize.exceptions.SerializationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Optional;

/**
 * <p>The default key serializer used by caches that require serializing the keys as strings. Builds a key from the
 * configured cache name and String conversion of the object as dictated by {@link ConversionService}.</p>
 * <p>
 * <p>Note this implementation does not support deserialization</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultStringKeySerializer implements ObjectSerializer {
    private final ConversionService<?> conversionService;
    private final String cacheName;
    private final Charset charset;

    /**
     * Construct a default serializer for given parameters.
     *
     * @param cacheName         The cache key or name
     * @param charset           The charset used for serialization and de-serializing instance from the to/from cache
     * @param conversionService To convert value/object from the cache to String
     */
    public DefaultStringKeySerializer(String cacheName, Charset charset, ConversionService<?> conversionService) {
        this.cacheName = cacheName;
        this.charset = charset;
        this.conversionService = conversionService;
    }

    @Override
    public Optional<byte[]> serialize(Object object) throws SerializationException {
        if (object == null) {
            return Optional.empty();
        }
        return Optional.of(toByteArray(object));
    }

    @Override
    public void serialize(Object object, OutputStream outputStream) throws SerializationException {
        byte[] bytes = toByteArray(object);
        try {
            outputStream.write(bytes);
        } catch (IOException e) {
            throw new SerializationException("Error serializing object [" + object + "]: " + e.getMessage(), e);
        }
    }

    private byte[] toByteArray(Object object) {
        String str = cacheName + ":" + conversionService.convert(object, String.class).orElse(String.valueOf(object.hashCode()));
        return str.getBytes(charset);
    }

    @Override
    public <T> Optional<T> deserialize(InputStream inputStream, Class<T> requiredType) throws SerializationException {
        throw new UnsupportedOperationException("Hash code based key deserialization not supported");
    }
}
