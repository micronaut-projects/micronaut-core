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
package io.micronaut.core.annotation;

import io.micronaut.core.naming.Named;

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

    private final String name;
    private final Class<T> theClass;

    /**
     * Constructs a class value for the type that is not present.
     *
     * @param name The name of the type.
     */
    @UsedByGeneratedCode
    public AnnotationClassValue(String name) {
        this.name = name.intern();
        this.theClass = null;
    }

    /**
     * Constructs a class value for a type that is present.
     *
     * @param theClass The type
     */
    @UsedByGeneratedCode
    public AnnotationClassValue(Class<T> theClass) {
        this.name = theClass.getName().intern();
        this.theClass = theClass;
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
