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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospectionFallback;
import io.micronaut.core.reflect.ClassUtils;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

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

    private static final String[] PLATFORM_PACKAGES = {
        "java.", "javax.", "jakarta.", "jdk.", "sun.", "com.sun.", "kotlin.", "groovy.", "scala."
    };

    // a lookup miss is frequent - InstantiationUtils, BeanWrapper and the binders ask for a type they expect
    // not to find - so what a miss costs is what this fallback costs. The cache therefore holds the whole
    // outcome and not only the introspections: a type this fallback cannot describe, and a type whose
    // description fails, are remembered as not served, so neither reflecting on the class nor a failure that
    // will happen again is repeated. What the cache holds is what the configuration in force at the time
    // yielded, so the version it was built at is held with it and the entry is rebuilt once that has moved;
    // the policy is still asked on every call, as a type is allowed while a context runs and by an allow(...)
    // call at any time.
    private static final ClassValue<AtomicReference<Described>> INTROSPECTIONS = new ClassValue<>() {

        @Override
        protected AtomicReference<Described> computeValue(Class<?> type) {
            return new AtomicReference<>();
        }
    };

    /**
     * Creates the fallback; it is instantiated by the service loader.
     */
    public ReflectionBeanIntrospectionFallback() {
        // empty on purpose - the service loader needs a no-arg constructor and there is no state to set up
    }

    @SuppressWarnings("java:S1181")
    private static Optional<ReflectionBeanIntrospection<?>> describe(Class<?> type) {
        if (!ReflectionBeanIntrospection.isIntrospectable(type) || isPlatformType(type)) {
            return Optional.empty();
        }
        if (ClassUtils.REFLECTION_LOGGER.isDebugEnabled()) {
            ClassUtils.REFLECTION_LOGGER.debug("Reflectively introspecting '{}', which has no generated BeanIntrospection", type.getName());
        }
        try {
            return Optional.of(ReflectionBeanIntrospection.of(type, configured(type)));
        } catch (Exception | LinkageError e) {
            // describing a class reads every declared member, which throws NoClassDefFoundError when a
            // member's type is an optional dependency the application does not have: a type this fallback
            // cannot serve, not a lookup that should fail. An error that says nothing about the type -
            // the stack or the heap running out - is not caught, as remembering the type as not served
            // would outlive the condition that caused it
            if (ClassUtils.REFLECTION_LOGGER.isDebugEnabled()) {
                ClassUtils.REFLECTION_LOGGER.debug("Cannot reflectively introspect '{}', it is not served", type.getName(), e);
            }
            return Optional.empty();
        }
    }

    /**
     * The annotations an application configured for a type, under the ones the type declares itself. A
     * configuration names types in bulk, by pattern, so it says how the types that say nothing of themselves
     * are to be described and does not displace what one of them declares; the annotations of the class are
     * therefore merged over the configured ones here, rather than handed to
     * {@link ReflectionBeanIntrospection#of(Class, AnnotationMetadata)} as annotations that win.
     */
    private static AnnotationMetadata configured(Class<?> type) {
        AnnotationMetadata configured = ReflectionIntrospectionPolicy.describe(type);
        if (configured.isEmpty()) {
            return AnnotationMetadata.EMPTY_METADATA;
        }
        return ReflectionAnnotations.merge(ReflectionAnnotations.metadataOf(type), configured);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<BeanIntrospection<T>> findIntrospection(Class<T> beanType) {
        if (!ReflectionIntrospectionPolicy.isAllowed(beanType)) {
            return Optional.empty();
        }
        AtomicReference<Described> cached = INTROSPECTIONS.get(beanType);
        Described described = cached.get();
        long version = ReflectionIntrospectionPolicy.version();
        if (described == null || described.version() != version) {
            // two threads describing the same type at once describe it the same way, so either answer serves;
            // the version is read before describing, so a change during it is not recorded as newer than it is
            described = new Described(version, describe(beanType));
            cached.set(described);
        }
        if (described.introspection().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of((BeanIntrospection<T>) described.introspection().get());
    }

    /**
     * Whether a type belongs to the platform or to a language running on it, which this fallback never
     * describes: it serves the types an application allowed of its own, and a caller meaning to reflect on a
     * platform type says so through {@link ReflectionBeanIntrospection#of(Class)} instead.
     */
    private static boolean isPlatformType(Class<?> type) {
        String name = type.getName();
        for (String platformPackage : PLATFORM_PACKAGES) {
            if (name.startsWith(platformPackage)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "ReflectionBeanIntrospectionFallback";
    }

    /**
     * What describing a type yielded, and the version of the configuration it was described at.
     *
     * @param version       The version of the configuration the type was described at
     * @param introspection The introspection, empty when the type is not served
     */
    @Internal
    private record Described(long version, Optional<ReflectionBeanIntrospection<?>> introspection) {
    }
}
