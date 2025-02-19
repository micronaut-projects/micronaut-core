/*
 * Copyright 2017-2021 original authors
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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.ObjectUtils;
import io.micronaut.inject.BeanType;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Qualifier used to resolve the interceptor binding when injection method interceptors for AOP.
 *
 * @param <T> The bean type
 * @author graemerocher
 * @since 2.4.0
 */
@Internal
public final class InterceptorBindingQualifier<T> extends FilteringQualifier<T> {
    public static final String META_BINDING_VALUES = "$bindingValues";
    private static final String META_MEMBER_INTERCEPTOR_TYPE = "interceptorType";
    private final Map<String, List<AnnotationValue<?>>> supportedAnnotationNames;
    private final Set<Class<?>> supportedInterceptorTypes;

    InterceptorBindingQualifier(AnnotationMetadata annotationMetadata) {
        final Collection<AnnotationValue<?>> annotationValues;
        AnnotationValue<Annotation> av = annotationMetadata.findAnnotation(AnnotationUtil.ANN_INTERCEPTOR_BINDING_QUALIFIER).orElse(null);
        if (av == null) {
            annotationValues = Collections.emptyList();
        } else {
            annotationValues = (Collection) av.getAnnotations(AnnotationMetadata.VALUE_MEMBER);
        }
        supportedAnnotationNames = findSupportedAnnotations(annotationValues);
        Set<Class<?>> supportedInterceptorTypes = CollectionUtils.newHashSet(annotationValues.size());
        for (AnnotationValue<?> annotationValue : annotationValues) {
            annotationValue.classValue(META_MEMBER_INTERCEPTOR_TYPE).ifPresent(supportedInterceptorTypes::add);
        }
        this.supportedInterceptorTypes = supportedInterceptorTypes;
    }

    /**
     * Interceptor binding qualifiers.
     * @param bindingAnnotations The binding annotations
     */
    InterceptorBindingQualifier(Collection<AnnotationValue<?>> bindingAnnotations) {
        if (CollectionUtils.isNotEmpty(bindingAnnotations)) {
            supportedAnnotationNames = findSupportedAnnotations(bindingAnnotations);
        } else {
            this.supportedAnnotationNames = Collections.emptyMap();
        }
        this.supportedInterceptorTypes = Collections.emptySet();
    }

    private static Map<String, List<AnnotationValue<?>>> findSupportedAnnotations(Collection<AnnotationValue<?>> annotationValues) {
        final Map<String, List<AnnotationValue<?>>> supportedAnnotationNames = CollectionUtils.newHashMap(annotationValues.size());
        for (AnnotationValue<?> annotationValue : annotationValues) {
            final String name = annotationValue.stringValue().orElse(null);
            if (name == null) {
                continue;
            }
            final AnnotationValue<?> members = annotationValue.getAnnotation(META_BINDING_VALUES).orElse(null);
            if (members != null) {
                List<AnnotationValue<?>> existing = supportedAnnotationNames
                    .computeIfAbsent(name, k -> new ArrayList<>(5));
                existing.add(members);
            } else {
                supportedAnnotationNames.put(name, null);
            }
        }
        return supportedAnnotationNames;
    }

    @Override
    public boolean doesQualify(Class<T> beanType, BeanType<T> candidate) {
        if (supportedInterceptorTypes.contains(candidate.getBeanType())) {
            return true;
        }
        final AnnotationMetadata annotationMetadata = candidate.getAnnotationMetadata();
        Collection<AnnotationValue<Annotation>> interceptorValues = resolveInterceptorAnnotationValues(annotationMetadata, null);
        if (interceptorValues.isEmpty()) {
            return false;
        }
        if (interceptorValues.size() == 1) {
            // single binding case, fast path
            final AnnotationValue<?> interceptorBinding = interceptorValues.iterator().next();
            final String annotationName = interceptorBinding.stringValue().orElse(null);
            if (annotationName == null) {
                return false;
            }
            final List<AnnotationValue<?>> bindingList = supportedAnnotationNames.get(annotationName);
            if (bindingList != null) {
                final AnnotationValue<Annotation> otherBinding =
                    interceptorBinding.getAnnotation(META_BINDING_VALUES).orElse(null);
                boolean matched = true;
                for (AnnotationValue<?> binding : bindingList) {
                    matched = matched && (!binding.isPresent(META_BINDING_VALUES) || binding.equals(otherBinding));
                }
                return matched;
            } else {
                return supportedAnnotationNames.containsKey(annotationName);
            }
        } else {
            // multiple binding case
            boolean matched = false;
            for (AnnotationValue<?> annotation : interceptorValues) {
                final String annotationName = annotation.stringValue().orElse(null);
                if (annotationName == null) {
                    continue;
                }
                final List<AnnotationValue<?>> bindingList = supportedAnnotationNames.get(annotationName);
                if (bindingList != null) {
                    final AnnotationValue<Annotation> otherBinding =
                        annotation.getAnnotation(META_BINDING_VALUES).orElse(null);
                    for (AnnotationValue<?> binding : bindingList) {
                        matched = (!binding.isPresent(META_BINDING_VALUES) || binding.equals(otherBinding));
                        if (matched) {
                            break;
                        }
                    }
                } else {
                    matched = supportedAnnotationNames.containsKey(annotationName);
                }

                if (matched) {
                    break;
                }
            }
            return matched;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        InterceptorBindingQualifier<?> that = (InterceptorBindingQualifier<?>) o;
        return supportedAnnotationNames.equals(that.supportedAnnotationNames) && supportedInterceptorTypes.equals(that.supportedInterceptorTypes);
    }

    @Override
    public int hashCode() {
        return ObjectUtils.hash(supportedAnnotationNames, supportedInterceptorTypes);
    }

    @Override
    public String toString() {
        if (CollectionUtils.isEmpty(supportedAnnotationNames) && CollectionUtils.isEmpty(supportedInterceptorTypes)) {
            return "@InterceptorBinding(NONE)";
        } else {
            return supportedAnnotationNames.keySet().stream().map((name) -> "@InterceptorBinding(" + NameUtils.getShortenedName(name) + ")").collect(Collectors.joining(" ")) +
                    supportedInterceptorTypes.stream().map((type) -> "@InterceptorBinding(interceptorType = " + NameUtils.getShortenedName(type.getTypeName()) + ")").collect(Collectors.joining(" "));
        }
    }

    private static @NonNull Collection<AnnotationValue<Annotation>> resolveInterceptorAnnotationValues(
            @NonNull AnnotationMetadata annotationMetadata,
            @Nullable String kind) {
        List<AnnotationValue<Annotation>> bindings = annotationMetadata.getAnnotationValuesByName(AnnotationUtil.ANN_INTERCEPTOR_BINDING);
        if (CollectionUtils.isEmpty(bindings)) {
            return Collections.emptyList();
        }
        List<AnnotationValue<Annotation>> result = new ArrayList<>(bindings.size());
        for (AnnotationValue<Annotation> av : bindings) {
            if (av.stringValue().isEmpty()) {
                continue;
            }
            if (kind == null || av.stringValue("kind").orElse(kind).equals(kind)) {
                result.add(av);
            }
        }
        return result;
    }
}
