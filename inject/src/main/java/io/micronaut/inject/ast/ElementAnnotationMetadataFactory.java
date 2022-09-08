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
package io.micronaut.inject.ast;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.NonNull;

import java.util.function.Function;

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
     * Build new element annotation metadata from the element with preloaded annotations.
     * This method will avoid fetching default annotation metadata from cache.
     *
     * @param element            The element
     * @param annotationMetadata The preloaded annotation
     * @return the element's metadata
     */
    @NonNull
    ElementAnnotationMetadata build(@NonNull Element element, @NonNull AnnotationMetadata annotationMetadata);

    default ElementAnnotationMetadataFactory readOnly() {
        throw new IllegalStateException("Unsupported operation!");
    }

    default ElementAnnotationMetadataFactory overrideForNativeType(Object nativeType,
                                                                   Function<Element, ElementAnnotationMetadata> fn) {
        ElementAnnotationMetadataFactory thisFactory = this;
        return new ElementAnnotationMetadataFactory() {

            private boolean fetched;

            @Override
            public ElementAnnotationMetadata build(Element element) {
                if (!fetched && element.getNativeType().equals(nativeType)) {
                    fetched = true;
                    return fn.apply(element);
                }
                return thisFactory.build(element);
            }

            @Override
            public ElementAnnotationMetadata build(Element element, AnnotationMetadata annotationMetadata) {
                if (!fetched && element.getNativeType().equals(nativeType)) {
                    fetched = true;
                    return fn.apply(element);
                }
                return thisFactory.build(element, annotationMetadata);
            }
        };
    }
}
