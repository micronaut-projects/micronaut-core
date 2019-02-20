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

package io.micronaut.core.beans;

import io.micronaut.core.annotation.AnnotationMetadataDelegate;
import io.micronaut.core.naming.Named;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Represents a bean property.
 *
 * @param <B> The bean type
 * @param <T> The bean property type
 * @author graemerocher
 * @since 1.1
 */
public interface BeanProperty<B, T> extends Named, AnnotationMetadataDelegate {

    /**
     * @return The declaring bean introspection.
     */
    @Nonnull BeanIntrospection<B> getDeclaringBean();

    /**
     * Read the bean value.
     * @param bean The bean to read from
     * @return The value
     * @throws IllegalArgumentException If the bean instance if not of the correct type
     */
    @Nullable T read(@Nonnull B bean);

    /**
     * Write the bean value.
     * @param bean The bean
     * @param value The value to write
     * @throws IllegalArgumentException If either the bean type or value type are not correct
     */
    default void write(@Nonnull B bean, @Nullable T value) {
        if (isReadOnly()) {
            throw new UnsupportedOperationException("Cannot write read-only property");
        } else {
            throw new UnsupportedOperationException("Write method unimplemented");
        }
    }

    /**
     * @return The property type.
     */
    @Nonnull Class<T> getType();

    /**
     * @return Whether the property is read-only
     */
    default boolean isReadOnly() {
        return false;
    }

    /**
     * The declaring type of the property.
     * @return The type
     */
    default Class<B> getDeclaringType() {
        return getDeclaringBean().getBeanType();
    }
}
