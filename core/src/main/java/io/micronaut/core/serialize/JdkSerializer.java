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
import io.micronaut.core.reflect.ReflectionUtils;
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
import java.lang.reflect.Proxy;
import java.util.Optional;

/**
 * A {@link ObjectSerializer} that uses JDK serialization.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public final class JdkSerializer implements ObjectSerializer {

    private final ConversionService conversionService;

    /**
     * @param conversionService The conversion service
     */
    public JdkSerializer(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    /**
     * Default constructor.
     */
    public JdkSerializer() {
        this(ConversionService.SHARED);
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
                    validateRequiredType(readObject, requiredType);

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
                    validateRequiredType(readObject, requiredType.getType());

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
        ObjectInputStream objectInputStream = new ObjectInputStream(inputStream) {
            @Override
            protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
                try {
                    return Class.forName(desc.getName(), false, resolveClassLoader(requiredType));
                } catch (ClassNotFoundException | NoClassDefFoundError e) {
                    return super.resolveClass(desc);
                }
            }
        };
        ObjectInputFilter requiredTypeFilter = new RequiredTypeObjectInputFilter(requiredType);
        ObjectInputFilter inheritedFilter = objectInputStream.getObjectInputFilter();
        objectInputStream.setObjectInputFilter(inheritedFilter == null
            ? requiredTypeFilter
            : ObjectInputFilter.merge(inheritedFilter, requiredTypeFilter));
        return objectInputStream;
    }

    private static @Nullable ClassLoader resolveClassLoader(Class<?> requiredType) {
        ClassLoader classLoader = requiredType.getClassLoader();
        if (classLoader == null) {
            classLoader = Thread.currentThread().getContextClassLoader();
        }
        if (classLoader == null) {
            classLoader = ClassLoader.getSystemClassLoader();
        }
        return classLoader;
    }

    private static void validateRequiredType(@Nullable Object object, Class<?> requiredType) {
        Class<?> wrapperType = ReflectionUtils.getWrapperType(requiredType);
        if (object != null && !wrapperType.isInstance(object)) {
            throw new SerializationException(
                "Invalid type deserialized from stream. Expected: " + requiredType.getName() +
                    ", actual: " + object.getClass().getName()
            );
        }
    }

    private static final class RequiredTypeObjectInputFilter implements ObjectInputFilter {
        private static final Module JAVA_BASE = Object.class.getModule();

        private final Class<?> requiredType;
        private boolean rootTypeChecked;

        private RequiredTypeObjectInputFilter(Class<?> requiredType) {
            this.requiredType = ReflectionUtils.getWrapperType(requiredType);
        }

        @Override
        public Status checkInput(FilterInfo filterInfo) {
            Class<?> serialClass = filterInfo.serialClass();
            if (rootTypeChecked || serialClass == null || filterInfo.depth() != 1) {
                return Status.UNDECIDED;
            }
            if (serialClass.isInterface() || serialClass == Proxy.class) {
                return Status.UNDECIDED;
            }
            if (requiredType.isAssignableFrom(serialClass) || isJavaBaseSerializationProxy(serialClass)) {
                rootTypeChecked = true;
                return Status.UNDECIDED;
            }
            return Status.REJECTED;
        }

        private boolean isJavaBaseSerializationProxy(Class<?> serialClass) {
            return requiredType.getModule() == JAVA_BASE &&
                serialClass.getModule() == JAVA_BASE &&
                requiredType.getPackageName().equals(serialClass.getPackageName());
        }
    }
}
