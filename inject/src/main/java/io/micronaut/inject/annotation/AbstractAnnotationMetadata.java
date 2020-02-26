/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract implementation of the {@link AnnotationMetadata} interface.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
abstract class AbstractAnnotationMetadata implements AnnotationMetadata {

    protected final Map<String, Annotation> annotationMap;
    protected final Map<String, Annotation> declaredAnnotationMap;
    private Annotation[] allAnnotationArray;
    private Annotation[] declaredAnnotationArray;

    /**
     * Constructs an instance for the given arguments.
     *
     * @param declaredAnnotations The declared annotations
     * @param allAnnotations All annotations
     */
    protected AbstractAnnotationMetadata(@Nullable Map<String, Map<CharSequence, Object>> declaredAnnotations,
                                         @Nullable Map<String, Map<CharSequence, Object>> allAnnotations) {
        this.declaredAnnotationMap = declaredAnnotations != null ? new ConcurrentHashMap<>(declaredAnnotations.size()) : null;
        this.annotationMap = allAnnotations != null ? new ConcurrentHashMap<>(allAnnotations.size()) : null;
    }

    /**
     * Constructs a default metadata.
     */
    protected AbstractAnnotationMetadata() {
        annotationMap = new ConcurrentHashMap<>(2);
        declaredAnnotationMap = new ConcurrentHashMap<>(2);
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nullable <T extends Annotation> T synthesize(@NonNull Class<T> annotationClass) {
        ArgumentUtils.requireNonNull("annotationClass", annotationClass);
        if (annotationMap == null) {
            return null;
        }
        if (hasAnnotation(annotationClass) || hasStereotype(annotationClass)) {
            String annotationName = annotationClass.getName();
            return (T) annotationMap.computeIfAbsent(annotationName, s -> {
                final AnnotationValue<T> annotationValue = findAnnotation(annotationClass).orElse(null);
                return AnnotationMetadataSupport.buildAnnotation(annotationClass, annotationValue);

            });
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nullable <T extends Annotation> T synthesizeDeclared(@NonNull Class<T> annotationClass) {
        ArgumentUtils.requireNonNull("annotationClass", annotationClass);
        if (declaredAnnotationMap == null) {
            return null;
        }
        String annotationName = annotationClass.getName();
        if (hasAnnotation(annotationName) || hasStereotype(annotationName)) {
            return (T) declaredAnnotationMap.computeIfAbsent(annotationName, s -> {
                final AnnotationValue<T> annotationValue = findAnnotation(annotationClass).orElse(null);
                return AnnotationMetadataSupport.buildAnnotation(annotationClass, annotationValue);

            });
        }
        return null;
    }

    @Override
    public @NonNull Annotation[] synthesizeAll() {
        if (annotationMap == null) {
            return AnnotationUtil.ZERO_ANNOTATIONS;
        }
        Annotation[] annotations = this.allAnnotationArray;
        if (annotations == null) {
            synchronized (this) { // double check
                annotations = this.allAnnotationArray;
                if (annotations == null) {
                    annotations = initializeAnnotations(getAnnotationNames());
                    this.allAnnotationArray = annotations;
                }
            }
        }
        return annotations;
    }

    @Override
    public @NonNull Annotation[] synthesizeDeclared() {
        if (declaredAnnotationMap == null) {
            return AnnotationUtil.ZERO_ANNOTATIONS;
        }
        Annotation[] annotations = this.declaredAnnotationArray;
        if (annotations == null) {
            synchronized (this) { // double check
                annotations = this.declaredAnnotationArray;
                if (annotations == null) {
                    annotations = initializeAnnotations(getDeclaredAnnotationNames());
                    this.declaredAnnotationArray = annotations;
                }
            }
        }
        return annotations;
    }

    /**
     * Adds any annotation values found in the values map to the results.
     *
     * @param results The results
     * @param values The values
     */
    protected final void addAnnotationValuesFromData(List results, Map<CharSequence, Object> values) {
        if (values != null) {
            Object v = values.get(AnnotationMetadata.VALUE_MEMBER);
            if (v instanceof io.micronaut.core.annotation.AnnotationValue[]) {
                io.micronaut.core.annotation.AnnotationValue[] avs = (io.micronaut.core.annotation.AnnotationValue[]) v;
                for (io.micronaut.core.annotation.AnnotationValue av : avs) {
                    addValuesToResults(results, av);
                }
            } else if (v instanceof Collection) {
                Collection c = (Collection) v;
                for (Object o : c) {
                    if (o instanceof io.micronaut.core.annotation.AnnotationValue) {
                        addValuesToResults(results, ((io.micronaut.core.annotation.AnnotationValue) o));
                    }
                }
            }
        }
    }

    /**
     * Adds a values instance to the results.
     *
     * @param results The results
     * @param values The values
     */
    protected void addValuesToResults(List<io.micronaut.core.annotation.AnnotationValue> results, io.micronaut.core.annotation.AnnotationValue values) {
        results.add(values);
    }

    private Annotation[] initializeAnnotations(Set<String> names) {
        if (CollectionUtils.isNotEmpty(names)) {
            List<Annotation> annotations = new ArrayList<>();
            for (String name : names) {
                Optional<Class> loaded = ClassUtils.forName(name, getClass().getClassLoader());
                loaded.ifPresent(aClass -> {
                    Annotation ann = synthesize(aClass);
                    if (ann != null) {
                        annotations.add(ann);
                    }
                });
            }
            return annotations.toArray(new Annotation[0]);
        }

        return AnnotationUtil.ZERO_ANNOTATIONS;
    }
}
