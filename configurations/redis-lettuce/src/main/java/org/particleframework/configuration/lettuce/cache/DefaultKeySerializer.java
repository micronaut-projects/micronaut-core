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

    public DefaultKeySerializer(RedisCacheConfiguration redisCacheConfiguration) {
        this.redisCacheConfiguration = redisCacheConfiguration;
    }

    @Override
    public void serialize(Object object, OutputStream outputStream) throws SerializationException {
        String str = redisCacheConfiguration.getCacheName() + ":" + (object instanceof CharSequence ? object.toString() : object.hashCode());
        try {
            outputStream.write(str.getBytes(redisCacheConfiguration.getCharset()));
        } catch (IOException e) {
            throw new SerializationException("Error serializing object [" + object + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> Optional<T> deserialize(InputStream inputStream, Class<T> requiredType) throws SerializationException {
        throw new UnsupportedOperationException("Hash code based key deserialization not supported");
    }
}
