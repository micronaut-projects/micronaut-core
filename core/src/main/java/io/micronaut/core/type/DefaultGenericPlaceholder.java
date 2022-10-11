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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;

/**
 * Implementation of {@link GenericPlaceholder}.
 *
 * @param <T> The generic type
 * @since 3.2.0
 */
@Internal
final class DefaultGenericPlaceholder<T>
        extends DefaultArgument<T>
        implements GenericPlaceholder<T> {
    private final String variableName;

    /**
     * Constructor for where the variable name and argument name are the same.
     *
     * @param type               The type
     * @param name               The name
     * @param annotationMetadata The annotation metadata
     * @param genericTypes       The generic types
     */
    DefaultGenericPlaceholder(
            Class<T> type,
            String name,
            AnnotationMetadata annotationMetadata,
            Argument<?>... genericTypes) {
        super(type, name, annotationMetadata, genericTypes);
        this.variableName = name;
    }

    /**
     * Constructor for where the variable name and argument name differ.
     *
     * @param type               The type
     * @param name               The name
     * @param variableName       The variable name
     * @param annotationMetadata The annotation metadata
     * @param genericTypes       The generic types
     */
    DefaultGenericPlaceholder(
            Class<T> type,
            String name,
            String variableName,
            AnnotationMetadata annotationMetadata,
            Argument<?>... genericTypes) {
        super(type, name, annotationMetadata, genericTypes);
        this.variableName = variableName;
    }

    @Override
    public String getVariableName() {
        return variableName;
    }

    @Override
    public boolean isTypeVariable() {
        return true;
    }
}
