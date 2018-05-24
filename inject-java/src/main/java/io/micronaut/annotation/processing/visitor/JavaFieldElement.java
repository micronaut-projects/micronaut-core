/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.annotation.processing.visitor;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.visitor.FieldElement;

import javax.lang.model.element.VariableElement;

/**
 * A field element returning data from a {@link VariableElement}.
 *
 * @author James Kleeh
 * @since 1.0
 */
class JavaFieldElement extends AbstractJavaElement implements FieldElement {

    private final VariableElement variableElement;

    /**
     * @param variableElement    The {@link VariableElement}
     * @param annotationMetadata The annotation metadata
     */
    JavaFieldElement(VariableElement variableElement, AnnotationMetadata annotationMetadata) {
        super(variableElement, annotationMetadata);
        this.variableElement = variableElement;
    }

}
