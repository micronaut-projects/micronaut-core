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
package io.micronaut.annotation.processing.visitor;

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.AnnotationElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;

/**
 * Represents an annotation in the AST for Java.
 *
 * @author graemerocher
 * @since 3.1.0
 */
@Internal
final class JavaAnnotationElement extends JavaClassElement implements AnnotationElement {

    /**
     * @param nativeElement             The native element
     * @param annotationMetadataFactory The annotation metadata factory
     * @param visitorContext            The visitor context
     */
    JavaAnnotationElement(JavaNativeElement.Class nativeElement,
                          ElementAnnotationMetadataFactory annotationMetadataFactory,
                          JavaVisitorContext visitorContext) {
        super(nativeElement, annotationMetadataFactory, visitorContext);
    }
}
