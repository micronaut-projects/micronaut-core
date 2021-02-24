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
import io.micronaut.inject.BeanType;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
final class InterceptorBindingQualifier<T> implements Qualifier<T> {

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
                .flatMap(av -> av.classValue("interceptorType").map(Stream::of).orElse(Stream.empty()))
                .collect(Collectors.toSet());
    }

    @Override
    public <BT extends BeanType<T>> Stream<BT> reduce(Class<T> beanType, Stream<BT> candidates) {
        return candidates.filter(candidate -> supportedInterceptorTypes.contains(candidate.getBeanType()) ||
                candidate.getAnnotationMetadata()
                    .findAnnotation(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS)
                    .map(av -> av.getAnnotations(AnnotationMetadata.VALUE_MEMBER).stream())
                    .orElse(Stream.empty())
                    .anyMatch(av ->
                        av.stringValue().map(supportedAnnotationNames::contains)
                            .orElse(false)
                    ));
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
}
