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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.BeanType;

import java.util.Objects;
import java.util.Optional;

/**
 * Qualifiers for a stereotype.
 * @param <T> The generic type
 * @since 3.0.0
 * @author graemerocher
 */
@Internal
final class AnnotationStereotypeQualifier<T> extends FilteringQualifier<T> {

    final String stereotype;

    /**
     * @param stereotype The stereotype
     */
    AnnotationStereotypeQualifier(@NonNull String stereotype) {
        this.stereotype = Objects.requireNonNull(stereotype, "Stereotype cannot be null");
    }

    @Override
    public boolean doesQualify(Class<T> beanType, BeanType<T> candidate) {
        AnnotationMetadata annotationMetadata = candidate.getAnnotationMetadata();
        Optional<String> repeatableAnnotation = annotationMetadata.findRepeatableAnnotation(stereotype);
        if (repeatableAnnotation.isPresent()) {
            return annotationMetadata.hasStereotype(repeatableAnnotation.get());
        }
        return annotationMetadata.hasStereotype(stereotype);
    }

    @Override
    public String toString() {
        return "@" + NameUtils.getSimpleName(stereotype);
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
        return stereotype.hashCode();
    }
}
