/*
 * Copyright 2017-2023 original authors
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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.MethodElement;

/**
 * The helper class to implement method element annotations.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
public final class MethodElementAnnotationsHelper {

    private final AbstractAnnotationElement methodElement;
    private final ElementAnnotationMetadataFactory elementAnnotationMetadataFactory;

    @Nullable
    private ElementAnnotationMetadata resolvedMethodAnnotationMetadata;
    @Nullable
    private AnnotationMetadata resolvedAnnotationMetadata;

    /**
     * The constructor.
     *
     * @param methodElement                    The method element
     * @param elementAnnotationMetadataFactory The annotations factory
     */
    public MethodElementAnnotationsHelper(AbstractAnnotationElement methodElement,
                                          ElementAnnotationMetadataFactory elementAnnotationMetadataFactory) {
        this.methodElement = methodElement;
        this.elementAnnotationMetadataFactory = elementAnnotationMetadataFactory;
    }

    /**
     * Returns the method annotations.
     *
     * @param presetAnnotationMetadata The preset annotations
     * @return The method annotations
     */
    @NonNull
    public ElementAnnotationMetadata getMethodAnnotationMetadata(@Nullable AnnotationMetadata presetAnnotationMetadata) {
        if (resolvedMethodAnnotationMetadata == null) {
            if (methodElement instanceof ConstructorElement) {
                resolvedMethodAnnotationMetadata = methodElement.getElementAnnotationMetadata();
            } else if (presetAnnotationMetadata instanceof AnnotationMetadataHierarchy annotationMetadataHierarchy) {
                // Preset overrides both class and method annotations
                AnnotationMetadata declaredMetadata = annotationMetadataHierarchy.getDeclaredMetadata();
                resolvedMethodAnnotationMetadata = getBuildMutable(declaredMetadata);
            } else if (presetAnnotationMetadata != null) {
                // Preset overrides method annotation
                resolvedMethodAnnotationMetadata = getBuildMutable(presetAnnotationMetadata);
            } else {
                resolvedMethodAnnotationMetadata = methodElement.getElementAnnotationMetadata();
            }
        }
        return resolvedMethodAnnotationMetadata;
    }

    @NonNull
    private ElementAnnotationMetadata getBuildMutable(@NonNull AnnotationMetadata declaredMetadata) {
        if (declaredMetadata instanceof ElementAnnotationMetadata elementAnnotationMetadata) {
            return elementAnnotationMetadata;
        }
        return elementAnnotationMetadataFactory.buildMutable(declaredMetadata);
    }

    /**
     * Returns the annotations.
     *
     * @param presetAnnotationMetadata The preset annotations
     * @return The annotations
     */
    @NonNull
    public AnnotationMetadata getAnnotationMetadata(@Nullable AnnotationMetadata presetAnnotationMetadata) {
        if (resolvedAnnotationMetadata == null) {
            if (methodElement instanceof ConstructorElement) {
                resolvedAnnotationMetadata = getMethodAnnotationMetadata(presetAnnotationMetadata);
            } else if (presetAnnotationMetadata instanceof AnnotationMetadataHierarchy annotationMetadataHierarchy) {
                // Preset overrides both class and method annotations
                resolvedAnnotationMetadata = new AnnotationMetadataHierarchy(annotationMetadataHierarchy.getRootMetadata(), getMethodAnnotationMetadata(presetAnnotationMetadata));
            } else if (presetAnnotationMetadata != null) {
                // Preset overrides method annotation
                resolvedAnnotationMetadata = new MutatedMethodElementAnnotationMetadata((MethodElement) methodElement, getMethodAnnotationMetadata(presetAnnotationMetadata));
            } else {
                // Combine class and method annotations
                resolvedAnnotationMetadata = new MethodElementAnnotationMetadata((MethodElement) methodElement);
            }
        }
        return resolvedAnnotationMetadata;
    }

}
