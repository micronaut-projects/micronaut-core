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

package io.micronaut.validation.validator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.validation.ConstraintViolation;
import java.util.Set;

/**
 * Interface that implements a subset of the {@code javax.validation} specification without the parts that
 * depend on reflection.
 *
 * @author graemerocher
 * @since 1.2
 */
public interface Validator extends javax.validation.Validator {

    /**
     * Validates all constraints on {@code object}.
     *
     * @param object object to validate
     * @param groups the group or list of groups targeted for validation (defaults to
     *        {@link javax.validation.groups.Default})
     * @param <T> the type of the object to validate
     * @return constraint violations or an empty set if none
     * @throws IllegalArgumentException if object is {@code null}
     *         or if {@code null} is passed to the varargs groups
     * @throws javax.validation.ValidationException if a non recoverable error happens
     *         during the validation process
     */
    @Nonnull <T> Set<ConstraintViolation<T>> validate(@Nullable T object, @Nullable Class<?>... groups);

    /**
     * Validates all constraints placed on the property of {@code object}
     * named {@code propertyName}.
     *
     * @param object object to validate
     * @param propertyName property to validate (i.e. field and getter constraints)
     * @param groups the group or list of groups targeted for validation (defaults to
     *        {@link javax.validation.groups.Default})
     * @param <T> the type of the object to validate
     * @return constraint violations or an empty set if none
     * @throws IllegalArgumentException if {@code object} is {@code null},
     *         if {@code propertyName} is {@code null}, empty or not a valid object property
     *         or if {@code null} is passed to the varargs groups
     * @throws javax.validation.ValidationException if a non recoverable error happens
     *         during the validation process
     */
    @Nonnull <T> Set<ConstraintViolation<T>> validateProperty(@Nullable T object,
                                                              @Nonnull String propertyName,
                                                              @Nullable Class<?>... groups);

    /**
     * Validates all constraints placed on the property named {@code propertyName}
     * of the class {@code beanType} would the property value be {@code value}.
     * <p>
     * {@link ConstraintViolation} objects return {@code null} for
     * {@link ConstraintViolation#getRootBean()} and
     * {@link ConstraintViolation#getLeafBean()}.
     *
     * @param beanType the bean type
     * @param propertyName property to validate
     * @param value property value to validate
     * @param groups the group or list of groups targeted for validation (defaults to
     *        {@link javax.validation.groups.Default}).
     * @param <T> the type of the object to validate
     * @return constraint violations or an empty set if none
     * @throws IllegalArgumentException if {@code beanType} is {@code null},
     *         if {@code propertyName} is {@code null}, empty or not a valid object property
     *         or if {@code null} is passed to the varargs groups
     * @throws javax.validation.ValidationException if a non recoverable error happens
     *         during the validation process
     */
    @Nonnull <T> Set<ConstraintViolation<T>> validateValue(@Nonnull Class<T> beanType,
                                                           @Nonnull String propertyName,
                                                           @Nullable Object value,
                                                           @Nullable Class<?>... groups);

    @Override
    ExecutableMethodValidator forExecutables();
}
