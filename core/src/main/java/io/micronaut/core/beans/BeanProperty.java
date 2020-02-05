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

import io.micronaut.core.annotation.AnnotatedElement;
import io.micronaut.core.annotation.AnnotationMetadataDelegate;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.annotation.concurrent.Immutable;
import java.util.Optional;

/**
 * Represents a bean property and associated annotation metadata.
 *
 * <p>A {@link BeanProperty} allows you to read the value of a property via {@link #get(Object)} or write to it via {@link #set(Object, Object)}, without using reflection.</p>
 *
 * <p>The annotations of a property can be inspected via the {@link #getAnnotationMetadata()} method.</p>
 *
 * @param <B> The bean type
 * @param <T> The bean property type
 * @author graemerocher
 * @since 1.1
 * @see BeanIntrospection
 */
@Immutable
public interface BeanProperty<B, T> extends AnnotatedElement, AnnotationMetadataDelegate {

    /**
     * @return The declaring bean introspection.
     */
    @NonNull BeanIntrospection<B> getDeclaringBean();

    /**
     * Read the bean value.
     * @param bean The bean to read from
     * @return The value
     * @throws IllegalArgumentException If the bean instance if not of the correct type
     */
    @Nullable T get(@NonNull B bean);

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
     * Write the bean value.
     * @param bean The bean
     * @param value The value to write
     * @throws IllegalArgumentException If either the bean type or value type are not correct
     */
    default void set(@NonNull B bean, @Nullable T value) {
        if (isReadOnly()) {
            throw new UnsupportedOperationException("Cannot write read-only property: " + getName());
        } else {
            throw new UnsupportedOperationException("Write method unimplemented for property: " + getName());
        }
    }

    /**
     * Convert the value and set if on the bean using the default conversion service.
     *
     * @param bean The bean
     * @param value The value
     * @throws io.micronaut.core.convert.exceptions.ConversionErrorException If the value couldn't be converted
     */
    default void convertAndSet(@NonNull B bean, @Nullable Object value) {
        ArgumentUtils.requireNonNull("bean", bean);
        if (value != null) {
            final Argument<T> argument = asArgument();
            final ArgumentConversionContext<T> context = ConversionContext.of(argument);
            final T converted = ConversionService.SHARED.convert(value, context).orElseThrow(() ->
                    new ConversionErrorException(argument, context.getLastError()
                            .orElse(() -> new IllegalArgumentException("Value [" + value + "] cannot be converted to type : " + getType())))
            );
            set(bean, converted);
        } else {
            //noinspection ConstantConditions,unchecked
            set(bean, (T) value);
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
    default Argument<T> asArgument() {
        return Argument.of(getType());
    }

    /**
     * @return Whether the property is read-only
     */
    default boolean isReadOnly() {
        return false;
    }

    /**
     * @return Whether the property is write-only
     */
    default boolean isWriteOnly() {
        return false;
    }

    /**
     * Whether the property can be written to and read from.
     *
     * @return True if it can.
     */
    default boolean isReadWrite() {
       return !isReadOnly() && !isWriteOnly();
    }

    /**
     * The declaring type of the property.
     * @return The type
     */
    default Class<B> getDeclaringType() {
        return getDeclaringBean().getBeanType();
    }
}
