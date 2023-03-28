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
package io.micronaut.inject.ast.annotation;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.GenericElement;

/**
 * Element's annotation metadata factory.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
public interface ElementAnnotationMetadataFactory {

    /**
     * Build new element annotation metadata from the element.
     *
     * @param element The element
     * @return the element's metadata
     */
    @NonNull
    ElementAnnotationMetadata build(@NonNull Element element);

    /**
     * Build new class element type annotation metadata from the class element.
     *
     * @param element The element
     * @return the element's metadata
     */
    @NonNull
    ElementAnnotationMetadata buildTypeAnnotations(@NonNull ClassElement element);

    /**
     * Build new generic element type annotation metadata from the class element.
     *
     * @param element The element
     * @return the element's metadata
     */
    @NonNull
    ElementAnnotationMetadata buildGenericTypeAnnotations(@NonNull GenericElement element);

    /**
     * Build new mutable element annotation metadata.
     *
     * @param annotationMetadata The preloaded annotation
     * @return the element's metadata
     */
    @NonNull
    ElementAnnotationMetadata buildMutable(@NonNull AnnotationMetadata annotationMetadata);

    /**
     * Makes this factory read-only. No modification to the annotation metadata should be persisted into the shared cache.
     *
     * @return read-only factory
     */
    @NonNull
    ElementAnnotationMetadataFactory readOnly();

}
