/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.inject.reflection;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospectionReference;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.reflect.ClassUtils;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * A {@link BeanIntrospector} that serves the generated introspections of another introspector first, and a
 * {@link ReflectionBeanIntrospection} for a type that has none.
 *
 * <p>The reflective introspections cannot be enumerated, so {@link #findIntrospections(Predicate)} and
 * {@link #findIntrospectedTypes(Predicate)} return what the delegate returns.</p>
 *
 * <p>This introspector is for the code paths a specification requires to handle any class; it is not a
 * replacement of {@link BeanIntrospector#SHARED}, whose callers expect a missing introspection to mean that
 * a type was not meant to be introspected.</p>
 *
 * @author Denis Stepanov
 * @since 5.1
 */
@Experimental
public final class ReflectionBeanIntrospector implements BeanIntrospector {

    private final BeanIntrospector delegate;
    private final Predicate<Class<?>> reflective;
    private final boolean supplementing;
    private final Map<Class<?>, Optional<BeanIntrospection<?>>> reflected = new ConcurrentHashMap<>();

    /**
     * Creates an introspector reflecting over every type the delegate does not know.
     *
     * @param delegate The introspector consulted first
     */
    public ReflectionBeanIntrospector(BeanIntrospector delegate) {
        this(delegate, type -> true, false);
    }

    /**
     * @param delegate   The introspector consulted first
     * @param reflective The types a reflective introspection may be created for
     */
    public ReflectionBeanIntrospector(BeanIntrospector delegate, Predicate<Class<?>> reflective) {
        this(delegate, reflective, false);
    }

    /**
     * @param delegate      The introspector consulted first
     * @param reflective    The types a reflective introspection may be created for
     * @param supplementing Whether a generated introspection is completed with the executables the
     *                      processor left out, as a {@link SupplementedBeanIntrospection}
     */
    public ReflectionBeanIntrospector(BeanIntrospector delegate, Predicate<Class<?>> reflective, boolean supplementing) {
        this.delegate = delegate;
        this.reflective = reflective;
        this.supplementing = supplementing;
    }

    @Override
    public Collection<BeanIntrospection<Object>> findIntrospections(Predicate<? super BeanIntrospectionReference<?>> filter) {
        return delegate.findIntrospections(filter);
    }

    @Override
    public Collection<Class<?>> findIntrospectedTypes(Predicate<? super BeanIntrospectionReference<?>> filter) {
        return delegate.findIntrospectedTypes(filter);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<BeanIntrospection<T>> findIntrospection(Class<T> beanType) {
        Optional<BeanIntrospection<T>> generated = delegate.findIntrospection(beanType);
        boolean introspectable = ReflectionBeanIntrospection.isIntrospectable(beanType) && reflective.test(beanType);
        if (generated.isPresent() && (!supplementing || !introspectable)) {
            return generated;
        }
        if (!introspectable) {
            return Optional.empty();
        }
        return (Optional<BeanIntrospection<T>>) (Optional<?>) reflected.computeIfAbsent(beanType, type -> {
            ReflectionBeanIntrospection<T> reflection = ReflectionBeanIntrospection.of(beanType);
            if (generated.isPresent()) {
                return Optional.of(new SupplementedBeanIntrospection<>(generated.get(), reflection));
            }
            if (ClassUtils.REFLECTION_LOGGER.isDebugEnabled()) {
                ClassUtils.REFLECTION_LOGGER.debug("Reflectively introspecting '{}', which has no generated BeanIntrospection", type.getName());
            }
            return Optional.of(reflection);
        });
    }
}
