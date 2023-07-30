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
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanType;

import java.lang.annotation.Annotation;
import java.util.stream.Stream;

/**
 * Qualifies using an annotation.
 *
 * @param <T> Type
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class AnnotationQualifier<T> implements Qualifier<T> {

    final Annotation annotation;

    /**
     * @param annotation The qualifier
     */
    AnnotationQualifier(Annotation annotation) {
        this.annotation = annotation;
    }

    @Override
    public <BT extends BeanType<T>> Stream<BT> reduce(Class<T> beanType, Stream<BT> candidates) {
        String qualifiedName = annotation.annotationType().getName();
        String annotationSimpleName = annotation.annotationType().getSimpleName();
        return candidates.filter(candidate -> {
            if (!QualifierUtils.matchType(beanType, candidate)) {
                return false;
            }
            if (QualifierUtils.matchAny(beanType, candidate)) {
                return true;
            }
            if (candidate.getAnnotationMetadata().hasDeclaredAnnotation(qualifiedName)) {
                return true;
            }
            return QualifierUtils.matchByCandidateName(candidate, beanType, annotationSimpleName);
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
        return QualifierUtils.annotationQualifiersEquals(this, o);
    }

    @Override
    public int hashCode() {
        return annotation.hashCode();
    }

    @Override
    public String toString() {
        return '@' + annotation.annotationType().getSimpleName();
    }
}
