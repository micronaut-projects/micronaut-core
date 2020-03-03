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
package io.micronaut.core.beans;

import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Optional;

/**
 * Wraps a bean allowing to read and write its properties via a underlying {@link BeanIntrospection}.
 *
 * @param <T> The bean type.
 * @author graemerocher
 * @since 1.1
 */
public interface BeanWrapper<T> extends AnnotationMetadataProvider {

    /**
     * @return The introspection.
     */
    @Nonnull BeanIntrospection<T> getIntrospection();

    /**
     * @return The bean;
     */
    @Nonnull T getBean();

    /**
     * The property names.
     *
     * @return The property names
     */
    default @Nonnull String[] getPropertyNames() {
        return getIntrospection().getPropertyNames();
    }

    /**
     * The properties.
     * @return The properties
     */
    default @Nonnull Collection<BeanProperty<T, Object>> getBeanProperties() {
        return getIntrospection().getBeanProperties();
    }

    /**
     * Get the property value of the given type or throw an exception if it is unobtainable.
     *
     * @param name The name
     * @param type The type
     * @param <P> The property generic type
     * @return The property value
     * @throws IntrospectionException if no property exists
     * @throws ConversionErrorException if the property cannot be converted to the given type
     */
    default @Nonnull <P> P getRequiredProperty(@Nonnull String name, @Nonnull Class<P> type) {
        ArgumentUtils.requireNonNull("name", name);
        ArgumentUtils.requireNonNull("type", type);
        final Argument<P> argument = Argument.of(type);
        return getRequiredProperty(name, argument);
    }

    /**
     * Get the property value of the given type or throw an exception if it is unobtainable.
     *
     * @param name The name
     * @param argument The argument type
     * @param <P> The property generic type
     * @return The property value
     * @throws IntrospectionException if no property exists
     * @throws ConversionErrorException if the property cannot be converted to the given type
     */
    default <P> P getRequiredProperty(@Nonnull String name, @Nonnull Argument<P> argument) {
        ArgumentUtils.requireNonNull("name", name);
        ArgumentUtils.requireNonNull("argument", argument);

        final ArgumentConversionContext<P> context = ConversionContext.of(argument);
        return getRequiredProperty(name, context);
    }

    /**
     * Get the property value of the given type or throw an exception if it is unobtainable.
     * @param name The name
     * @param context The type
     * @param <P> The property generic type
     * @return The property value
     * @throws IntrospectionException if no property exists
     * @throws ConversionErrorException if the property cannot be converted to the given type
     */
    default @Nonnull <P> P getRequiredProperty(@Nonnull String name, @Nonnull ArgumentConversionContext<P> context) {
        ArgumentUtils.requireNonNull("name", name);
        ArgumentUtils.requireNonNull("type", context);

        return getIntrospection().getProperty(name)
                .map(prop -> {
                    final Optional<P> converted = prop.get(getBean(), context);
                    return converted.orElseThrow(() -> {
                        final ConversionError conversionError = context.getLastError().orElseGet(() -> new ConversionError() {
                            @Override
                            public Exception getCause() {
                                return new IntrospectionException("Property of type [" + prop.getType() + "] cannot be converted to type: " + context.getArgument().getType());
                            }

                            @Override
                            public Optional<Object> getOriginalValue() {
                                return Optional.ofNullable(prop.get(getBean()));
                            }
                        });

                        return new ConversionErrorException(context.getArgument(), conversionError);
                    });
                })
                .orElseThrow(() -> new IntrospectionException("No property found for name: " + name));
    }

    /**
     * Get the property of the given name and type.
     * @param name The name
     * @param type The required type
     * @param <P> The required generic type
     * @return The property if found and can be converted to the given type
     */
    default @Nonnull <P> Optional<P> getProperty(@Nonnull String name, @Nonnull Class<P> type) {
        ArgumentUtils.requireNonNull("name", name);
        ArgumentUtils.requireNonNull("type", type);
        final Argument<P> argument = Argument.of(type);
        return getProperty(name, argument);
    }

