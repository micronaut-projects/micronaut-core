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

import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.ArgumentUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Default implementation of the {@link BeanIntrospector} interface that uses service loader to discovery introspections.
 *
 * @author graemerocher
 * @since 1.1
 * @see BeanIntrospector
 * @see BeanIntrospection
 */
class DefaultBeanIntrospector implements BeanIntrospector {

    private static final Logger LOG = ClassUtils.getLogger(DefaultBeanIntrospector.class);
    private static final String MICRONAUT_INTROSPECTIONS_USE_CONTEXT_CLASSLOADER = "micronaut.introspections.use.context.classloader";

    @Nullable
    private Map<String, BeanIntrospectionReference<Object>> introspectionMap;
    private final ClassLoader classLoader;
    private final boolean useContextClassLoader;

    /**
     * Creates an introspector that uses this class' class loader and may follow the context class loader setting.
     */
    DefaultBeanIntrospector() {
        this(DefaultBeanIntrospector.class.getClassLoader(), true);
    }

    /**
     * Creates an introspector bound to the supplied class loader.
     *
     * @param classLoader The class loader to load introspections
     */
    DefaultBeanIntrospector(ClassLoader classLoader) {
        this(classLoader, false);
    }

    /**
     * Creates an introspector bound to the supplied class loader.
     *
     * @param classLoader The class loader to load introspections
     * @param useContextClassLoader Whether to allow the context class loader setting to override the supplied class loader
     */
    private DefaultBeanIntrospector(ClassLoader classLoader, boolean useContextClassLoader) {
        this.classLoader = classLoader;
        this.useContextClassLoader = useContextClassLoader;
    }

    @Override
    public Collection<BeanIntrospection<Object>> findIntrospections(Predicate<? super BeanIntrospectionReference<?>> filter) {
        ArgumentUtils.requireNonNull("filter", filter);
        return getIntrospections()
                .values()
                .stream()
                .filter(filter)
                .map(BeanIntrospectionReference::load)
                .collect(Collectors.toList());
    }

    @Override
    public Collection<Class<?>> findIntrospectedTypes(Predicate<? super BeanIntrospectionReference<?>> filter) {
        ArgumentUtils.requireNonNull("filter", filter);
        return getIntrospections()
                .values()
                .stream()
                .filter(filter)
                .map(BeanIntrospectionReference::getBeanType)
                .collect(Collectors.toSet());
    }

    @Override
    @SuppressWarnings("java:S1181")
    public <T> Optional<BeanIntrospection<T>> findIntrospection(Class<T> beanType) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        ClassLoader effectiveClassLoader = resolveClassLoader();
        @SuppressWarnings("unchecked") final BeanIntrospectionReference<T> reference =
                (BeanIntrospectionReference<T>) findIntrospectionReference(beanType);
        try {
            if (reference != null) {
                return Optional.of(reference).map((Function<BeanIntrospectionReference<T>, BeanIntrospection<T>>) ref -> {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Found BeanIntrospection for type: {},", ref.getBeanType());
                    }
                    return ref.load();
                });
            }
            if (useContextClassLoader && Boolean.getBoolean(MICRONAUT_INTROSPECTIONS_USE_CONTEXT_CLASSLOADER)) {
                ClassLoader beanClassLoader = beanType.getClassLoader();
                if (beanClassLoader != null && beanClassLoader != effectiveClassLoader) {
                    @SuppressWarnings("unchecked") final BeanIntrospectionReference<T> beanClassLoaderReference =
                            (BeanIntrospectionReference<T>) getIntrospections(beanClassLoader).get(beanType.getName());
                    if (beanClassLoaderReference != null) {
                        return Optional.of(beanClassLoaderReference).map((Function<BeanIntrospectionReference<T>, BeanIntrospection<T>>) ref -> {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Found BeanIntrospection for type: {},", ref.getBeanType());
                            }
                            return ref.load();
                        });
                    }
                }
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("No BeanIntrospection found for bean type: {}", beanType);
            }
            return Optional.empty();
        } catch (Throwable e) {
            throw new IntrospectionException("Error loading BeanIntrospection for type [" + beanType + "]: " + e.getMessage(), e);
        }
    }

    @Nullable
    private BeanIntrospectionReference<Object> findIntrospectionReference(Class<?> beanType) {
        String beanTypeName = beanType.getName();
        BeanIntrospectionReference<Object> reference = getIntrospections().get(beanTypeName);
        if (reference != null) {
            return reference;
        }
        ClassLoader beanClassLoader = beanType.getClassLoader();
        ClassLoader effectiveClassLoader = resolveClassLoader();
        if (beanClassLoader != null && beanClassLoader != effectiveClassLoader) {
            return resolveIntrospections(beanClassLoader).get(beanTypeName);
        }
        return null;
    }

    private Map<String, BeanIntrospectionReference<Object>> getIntrospections() {
        return getIntrospections(resolveClassLoader());
    }

    private Map<String, BeanIntrospectionReference<Object>> getIntrospections(ClassLoader effectiveClassLoader) {
        if (effectiveClassLoader != classLoader) {
            return resolveIntrospections(effectiveClassLoader);
        }
        Map<String, BeanIntrospectionReference<Object>> resolvedIntrospectionMap = this.introspectionMap;
        if (resolvedIntrospectionMap == null) {
            synchronized (this) { // double check
                resolvedIntrospectionMap = this.introspectionMap;
                if (resolvedIntrospectionMap == null) {
                    resolvedIntrospectionMap = resolveIntrospections(classLoader);
                    this.introspectionMap = resolvedIntrospectionMap;
                }
            }
        }
        return resolvedIntrospectionMap;
    }

    private ClassLoader resolveClassLoader() {
        if (useContextClassLoader && Boolean.getBoolean(MICRONAUT_INTROSPECTIONS_USE_CONTEXT_CLASSLOADER)) {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            if (contextClassLoader != null) {
                return contextClassLoader;
            }
        }
        return classLoader;
    }

    private Map<String, BeanIntrospectionReference<Object>> resolveIntrospections(ClassLoader classLoader) {
        Map<String, BeanIntrospectionReference<Object>> resolvedIntrospectionMap = new HashMap<>(30);
        List<BeanIntrospectionReference<Object>> beanIntrospectionReferences = BeanIntrospectionProviders.get().provide(classLoader);
        for (BeanIntrospectionReference<Object> reference : beanIntrospectionReferences) {
            resolvedIntrospectionMap.put(reference.getName(), reference);
        }
        return resolvedIntrospectionMap;
    }
}
