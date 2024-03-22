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
package io.micronaut.core.beans;

import io.micronaut.core.annotation.AnnotatedElement;
import io.micronaut.core.annotation.AnnotationMetadataDelegate;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ArgumentCoercible;
import io.micronaut.core.util.ArgumentUtils;

import java.util.Optional;

/**
 * Represents a read write property of a bean.
 *
 * <p>A {@link BeanReadProperty} allows you to read the value of a property via {@link #get(Object)}, without using reflection.</p>
 *
 * <p>The annotations of a property can be inspected via the {@link #getAnnotationMetadata()} method.</p>
 *
 * @param <B> The bean type
 * @param <T> The bean property type
 * @author Denis Stepanov
 * @since 4.4.0
 * @see BeanIntrospection
 */
public interface BeanReadProperty<B, T> extends AnnotatedElement, AnnotationMetadataDelegate, ArgumentCoercible<T> {

    /**
     * @return The declaring bean introspection.
     */
    @NonNull
    BeanIntrospection<B> getDeclaringBean();

    /**
     * The declaring type of the property.
     * @return The type
     */
    default Class<B> getDeclaringType() {
        return getDeclaringBean().getBeanType();
    }

    /**
     * Read the bean value.
     * @param bean The bean to read from
     * @return The value
     * @throws IllegalArgumentException If the bean instance if not of the correct type
     */
    @Nullable
    T get(@NonNull B bean);

    /**
     * Read the value and try to convert it to the given type.
     * @param bean The bean
     * @param type The type
     * @param <T2> The generic type
     * @return The value if conversion was possible.
     */
    default @NonNull <T2> Optional<T2> get(@NonNull B bean, @NonNull Class<T2> type) {
        ArgumentUtils.requireNonNull("bean", bean);
        ArgumentUtils.requireNonNull("type", type);
        final Argument<T2> argument = Argument.of(type);
        return get(bean, argument);
    }

    /**
     * Read the value and try to convert it to the given type.
     * @param bean The bean
     * @param argument The type
     * @param <T2> The generic type
     * @return The value if conversion was possible.
     */
    default <T2> Optional<T2> get(@NonNull B bean, @NonNull Argument<T2> argument) {
        ArgumentUtils.requireNonNull("bean", bean);
        ArgumentUtils.requireNonNull("type", argument);

        final ArgumentConversionContext<T2> conversionContext = ConversionContext.of(argument);
        return get(bean, conversionContext);
    }

    /**
     * Read the value and try to convert it to the given type.
     * @param bean The bean
     * @param conversionContext The conversion context to use
     * @param <T2> The generic type
     * @return The value if conversion was possible.
     */
    default <T2> Optional<T2> get(@NonNull B bean, @NonNull ArgumentConversionContext<T2> conversionContext) {
        ArgumentUtils.requireNonNull("bean", bean);
        ArgumentUtils.requireNonNull("conversionContext", conversionContext);

        final T v = get(bean);
        return ConversionService.SHARED.convert(v, conversionContext);
    }

    /**
     * Read the value and try to convert it to the given type.
     * @param bean The bean
     * @param type The type
     * @param defaultValue The default value if conversion is not possible
     * @param <T2> The generic type
     * @return The value if conversion was possible.
     */
    default @Nullable <T2> T2 get(@NonNull B bean, @NonNull Class<T2> type, @Nullable T2 defaultValue) {
        ArgumentUtils.requireNonNull("bean", bean);
        //noinspection ConstantConditions
        if (type == null) {
            return defaultValue;
        } else {
            final T v = get(bean);
            return ConversionService.SHARED.convert(v, type).orElse(defaultValue);
        }
    }

    /**
     * @return The property type.
     */
    @NonNull Class<T> getType();

    /**
     * Represent the type as an argument, including any generic type information.
     *
     * @return The argument
     */
    @Override
    @NonNull
    default Argument<T> asArgument() {
        return Argument.of(getType());
    }
}
