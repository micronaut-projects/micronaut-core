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

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanType;
import io.micronaut.inject.QualifiedBeanType;

import java.lang.annotation.Annotation;

/**
 * Qualifies using an annotation.
 *
 * @param <T> Type
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class AnnotationQualifier<T> extends FilteringQualifier<T> {

    final Annotation annotation;
    private String qualifiedName;
    private String annotationSimpleName;
    /**
     * @param annotation The qualifier
     */
    AnnotationQualifier(Annotation annotation) {
        this.annotation = annotation;
        this.qualifiedName = annotation.annotationType().getName();
        this.annotationSimpleName = annotation.annotationType().getSimpleName();
    }

    @Override
    public boolean doesQualify(Class<T> beanType, BeanType<T> candidate) {
        if (QualifierUtils.match(candidate, this)) {
            return true;
        }
        if (candidate.getAnnotationMetadata().hasDeclaredAnnotation(qualifiedName)) {
            return true;
        }
        return QualifierUtils.matchByCandidateName(candidate, beanType, annotationSimpleName);
    }

    @Override
    public boolean doesQualify(Class<T> beanType, QualifiedBeanType<T> candidate) {
        if (QualifierUtils.matchQualified(candidate, this)) {
            return true;
        }
        if (candidate.getAnnotationMetadata().hasDeclaredAnnotation(qualifiedName)) {
            return true;
        }
        return QualifierUtils.matchByCandidateName(candidate, beanType, annotationSimpleName);
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
