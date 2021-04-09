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

import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.BeanType;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Qualifier used to resolve the interceptor binding when injection method interceptors for AOP.
 *
 * @param <T> The bean type
 * @author graemerocher
 * @since 2.4.0
 */
@Internal
public final class InterceptorBindingQualifier<T> implements Qualifier<T> {

    private static final String META_MEMBER_INTERCEPTOR_TYPE = "interceptorType";
    private final Set<String> supportedAnnotationNames;
    private final Set<Class<?>> supportedInterceptorTypes;

    InterceptorBindingQualifier(AnnotationMetadata annotationMetadata) {
        final List<AnnotationValue<Annotation>> annotationValues = annotationMetadata
                .findAnnotation(AnnotationUtil.ANN_INTERCEPTOR_BINDING_QUALIFIER)
                .map(av -> av.getAnnotations(AnnotationMetadata.VALUE_MEMBER))
                .orElse(Collections.emptyList());
        this.supportedAnnotationNames = annotationValues
                .stream()
                .flatMap(av -> av.stringValue().map(Stream::of).orElse(Stream.empty()))
                .collect(Collectors.toSet());
        this.supportedInterceptorTypes = annotationValues
                .stream()
                .flatMap(av -> av.classValue(META_MEMBER_INTERCEPTOR_TYPE).map(Stream::of).orElse(Stream.empty()))
                .collect(Collectors.toSet());
    }

    /**
     * Interceptor binding qualifiers.
     * @param bindingAnnotations The binding annotations
     */
    InterceptorBindingQualifier(Collection<String> bindingAnnotations) {
        this.supportedAnnotationNames = CollectionUtils.isNotEmpty(bindingAnnotations) ? new HashSet<>(bindingAnnotations) : Collections.emptySet();
        this.supportedInterceptorTypes = Collections.emptySet();
    }

    @Override
    public <BT extends BeanType<T>> Stream<BT> reduce(Class<T> beanType, Stream<BT> candidates) {
        return candidates.filter(candidate -> {
            if (supportedInterceptorTypes.contains(candidate.getBeanType())) {
                return true;
            }
            List<String> annotationNames = new ArrayList<>(resolveInterceptorValues(candidate.getAnnotationMetadata()));
            annotationNames.retainAll(supportedAnnotationNames);
            return !annotationNames.isEmpty();
        });
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
        return Objects.hash(supportedAnnotationNames, supportedInterceptorTypes);
    }

    public static List<String> resolveInterceptorValues(AnnotationMetadata annotationMetadata) {
        AnnotationValue<?> bindings = annotationMetadata
                .getAnnotation(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS);
        if (bindings != null) {
            return bindings.getAnnotations(AnnotationMetadata.VALUE_MEMBER)
                    .stream()
                    .map(AnnotationValue::stringValue)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public String toString() {
        if (CollectionUtils.isEmpty(supportedAnnotationNames) && CollectionUtils.isEmpty(supportedInterceptorTypes)) {
            return "@InterceptorBinding(NONE)";
        } else {
            return supportedAnnotationNames.stream().map((name) -> "@InterceptorBinding(" + name + ")").collect(Collectors.joining( " ")) +
                    supportedInterceptorTypes.stream().map((name) -> "@InterceptorBinding(interceptorType = " + name + ")").collect(Collectors.joining( " "));
        }
    }
}
