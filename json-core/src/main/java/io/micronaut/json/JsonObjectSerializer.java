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
package io.micronaut.json;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.serialize.ObjectSerializer;
import io.micronaut.core.serialize.exceptions.SerializationException;
import io.micronaut.core.type.Argument;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;

/**
 * An implementation of the {@link ObjectSerializer} interface for Jackson.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Experimental
public class JsonObjectSerializer implements ObjectSerializer {
    private final JsonMapper jsonMapper;

    /**
     * @param jsonMapper To read/write JSON
     */
    public JsonObjectSerializer(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Optional<byte[]> serialize(Object object) throws SerializationException {
        try {
            return Optional.ofNullable(jsonMapper.writeValueAsBytes(object));
        } catch (IOException e) {
            throw new SerializationException("Error serializing object to JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public void serialize(Object object, OutputStream outputStream) throws SerializationException {
        try {
            jsonMapper.writeValue(outputStream, object);
        } catch (IOException e) {
            throw new SerializationException("Error serializing object to JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> Optional<T> deserialize(byte[] bytes, Class<T> requiredType) throws SerializationException {
        try {
            return Optional.ofNullable(jsonMapper.readValue(bytes, Argument.of(requiredType)));
        } catch (IOException e) {
            throw new SerializationException("Error deserializing object from JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> Optional<T> deserialize(InputStream inputStream, Class<T> requiredType) throws SerializationException {
        try {
            return Optional.ofNullable(jsonMapper.readValue(inputStream, Argument.of(requiredType)));
        } catch (IOException e) {
            throw new SerializationException("Error deserializing object from JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> Optional<T> deserialize(byte[] bytes, Argument<T> requiredType) throws SerializationException {
        try {
            return Optional.ofNullable(jsonMapper.readValue(bytes, requiredType));
        } catch (IOException e) {
            throw new SerializationException("Error deserializing object from JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> Optional<T> deserialize(InputStream inputStream, Argument<T> requiredType) throws SerializationException {
        try {
            return Optional.ofNullable(jsonMapper.readValue(inputStream, requiredType));
        } catch (IOException e) {
            throw new SerializationException("Error deserializing object from JSON: " + e.getMessage(), e);
        }
    }
}
