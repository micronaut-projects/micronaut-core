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
package io.micronaut.inject.annotation;

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The defaults an annotation type declares, read from the type itself and keyed by the type.
 *
 * <p>{@link AnnotationMetadataSupport#getDefaultValues(String)} answers from a registry keyed by annotation
 * name, which generated code fills in as classes load. That registry serves the accessors of metadata built at
 * compilation time, where the defaults are written into the generated class; it is the wrong source for anything
 * that has the annotation type in hand, for two reasons. It is mutable and filled in over the life of the
 * process, so the same question can be answered differently depending on what has been loaded, and it is keyed
 * by name, so the same annotation name defined by two class loaders resolves to whichever was registered last.</p>
 *
 * <p>What is read here is a property of the type alone: the {@link Method#getDefaultValue()} of each of its
 * members, converted into the shapes the metadata records values in. A {@link ClassValue} keys it by the class
 * rather than the name, so two deployments of the same annotation name keep their own defaults, and nothing
 * outside can change what a type answers.</p>
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Internal
public final class AnnotationDefaults {

    private static final ClassValue<List<Method>> MEMBERS = new ClassValue<>() {
        @Override
        protected List<Method> computeValue(Class<?> type) {
            List<Method> members = new ArrayList<>();
            for (Method method : type.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers()) && !method.isSynthetic() && method.getParameterCount() == 0) {
                    method.trySetAccessible();
                    members.add(method);
                }
            }
            // getDeclaredMethods gives no order, and the members are to come out the same on every run
            members.sort(Comparator.comparing(Method::getName));
            return Collections.unmodifiableList(members);
        }
    };

    private static final ClassValue<Map<CharSequence, Object>> DEFAULTS = new ClassValue<>() {
        @Override
        protected Map<CharSequence, Object> computeValue(Class<?> type) {
            Map<CharSequence, Object> defaults = new LinkedHashMap<>();
            for (Method member : MEMBERS.get(type)) {
                Object defaultValue = member.getDefaultValue();
                if (defaultValue != null) {
                    defaults.put(member.getName(), convert(defaultValue));
                }
            }
            return Collections.unmodifiableMap(defaults);
        }
    };

    private AnnotationDefaults() {
    }

    /**
     * The members of an annotation type, made accessible - the type may not be public - and ordered by name.
     *
     * @param annotationType The annotation type
     * @return The members, unmodifiable
     */
    public static List<Method> membersOf(Class<? extends Annotation> annotationType) {
        return MEMBERS.get(annotationType);
    }

    /**
     * The defaults of an annotation type, in the shapes the metadata records values in.
     *
     * @param annotationType The annotation type
     * @return The defaults by member name, unmodifiable and never null
     */
    public static Map<CharSequence, Object> of(Class<? extends Annotation> annotationType) {
        return DEFAULTS.get(annotationType);
    }

    /**
     * A member value as the metadata records it: a class as an {@link AnnotationClassValue}, an enum by the name
     * of its constant and a nested annotation as an {@link AnnotationValue}, arrays of each likewise.
     *
     * @param value The value read off the annotation type
     * @return The recorded form
     */
    public static Object convert(Object value) {
        if (value instanceof Class<?> type) {
            return new AnnotationClassValue<>(type);
        }
        if (value instanceof Enum<?> constant) {
            return constant.name();
        }
        if (value instanceof Annotation annotation) {
            return AnnotationValue.of(annotation);
        }
        if (value instanceof Class<?>[] types) {
            AnnotationClassValue<?>[] converted = new AnnotationClassValue[types.length];
            for (int i = 0; i < types.length; i++) {
                converted[i] = new AnnotationClassValue<>(types[i]);
            }
            return converted;
        }
        if (value instanceof Enum<?>[] constants) {
            String[] converted = new String[constants.length];
            for (int i = 0; i < constants.length; i++) {
                converted[i] = constants[i].name();
            }
            return converted;
        }
        if (value instanceof Annotation[] annotations) {
            AnnotationValue<?>[] converted = new AnnotationValue[annotations.length];
            for (int i = 0; i < annotations.length; i++) {
                converted[i] = AnnotationValue.of(annotations[i]);
            }
            return converted;
        }
        return value;
    }
}
