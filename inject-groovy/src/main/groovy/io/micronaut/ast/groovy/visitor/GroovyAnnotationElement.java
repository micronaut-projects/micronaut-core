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
package io.micronaut.ast.groovy.visitor;

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.AnnotationElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;

/**
 * Groovy implementation of {@link io.micronaut.inject.ast.AnnotationElement}.
 *
 * @since 3.1.0
 * @author graemerocher
 */
@Internal
final class GroovyAnnotationElement extends GroovyClassElement implements AnnotationElement {

    public GroovyAnnotationElement(GroovyVisitorContext visitorContext,
                                   GroovyNativeElement nativeElement,
                                   ElementAnnotationMetadataFactory annotationMetadataFactory) {
        super(visitorContext, nativeElement, annotationMetadataFactory);
    }
}
