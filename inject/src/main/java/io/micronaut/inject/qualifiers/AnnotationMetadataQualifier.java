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

import io.micronaut.context.annotation.Any;
import io.micronaut.core.annotation.*;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.BeanType;
import jakarta.inject.Named;

import java.lang.annotation.Annotation;
import java.util.*;
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
class AnnotationMetadataQualifier<T> extends NameQualifier<T> {

    private static final String NAMED_SIMPLE = "Named";
    final AnnotationValue<Annotation> qualifierAnn;
    final String qualifiedName;
    private final AnnotationMetadata annotationMetadata;
    private final Set<String> nonBinding;

    /**
     * @param metadata The annotation metadata
     * @param name     The name
     */
    AnnotationMetadataQualifier(AnnotationMetadata metadata, String name) {
        super(metadata, name);
        this.annotationMetadata = metadata;
        this.qualifiedName = annotationType != null ? annotationType.getName() : name;
        if (AnnotationUtil.NAMED.equals(name) || Named.class.getName().equals(name)) {
            this.nonBinding = null;
            qualifierAnn = null;
        } else {
            this.nonBinding = resolveNonBindingMembers(annotationMetadata);
            Map<CharSequence, Object> bindingValues = resolveBindingValues(annotationMetadata, qualifiedName, nonBinding);
            if (CollectionUtils.isNotEmpty(bindingValues)) {
                qualifierAnn = new AnnotationValue<>(qualifiedName, bindingValues);
            } else {
                qualifierAnn = null;
            }
        }
    }

    /**
     * @param metadata The annotation metadata
     * @param annotationType     The name
     */
    AnnotationMetadataQualifier(AnnotationMetadata metadata, Class<? extends Annotation> annotationType) {
        super(annotationType);
        this.annotationMetadata = metadata;
        this.qualifiedName = annotationType.getName();
        if (!getName().equals(NAMED_SIMPLE)) {
            this.nonBinding = resolveNonBindingMembers(annotationMetadata);
            Map<CharSequence, Object> bindingValues = resolveBindingValues(annotationMetadata, qualifiedName, nonBinding);
            if (CollectionUtils.isNotEmpty(bindingValues)) {
                qualifierAnn = new AnnotationValue<>(qualifiedName, bindingValues);
            } else {
                qualifierAnn = null;
            }
        } else {
            this.nonBinding = null;
            qualifierAnn = null;
        }
    }

    @Override
    public <BT extends BeanType<T>> Stream<BT> reduce(Class<T> beanType, Stream<BT> candidates) {
        if (beanType != Object.class && annotationMetadata.hasDeclaredAnnotation(Any.class)) {
            return candidates;
        }
        String name;
        String v = annotationMetadata.stringValue(AnnotationUtil.NAMED).orElse(null);
        if (StringUtils.isNotEmpty(v)) {
            name = Character.toUpperCase(v.charAt(0)) + v.substring(1);
            return reduceByName(beanType, candidates, name);
        } else {
            name = getName();
            final Stream<BT> reduced = reduceByAnnotation(beanType, candidates, name, qualifiedName);
            if (qualifierAnn != null) {
                return reduced
                        .filter(candidate -> {
                            if (beanType != Object.class && candidate.getAnnotationMetadata().hasDeclaredAnnotation(Any.class)) {
                                return true;
                            }
                            final AnnotationMetadata annotationMetadata = candidate.getAnnotationMetadata();
                            final AnnotationValue<Annotation> av = candidate.getAnnotation(qualifiedName);
                            if (av != null) {
                                Set<String> nonBinding = resolveNonBindingMembers(annotationMetadata);
                                final Map<CharSequence, Object> values = resolveBindingValues(annotationMetadata, qualifiedName, nonBinding);
                                return qualifierAnn.equals(new AnnotationValue<>(qualifiedName, values));
                            }
                            return false;
                        });
            }
            return reduced;
        }

    }

    private @Nullable
    Map<CharSequence, Object> resolveBindingValues(AnnotationMetadata annotationMetadata, String qualifierName, Set<String> nonBinding) {
        Map<CharSequence, Object> bindingValues = annotationMetadata.getValues(qualifierName);
        if (nonBinding != null && !bindingValues.isEmpty()) {
            if (!nonBinding.isEmpty()) {
                bindingValues = bindingValues.entrySet()
                        .stream()
                        .filter((entry) -> !nonBinding.contains(entry.getKey().toString()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            }
        }
        return bindingValues;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        if (o instanceof AnnotationMetadataQualifier) {
            AnnotationMetadataQualifier<?> that = (AnnotationMetadataQualifier<?>) o;
            return qualifiedName.equals(that.qualifiedName) && Objects.equals(qualifierAnn, that.qualifierAnn);
        } else if (qualifierAnn == null && o instanceof NamedAnnotationStereotypeQualifier) {
            NamedAnnotationStereotypeQualifier<?> that = (NamedAnnotationStereotypeQualifier<?>) o;
            return qualifiedName.equals(that.stereotype);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), qualifiedName, qualifierAnn);
    }

    @NonNull
    private Set<String> resolveNonBindingMembers(AnnotationMetadata annotationMetadata) {
        final String[] nonBindingArray = annotationMetadata.stringValues(AnnotationUtil.QUALIFIER, "nonBinding");
        return ArrayUtils.isNotEmpty(nonBindingArray) ? new HashSet<>(Arrays.asList(nonBindingArray)) : Collections.emptySet();
    }

    @Override
    public String toString() {
        String annName = annotationType == null ? super.toString() : "@" + annotationType.getSimpleName();
        if (this.qualifierAnn != null) {
            final Map<CharSequence, Object> values = qualifierAnn.getValues();
            annName += "(" + values.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining(", ")) + ")";
        }
        return annName;
    }
}
