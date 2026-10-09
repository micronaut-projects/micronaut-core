/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.context.watch;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.ExecutableMethod;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;
import java.util.Set;

/**
 * Compares the annotation metadata of two generations of the same element.
 */
@Internal
@NullMarked
final class MetadataComparison {

    private MetadataComparison() {
    }

    /**
     * Whether two metadata carry the same annotations with the same values.
     *
     * @param before The metadata of the retired element
     * @param after The metadata of the replacement
     * @return True if nothing an annotation says differs
     */
    static boolean same(AnnotationMetadata before, AnnotationMetadata after) {
        return sameValues(before.getAnnotationNames(), before, after.getAnnotationNames(), after)
            // a composed annotation's stereotypes carry values of their own, such as the fixedDelay of the
            // @Scheduled a @Poll annotation is meta-annotated with, which change while the annotation stays
            && sameValues(before.getStereotypeAnnotationNames(), before, after.getStereotypeAnnotationNames(), after);
    }

    /**
     * Whether two generations of a method carry the same annotations: on the method, on each parameter and on
     * its return type, type arguments included.
     *
     * @param before The retired method
     * @param after The replacement
     * @return True if nothing an annotation of the method, its parameters or its return type says differs
     */
    static boolean sameMethod(ExecutableMethod<?, ?> before, ExecutableMethod<?, ?> after) {
        if (!same(before.getAnnotationMetadata(), after.getAnnotationMetadata())) {
            return false;
        }
        Argument<?>[] previous = before.getArguments();
        Argument<?>[] current = after.getArguments();
        if (previous.length != current.length) {
            return false;
        }
        for (int i = 0; i < previous.length; i++) {
            if (!sameArgument(previous[i], current[i])) {
                return false;
            }
        }
        return sameArgument(before.getReturnType().asArgument(), after.getReturnType().asArgument());
    }

    private static boolean sameArgument(Argument<?> before, Argument<?> after) {
        if (!same(before.getAnnotationMetadata(), after.getAnnotationMetadata())) {
            return false;
        }
        Argument<?>[] previous = before.getTypeParameters();
        Argument<?>[] current = after.getTypeParameters();
        if (previous.length != current.length) {
            return false;
        }
        for (int i = 0; i < previous.length; i++) {
            if (!sameArgument(previous[i], current[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameValues(Set<String> names, AnnotationMetadata before, Set<String> afterNames, AnnotationMetadata after) {
        if (!names.equals(afterNames)) {
            return false;
        }
        for (String name : names) {
            AnnotationValue<?> previous = before.getAnnotation(name);
            AnnotationValue<?> current = after.getAnnotation(name);
            if (!Objects.equals(previous, current)) {
                return false;
            }
        }
        return true;
    }
}
