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
        Set<String> names = before.getAnnotationNames();
        if (!names.equals(after.getAnnotationNames())) {
            return false;
        }
        for (String name : names) {
            AnnotationValue<?> previous = before.getAnnotation(name);
            AnnotationValue<?> current = after.getAnnotation(name);
            if (!Objects.equals(previous, current)) {
                return false;
            }
        }
        return before.getStereotypeAnnotationNames().equals(after.getStereotypeAnnotationNames());
    }
}
