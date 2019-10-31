/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.jackson.serialize;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.core.serialize.ObjectSerializer;
import io.micronaut.core.serialize.exceptions.SerializationException;

import javax.inject.Singleton;
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
public class JacksonObjectSerializer implements ObjectSerializer {

    private final ObjectMapper objectMapper;

    /**
     * @param objectMapper To read/write JSON
     */
    public JacksonObjectSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<byte[]> serialize(Object object) throws SerializationException {
        try {
            return Optional.ofNullable(objectMapper.writeValueAsBytes(object));
        } catch (JsonProcessingException e) {
            throw new SerializationException("Error serializing object to JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public void serialize(Object object, OutputStream outputStream) throws SerializationException {
        try {
            objectMapper.writeValue(outputStream, object);
        } catch (IOException e) {
            throw new SerializationException("Error serializing object to JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> Optional<T> deserialize(byte[] bytes, Class<T> requiredType) throws SerializationException {
        try {
            return Optional.ofNullable(objectMapper.readValue(bytes, requiredType));
        } catch (IOException e) {
            throw new SerializationException("Error deserializing object from JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> Optional<T> deserialize(InputStream inputStream, Class<T> requiredType) throws SerializationException {
        try {
            return Optional.ofNullable(objectMapper.readValue(inputStream, requiredType));
        } catch (IOException e) {
            throw new SerializationException("Error deserializing object from JSON: " + e.getMessage(), e);
        }
    }
}
