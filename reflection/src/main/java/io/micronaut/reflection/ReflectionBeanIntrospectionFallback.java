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

    // a lookup miss is frequent - InstantiationUtils, BeanWrapper and the binders ask for a type they expect
    // not to find - so what a miss costs is what this fallback costs. The cache therefore holds the whole
    // outcome and not only the introspections: a type this fallback cannot describe, and a type whose
    // description fails, are remembered as not served, so neither reflecting on the class nor a failure that
    // will happen again is repeated. Only the part of the answer that cannot change is cached; the policy is
    // asked on every call, as a type is allowed while a context runs and by an allow(...) call at any time.
    private static final ClassValue<Optional<ReflectionBeanIntrospection<?>>> INTROSPECTIONS = new ClassValue<>() {

        @Override
        @SuppressWarnings("java:S1181")
        protected Optional<ReflectionBeanIntrospection<?>> computeValue(Class<?> type) {
            if (!ReflectionBeanIntrospection.isIntrospectable(type)) {
                return Optional.empty();
            }
            if (ClassUtils.REFLECTION_LOGGER.isDebugEnabled()) {
                ClassUtils.REFLECTION_LOGGER.debug("Reflectively introspecting '{}', which has no generated BeanIntrospection", type.getName());
            }
            try {
                return Optional.of(ReflectionBeanIntrospection.of(type));
            } catch (Throwable e) {
                // describing a class reads every declared member, which throws NoClassDefFoundError when a
                // member's type is an optional dependency the application does not have: a type this fallback
                // cannot serve, not a lookup that should fail
                if (ClassUtils.REFLECTION_LOGGER.isDebugEnabled()) {
                    ClassUtils.REFLECTION_LOGGER.debug("Cannot reflectively introspect '{}', it is not served", type.getName(), e);
                }
                return Optional.empty();
            }
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
        if (!ReflectionIntrospectionPolicy.isAllowed(beanType)) {
            return Optional.empty();
        }
        Optional<ReflectionBeanIntrospection<?>> introspection = INTROSPECTIONS.get(beanType);
        if (introspection.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of((BeanIntrospection<T>) introspection.get());
    }

    @Override
    public String toString() {
        return "ReflectionBeanIntrospectionFallback";
    }
}
