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
package io.micronaut.core.type;

import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.NonNull;

import java.util.Optional;

/**
 * Interface that implementors can hook into to control the logic of methods like {@link Argument#isReactive()}.
 *
 * @author graemerocher
 * @since 2.4.0
 */
public interface TypeInformationProvider {
    /**
     * Returns whether the annotation metadata specifies the type as single.
     * @param annotationMetadataProvider The annotation metadata provider
     * @return True if it does
     */
    default boolean isSpecifiedSingle(@NonNull AnnotationMetadataProvider annotationMetadataProvider) {
        return false;
    }

    /**
     * does the given type represent a type that emits a single item.
     * @param type True if it does
     * @return True if it is single
     */
    default boolean isSingle(@NonNull Class<?> type) {
        return false;
    }

    /**
     * does the type represent a reactive type.
     * @param type The type
     * @return True if it is reactive
     */
    default boolean isReactive(@NonNull Class<?> type) {
        return false;
    }

    /**
     * does the type represent a completable type.
     * @param type The type
     * @return True if it is completable
     */
    default boolean isCompletable(@NonNull Class<?> type) {
        return false;
    }

    /**
     * Does the type represent a wrapper type.
     * @param type The type
     * @return True if it is a wrapper type
     * @see TypeInformation#isWrapperType()
     */
    default boolean isWrapperType(Class<?> type) {
        return type == Optional.class;
    }
}
