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
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.BeanType;

import io.micronaut.core.annotation.Indexed;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;

import java.lang.annotation.Annotation;
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
     * The annotation type when this qualifier selects the beans that a self-indexed annotation is declared on,
     * which the compile-time index answers exhaustively. Null otherwise.
     */
    @Nullable
    private final Argument<?> indexedArgument;

    /**
     * @param stereotype The stereotype
     */
    AnnotationStereotypeQualifier(String stereotype) {
        this.stereotype = Objects.requireNonNull(stereotype, "Stereotype cannot be null");
        this.indexedArgument = null;
    }

    /**
     * @param stereotype The stereotype annotation
     */
    AnnotationStereotypeQualifier(Class<? extends Annotation> stereotype) {
        Objects.requireNonNull(stereotype, "Stereotype cannot be null");
        this.stereotype = stereotype.getName();
        this.indexedArgument = isSelfIndexed(stereotype) ? Argument.of(stereotype) : null;
    }

    /**
     * An annotation meta-annotated with {@link Indexed} by its own type indexes every bean it is declared on,
     * so the index holds all of them and nothing else.
     *
     * @param stereotype The stereotype annotation
     * @return True if the annotation indexes the beans carrying it by itself
     */
    private static boolean isSelfIndexed(Class<? extends Annotation> stereotype) {
        for (Indexed indexed : stereotype.getAnnotationsByType(Indexed.class)) {
            if (indexed.value() == stereotype) {
                return true;
            }
        }
        return false;
    }

    @Override
    @Nullable
    public Argument<?> getIndexedArgument() {
        return indexedArgument;
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
