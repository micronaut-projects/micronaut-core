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
package io.micronaut.validation.validator;

import io.micronaut.core.beans.BeanIntrospection;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.validation.ConstraintViolation;
import java.util.Set;

/**
 * Extended version of the {@link javax.validation.Valid} interface for Micronaut's implementation.
 *
 * <p>The {@link #getConstraintsForClass(Class)} method is not supported by the implementation.</p>
 *
 * @author graemerocher
 * @since 1.2
 */
public interface Validator extends javax.validation.Validator {

    /**
     * Overridden variation that returns a {@link ExecutableMethodValidator}.
     * @return The validator
     */
    @Override
    @Nonnull ExecutableMethodValidator forExecutables();

    @Override
    @Nonnull <T> Set<ConstraintViolation<T>> validate(
            @Nonnull T object,
            Class<?>... groups
    );

    /**
     * Validate the given introspection and object.
     * @param introspection The introspection
     * @param object The object
     * @param groups The groups
     * @param <T> The object type
     * @return The constraint violations
     */
    @Nonnull
    <T> Set<ConstraintViolation<T>> validate(
            @Nonnull BeanIntrospection<T> introspection,
            @Nonnull T object, @Nullable Class<?>... groups);

    @Override
    @Nonnull <T> Set<ConstraintViolation<T>> validateProperty(
            @Nonnull T object,
            @Nonnull String propertyName,
            Class<?>... groups
    );

    @Override
    @Nonnull <T> Set<ConstraintViolation<T>> validateValue(
            @Nonnull Class<T> beanType,
            @Nonnull String propertyName,
            @Nullable Object value,
            Class<?>... groups
    );

    /**
     * Constructs a new default instance. Note that the returned instance will not contain
     * managed {@link io.micronaut.validation.validator.constraints.ConstraintValidator} instances and using
     * {@link javax.inject.Inject} should be preferred.
     *
     * @return The validator.
     */
    static @Nonnull Validator getInstance() {
        return new DefaultValidator(
                new DefaultValidatorConfiguration()
        );
    }
}
