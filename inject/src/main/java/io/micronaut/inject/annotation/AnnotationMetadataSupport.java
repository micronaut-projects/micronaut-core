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
package io.micronaut.inject.annotation;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.context.annotation.Aliases;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Configuration;
import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.PropertySource;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Provided;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requirements;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.context.annotation.Type;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueProvider;
import io.micronaut.core.annotation.Indexed;
import io.micronaut.core.annotation.Indexes;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.ReflectionConfig;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.StringUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Qualifier;
import jakarta.inject.Scope;
import jakarta.inject.Singleton;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Support method for {@link io.micronaut.core.annotation.AnnotationMetadata}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public final class AnnotationMetadataSupport {

    private static final Map<String, Map<CharSequence, Object>> ANNOTATION_DEFAULTS = new ConcurrentHashMap<>(20);
    private static final Map<String, String> REPEATABLE_ANNOTATIONS_CONTAINERS = new ConcurrentHashMap<>(20);

    private static final Map<Class<? extends Annotation>, Optional<Constructor<InvocationHandler>>> ANNOTATION_PROXY_CACHE = new ConcurrentHashMap<>(20);
    private static final Map<String, Class<? extends Annotation>> ANNOTATION_TYPES = new ConcurrentHashMap<>(20);

    static {
        // some common ones for startup optimization
        Arrays.asList(
                Nullable.class,
                NonNull.class,
                PreDestroy.class,
                PostConstruct.class,
                Named.class,
                Singleton.class,
                Inject.class,
                Qualifier.class,
                Scope.class,
                Prototype.class,
                Executable.class,
                Bean.class,
                Primary.class,
                Value.class,
                Property.class,
                Provided.class,
                Requires.class,
                Secondary.class,
                Type.class,
                Context.class,
                EachBean.class,
                EachProperty.class,
                Configuration.class,
                ConfigurationProperties.class,
                ConfigurationBuilder.class,
                Introspected.class,
                Parameter.class,
                Replaces.class,
                Requirements.class,
                Factory.class).forEach(ann ->
                ANNOTATION_TYPES.put(ann.getName(), ann)
        );


        for (Map.Entry<Class<? extends Annotation>, Class<? extends Annotation>> e : getCoreRepeatableAnnotations()) {
            REPEATABLE_ANNOTATIONS_CONTAINERS.put(e.getKey().getName(), e.getValue().getName());
        }

        REPEATABLE_ANNOTATIONS_CONTAINERS.put("io.micronaut.aop.InterceptorBinding", "io.micronaut.aop.InterceptorBindingDefinitions");
    }

    /**
     * @return core repeatable annotations
     */
    @Internal
    public static List<Map.Entry<Class<? extends Annotation>, Class<? extends Annotation>>> getCoreRepeatableAnnotations() {
        return Arrays.asList(
                new AbstractMap.SimpleEntry<>(Indexed.class, Indexes.class),
                new AbstractMap.SimpleEntry<>(Requires.class, Requirements.class),
                new AbstractMap.SimpleEntry<>(AliasFor.class, Aliases.class),
                new AbstractMap.SimpleEntry<>(Property.class, PropertySource.class),
                new AbstractMap.SimpleEntry<>(ReflectionConfig.class, ReflectionConfig.ReflectionConfigList.class)
        );
    }

    /**
     * @param annotation The annotation
     * @return The default values for the annotation
     */
    @UsedByGeneratedCode
    @NonNull
    public static Map<CharSequence, Object> getDefaultValues(String annotation) {
        return ANNOTATION_DEFAULTS.getOrDefault(annotation, Collections.emptyMap());
    }

    /**
     * @param annotation The annotation
     * @return The default values for the annotation
     */
    @Nullable
    public static Map<CharSequence, Object> getDefaultValuesOrNull(String annotation) {
        return ANNOTATION_DEFAULTS.get(annotation);
    }

    /**
     * @param annotation The annotation
     * @return The repeatable annotation container.
     */
    @Internal
    public static String getRepeatableAnnotation(String annotation) {
        return REPEATABLE_ANNOTATIONS_CONTAINERS.get(annotation);
    }

    /**
     * Gets a registered annotation type.
     *
     * @param name The name of the annotation type
     * @return The annotation
     */
    static Optional<Class<? extends Annotation>> getAnnotationType(String name) {
        return getAnnotationType(name, AnnotationMetadataSupport.class.getClassLoader());
    }

    /**
     * Gets a registered annotation type.
     *
     * @param name        The name of the annotation type
     * @param classLoader The classloader to retrieve the type
     * @return The annotation
     */
    static Optional<Class<? extends Annotation>> getAnnotationType(String name, ClassLoader classLoader) {
        final Class<? extends Annotation> type = ANNOTATION_TYPES.get(name);
        if (type != null) {
            return Optional.of(type);
        } else {
            // last resort, try dynamic load, shouldn't normally happen.
            @SuppressWarnings("unchecked") final Class<? extends Annotation> aClass =
                (Class<? extends Annotation>) ClassUtils.forName(name, classLoader).orElse(null);
            if (aClass != null && Annotation.class.isAssignableFrom(aClass)) {
                ANNOTATION_TYPES.put(name, aClass);
                return Optional.of(aClass);
            }
            return Optional.empty();
        }
    }

    /**
     * Gets a registered annotation type.
     *
     * @param name The name of the annotation type
     * @return The annotation
     */
    static Optional<Class<? extends Annotation>> getRegisteredAnnotationType(String name) {
        final Class<? extends Annotation> type = ANNOTATION_TYPES.get(name);
        if (type != null) {
            return Optional.of(type);
        }
        return Optional.empty();
    }

    /**
     * @param annotation The annotation
     * @return The default values for the annotation
     */
    @SuppressWarnings("unchecked")
    static Map<CharSequence, Object> getDefaultValues(Class<? extends Annotation> annotation) {
        return getDefaultValues(annotation.getName());
    }

    /**
     * Whether default values for the given annotation are present.
     *
     * @param annotation The annotation
     * @return True if they are
     */
    static boolean hasDefaultValues(String annotation) {
        return ANNOTATION_DEFAULTS.containsKey(annotation);
    }

    /**
     * Registers default values for the given annotation and values.
     *
     * @param annotation    The annotation
     * @param defaultValues The default values
     */
    static void registerDefaultValues(String annotation, Map<CharSequence, Object> defaultValues) {
        if (StringUtils.isNotEmpty(annotation)) {
            ANNOTATION_DEFAULTS.put(annotation, defaultValues);
        }
    }

    /**
     * Registers default values for the given annotation and values.
     *
     * @param annotation    The annotation
     * @param defaultValues The default values
     */
    static void registerDefaultValues(AnnotationClassValue<?> annotation, Map<CharSequence, Object> defaultValues) {
        if (defaultValues != null) {
            registerDefaultValues(annotation.getName(), defaultValues);
        }
        registerAnnotationType(annotation);
    }

    /**
     * Registers an annotation type.
     *
     * @param annotationClassValue the annotation class value
     */
    @SuppressWarnings("unchecked")
    static void registerAnnotationType(AnnotationClassValue<?> annotationClassValue) {
        final String name = annotationClassValue.getName();
        if (!ANNOTATION_TYPES.containsKey(name)) {
            Class<?> aClass = annotationClassValue.getType().orElse(null);
            if (aClass != null && Annotation.class.isAssignableFrom(aClass)) {
                ANNOTATION_TYPES.put(name, (Class<? extends Annotation>) aClass);
            }
        }
    }

    /**
     * Registers repeatable annotation containers.
     * @MyRepeatable -> @MyRepeatableContainer
     *
     * @param repeatableAnnotations the repeatable annotations
     */
    @Internal
    static void registerRepeatableAnnotations(Map<String, String> repeatableAnnotations) {
        REPEATABLE_ANNOTATIONS_CONTAINERS.putAll(repeatableAnnotations);
    }

    /**
     * Registers repeatable annotation containers.
     *
     * @param repeatable          the repeatable annotations
     * @param repeatableContainer the repeatable annotation container
     * @MyRepeatable -> @MyRepeatableContainer
     * @since 4.0.0
     */
    @Internal
    static void registerRepeatableAnnotation(@NonNull String repeatable, @NonNull String repeatableContainer) {
        REPEATABLE_ANNOTATIONS_CONTAINERS.put(repeatable, repeatableContainer);
    }

    /**
     * @param annotation The annotation
     * @return The proxy class
     */
    @SuppressWarnings("unchecked")
    static Optional<Constructor<InvocationHandler>> getProxyClass(Class<? extends Annotation> annotation) {
        return ANNOTATION_PROXY_CACHE.computeIfAbsent(annotation, aClass -> {
            Class proxyClass = Proxy.getProxyClass(annotation.getClassLoader(), annotation, AnnotationValueProvider.class);
            return ReflectionUtils.findConstructor(proxyClass, InvocationHandler.class);
        });
    }

    /**
     * Builds the annotation based on the annotation value.
     *
     * @param annotationClass The annotation class
     * @param annotationValue The annotation value
     * @param <T>             The type
     * @return The annotation
     */
    @Internal
    public static <T extends Annotation> T buildAnnotation(Class<T> annotationClass, @Nullable AnnotationValue<T> annotationValue) {
        Optional<Constructor<InvocationHandler>> proxyClass = getProxyClass(annotationClass);
        if (proxyClass.isPresent()) {
            Map<CharSequence, Object> values = new HashMap<>(getDefaultValues(annotationClass));
            if (annotationValue != null) {
                final Map<CharSequence, Object> annotationValues = annotationValue.getValues();
                annotationValues.forEach((key, o) -> values.put(key.toString(), o));
            }
            int hashCode = AnnotationUtil.calculateHashCode(values);

            Optional instantiated = InstantiationUtils.tryInstantiate(proxyClass.get(), (InvocationHandler) new AnnotationProxyHandler(hashCode, annotationClass, annotationValue));
            if (instantiated.isPresent()) {
                return (T) instantiated.get();
            }
        }
        throw new AnnotationMetadataException("Failed to build annotation for type: " + annotationClass.getName());
    }

    /**
     * Annotation proxy handler.
     *
     * @param <A> The annotation type
     */
    private static class AnnotationProxyHandler<A extends Annotation> implements InvocationHandler, AnnotationValueProvider<A> {
        private final int hashCode;
        private final Class<A> annotationClass;
        private final AnnotationValue<A> annotationValue;

        AnnotationProxyHandler(int hashCode, Class<A> annotationClass, @Nullable AnnotationValue<A> annotationValue) {
            this.hashCode = hashCode;
            this.annotationClass = annotationClass;
            this.annotationValue = annotationValue;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (!annotationClass.isInstance(obj)) {
                return false;
            }

            Annotation other = (Annotation) annotationClass.cast(obj);

            final AnnotationValue<?> otherValues = getAnnotationValues(other);

            if (this.annotationValue == null && otherValues == null) {
                return true;
            } else if (this.annotationValue == null || otherValues == null) {
                return false;
            } else {
                return annotationValue.equals(otherValues);
            }
        }

        private AnnotationValue<?> getAnnotationValues(Annotation other) {
            if (other instanceof AnnotationProxyHandler) {
                return ((AnnotationProxyHandler) other).annotationValue;
            }
            return null;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ((args == null || args.length == 0) && "hashCode".equals(name)) {
                return hashCode;
            } else if ((args != null && args.length == 1) && "equals".equals(name)) {
                return equals(args[0]);
            } else if ("annotationType".equals(name)) {
                return annotationClass;
            } else if (method.getReturnType() == AnnotationValue.class) {
                return annotationValue;
            } else if (annotationValue != null && annotationValue.contains(name)) {
                return annotationValue.getRequiredValue(name, method.getReturnType());
            }
            return method.getDefaultValue();
        }

        @NonNull
        @Override
        public AnnotationValue<A> annotationValue() {
            if (annotationValue != null) {
                return this.annotationValue;
            } else {
                return new AnnotationValue<A>(annotationClass.getName());
            }
        }
    }
}
