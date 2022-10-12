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

import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.*;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.StringUtils;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Qualifier;
import jakarta.inject.Scope;
import jakarta.inject.Singleton;

import javax.validation.constraints.NotNull;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Support method for {@link io.micronaut.core.annotation.AnnotationMetadata}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public final class AnnotationMetadataSupport {

    private static final Map<String, Map<String, Object>> ANNOTATION_DEFAULTS = new ConcurrentHashMap<>(20);
    private static final Map<String, String> REPEATABLE_ANNOTATIONS = new ConcurrentHashMap<>(20);

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
            REPEATABLE_ANNOTATIONS.put(e.getKey().getName(), e.getValue().getName());
        }

        REPEATABLE_ANNOTATIONS.put("io.micronaut.aop.InterceptorBinding", "io.micronaut.aop.InterceptorBindingDefinitions");
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
                new AbstractMap.SimpleEntry<>(Property.class, PropertySource.class)
        );
    }

    /**
     * @param annotation The annotation
     * @return The default values for the annotation
     */
    @UsedByGeneratedCode
    public static Map<String, Object> getDefaultValues(String annotation) {
        return ANNOTATION_DEFAULTS.getOrDefault(annotation, Collections.emptyMap());
    }

    /**
     * @param annotation The annotation
     * @return The repeatable annotation container.
     */
    @Internal
    public static String getRepeatableAnnotation(String annotation) {
        return REPEATABLE_ANNOTATIONS.get(annotation);
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
            final Optional<Class> aClass = ClassUtils.forName(name, classLoader);
            return aClass.flatMap((Function<Class, Optional<Class<? extends Annotation>>>) aClass1 -> {
                if (Annotation.class.isAssignableFrom(aClass1)) {
                    //noinspection unchecked
                    ANNOTATION_TYPES.put(name, aClass1);
                    return Optional.of(aClass1);
                }
                return Optional.empty();
            });
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
    static Map<String, Object> getDefaultValues(Class<? extends Annotation> annotation) {
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
    static void registerDefaultValues(String annotation, Map<String, Object> defaultValues) {
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
    static void registerDefaultValues(AnnotationClassValue<?> annotation, Map<String, Object> defaultValues) {
        if (defaultValues != null) {
            registerDefaultValues(annotation.getName(), defaultValues);
        }
        registerAnnotationType(annotation);
    }

    /**
     * Registers a annotation type.
     *
     * @param annotationClassValue the annotation class value
     */
    @SuppressWarnings("unchecked")
    static void registerAnnotationType(AnnotationClassValue<?> annotationClassValue) {
        final String name = annotationClassValue.getName();
        if (!ANNOTATION_TYPES.containsKey(name)) {
            annotationClassValue.getType().ifPresent((Consumer<Class<?>>) aClass -> {
                if (Annotation.class.isAssignableFrom(aClass)) {
                    ANNOTATION_TYPES.put(name, (Class<? extends Annotation>) aClass);
                }
            });
        }
    }

    /**
     * Registers repeatable annotations.
     *
     * @param repeatableAnnotations the repeatable annotations
     */
    @Internal
    static void registerRepeatableAnnotations(Map<String, String> repeatableAnnotations) {
        REPEATABLE_ANNOTATIONS.putAll(repeatableAnnotations);
    }

    /**
     * Remove Core repeatable annotations.
     *
     * @param repeatableAnnotations the repeatable annotations
     */
    @Internal
    static void removeCoreRepeatableAnnotations(@NotNull Map<String, String> repeatableAnnotations) {
        for (Map.Entry<Class<? extends Annotation>, Class<? extends Annotation>> e : getCoreRepeatableAnnotations()) {
            repeatableAnnotations.remove(e.getKey().getName());
        }
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
     * @param annotationClass The annotation class
     * @param annotationValue The annotation value
     * @param <T>             The type
     * @return The annotation
     */
    static <T extends Annotation> T buildAnnotation(Class<T> annotationClass, @Nullable AnnotationValue<T> annotationValue) {
        Optional<Constructor<InvocationHandler>> proxyClass = getProxyClass(annotationClass);
        if (proxyClass.isPresent()) {
            Map<String, Object> values = new HashMap<>(getDefaultValues(annotationClass));
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
