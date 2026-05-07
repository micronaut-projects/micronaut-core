/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.python.processing.visitor;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.ClassElement;

import java.util.List;
import java.util.Objects;

/**
 * Represents a member of a Python annotation (decorator parameter).
 * This record implements ElementDef to provide annotation member information
 * for Micronaut's annotation processing system.
 *
 * @param name       The name of the annotation member
 * @param memberType The member type of the annotation
 * @author Micronaut Team
 * @since 5.0.0
 */
public record AnnotationMemberDef(String name,
                                  @Nullable ClassElement memberType,
                                  @Nullable AnnotationMetadata annotationMetadata,
                                  @Nullable List<DecoratorDef> decorators) implements ElementDef, AnnotationMetadataProvider {
    public AnnotationMemberDef(String name,
                               @Nullable ClassElement memberType,
                               @Nullable AnnotationMetadata annotationMetadata) {
        this(name, memberType, annotationMetadata, List.of());
    }

    @Override
    public List<DecoratorDef> decorators() {
        return decorators;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        if (annotationMetadata == null) {
            return AnnotationMetadata.EMPTY_METADATA;
        }
        return annotationMetadata;
    }

    public AnnotationMemberDef {
        Objects.requireNonNull(name, "Annotation member name cannot be null");
        if (decorators == null) {
            decorators = List.of();
        } else {
            decorators = List.copyOf(decorators);
        }
    }
}
