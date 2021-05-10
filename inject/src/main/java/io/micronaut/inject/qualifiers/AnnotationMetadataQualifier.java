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

    private final AnnotationMetadata annotationMetadata;
    private final Class<? extends Annotation> annotationType;
    private final String qualifiedName;

    /**
     * @param metadata The annotation metadata
     * @param name     The name
     */
    AnnotationMetadataQualifier(AnnotationMetadata metadata, String name) {
        super(name);
        this.annotationMetadata = metadata;
        this.annotationType = null;
        this.qualifiedName = null;
    }

    /**
     * @param metadata The annotation metadata
     * @param annotationType     The name
     */
    AnnotationMetadataQualifier(AnnotationMetadata metadata, Class<? extends Annotation> annotationType) {
        super(annotationType.getSimpleName());
        this.annotationMetadata = metadata;
        this.annotationType = annotationType;
        this.qualifiedName = annotationType.getName();
    }

    @Override
    public <BT extends BeanType<T>> Stream<BT> reduce(Class<T> beanType, Stream<BT> candidates) {
        if (annotationMetadata.hasDeclaredAnnotation(Any.class)) {
            return candidates;
        }
        String name;
        String v = annotationMetadata.stringValue(AnnotationMetadata.NAMED).orElse(null);
        if (StringUtils.isNotEmpty(v)) {
            name = Character.toUpperCase(v.charAt(0)) + v.substring(1);
            return reduceByName(beanType, candidates, name);
        } else {
            name = getName();
            final String qualifierName = annotationMetadata
                    .getAnnotationNameByStereotype(AnnotationMetadata.QUALIFIER).orElse(null);
            AnnotationValue<Annotation> qualifierAnn;
            if (qualifierName != null) {
                Set<String> nonBinding = resolveNonBindingMembers(annotationMetadata);
                Map<CharSequence, Object> bindingValues = resolveBindingValues(annotationMetadata, qualifierName, nonBinding);
                if (CollectionUtils.isNotEmpty(bindingValues)) {
                    qualifierAnn = new AnnotationValue<>(qualifierName, bindingValues);
                } else {
                    qualifierAnn = null;
                }
            } else {
                qualifierAnn = null;
            }
            final Stream<BT> reduced = reduceByAnnotation(beanType, candidates, name, qualifiedName);
            if (qualifierAnn != null) {
                return reduced
                        .filter(candidate -> {
                            final AnnotationMetadata annotationMetadata = candidate.getAnnotationMetadata();
                            final AnnotationValue<Annotation> av = candidate.getAnnotation(qualifierName);
                            if (av != null) {
                                Set<String> nonBinding = resolveNonBindingMembers(annotationMetadata);
                                final Map<CharSequence, Object> values = resolveBindingValues(annotationMetadata, qualifierName, nonBinding);
                                return qualifierAnn.equals(new AnnotationValue<>(qualifierName, values));
                            }
                            return false;
                        });
            }
            return reduced;
        }

    }

    private @Nullable
    Map<CharSequence, Object> resolveBindingValues(AnnotationMetadata annotationMetadata, String qualifierName, Set<String> nonBinding) {
        Map<CharSequence, Object> bindingValues = null;
        final AnnotationValue<Annotation> av = annotationMetadata.getAnnotation(qualifierName);
        if (av != null) {
            bindingValues = av.getValues();
            if (!nonBinding.isEmpty()) {
                bindingValues = bindingValues.entrySet()
                        .stream()
                        .filter((entry) -> !nonBinding.contains(entry.getKey().toString()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            }
        }
        return bindingValues;
    }

    @NonNull
    private Set<String> resolveNonBindingMembers(AnnotationMetadata annotationMetadata) {
        final String[] nonBindingArray = annotationMetadata.stringValues(AnnotationMetadata.QUALIFIER, "nonBinding");
        Set<String> nonBinding = ArrayUtils.isNotEmpty(nonBindingArray) ? new HashSet<>(Arrays.asList(nonBindingArray)) : Collections.emptySet();
        return nonBinding;
    }

    @Override
    public String toString() {
        return annotationType == null ? super.toString() : "@" + annotationType.getSimpleName();
    }
}
