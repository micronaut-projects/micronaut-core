/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.core.annotation;


import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * An interface for types capable of resolving {@link AnnotationMetadata}.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface AnnotationMetadataResolver {

    /**
     * The default resolver.
     */
    AnnotationMetadataResolver DEFAULT = new AnnotationMetadataResolver() {
    };

    /**
     * Resolve the {@link AnnotationMetadata} for the given type.
     *
     * @param type The type
     * @return The {@link AnnotationMetadata}
     */
    default @NonNull AnnotationMetadata resolveMetadata(@Nullable Class<?> type) {
        return AnnotationMetadata.EMPTY_METADATA;
    }

    /**
     * Resolve the {@link AnnotationMetadata} for the given object.
     *
     * @param object The object
     * @return The {@link AnnotationMetadata}
     */
    default @NonNull AnnotationMetadata resolveMetadata(Object object) {
        return resolveMetadata(object != null ? object.getClass() : null);
    }
}
