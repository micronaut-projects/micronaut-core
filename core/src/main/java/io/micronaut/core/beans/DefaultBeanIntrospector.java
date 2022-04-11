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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.ArgumentUtils;
import org.slf4j.Logger;

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

    private Map<String, BeanIntrospectionReference<Object>> introspectionMap;
    private final ClassLoader classLoader;

    DefaultBeanIntrospector() {
        this.classLoader = DefaultBeanIntrospector.class.getClassLoader();
    }

    DefaultBeanIntrospector(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @NonNull
    @Override
    public Collection<BeanIntrospection<Object>> findIntrospections(@NonNull Predicate<? super BeanIntrospectionReference<?>> filter) {
        ArgumentUtils.requireNonNull("filter", filter);
        return getIntrospections()
                .values()
                .stream()
                .filter(filter)
                .map(BeanIntrospectionReference::load)
                .collect(Collectors.toList());
    }

    @NonNull
    @Override
    public Collection<Class<?>> findIntrospectedTypes(@NonNull Predicate<? super BeanIntrospectionReference<?>> filter) {
        ArgumentUtils.requireNonNull("filter", filter);
        return getIntrospections()
                .values()
                .stream()
                .filter(filter)
                .map(BeanIntrospectionReference::getBeanType)
                .collect(Collectors.toSet());
    }

    @NonNull
    @Override
    @SuppressWarnings("java:S1181")
    public <T> Optional<BeanIntrospection<T>> findIntrospection(@NonNull Class<T> beanType) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        @SuppressWarnings("unchecked") final BeanIntrospectionReference<T> reference =
                (BeanIntrospectionReference<T>) getIntrospections().get(beanType.getName());
        try {
            if (reference != null) {
                return Optional.of(reference).map((Function<BeanIntrospectionReference<T>, BeanIntrospection<T>>) ref -> {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Found BeanIntrospection for type: {},", ref.getBeanType());
                    }
                    return ref.load();
                });
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("No BeanIntrospection found for bean type: {}", beanType);
                }
                return Optional.empty();
            }
        } catch (Throwable e) {
            throw new IntrospectionException("Error loading BeanIntrospection for type [" + beanType + "]: " + e.getMessage(), e);
        }
    }

    private Map<String, BeanIntrospectionReference<Object>> getIntrospections() {
        Map<String, BeanIntrospectionReference<Object>> resolvedIntrospectionMap = this.introspectionMap;
        if (resolvedIntrospectionMap == null) {
            synchronized (this) { // double check
                resolvedIntrospectionMap = this.introspectionMap;
                if (resolvedIntrospectionMap == null) {
                    resolvedIntrospectionMap = new HashMap<>(30);
                    @SuppressWarnings("unchecked") final SoftServiceLoader<BeanIntrospectionReference<Object>> services = loadReferences();
                    List<BeanIntrospectionReference<Object>> beanIntrospectionReferences = new ArrayList<>(300);
                    services.collectAll(
                            beanIntrospectionReferences,
                            BeanIntrospectionReference::isPresent
                    );
                    for (BeanIntrospectionReference<Object> reference : beanIntrospectionReferences) {
                        resolvedIntrospectionMap.put(reference.getName(), reference);
                    }
                    this.introspectionMap = resolvedIntrospectionMap;
                }
            }
        }
        return resolvedIntrospectionMap;
    }

    @SuppressWarnings("java:S3740")
    private SoftServiceLoader loadReferences() {
        return SoftServiceLoader.load(BeanIntrospectionReference.class, classLoader);
    }
}
