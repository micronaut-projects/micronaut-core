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
package io.micronaut.reflection;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospectionFallback;
import io.micronaut.core.reflect.ClassUtils;

import java.util.Optional;

/**
 * The {@link BeanIntrospectionFallback} describing reflectively the types the shared introspector has no
 * generated introspection for, when {@link ReflectionIntrospectionPolicy} allows them. Registered as a service
 * by this module.
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Experimental
public final class ReflectionBeanIntrospectionFallback implements BeanIntrospectionFallback {

    private static final ClassValue<ReflectionBeanIntrospection<?>> INTROSPECTIONS = new ClassValue<>() {
        @Override
        protected ReflectionBeanIntrospection<?> computeValue(Class<?> type) {
            if (ClassUtils.REFLECTION_LOGGER.isDebugEnabled()) {
                ClassUtils.REFLECTION_LOGGER.debug("Reflectively introspecting '{}', which has no generated BeanIntrospection", type.getName());
            }
            return ReflectionBeanIntrospection.of(type);
        }
    };

    /**
     * Creates the fallback; it is instantiated by the service loader.
     */
    public ReflectionBeanIntrospectionFallback() {
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<BeanIntrospection<T>> findIntrospection(Class<T> beanType) {
        if (!ReflectionBeanIntrospection.isIntrospectable(beanType) || !ReflectionIntrospectionPolicy.isAllowed(beanType)) {
            return Optional.empty();
        }
        return Optional.of((BeanIntrospection<T>) INTROSPECTIONS.get(beanType));
    }

    @Override
    public String toString() {
        return "ReflectionBeanIntrospectionFallback";
    }
}
