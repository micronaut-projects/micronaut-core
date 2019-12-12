/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.*;
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
class AnnotationMetadataSupport {

    private static final Map<String, Map<String, Object>> ANNOTATION_DEFAULTS = new ConcurrentHashMap<>(20);

    private static final Map<Class<? extends Annotation>, Optional<Constructor<InvocationHandler>>> ANNOTATION_PROXY_CACHE = new ConcurrentHashMap<>(20);
    private static final Map<String, Class<? extends Annotation>> ANNOTATION_TYPES = new ConcurrentHashMap<>(20);

    static {
        // some common ones for startup optimization
        Arrays.asList(
                Nullable.class,
                Nonnull.class,
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
    }

    /**
     * @param annotation The annotation
     * @return The default values for the annotation
     */
    static Map<String, Object> getDefaultValues(String annotation) {
        return ANNOTATION_DEFAULTS.computeIfAbsent(annotation, s -> Collections.emptyMap());
    }

    /**
     * Gets a registered annotation type.
     *
     * @param name The name of the annotation type
     * @return The annotation
     */
    static Optional<Class<? extends Annotation>> getAnnotationType(String name) {
        final Class<? extends Annotation> type = ANNOTATION_TYPES.get(name);
        if (type != null) {
            return Optional.of(type);
        } else {
            // last resort, try dynamic load, shouldn't normally happen.
            final Optional<Class> aClass = ClassUtils.forName(name, AnnotationMetadataSupport.class.getClassLoader());
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
     * @param annotation The annotation
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
     * @param annotation The annotation
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
     * @param annotation The annotation
     * @return The proxy class
     */
    @SuppressWarnings("unchecked")
    static Optional<Constructor<InvocationHandler>> getProxyClass(Class<? extends Annotation> annotation) {
        return ANNOTATION_PROXY_CACHE.computeIfAbsent(annotation, aClass -> {
            Class proxyClass = Proxy.getProxyClass(annotation.getClassLoader(), annotation);
            return ReflectionUtils.findConstructor(proxyClass, InvocationHandler.class);
        });
    }

    /**
     * @param annotationClass  The annotation class
     * @param annotationValue The annotation value
     * @param <T>              The type
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
     */
    private static class AnnotationProxyHandler implements InvocationHandler {
        private final int hashCode;
        private final Class<?> annotationClass;
        private final AnnotationValue<?> annotationValue;

        AnnotationProxyHandler(int hashCode, Class<?> annotationClass, @Nullable AnnotationValue<?> annotationValue) {
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
            } else if (annotationValue != null && annotationValue.contains(name)) {
                return annotationValue.getRequiredValue(name, method.getReturnType());
            }
            return method.getDefaultValue();
        }
    }
}
