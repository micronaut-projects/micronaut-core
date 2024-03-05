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

import io.micronaut.core.annotation.NextMajorVersion;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ArgumentCoercible;
import io.micronaut.core.util.ArgumentUtils;

import java.util.Arrays;
import java.util.Collection;
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
@NextMajorVersion("Remove all the methods, only keeping those in read/write interfaces")
public interface BeanProperty<B, T> extends BeanReadProperty<B, T>, BeanWriteProperty<B, T>, ArgumentCoercible<T> {

    /**
     * @return The declaring bean introspection.
     */
    @Override
    @NonNull BeanIntrospection<B> getDeclaringBean();

    /**
     * Read the bean value.
     * @param bean The bean to read from
     * @return The value
     * @throws IllegalArgumentException If the bean instance if not of the correct type
     */
    @Override
    @Nullable T get(@NonNull B bean);

    /**
     * Read the value and try to convert it to the given type.
     * @param bean The bean
     * @param type The type
     * @param <T2> The generic type
     * @return The value if conversion was possible.
     */
    @Override
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
    @Override
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
    @Override
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
    @Override
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
     * This method returns true if the property can be mutated either via copy constructor or bean setter.
     *
     * @return True if it is mutable
     * @see #withValue(Object, Object)
     * @since 2.3.0
     */
    default boolean hasSetterOrConstructorArgument() {
        BeanIntrospection<B> declaringBean = getDeclaringBean();
        return !isReadOnly() || Arrays.stream(declaringBean.getConstructorArguments())
                                    .anyMatch(arg -> declaringBean.getProperty(arg.getName(), arg.getType()).isPresent());
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
     * @since 2.3.0
     */
    @Override
    default B withValue(@NonNull B bean, @Nullable T value) {
        if (isReadOnly())  {
            if (value == get(bean)) {
                return bean;
            }
            BeanIntrospection<B> declaringBean = getDeclaringBean();
            Argument<?>[] constructorArguments = declaringBean.getConstructorArguments();
            Object[] values = new Object[constructorArguments.length];
            boolean found = false;
            for (int i = 0; i < constructorArguments.length; i++) {
                Argument<?> constructorArgument = constructorArguments[i];
                String argumentName = constructorArgument.getName();
                Class<?> argumentType = constructorArgument.getType();
                BeanProperty<B, ?> prop = declaringBean.getProperty(argumentName, argumentType).orElse(null);
                if (prop == null) {
                    throw new UnsupportedOperationException("Cannot create copy of type [" + declaringBean.getBeanType() + "]. Constructor contains argument [" + argumentName + "] that is not a readable property");
                } else if (prop == this) {
                    found = true;
                    values[i] = value;
                } else {
                    values[i] = prop.get(bean);
                }
            }
            B newInstance = declaringBean.instantiate(values);
            Collection<BeanProperty<B, Object>> beanProperties = declaringBean.getBeanProperties();
            if (found) {
                for (BeanProperty<B, Object> beanProperty : beanProperties) {
                    if (beanProperty != this && beanProperty.isReadWrite()) {
                        beanProperty.set(newInstance, beanProperty.get(bean));
                    }
                }
                return newInstance;
            } else {
                for (BeanProperty<B, Object> beanProperty : beanProperties) {
                    if (beanProperty == this && beanProperty.isReadWrite()) {
                        found = true;
                        beanProperty.set(newInstance, beanProperty.get(bean));
                    }
                }
                if (!found) {
                    throw new UnsupportedOperationException("Cannot mutate property [" + getName() + "] that is not mutable via a setter method or constructor argument for type: " + declaringBean.getBeanType().getName());
                } else {
                    return newInstance;
                }
            }
        } else {
            set(bean, value);
            return bean;
        }
    }

    /**
     * Write the bean value.
     * @param bean The bean
     * @param value The value to write
     * @throws IllegalArgumentException If either the bean type or value type are not correct
     */
    @Override
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
    @Override
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
    @Override
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
    @Override
    default Class<B> getDeclaringType() {
        return getDeclaringBean().getBeanType();
    }
}
