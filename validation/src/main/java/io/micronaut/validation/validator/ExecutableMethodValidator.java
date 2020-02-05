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

import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableArgumentValue;
import io.micronaut.inject.ExecutableMethod;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.executable.ExecutableValidator;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Set;

/**
 * Extended version of {@link ExecutableValidator} that operates on {@link io.micronaut.inject.ExecutableMethod} instances.
 *
 * @author graemerocher
 * @since 1.2
 */
public interface ExecutableMethodValidator extends ExecutableValidator  {


    /**
     * Create a new valid instance.
     *
     * @param type The type
     * @param arguments The arguments
     * @param <T> the generic type
     * @return The instance
     * @throws ConstraintViolationException If a valid instance couldn't be constructed
     * @throws IllegalArgumentException If an argument is invalid
     */
    @NonNull <T> T createValid(@NonNull Class<T> type, Object... arguments) throws ConstraintViolationException;

    /**
     * Validate the parameter values of the given {@link ExecutableMethod}.
     * @param object The object
     * @param method The method
     * @param parameterValues The values
     * @param groups The groups
     * @param <T> The object type
     * @return The constraint violations.
     */
    @NonNull <T> Set<ConstraintViolation<T>> validateParameters(
            @NonNull T object,
            @NonNull ExecutableMethod method,
            @NonNull Object[] parameterValues,
            @Nullable Class<?>... groups);

    /**
     * Validate the parameter values of the given {@link ExecutableMethod}.
     * @param object The object
     * @param method The method
     * @param argumentValues The values
     * @param groups The groups
     * @param <T> The object type
     * @return The constraint violations.
     */
    @NonNull <T> Set<ConstraintViolation<T>> validateParameters(
            @NonNull T object,
            @NonNull ExecutableMethod method,
            @NonNull Collection<MutableArgumentValue<?>> argumentValues,
            @Nullable Class<?>... groups);

    /**
     * Validates the return value of a {@link ExecutableMethod}.
     * @param object The object
     * @param executableMethod The method
     * @param returnValue The return value
     * @param groups The groups
     * @param <T> The object type
     * @return A set of contstraint violations
     */
    @NonNull <T> Set<ConstraintViolation<T>> validateReturnValue(
            @NonNull T object,
            @NonNull ExecutableMethod<?, Object> executableMethod,
            @Nullable Object returnValue,
            @Nullable Class<?>... groups);

    /**
     * Validates parameters for the given introspection and values.
     * @param introspection The introspection
     * @param parameterValues The parameter values
     * @param groups The groups
     * @param <T> The bean type.
     * @return The constraint violations
     */
    @NonNull
    <T> Set<ConstraintViolation<T>> validateConstructorParameters(
            @NonNull BeanIntrospection<? extends T> introspection,
            @NonNull Object[] parameterValues,
            @Nullable Class<?>... groups);

    /**
     * Validates arguments for the given bean type and constructor arguments.
     * @param beanType The bean type
     * @param constructorArguments The constructor arguments
     * @param parameterValues The parameter values
     * @param groups The validation groups
     * @param <T> The generic type of the bean
     * @return A set of constraint violations, if any
     */
    <T> Set<ConstraintViolation<T>> validateConstructorParameters(
            @NonNull Class<? extends T> beanType,
            @NonNull Argument<?>[] constructorArguments,
            @NonNull Object[] parameterValues,
            @Nullable Class<?>[] groups
    );

    @Override
    @NonNull <T> Set<ConstraintViolation<T>> validateParameters(@NonNull T object, @NonNull Method method, @NonNull Object[] parameterValues, @Nullable Class<?>... groups);

    @Override
    @NonNull <T> Set<ConstraintViolation<T>> validateReturnValue(@NonNull T object, @NonNull Method method, @Nullable Object returnValue, @Nullable Class<?>... groups);

    @Override
    @NonNull <T> Set<ConstraintViolation<T>> validateConstructorParameters(@NonNull Constructor<? extends T> constructor, @NonNull Object[] parameterValues, @Nullable Class<?>... groups);

    @Override
    @NonNull <T> Set<ConstraintViolation<T>> validateConstructorReturnValue(@NonNull Constructor<? extends T> constructor, @NonNull T createdObject, @Nullable Class<?>... groups);
}
