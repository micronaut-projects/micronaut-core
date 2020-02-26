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
package io.micronaut.core.serialize;

import io.micronaut.core.serialize.exceptions.SerializationException;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;

/**
 * Interface for implementations capable of serializing objects.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ObjectSerializer {

    /**
     * The default JDK serializer.
     */
    ObjectSerializer JDK = new JdkSerializer();

    /**
     * Serialize the given object to a byte[].
     *
     * @param object       The object to serialize
     * @param outputStream The output stream
     * @throws SerializationException if there is a serialization problem
     */
    void serialize(@Nullable Object object, OutputStream outputStream) throws SerializationException;

    /**
     * Deserialize the given object to bytes.
     *
     * @param inputStream  The input stream
     * @param requiredType The required type
     * @param <T>          The required generic type
     * @return An {@link Optional} of the object
     * @throws SerializationException if there is a serialization problem
     */
    <T> Optional<T> deserialize(@Nullable InputStream inputStream, Class<T> requiredType) throws SerializationException;

    /**
     * Serialize the given object to a byte[].
     *
     * @param object The object to serialize
     * @return An optional of the bytes of the object
     * @throws SerializationException if there is a serialization problem
     */
    default Optional<byte[]> serialize(@Nullable Object object) throws SerializationException {
        if (object == null) {
            return Optional.empty();
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        serialize(object, outputStream);
        return Optional.of(outputStream.toByteArray());
    }

    /**
     * Deserialize the given object to bytes.
     *
     * @param bytes        The byte array
     * @param requiredType The required type
     * @param <T>          The required generic type
     * @return An {@link Optional} of the object
     * @throws SerializationException if there is a serialization problem
     */
    default <T> Optional<T> deserialize(@Nullable byte[] bytes, Class<T> requiredType) throws SerializationException {
        if (bytes == null) {
            return Optional.empty();
        }
        try {
            try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
                return deserialize(input, requiredType);
            }
        } catch (IOException e) {
            throw new SerializationException("I/O error occurred during deserialization: " + e.getMessage(), e);
        }
    }

    /**
     * Deserialize the given object to bytes.
     *
     * @param bytes The byte array
     * @return An {@link Optional} of the object
     * @throws SerializationException if there is a serialization problem
     */
    default Optional<Object> deserialize(@Nullable byte[] bytes) throws SerializationException {
        if (bytes == null) {
            return Optional.empty();
        }
        return deserialize(bytes, Object.class);
    }
}
