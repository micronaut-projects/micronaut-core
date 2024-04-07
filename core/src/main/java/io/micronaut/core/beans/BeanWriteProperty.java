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
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ArgumentCoercible;
import io.micronaut.core.util.ArgumentUtils;

/**
 * Represents a write property of a bean.
 *
 * <p>A {@link BeanWriteProperty} allows you to write to it via {@link #set(Object, Object)}, without using reflection.</p>
 *
 * <p>The annotations of a property can be inspected via the {@link #getAnnotationMetadata()} method.</p>
 *
 * @param <B> The bean type
 * @param <T> The bean property type
 * @author Denis Stepanov
 * @since 4.4.0
 * @see BeanIntrospection
 */
public interface BeanWriteProperty<B, T> extends AnnotatedElement, AnnotationMetadataDelegate, ArgumentCoercible<T> {

    /**
     * @return The declaring bean introspection.
     */
    @NonNull
    BeanIntrospection<B> getDeclaringBean();


    /**
     * The declaring type of the property.
     * @return The type
     */
    @NonNull
    default Class<B> getDeclaringType() {
        return getDeclaringBean().getBeanType();
    }

    /**
     * This method will attempt to modify the property or if this is an immutable type using a copy constructor to return a new instance with the new value.
     *
     * <p>This differs from {@link #set(Object, Object)} which will throw an exception if the property does not have a setter.</p>
     *
     * <ul>
     *     <li>If the property is read-only but can be provided via constructor argument a new instance representing a copy of the bean is returned.</li>
     *     <li>If the property is mutable then the passed instance is returned and {@link #set(Object, Object)} invoked  to mutate the property</li>
     *     <li>If there is no way for the property to be mutated then an {@link UnsupportedOperationException} is thrown</li>
     * </ul>
     *
     * @param bean The bean
     * @param value The new value
     * @return Either the existing instance or the property is mutable or a newly created instance via the copy constructor pattern.
     * @throws UnsupportedOperationException if the property cannot be mutated
     */
    B withValue(@NonNull B bean, @Nullable T value);

    /**
     * Write the bean value.
     * @param bean The bean
     * @param value The value to write
     * @throws IllegalArgumentException If either the bean type or value type are not correct
     */
    void set(@NonNull B bean, @Nullable T value);

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
    @Override
    @NonNull
    default Argument<T> asArgument() {
        return Argument.of(getType());
    }

}
