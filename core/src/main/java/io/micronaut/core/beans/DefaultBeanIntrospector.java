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
import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.ArgumentUtils;
import org.slf4j.Logger;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;
import java.util.HashMap;
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

    private Map<String, BeanIntrospectionReference<Object>> introspectionMap;

    @NonNull
    @Override
    public Collection<BeanIntrospection<Object>> findIntrospections(@NonNull Predicate<? super BeanIntrospectionReference> filter) {
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
    public <T> Optional<BeanIntrospection<T>> findIntrospection(@NonNull Class<T> beanType) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        final BeanIntrospectionReference reference = getIntrospections().get(beanType.getName());
        try {
            if (reference != null) {
                return Optional.of(reference).map((Function<BeanIntrospectionReference, BeanIntrospection<T>>) ref -> {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Found BeanIntrospection for type: " + ref.getBeanType());
                    }
                    return ref.load();
                });
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("No BeanIntrospection found for bean type: " + beanType);
                }
                return Optional.empty();
            }
        } catch (Throwable e) {
            throw new IntrospectionException("Error loading BeanIntrospection for type [" + beanType + "]: " + e.getMessage(), e);
        }
    }

    private Map<String, BeanIntrospectionReference<Object>> getIntrospections() {
        Map<String, BeanIntrospectionReference<Object>> introspectionMap = this.introspectionMap;
        if (introspectionMap == null) {
            synchronized (this) { // double check
                introspectionMap = this.introspectionMap;
                if (introspectionMap == null) {
                    introspectionMap = new HashMap<>(30);
                    final SoftServiceLoader<BeanIntrospectionReference> services = SoftServiceLoader.load(BeanIntrospectionReference.class);

                    for (ServiceDefinition<BeanIntrospectionReference> definition : services) {
                        if (definition.isPresent()) {
                            final BeanIntrospectionReference ref = definition.load();
                            introspectionMap.put(ref.getName(), ref);
                        } else {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("BeanIntrospection {} not loaded since associated bean is not present on the classpath", definition.getName());
                            }
                        }
                    }

                    this.introspectionMap = introspectionMap;
                }
            }
        }
        return introspectionMap;
    }
}
