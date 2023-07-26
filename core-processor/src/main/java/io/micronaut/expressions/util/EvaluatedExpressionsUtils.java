/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.expressions.util;

import io.micronaut.core.expressions.EvaluatedExpressionReference;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * Utility class for working with annotation metadata containing
 * evaluated expressions.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public final class EvaluatedExpressionsUtils {

    /**
     * Finds evaluated expression references in provided annotation metadata,
     * including nested annotation values.
     *
     * @param annotationMetadata metadata to search references in
     * @return collection of expression references
     */
    public static Collection<EvaluatedExpressionReference> findEvaluatedExpressionReferences(AnnotationMetadata annotationMetadata) {
        return Stream.concat(
                annotationMetadata.getAnnotationNames().stream(),
                annotationMetadata.getStereotypeAnnotationNames().stream())
                   .map(annotationMetadata::getAnnotation)
                   .flatMap(annotation -> getNestedAnnotationValues(annotation).stream())
                   .flatMap(av -> av.getValues().values().stream())
                   .filter(EvaluatedExpressionReference.class::isInstance)
                   .map(EvaluatedExpressionReference.class::cast)
                   .distinct()
                   .toList();
    }

    private static Collection<AnnotationValue<?>> getNestedAnnotationValues(Object value) {
        List<AnnotationValue<?>> result = new ArrayList<>();
        if (value instanceof AnnotationValue annotationValue) {
            for (Object nestedValue: annotationValue.getValues().values()) {
                result.addAll(getNestedAnnotationValues(nestedValue));
            }
            result.add(annotationValue);
        } else {
            Iterable<?> nestedValues = null;
            if (value instanceof Iterable iterable) {
                nestedValues = iterable;
            } else if (value instanceof AnnotationValue<?>[] values) {
                nestedValues = Arrays.asList(values);
            }

            if (nestedValues != null) {
                for (Object nextValue: nestedValues) {
                    if (nextValue instanceof AnnotationValue) {
                        result.addAll(getNestedAnnotationValues(nextValue));
                    }
                }
            }
        }

        return result;
    }
}
