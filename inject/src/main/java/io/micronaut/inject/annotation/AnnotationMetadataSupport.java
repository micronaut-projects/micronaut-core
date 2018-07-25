/*
 * Copyright 2017-2018 original authors
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

import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
class AnnotationMetadataSupport {

    private static final Map<Class<? extends Annotation>, Optional<Constructor<InvocationHandler>>> ANNOTATION_PROXY_CACHE = new ConcurrentHashMap<>(20);
    private static final Map<String, Map<String, Object>> ANNOTATION_DEFAULTS = new ConcurrentHashMap<>(20);

    /**
     * @param annotation The annotation
     * @return The default values for the annotation
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> getDefaultValues(String annotation) {
        return ANNOTATION_DEFAULTS.computeIfAbsent(annotation, s -> Collections.EMPTY_MAP);
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
        if (StringUtils.isNotEmpty(annotation) && CollectionUtils.isNotEmpty(defaultValues)) {
            ANNOTATION_DEFAULTS.put(annotation.intern(), defaultValues);
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
     * @param annotationValues The annotation values
     * @param <T>              The type
     * @return The annotation
     */
    static <T extends Annotation> T buildAnnotation(Class<T> annotationClass, ConvertibleValues<Object> annotationValues) {
        Optional<Constructor<InvocationHandler>> proxyClass = getProxyClass(annotationClass);
        if (proxyClass.isPresent()) {
            Method[] declaredMethods = annotationClass.getDeclaredMethods();
            Map<CharSequence, Object> resolvedValues = new LinkedHashMap<>(declaredMethods.length);
            for (Method declaredMethod : declaredMethods) {
                String name = declaredMethod.getName();
                if (annotationValues.contains(name)) {
                    Optional<?> converted = annotationValues.get(name, declaredMethod.getReturnType());
                    converted.ifPresent(o -> resolvedValues.put(name, o));
                }
            }
            Map<String, Object> values = new HashMap<>(getDefaultValues(annotationClass));
            values.putAll(annotationValues.asMap());
            int hashCode = AnnotationUtil.calculateHashCode(values);

            Optional instantiated = InstantiationUtils.tryInstantiate(proxyClass.get(), (InvocationHandler) new AnnotationProxyHandler(hashCode, annotationClass, resolvedValues));
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

        private final Map<CharSequence, Object> resolvedValues;

        AnnotationProxyHandler(int hashCode, Class<?> annotationClass, Map<CharSequence, Object> resolvedValues) {
            this.hashCode = hashCode;
            this.annotationClass = annotationClass;
            this.resolvedValues = resolvedValues;
        }

        public Map<CharSequence, Object> getValues() {
            return resolvedValues;
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

            Map<CharSequence, Object> otherValues = getAnnotationValues(other);

            if (resolvedValues.size() != otherValues.size()) {
                return false;
            }

            // compare annotation member values
            for (Map.Entry<CharSequence, Object> member : resolvedValues.entrySet()) {
                Object value = member.getValue();
                Object otherValue = otherValues.get(member.getKey());

                if (!AnnotationUtil.areEqual(value, otherValue)) {
                    return false;
                }
            }

            return true;
        }

        private Map<CharSequence, Object> getAnnotationValues(Annotation other) {
            if (other instanceof AnnotationProxyHandler) {
                return ((AnnotationProxyHandler) other).resolvedValues;
            }
            return Collections.emptyMap();
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
            } else if (resolvedValues.containsKey(name)) {
                return resolvedValues.get(name);
            }
            return method.getDefaultValue();
        }
    }
}
