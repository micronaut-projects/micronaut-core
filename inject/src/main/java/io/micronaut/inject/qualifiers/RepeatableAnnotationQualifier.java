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

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.BeanType;

/**
 * A qualifier for repeatable annotations.
 *
 * @author graemerocher
 * @since 3.1.0
 * @param <T> The bean type
 */
final class RepeatableAnnotationQualifier<T> implements Qualifier<T> {
    private final List<AnnotationValue<Annotation>> repeatableValues;
    private final String repeatableName;

    RepeatableAnnotationQualifier(AnnotationMetadata annotationMetadata, String repeatableName) {
        this.repeatableName = repeatableName;
        this.repeatableValues = annotationMetadata.findAnnotation(repeatableName)
                .map(av -> av.getAnnotations(AnnotationMetadata.VALUE_MEMBER))
                .orElse(Collections.emptyList());

        if (repeatableValues.isEmpty()) {
            throw new IllegalArgumentException("Repeatable qualifier [" + repeatableName + "] declared with no values");
        }
    }

    @Override
    public String toString() {
        return Arrays.toString(repeatableValues.toArray());
    }

    @Override
    public <BT extends BeanType<T>> Stream<BT> reduce(Class<T> beanType, Stream<BT> candidates) {
        return candidates.filter(candidate -> {
            final AnnotationValue<Annotation> declared = candidate.getAnnotationMetadata().getAnnotation(repeatableName);
            if (declared != null) {
                final List<AnnotationValue<Annotation>> repeated = declared.getAnnotations(AnnotationMetadata.VALUE_MEMBER);
                return repeated.containsAll(repeatableValues);
            }
            return false;
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
        RepeatableAnnotationQualifier<?> that = (RepeatableAnnotationQualifier<?>) o;
        return repeatableValues.equals(that.repeatableValues) && repeatableName.equals(that.repeatableName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(repeatableValues, repeatableName);
    }
}
