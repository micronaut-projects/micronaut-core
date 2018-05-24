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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.core.reflect.ReflectionUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

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
        Optional<Class> cls = ClassUtils.forName(annotation, AnnotationMetadataSupport.class.getClassLoader());
        return cls.map((Function<Class, Map>) AnnotationMetadataSupport::getDefaultValues).orElseGet(Collections::emptyMap);
    }

    /**
     * @param annotation The annotation
     * @return The default values for the annotation
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> getDefaultValues(Class<? extends Annotation> annotation) {
        return ANNOTATION_DEFAULTS.computeIfAbsent(annotation.getName().intern(), aClass -> {
            Map<String, Object> defaultValues = new LinkedHashMap<>();
            Method[] declaredMethods = annotation.getDeclaredMethods();
            for (Method declaredMethod : declaredMethods) {
                Object defaultValue = declaredMethod.getDefaultValue();
                if (defaultValue != null) {
                    defaultValues.put(declaredMethod.getName().intern(), defaultValue);
                }
            }
            return defaultValues;
        });
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
            int hashCode = calculateHashCode(values);

            Optional instantiated = InstantiationUtils.tryInstantiate(proxyClass.get(), (InvocationHandler) new AnnotationProxyHandler(hashCode, annotationClass, resolvedValues));
            if (instantiated.isPresent()) {
                return (T) instantiated.get();
            }
        }
        throw new AnnotationMetadataException("Failed to build annotation for type: " + annotationClass.getName());
    }

    /**
     * Calculates the hash code.
     *
     * @param values The map to calculate values' hash code
     * @return The hash code
     */
    @SuppressWarnings("MagicNumber")
    static int calculateHashCode(Map<? extends CharSequence, Object> values) {
        int hashCode = 0;

        for (Map.Entry<? extends CharSequence, Object> member : values.entrySet()) {
            Object value = member.getValue();

            int nameHashCode = member.getKey().hashCode();

            int valueHashCode =
                !value.getClass().isArray() ? value.hashCode() :
                    value.getClass() == boolean[].class ? Arrays.hashCode((boolean[]) value) :
                        value.getClass() == byte[].class ? Arrays.hashCode((byte[]) value) :
                            value.getClass() == char[].class ? Arrays.hashCode((char[]) value) :
                                value.getClass() == double[].class ? Arrays.hashCode((double[]) value) :
                                    value.getClass() == float[].class ? Arrays.hashCode((float[]) value) :
                                        value.getClass() == int[].class ? Arrays.hashCode((int[]) value) :
                                            value.getClass() == long[].class ? Arrays.hashCode(
                                                (long[]) value
                                            ) :
                                                value.getClass() == short[].class ? Arrays
                                                    .hashCode((short[]) value) :
                                                    Arrays.hashCode((Object[]) value);

            hashCode += 127 * nameHashCode ^ valueHashCode;
        }

        return hashCode;
    }

    /**
     * @param o1 One object
     * @param o2 Another object
     * @return Whether both objects are equal
     */
    static boolean areEqual(Object o1, Object o2) {
        return
            !o1.getClass().isArray() ? o1.equals(o2) :
                o1.getClass() == boolean[].class ? Arrays.equals((boolean[]) o1, (boolean[]) o2) :
                    o1.getClass() == byte[].class ? Arrays.equals((byte[]) o1, (byte[]) o2) :
                        o1.getClass() == char[].class ? Arrays.equals((char[]) o1, (char[]) o2) :
                            o1.getClass() == double[].class ? Arrays.equals(
                                (double[]) o1,
                                (double[]) o2
                            ) :
                                o1.getClass() == float[].class ? Arrays.equals(
                                    (float[]) o1,
                                    (float[]) o2
                                ) :
                                    o1.getClass() == int[].class ? Arrays.equals(
                                        (int[]) o1,
                                        (int[]) o2
                                    ) :
                                        o1.getClass() == long[].class ? Arrays.equals(
                                            (long[]) o1,
                                            (long[]) o2
                                        ) :
                                            o1.getClass() == short[].class ? Arrays.equals(
                                                (short[]) o1,
                                                (short[]) o2
                                            ) :
                                                Arrays.equals(
                                                    (Object[]) o1,
                                                    (Object[]) o2
                                                );
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

                if (!areEqual(value, otherValue)) {
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
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
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
