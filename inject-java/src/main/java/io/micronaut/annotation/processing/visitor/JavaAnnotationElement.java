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

import javax.lang.model.element.TypeElement;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.ast.AnnotationElement;

/**
 * Represents an annotation in the AST for Java.
 *
 * @author graemerocher
 * @since 3.1.0
 */
final class JavaAnnotationElement extends JavaClassElement implements AnnotationElement {
    /**
     * @param classElement       The {@link javax.lang.model.element.TypeElement}
     * @param annotationMetadata The annotation metadata
     * @param visitorContext The visitor context
     */
    JavaAnnotationElement(TypeElement classElement, AnnotationMetadata annotationMetadata, JavaVisitorContext visitorContext) {
        super(classElement, annotationMetadata, visitorContext);
    }
}
