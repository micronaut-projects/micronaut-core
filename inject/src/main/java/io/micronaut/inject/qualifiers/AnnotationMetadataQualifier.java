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
package io.micronaut.inject.qualifiers;

import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.BeanType;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A {@link io.micronaut.context.Qualifier} that uses {@link AnnotationMetadata}.
 *
 * @param <T> The type
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class AnnotationMetadataQualifier<T> implements Qualifier<T> {

    @NonNull
    final String annotationName;
    @NonNull
    final String annotationSimpleName;
    @Nullable
    final AnnotationValue<Annotation> qualifierAnn;

    /**
     * @param annotationMetadata The annotation metadata
     * @param name               The name
     */
    AnnotationMetadataQualifier(@NonNull AnnotationMetadata annotationMetadata,
                                @NonNull String name) {
        this(annotationMetadata, name, annotationMetadata.getAnnotationType(name).orElse(null));
    }

    /**
     * @param annotationMetadata The annotation metadata
     * @param annotationType     The name
     */
    AnnotationMetadataQualifier(@NonNull AnnotationMetadata annotationMetadata,
                                @NonNull Class<? extends Annotation> annotationType) {
        this(annotationMetadata, null, annotationType);
    }

    /**
     * @param annotationMetadata The annotation metadata
     * @param annotationType     The name
     */
    private AnnotationMetadataQualifier(@NonNull AnnotationMetadata annotationMetadata,
                                @Nullable String annotationTypeName,
                                @Nullable Class<? extends Annotation> annotationType) {
        if (annotationType == null) {
            Objects.requireNonNull(annotationTypeName, "Annotation name cannot be null");
            annotationType = annotationMetadata.getAnnotationType(annotationTypeName).orElse(null);
        }
        if (annotationType == null) {
            this.annotationName = annotationTypeName;
            this.annotationSimpleName = NameUtils.getSimpleName(annotationName);
        } else {
            this.annotationName = annotationType.getName();
            this.annotationSimpleName = annotationType.getSimpleName();
        }
        Set<String> nonBinding = resolveNonBindingMembers(annotationMetadata);
        Map<CharSequence, Object> bindingValues = resolveBindingValues(annotationMetadata, annotationName, nonBinding);
        if (CollectionUtils.isNotEmpty(bindingValues)) {
            qualifierAnn = new AnnotationValue<>(annotationName, bindingValues);
        } else {
            qualifierAnn = null;
        }
    }

    @Override
    public <BT extends BeanType<T>> Stream<BT> reduce(Class<T> beanType, Stream<BT> candidates) {
        return candidates.filter(candidate -> {
            if (!QualifierUtils.matchType(beanType, candidate)) {
                return false;
            }
            if (QualifierUtils.matchAny(beanType, candidate)) {
                return true;
            }
            if (qualifierAnn == null) {
                if (candidate.getAnnotationMetadata().hasDeclaredAnnotation(annotationName)) {
                    return true;
                }
            } else {
                final AnnotationMetadata annotationMetadata = candidate.getAnnotationMetadata();
                final AnnotationValue<Annotation> av = candidate.getAnnotation(annotationName);
                if (av != null) {
                    Set<String> nonBinding = resolveNonBindingMembers(annotationMetadata);
                    final Map<CharSequence, Object> values = resolveBindingValues(annotationMetadata, annotationName, nonBinding);
                    if (qualifierAnn.equals(new AnnotationValue<>(annotationName, values))) {
                        return true;
                    }
                }
            }
            return QualifierUtils.matchByCandidateName(candidate, beanType, annotationSimpleName);
        });
    }

    @Nullable
    private Map<CharSequence, Object> resolveBindingValues(AnnotationMetadata annotationMetadata, String qualifierName, Set<String> nonBinding) {
        Map<CharSequence, Object> bindingValues = annotationMetadata.getValues(qualifierName);
        if (nonBinding == null || bindingValues.isEmpty() || nonBinding.isEmpty()) {
            return bindingValues;
        }
        Map<CharSequence, Object> map = new HashMap<>();
        for (Map.Entry<CharSequence, Object> entry : bindingValues.entrySet()) {
            if (!nonBinding.contains(entry.getKey().toString()) && map.put(entry.getKey(), entry.getValue()) != null) {
                throw new IllegalStateException("Duplicate key: " + entry.getKey());
            }
        }
        return map;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        return QualifierUtils.annotationQualifiersEquals(this, o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(annotationName, qualifierAnn);
    }

    @NonNull
    private Set<String> resolveNonBindingMembers(AnnotationMetadata annotationMetadata) {
        final String[] nonBindingArray = annotationMetadata.stringValues(AnnotationUtil.QUALIFIER, "nonBinding");
        return ArrayUtils.isNotEmpty(nonBindingArray) ? new HashSet<>(Arrays.asList(nonBindingArray)) : Collections.emptySet();
    }

    @Override
    public String toString() {
        if (this.qualifierAnn != null) {
            return "@" + annotationSimpleName + "(" + qualifierAnn.getValues().entrySet().stream().map(entry -> entry.getKey() + "=" + valueToString(entry)).collect(Collectors.joining(", ")) + ")";
        }
        return "@" + annotationSimpleName;
    }

    private Object valueToString(Map.Entry<CharSequence, Object> entry) {
        final Object v = entry.getValue();
        if (v instanceof Object[]) {
            return Arrays.toString((Object[]) v);
        }
        return v;
    }
}
