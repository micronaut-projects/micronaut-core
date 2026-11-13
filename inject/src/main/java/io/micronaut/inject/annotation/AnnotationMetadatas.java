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
package io.micronaut.inject.annotation;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

/**
 * Combines and materializes annotation metadata at runtime.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class AnnotationMetadatas {

    private AnnotationMetadatas() {
    }

    /**
     * Merges levels of metadata into one view in which every level is declared. The levels are read as they are,
     * not copied: a shared generated metadata is never altered.
     *
     * <ul>
     *     <li>A single occurrence, as {@link AnnotationMetadata#findAnnotation(String)} and the member accessors such
     *     as {@link AnnotationMetadata#stringValue(String)} read it, is the annotation of every level holding it with
     *     its members merged: a member the last level sets wins. Its retained stereotypes are those of the last level
     *     holding the annotation.</li>
     *     <li>The occurrences, as {@link AnnotationMetadata#getAnnotationValuesByName(String)},
     *     {@link AnnotationMetadata#getAnnotationValuesByType(Class)} and the plural member accessors such as
     *     {@link AnnotationMetadata#stringValues(String, String)} read them, are those of every level, the last level
     *     first, whether or not the annotation is repeatable.</li>
     *     <li>{@link AnnotationMetadata#getDeclaredMetadata()} is the merged view itself, every level being declared.</li>
     * </ul>
     *
     * <p>An empty level is skipped. No level gives {@link AnnotationMetadata#EMPTY_METADATA}, a single one is
     * returned itself.</p>
     *
     * @param levelsLastWins The levels, the one to win last
     * @return The merged metadata
     */
    public static AnnotationMetadata merge(AnnotationMetadata... levelsLastWins) {
        List<AnnotationMetadata> present = new ArrayList<>(levelsLastWins.length);
        for (AnnotationMetadata level : levelsLastWins) {
            if (level != null && !level.isEmpty()) {
                present.add(level);
            }
        }
        return switch (present.size()) {
            case 0 -> AnnotationMetadata.EMPTY_METADATA;
            case 1 -> present.get(0);
            // the constructor reverses the array it is given, so it gets its own
            default -> new AnnotationMetadataHierarchy(true, true, present.toArray(AnnotationMetadata[]::new));
        };
    }

    /**
     * Merges levels of metadata, see {@link #merge(AnnotationMetadata...)}.
     *
     * @param levelsLastWins The levels, the one to win last
     * @return The merged metadata
     */
    public static AnnotationMetadata merge(List<? extends AnnotationMetadata> levelsLastWins) {
        return merge(levelsLastWins.toArray(AnnotationMetadata[]::new));
    }

    /**
     * Materializes an annotation value as an instance of its annotation type: with the generated annotation builder
     * of the type when there is one and the value has no expressions to evaluate, else with a proxy. Members the
     * value does not set answer their defaults; the internal members and the retained stereotypes are left out.
     *
     * @param annotationType The annotation type
     * @param value          The value, or null for an instance with the defaults
     * @param <T>            The annotation type
     * @return The annotation
     */
    public static <T extends Annotation> T synthesize(Class<T> annotationType, @Nullable AnnotationValue<T> value) {
        return AnnotationMetadataSupport.buildAnnotation(annotationType, value);
    }
}
