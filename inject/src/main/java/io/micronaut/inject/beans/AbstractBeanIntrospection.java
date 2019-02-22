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

package io.micronaut.inject.beans;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.util.*;

/**
 * Abstract implementation of the {@link BeanIntrospection} interface.
 *
 * @param <T> The generic type
 * @author graemerocher
 * @since 1.1
 */
@UsedByGeneratedCode
@Internal
public abstract class AbstractBeanIntrospection<T> implements BeanIntrospection<T> {

    protected final AnnotationMetadata annotationMetadata;
    protected final Class<T> beanType;
    @SuppressWarnings("WeakerAccess")
    protected final Map<String, BeanProperty<T, Object>> beanProperties;

    // used for indexed properties
    private Map<Class<? extends Annotation>, List<BeanProperty<T, Object>>> indexed;

    /**
     * Base class for bean instrospections.
     * @param beanType The bean type
     * @param annotationMetadata The annotation metadata
     * @param propertyCount The property count
     */
    @UsedByGeneratedCode
    protected AbstractBeanIntrospection(
            @Nonnull Class<T> beanType,
            @Nullable AnnotationMetadata annotationMetadata,
            int propertyCount) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        this.beanType = beanType;
        this.annotationMetadata = annotationMetadata == null ? AnnotationMetadata.EMPTY_METADATA : annotationMetadata;
        //noinspection unchecked
        this.beanProperties = new LinkedHashMap<>(propertyCount);
    }

    @Nonnull
    @Override
    public T instantiate(Object... arguments) throws InstantiationException {
        ArgumentUtils.requireNonNull("arguments", arguments);
        final Argument<?>[] constructorArguments = getConstructorArguments();
        if (constructorArguments.length != arguments.length) {
            throw new InstantiationException("Argument count [" + arguments.length + "] doesn't match required argument count: " + constructorArguments.length);
        }

        for (int i = 0; i < constructorArguments.length; i++) {
            Argument<?> constructorArgument = constructorArguments[i];
            final Object specified = arguments[i];
            if (specified == null) {
                if (constructorArgument.isAnnotationPresent(Nullable.class)) {
                    continue;
                } else {
                    throw new InstantiationException("Null argument specified for [" + constructorArgument.getName() + "]. If this argument is allowed be null annotate it with @Nullable");
                }
            }
            if (!ReflectionUtils.getWrapperType(constructorArgument.getType()).isInstance(specified)) {
                throw new InstantiationException("Invalid argument [" + specified + "] specified for argument: " + constructorArgument);
            }
        }

        return instantiateInternal(arguments);
    }

    @Nonnull
    @Override
    public Optional<BeanProperty<T, Object>> getProperty(@Nonnull String name) {
        return Optional.ofNullable(beanProperties.get(name));
    }

    @Nonnull
    @Override
    public Collection<BeanProperty<T, Object>> getBeanProperties(Class<? extends Annotation> annotationType) {
        if (indexed != null) {
            final List<BeanProperty<T, Object>> indexed = this.indexed.get(annotationType);
            if (indexed != null) {
                return Collections.unmodifiableCollection(indexed);
            }
        }
        return Collections.emptyList();
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    @Nonnull
    @Override
    public Collection<BeanProperty<T, Object>> getBeanProperties() {
        return Collections.unmodifiableCollection(beanProperties.values());
    }

    @Nonnull
    @Override
    public Class<T> getBeanType() {
        return beanType;
    }

    /**
     * Reflection free bean instantiation implementation for the given arguments.
     * @param arguments The arguments
     * @return The bean
     */
    @Internal
    @UsedByGeneratedCode
    protected abstract T instantiateInternal(Object[] arguments);

    /**
     * Adds a property at a particular index of the internal array passed to the constructor. Used by
     * generated byte code for subclasses and not for public consumption.
     *
     * @param property The property.
     */
    @SuppressWarnings("unused")
    @Internal
    @UsedByGeneratedCode
    protected final void addProperty(@Nonnull BeanProperty<T, Object> property) {
        ArgumentUtils.requireNonNull("property", property);
        beanProperties.put(property.getName(), property);
    }

    /**
     * Used to produce an index for particular annotation type. Method referenced by generated byte code and
     * not for public consumption. Should be called after {@link #addProperty(BeanProperty)} if required.
     *
     * @param annotationType The annotation type
     * @param name The property name
     */
    @SuppressWarnings("unused")
    @Internal
    @UsedByGeneratedCode
    protected final void indexProperty(Class<? extends Annotation> annotationType, @Nonnull String name) {
        if (annotationType != null && StringUtils.isNotEmpty(name)) {
            final BeanProperty<T, Object> property = beanProperties.get(name);
            if (property == null) {
                throw new IllegalStateException("Invalid byte code generated during bean introspection. Call addProperty first!");
            }
            if (indexed == null) {
                indexed = new HashMap<>(2);
            }
            final List<BeanProperty<T, Object>> indexed = this.indexed.computeIfAbsent(annotationType, aClass -> new ArrayList<>(2));

            indexed.add(property);
        }
    }
}
