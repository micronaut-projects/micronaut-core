/*
 * Copyright 2017 original authors
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
package org.particleframework.configuration.lettuce.cache;

import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.serialize.ObjectSerializer;
import org.particleframework.core.serialize.exceptions.SerializationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;

/**
 * <p>The default key serializer used by {@link RedisCache}. Builds a key from the configured cache name and the object hash code.</p>
 *
 * <p>Note this implementation does not support deserialization</p>
 *
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultKeySerializer implements ObjectSerializer {
    private final RedisCacheConfiguration redisCacheConfiguration;
    private final ConversionService<?> conversionService;

    public DefaultKeySerializer(RedisCacheConfiguration redisCacheConfiguration, ConversionService<?> conversionService) {
        this.redisCacheConfiguration = redisCacheConfiguration;
        this.conversionService = conversionService;
    }

    @Override
    public Optional<byte[]> serialize(Object object) throws SerializationException {
        if(object == null) return Optional.empty();
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
        String str = redisCacheConfiguration.getCacheName() + ":" + conversionService.convert(object, String.class).orElse(String.valueOf(object.hashCode()));
        return str.getBytes(redisCacheConfiguration.getCharset());
    }

    @Override
    public <T> Optional<T> deserialize(InputStream inputStream, Class<T> requiredType) throws SerializationException {
        throw new UnsupportedOperationException("Hash code based key deserialization not supported");
    }
}
