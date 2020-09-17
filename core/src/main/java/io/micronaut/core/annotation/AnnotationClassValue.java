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
package io.micronaut.core.annotation;

import io.micronaut.core.naming.Named;
import io.micronaut.core.util.ArgumentUtils;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.Optional;

/**
 * An annotation class value is a reference to a class in annotation metadata. The class may or may not be present
 * on the classpath. If it is present then the {@link #getType()} method will return a non-empty optional.
 *
 * @param <T> The generic type of the underlying class
 * @author graemerocher
 * @since 1.0
 */
@UsedByGeneratedCode
public final class AnnotationClassValue<T> implements CharSequence, Named {

    /**
     * An empty array of class values.
     */
    public static final AnnotationClassValue<?>[] EMPTY_ARRAY = new AnnotationClassValue[0];

    private final String name;
    private final Class<T> theClass;
    private final T instance;
    private final boolean instantiated;

    /**
     * Constructs a class value for the type that is not present.
     *
     * @param name The name of the type.
     */
    @UsedByGeneratedCode
    @Internal
    public AnnotationClassValue(String name) {
        this(name, false);
    }

    /**
     * Constructs a class value for a type that is present.
     *
     * @param theClass The type
     */
    @UsedByGeneratedCode
    public AnnotationClassValue(Class<T> theClass) {
        this.name = theClass.getName();
        this.theClass = theClass;
        this.instantiated = false;
        this.instance = null;
    }

    /**
     * Constructs a class value for a type that is present.
     *
     * @param name the class name
     * @param instantiated Whether at runtime an instance should be instantiated
     */
    @UsedByGeneratedCode
    @Internal
    public AnnotationClassValue(@NonNull String name, boolean instantiated) {
        ArgumentUtils.requireNonNull("name", name);
        this.name = name;
        this.theClass = null;
        this.instance = null;
        this.instantiated = instantiated;
    }

    /**
     * Constructs a class value for a type that is present.
     *
     * @param instance The instnace
     * @since 1.1
     */
    @SuppressWarnings("unchecked")
    @UsedByGeneratedCode
    public AnnotationClassValue(@NonNull T instance) {
        ArgumentUtils.requireNonNull("instance", instance);
        this.theClass = (Class<T>) instance.getClass();
        this.name = theClass.getName();
        this.instance = instance;
        this.instantiated = true;
    }

    /**
     * Returns the backing instance if there is one. Note this method will
     * not attempt to instantiate the class via reflection and is designed for use
     * via byte code generation.
     *
     * @return The instance
     * @since 1.1
     */
    public @NonNull Optional<T> getInstance() {
        return Optional.ofNullable(instance);
    }

    /**
     * Return whether the class value is instantiated. Normally this is the same as using isPresent on {@link #getInstance()}, except at compilation time where instances are not instantiated.
     *
     * @return Whether this class value is instantiated
     * @since 1.1
     */
    public boolean isInstantiated() {
        return instantiated || getInstance().isPresent();
    }

    /**
     * The type if present on the classpath.
     *
     * @return The type
     */
    public Optional<Class<T>> getType() {
        return Optional.ofNullable(theClass);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int length() {
        return name.length();
    }

    @Override
    public char charAt(int index) {
        return name.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return name.subSequence(start, end);
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AnnotationClassValue<?> that = (AnnotationClassValue<?>) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
