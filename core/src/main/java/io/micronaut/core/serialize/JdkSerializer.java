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
package io.micronaut.core.serialize;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.serialize.exceptions.SerializationException;
import io.micronaut.core.type.Argument;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;
import java.util.Optional;

/**
 * A {@link ObjectSerializer} that uses JDK serialization.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public final class JdkSerializer implements ObjectSerializer {

    /**
     * System property that, when set to a non-blank {@link ObjectInputFilter} pattern (as accepted by
     * {@link ObjectInputFilter.Config#createFilter(String)}), installs a JEP-290 deserialization filter
     * on every stream created by this serializer. Unset by default, which preserves the previous
     * (unfiltered) behaviour.
     *
     * @since 5.2.0
     */
    public static final String SERIAL_FILTER_PROPERTY = "micronaut.serializer.jdk.serial-filter";

    private final ConversionService conversionService;
    private final @Nullable ObjectInputFilter objectInputFilter;

    /**
     * @param conversionService The conversion service
     */
    public JdkSerializer(ConversionService conversionService) {
        this(conversionService, resolveDefaultFilter());
    }

    /**
     * @param conversionService The conversion service
     * @param objectInputFilter The {@link ObjectInputFilter} to apply when deserializing, or {@code null} to apply none
     * @since 5.2.0
     */
    public JdkSerializer(ConversionService conversionService, @Nullable ObjectInputFilter objectInputFilter) {
        this.conversionService = conversionService;
        this.objectInputFilter = objectInputFilter;
    }

    /**
     * Default constructor.
     */
    public JdkSerializer() {
        this(ConversionService.SHARED);
    }

    private static @Nullable ObjectInputFilter resolveDefaultFilter() {
        String pattern = System.getProperty(SERIAL_FILTER_PROPERTY);
        if (pattern == null || pattern.isBlank()) {
            return null;
        }
        return ObjectInputFilter.Config.createFilter(pattern);
    }

    @Override
    public void serialize(@Nullable Object object, OutputStream outputStream) throws SerializationException {
        try {
            try (ObjectOutputStream objectOut = createObjectOutput(outputStream)) {
                objectOut.writeObject(object);
                objectOut.flush();
            }
        } catch (IOException e) {
            throw new SerializationException("I/O error occurred during serialization: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> Optional<T> deserialize(@Nullable InputStream inputStream, Class<T> requiredType) throws SerializationException {
        if (inputStream == null) {
            return Optional.empty();
        }
        try {
            try (ObjectInputStream objectIn = createObjectInput(inputStream, requiredType)) {
                try {
                    Object readObject = objectIn.readObject();

                    return conversionService.convert(readObject, requiredType);
                } catch (ClassCastException cce) {
                    throw new SerializationException("Invalid type deserialized from stream: " + cce.getMessage(), cce);
                } catch (ClassNotFoundException e) {
                    throw new SerializationException("Type not found deserializing from stream: " + e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            throw new SerializationException("I/O error occurred during deserialization: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> Optional<T> deserialize(@Nullable InputStream inputStream, Argument<T> requiredType) throws SerializationException {
        if (inputStream == null) {
            return Optional.empty();
        }
        try {
            try (ObjectInputStream objectIn = createObjectInput(inputStream, requiredType.getType())) {
                try {
                    Object readObject = objectIn.readObject();

                    return conversionService.convert(readObject, requiredType);
                } catch (ClassCastException cce) {
                    throw new SerializationException("Invalid type deserialized from stream: " + cce.getMessage(), cce);
                } catch (ClassNotFoundException e) {
                    throw new SerializationException("Type not found deserializing from stream: " + e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            throw new SerializationException("I/O error occurred during deserialization: " + e.getMessage(), e);
        }
    }

    /**
     * @param outputStream The output stream
     * @return A new {@link ObjectOutputStream}
     * @throws IOException if there is an error
     */
    private ObjectOutputStream createObjectOutput(OutputStream outputStream) throws IOException {
        return new ObjectOutputStream(outputStream);
    }

    /**
     * @param inputStream  The input stream
     * @param requiredType The required type
     * @return A {@link ObjectOutputStream}
     * @throws IOException if there is an error
     */
    private ObjectInputStream createObjectInput(InputStream inputStream, Class<?> requiredType) throws IOException {
        ObjectInputStream objectInput = new ObjectInputStream(inputStream) {
            @Override
            protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
                Optional<Class<?>> aClass = ClassUtils.forName(desc.getName(), requiredType.getClassLoader());
                if (aClass.isPresent()) {
                    return aClass.get();
                }
                return super.resolveClass(desc);
            }
        };
        if (objectInputFilter != null) {
            objectInput.setObjectInputFilter(objectInputFilter);
        }
        return objectInput;
    }
}
