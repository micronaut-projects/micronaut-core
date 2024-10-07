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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static io.micronaut.core.annotation.AnnotationUtil.ZERO_ANNOTATIONS;

/**
 * Abstract implementation of the {@link AnnotationMetadata} interface.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
abstract class AbstractAnnotationMetadata implements AnnotationMetadata {

    private volatile Map<String, Annotation> annotationMap;
    private volatile Map<String, Annotation> declaredAnnotationMap;
    private volatile Annotation[] allAnnotationArray;
    private volatile Annotation[] declaredAnnotationArray;

    /**
     * Constructs a default metadata.
     */
    protected AbstractAnnotationMetadata() {
    }

    private Map<String, Annotation> getAnnotationMap() {
        Map<String, Annotation> map = annotationMap;
        if (map == null) {
            synchronized (this) { // double check
                map = annotationMap;
                if (map == null) {
                    annotationMap = new ConcurrentHashMap<>();
                    return annotationMap;
                }
            }
        }
        return annotationMap;
    }

    private Map<String, Annotation> getDeclaredAnnotationMap() {
        Map<String, Annotation> map = declaredAnnotationMap;
        if (map == null) {
            synchronized (this) { // double check
                map = declaredAnnotationMap;
                if (map == null) {
                    declaredAnnotationMap = new ConcurrentHashMap<>();
                    return declaredAnnotationMap;
                }
            }
        }
        return declaredAnnotationMap;
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nullable <T extends Annotation> T synthesize(@NonNull Class<T> annotationClass) {
        ArgumentUtils.requireNonNull("annotationClass", annotationClass);
        if (hasAnnotation(annotationClass) || hasStereotype(annotationClass)) {
            String annotationName = annotationClass.getName();
            return (T) getAnnotationMap().computeIfAbsent(annotationName, s -> {
                final AnnotationValue<T> annotationValue = findAnnotation(annotationClass).orElse(null);
                return AnnotationMetadataSupport.buildAnnotation(annotationClass, annotationValue);

            });
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <T extends Annotation> T synthesize(@NonNull Class<T> annotationClass, @NonNull String sourceAnnotation) {
        ArgumentUtils.requireNonNull("annotationClass", annotationClass);
        ArgumentUtils.requireNonNull("sourceAnnotation", sourceAnnotation);
        if (hasAnnotation(sourceAnnotation) || hasStereotype(sourceAnnotation)) {
            String annotationName = annotationClass.getName();
            return (T) getAnnotationMap().computeIfAbsent(annotationName, s -> {
                final AnnotationValue<T> annotationValue = (AnnotationValue<T>) findAnnotation(sourceAnnotation).orElse(null);
                return AnnotationMetadataSupport.buildAnnotation(annotationClass, annotationValue);

            });
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <T extends Annotation> T synthesizeDeclared(@NonNull Class<T> annotationClass, @NonNull String sourceAnnotation) {
        ArgumentUtils.requireNonNull("annotationClass", annotationClass);
        ArgumentUtils.requireNonNull("sourceAnnotation", sourceAnnotation);
        String annotationName = annotationClass.getName();
        if (hasDeclaredAnnotation(sourceAnnotation) || hasDeclaredStereotype(sourceAnnotation)) {
            return (T) getDeclaredAnnotationMap().computeIfAbsent(annotationName, s -> {
                final AnnotationValue<T> annotationValue = (AnnotationValue<T>) findDeclaredAnnotation(sourceAnnotation).orElse(null);
                return AnnotationMetadataSupport.buildAnnotation(annotationClass, annotationValue);

            });
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nullable <T extends Annotation> T synthesizeDeclared(@NonNull Class<T> annotationClass) {
        ArgumentUtils.requireNonNull("annotationClass", annotationClass);
        String annotationName = annotationClass.getName();
        if (hasDeclaredAnnotation(annotationName) || hasDeclaredStereotype(annotationName)) {
            return (T) getDeclaredAnnotationMap().computeIfAbsent(annotationName, s -> {
                final AnnotationValue<T> annotationValue = findDeclaredAnnotation(annotationClass).orElse(null);
                return AnnotationMetadataSupport.buildAnnotation(annotationClass, annotationValue);

            });
        }
        return null;
    }

    @Override
    public @NonNull Annotation[] synthesizeAll() {
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

    private Annotation[] initializeAnnotations(Set<String> names) {
        if (CollectionUtils.isEmpty(names)) {
            return AnnotationUtil.ZERO_ANNOTATIONS;
        }
        List<Annotation> annotations = new ArrayList<>(names.size());
        for (String name : names) {
            @SuppressWarnings("unchecked")
            Class<? extends Annotation> aClass = (Class<? extends Annotation>) ClassUtils.forName(name, getClass().getClassLoader()).orElse(null);
            if (aClass != null) {
                Annotation ann = synthesize(aClass);
                if (ann != null) {
                    annotations.add(ann);
                }
            }
        }
        return annotations.toArray(ZERO_ANNOTATIONS);
    }
}
