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
import org.jspecify.annotations.Nullable;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static io.micronaut.core.annotation.AnnotationUtil.ZERO_ANNOTATIONS;

/**
 * Abstract implementation of the {@link AnnotationMetadata} interface.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
abstract class AbstractAnnotationMetadata implements AnnotationMetadata {

    @Nullable
    private volatile Map<String, Annotation> annotationMap;
    @Nullable
    private volatile Map<String, Annotation> declaredAnnotationMap;
    private volatile Annotation @Nullable [] allAnnotationArray;
    private volatile Annotation @Nullable [] declaredAnnotationArray;

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
        return Objects.requireNonNull(annotationMap);
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
        return Objects.requireNonNull(declaredAnnotationMap);
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nullable <T extends Annotation> T synthesize(Class<T> annotationClass) {
        ArgumentUtils.requireNonNull("annotationClass", annotationClass);
        if (hasAnnotation(annotationClass) || hasStereotype(annotationClass)) {
            return synthesizeCached(getAnnotationMap(), annotationClass, () -> findAnnotation(annotationClass).orElse(null));
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <T extends Annotation> T synthesize(Class<T> annotationClass, String sourceAnnotation) {
        ArgumentUtils.requireNonNull("annotationClass", annotationClass);
        ArgumentUtils.requireNonNull("sourceAnnotation", sourceAnnotation);
        if (hasAnnotation(sourceAnnotation) || hasStereotype(sourceAnnotation)) {
            return synthesizeCached(getAnnotationMap(), annotationClass, () -> (AnnotationValue<T>) findAnnotation(sourceAnnotation).orElse(null));
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <T extends Annotation> T synthesizeDeclared(Class<T> annotationClass, String sourceAnnotation) {
        ArgumentUtils.requireNonNull("annotationClass", annotationClass);
        ArgumentUtils.requireNonNull("sourceAnnotation", sourceAnnotation);
        if (hasDeclaredAnnotation(sourceAnnotation) || hasDeclaredStereotype(sourceAnnotation)) {
            return synthesizeCached(getDeclaredAnnotationMap(), annotationClass, () -> (AnnotationValue<T>) findDeclaredAnnotation(sourceAnnotation).orElse(null));
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nullable <T extends Annotation> T synthesizeDeclared(Class<T> annotationClass) {
        ArgumentUtils.requireNonNull("annotationClass", annotationClass);
        String annotationName = annotationClass.getName();
        if (hasDeclaredAnnotation(annotationName) || hasDeclaredStereotype(annotationName)) {
            return synthesizeCached(getDeclaredAnnotationMap(), annotationClass, () -> findDeclaredAnnotation(annotationClass).orElse(null));
        }
        return null;
    }

    /**
     * The annotation cached under the name of the given type, if it is of that very type, or else a new one. The same
     * name may be that of a type of another class loader, as when each deployment of a container defines its own copy
     * of an annotation: the annotation of the type first asked for is the one kept.
     */
    @SuppressWarnings("unchecked")
    private static <T extends Annotation> @Nullable T synthesizeCached(Map<String, Annotation> cache,
                                                                       Class<T> annotationClass,
                                                                       Supplier<@Nullable AnnotationValue<T>> annotationValue) {
        String annotationName = annotationClass.getName();
        Annotation cached = cache.get(annotationName);
        if (cached != null && cached.annotationType() == annotationClass) {
            return (T) cached;
        }
        T annotation = AnnotationMetadataSupport.buildAnnotation(annotationClass, annotationValue.get());
        if (cached == null && annotation != null) {
            Annotation previous = cache.putIfAbsent(annotationName, annotation);
            if (previous != null && previous.annotationType() == annotationClass) {
                return (T) previous;
            }
        }
        return annotation;
    }

    @Override
    public Annotation[] synthesizeAll() {
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
    public Annotation[] synthesizeDeclared() {
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
