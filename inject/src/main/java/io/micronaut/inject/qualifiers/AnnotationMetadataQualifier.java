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
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanType;
import io.micronaut.inject.DelegatingBeanDefinition;

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
final class AnnotationMetadataQualifier<T> implements Qualifier<T> {

    @NonNull
    final String annotationName;
    @NonNull
    final String annotationSimpleName;
    @Nullable
    final AnnotationValue<Annotation> qualifierAnn;

    private AnnotationMetadataQualifier(@NonNull String annotationName,
                                        @NonNull String annotationSimpleName,
                                        @Nullable AnnotationValue<Annotation> value) {
        this.annotationName = annotationName;
        this.annotationSimpleName = annotationSimpleName;
        this.qualifierAnn = value;
    }

    static <T> AnnotationMetadataQualifier<T> fromType(@NonNull AnnotationMetadata annotationMetadata,
                                                       @NonNull Class<? extends Annotation> annotationType) {
        return new AnnotationMetadataQualifier<>(
            annotationType.getName(),
            annotationType.getSimpleName(),
            resolveBindingAnnotationValue(annotationMetadata, annotationType.getName())
        );
    }

    static <T> AnnotationMetadataQualifier<T> fromTypeName(@NonNull AnnotationMetadata annotationMetadata,
                                                           @NonNull String annotationTypeName) {
        return new AnnotationMetadataQualifier<>(
            annotationTypeName,
            NameUtils.getSimpleName(annotationTypeName),
            resolveBindingAnnotationValue(annotationMetadata, annotationTypeName)
        );
    }

    static <T extends Annotation> AnnotationMetadataQualifier<T> fromValue(@NonNull AnnotationMetadata annotationMetadata,
                                                                           @NonNull AnnotationValue<T> annotationValue) {
        return new AnnotationMetadataQualifier<>(
            annotationValue.getAnnotationName(),
            NameUtils.getSimpleName(annotationValue.getAnnotationName()),
            resolveBindingAnnotationValue(annotationMetadata, annotationValue.getAnnotationName(), annotationValue.getValues())
        );
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
            if (candidate instanceof BeanDefinition) {
                BeanDefinition<T> bdCandidate = (BeanDefinition<T>) candidate;
                Qualifier<T> candidateDeclaredQualifier = bdCandidate.getDeclaredQualifier();
                if (candidateDeclaredQualifier != null && candidateDeclaredQualifier.contains(this)) {
                    return true;
                }
                if (candidate instanceof DelegatingBeanDefinition) {
                    if (matchByAnnotationMetadata(candidate)) {
                        return true;
                    }
                }
            } else if (matchByAnnotationMetadata(candidate)) {
                return true;
            }
            return QualifierUtils.matchByCandidateName(candidate, beanType, annotationSimpleName);
        });
    }

    private <BT extends BeanType<T>> boolean matchByAnnotationMetadata(BT candidate) {
        if (qualifierAnn == null) {
            return candidate.getAnnotationMetadata().hasAnnotation(annotationName);
        }
        return qualifierAnn.equals(resolveBindingAnnotationValue(candidate.getAnnotationMetadata()));
    }

    @Nullable
    private <K extends Annotation> AnnotationValue<K> resolveBindingAnnotationValue(AnnotationMetadata annotationMetadata) {
        return resolveBindingAnnotationValue(annotationMetadata, annotationName, annotationMetadata.getValues(annotationName));
    }

    @Nullable
    private static <K extends Annotation> AnnotationValue<K> resolveBindingAnnotationValue(AnnotationMetadata annotationMetadata,
                                                                                           String annotationName) {
        return resolveBindingAnnotationValue(annotationMetadata, annotationName, annotationMetadata.getValues(annotationName));
    }

    @Nullable
    private static <K extends Annotation> AnnotationValue<K> resolveBindingAnnotationValue(AnnotationMetadata annotationMetadata,
                                                                                           String annotationName,
                                                                                           Map<CharSequence, Object> values) {
        Map<CharSequence, Object> bindingValues = resolveBindingValues(annotationMetadata, values);
        if (CollectionUtils.isNotEmpty(bindingValues)) {
            return new AnnotationValue<>(annotationName, bindingValues);
        }
        return null;
    }

    @Nullable
    private static Map<CharSequence, Object> resolveBindingValues(AnnotationMetadata annotationMetadata,
                                                                  Map<CharSequence, Object> values) {
        Set<String> nonBinding = resolveNonBindingMembers(annotationMetadata);
        if (values.isEmpty() || nonBinding.isEmpty()) {
            return values;
        }
        Map<CharSequence, Object> map = new HashMap<>();
        for (Map.Entry<CharSequence, Object> entry : values.entrySet()) {
            if (!nonBinding.contains(entry.getKey().toString()) && map.put(entry.getKey(), entry.getValue()) != null) {
                throw new IllegalStateException("Duplicate key: " + entry.getKey());
            }
        }
        return map;
    }

    @NonNull
    private static Set<String> resolveNonBindingMembers(AnnotationMetadata annotationMetadata) {
        final String[] nonBindingArray = annotationMetadata.stringValues(AnnotationUtil.QUALIFIER, "nonBinding");
        return ArrayUtils.isNotEmpty(nonBindingArray) ? new HashSet<>(Arrays.asList(nonBindingArray)) : Collections.emptySet();
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
