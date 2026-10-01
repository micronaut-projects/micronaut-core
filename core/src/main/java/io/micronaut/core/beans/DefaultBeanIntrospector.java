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
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
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
    @SuppressWarnings("java:S3077") // resolveIntrospections returns an unmodifiable map, published once under double checked locking
    private volatile Map<String, BeanIntrospectionReference<Object>> introspectionMap;
    @Nullable
    @SuppressWarnings("java:S3077") // resolveFallbacks returns an immutable List.copyOf, published once under double checked locking
    private volatile List<BeanIntrospectionFallback> fallbacks;
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
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    @SuppressWarnings("java:S1181")
    public <T> Optional<BeanIntrospection<T>> findIntrospection(Class<T> beanType) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        ClassLoader effectiveClassLoader = resolveClassLoader();
        @SuppressWarnings("unchecked") final BeanIntrospectionReference<T> reference =
                (BeanIntrospectionReference<T>) findIntrospectionReference(beanType, effectiveClassLoader);
        try {
            if (reference != null) {
                return Optional.of(reference).map((Function<BeanIntrospectionReference<T>, BeanIntrospection<T>>) ref -> {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Found BeanIntrospection for type: {},", ref.getBeanType());
                    }
                    return ref.load();
                });
            }
        } catch (Throwable e) {
            throw new IntrospectionException("Error loading BeanIntrospection for type [" + beanType + "]: " + e.getMessage(), e);
        }
        // a fallback describes a class it was never compiled against, so asking it can fail on a type it does
        // not serve - reading a member whose type is an absent optional dependency throws NoClassDefFoundError -
        // and a failure of one fallback must stay a lookup miss, which is what the callers of a find expect.
        // An error of the virtual machine is not such a failure and is left to propagate
        for (BeanIntrospectionFallback fallback : getFallbacks(effectiveClassLoader)) {
            try {
                Optional<BeanIntrospection<T>> fallbackIntrospection = fallback.findIntrospection(beanType);
                if (fallbackIntrospection.isPresent()) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Found BeanIntrospection for type {} from fallback {}", beanType, fallback);
                    }
                    return fallbackIntrospection;
                }
            } catch (Exception | LinkageError e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Fallback {} failed to supply a BeanIntrospection for type {}, continuing", fallback, beanType, e);
                }
            }
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("No BeanIntrospection found for bean type: {}", beanType);
        }
        return Optional.empty();
    }

    /**
     * The introspection reference of the class loader the introspections are resolved with, or else of the class loader
     * of the bean type, which may be a child class loader that the introspector cannot see.
     * The class loader of the bean type is only asked when it shares this class' {@link BeanIntrospectionReference}:
     * one that loads Micronaut on its own, as the application class loader does under a test harness that runs Micronaut
     * in a class loader of its own, has introspections that cannot be cast to it, and a type from there is a lookup miss.
     */
    @Nullable
    private BeanIntrospectionReference<Object> findIntrospectionReference(Class<?> beanType, ClassLoader effectiveClassLoader) {
        String beanTypeName = beanType.getName();
        BeanIntrospectionReference<Object> reference = getIntrospections(effectiveClassLoader).get(beanTypeName);
        if (reference != null) {
            return reference;
        }
        ClassLoader beanClassLoader = beanType.getClassLoader();
        if (beanClassLoader != null && beanClassLoader != effectiveClassLoader && sharesBeanIntrospectionReference(beanClassLoader)) {
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

    /**
     * The fallbacks registered as services, in order, resolved with the same class loader the introspections
     * are resolved with so that a fallback and a generated introspection are never looked for in two places.
     * Only the fallbacks of the class loader of this introspector are cached, as {@link #getIntrospections()}
     * caches only that class loader's introspections.
     */
    private List<BeanIntrospectionFallback> getFallbacks(ClassLoader effectiveClassLoader) {
        if (effectiveClassLoader != classLoader) {
            return resolveFallbacks(effectiveClassLoader);
        }
        List<BeanIntrospectionFallback> resolvedFallbacks = this.fallbacks;
        if (resolvedFallbacks == null) {
            synchronized (this) { // double check
                resolvedFallbacks = this.fallbacks;
                if (resolvedFallbacks == null) {
                    resolvedFallbacks = resolveFallbacks(classLoader);
                    this.fallbacks = resolvedFallbacks;
                }
            }
        }
        return resolvedFallbacks;
    }

    private List<BeanIntrospectionFallback> resolveFallbacks(ClassLoader classLoader) {
        List<BeanIntrospectionFallback> resolvedFallbacks = SoftServiceLoader.load(BeanIntrospectionFallback.class, classLoader).collectAll();
        OrderUtil.sort(resolvedFallbacks);
        return List.copyOf(resolvedFallbacks);
    }

    private ClassLoader resolveClassLoader() {
        if (useContextClassLoader && Boolean.getBoolean(MICRONAUT_INTROSPECTIONS_USE_CONTEXT_CLASSLOADER)) {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            if (contextClassLoader != null && (contextClassLoader == classLoader || sharesBeanIntrospectionReference(contextClassLoader))) {
                return contextClassLoader;
            }
        }
        return classLoader;
    }

    /**
     * Whether the given class loader resolves {@link BeanIntrospectionReference} to this class' own, so that the
     * introspections and fallbacks it lists as services can be used here. A class loader that fails to answer, as a
     * restricted or custom class loader may with any runtime exception, is taken as not sharing them.
     *
     * @param otherClassLoader The class loader
     * @return Whether it shares the introspection types of this class
     */
    private static boolean sharesBeanIntrospectionReference(ClassLoader otherClassLoader) {
        try {
            return Class.forName(BeanIntrospectionReference.class.getName(), false, otherClassLoader) == BeanIntrospectionReference.class;
        } catch (ClassNotFoundException | RuntimeException | LinkageError e) {
            return false;
        }
    }

    /**
     * The introspections keyed by name, in a {@link HashMap}. {@link #findIntrospections(Predicate)} and
     * {@link #findIntrospectedTypes(Predicate)} iterate this map, so its order is what they return: a {@code HashMap}
     * of names iterates the same way on every run, where a {@code Map.copyOf} is randomized per JVM. The order is
     * kept as it was before the map was made immutable, since consumers came to depend on it.
     */
    private Map<String, BeanIntrospectionReference<Object>> resolveIntrospections(ClassLoader classLoader) {
        List<BeanIntrospectionReference<Object>> beanIntrospectionReferences = BeanIntrospectionProviders.get().provide(classLoader);
        Map<String, BeanIntrospectionReference<Object>> resolvedIntrospectionMap = CollectionUtils.newHashMap(beanIntrospectionReferences.size());
        for (BeanIntrospectionReference<Object> reference : beanIntrospectionReferences) {
            resolvedIntrospectionMap.put(reference.getName(), reference);
        }
        return Collections.unmodifiableMap(resolvedIntrospectionMap);
    }
}