    /**
     * Get the property of the given name and type.
     * @param name The name
     * @param type The required type
     * @param <P> The required generic type
     * @return The property if found and can be converted to the given type
     */
    default <P> Optional<P> getProperty(@Nonnull String name, Argument<P> type) {
        ArgumentUtils.requireNonNull("name", name);
        ArgumentUtils.requireNonNull("type", type);
        final ArgumentConversionContext<P> context = ConversionContext.of(type);
        return getProperty(name, context);
    }

    /**
     * Get the property of the given name and type.
     * @param name The name
     * @param context The conversion context
     * @param <P> The required generic type
     * @return The property if found and can be converted to the given type
     */
    default <P> Optional<P> getProperty(@Nonnull String name, ArgumentConversionContext<P> context) {
        ArgumentUtils.requireNonNull("name", name);
        ArgumentUtils.requireNonNull("context", context);

        return getIntrospection().getProperty(name)
                                 .flatMap(prop -> prop.get(getBean(), context));
    }

    /**
     * Sets a property of the given name to the given value.
     * @param name The name
     * @param value The value
     * @return This wrapper
     * @throws ConversionErrorException if the value cannot be converted to the underlying property type.
     */
    default BeanWrapper<T> setProperty(@Nonnull String name, @Nullable Object value) {
        ArgumentUtils.requireNonNull("name", name);

        getIntrospection()
                .getProperty(name)
                .ifPresent(prop -> prop.convertAndSet(getBean(), value));
        return this;
    }

    /**
     * Obtain a bean wrapper for the given bean.
     * @param bean The bean
     * @param <T2> The bean type
     * @return The wrapper
     * @throws io.micronaut.core.beans.exceptions.IntrospectionException If the wrapper cannot be created
     */
    static @Nonnull <T2>  BeanWrapper<T2> getWrapper(@Nonnull T2 bean) {
        ArgumentUtils.requireNonNull("bean", bean);
        @SuppressWarnings("unchecked") final Class<T2> aClass = (Class<T2>) bean.getClass();
        final BeanIntrospection<T2> introspection = BeanIntrospection.getIntrospection(aClass);
        return new DefaultBeanWrapper<>(bean, introspection);
    }


    /**
     * Obtain a bean wrapper for the given bean.
     * @param bean The bean
     * @param <T2> The bean type
     * @return The wrapper
     * @throws io.micronaut.core.beans.exceptions.IntrospectionException If the wrapper cannot be created
     */
    @SuppressWarnings("unchecked")
    static @Nonnull <T2>  Optional<BeanWrapper<T2>> findWrapper(@Nonnull T2 bean) {
        ArgumentUtils.requireNonNull("bean", bean);
        @SuppressWarnings("unchecked") final Class<T2> aClass = (Class<T2>) bean.getClass();
        return BeanIntrospector.SHARED.findIntrospection(aClass).map(i ->
                new DefaultBeanWrapper(bean, i)
        );
    }

    /**
     * Obtain a bean wrapper for the given bean.
     * @param type the type
     * @param bean The bean
     * @param <T2> The bean type
     * @return The wrapper
     * @throws io.micronaut.core.beans.exceptions.IntrospectionException If the wrapper cannot be created
     */
    @SuppressWarnings("unchecked")
    static @Nonnull <T2>  Optional<BeanWrapper<T2>> findWrapper(Class<T2> type, @Nonnull T2 bean) {
        ArgumentUtils.requireNonNull("type", type);
        ArgumentUtils.requireNonNull("bean", bean);
        return BeanIntrospector.SHARED.findIntrospection(type).map(i ->
                new DefaultBeanWrapper(bean, i)
        );
    }

    /**
     * Obtain a bean wrapper for the given bean.
     * @param type the type
     * @param bean The bean
     * @param <T2> The bean type
     * @return The wrapper
     * @throws io.micronaut.core.beans.exceptions.IntrospectionException If the wrapper cannot be created
     */
    static @Nonnull <T2>  BeanWrapper<T2> getWrapper(Class<T2> type, @Nonnull T2 bean) {
        ArgumentUtils.requireNonNull("type", type);
        ArgumentUtils.requireNonNull("bean", bean);
        final BeanIntrospection<T2> introspection = BeanIntrospection.getIntrospection(type);
        return new DefaultBeanWrapper<>(bean, introspection);
    }
}
