/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

/**
 * A field element returning data from a {@link VariableElement}.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Internal
class JavaFieldElement extends AbstractJavaElement implements FieldElement {

    private final JavaVisitorContext visitorContext;
    private final VariableElement variableElement;
    private ClassElement declaringElement;

    /**
     * @param variableElement    The {@link VariableElement}
     * @param annotationMetadata The annotation metadata
     * @param visitorContext     The visitor context
     */
    JavaFieldElement(VariableElement variableElement, AnnotationMetadata annotationMetadata, JavaVisitorContext visitorContext) {
        super(variableElement, annotationMetadata, visitorContext);
        this.variableElement = variableElement;
        this.visitorContext = visitorContext;
    }

    /**
     * @param declaringElement  The declaring element
     * @param variableElement    The {@link VariableElement}
     * @param annotationMetadata The annotation metadata
     * @param visitorContext     The visitor context
     */
    JavaFieldElement(ClassElement declaringElement,
                     VariableElement variableElement,
                     AnnotationMetadata annotationMetadata,
                     JavaVisitorContext visitorContext) {
        this(variableElement, annotationMetadata, visitorContext);
        this.declaringElement = declaringElement;
    }

    @NonNull
    @Override
    public ClassElement getType() {
        TypeMirror returnType = variableElement.asType();
        return mirrorToClassElement(returnType, visitorContext);
    }

    @Override
    public ClassElement getDeclaringType() {
        if (declaringElement == null) {

            final Element enclosingElement = variableElement.getEnclosingElement();
            if (!(enclosingElement instanceof TypeElement)) {
                throw new IllegalStateException("Enclosing element should be a type element");
            }
            declaringElement = new JavaClassElement(
                    (TypeElement) enclosingElement,
                    visitorContext.getAnnotationUtils().getAnnotationMetadata(enclosingElement),
                    visitorContext
            );
        }

        return declaringElement;
    }
}
