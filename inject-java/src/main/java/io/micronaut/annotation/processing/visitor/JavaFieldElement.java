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

import io.micronaut.core.annotation.NonNull;
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
    private JavaClassElement declaringElement;
    private ClassElement typeElement;
    private ClassElement genericType;
    private ClassElement resolvedDeclaringClass;

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
    JavaFieldElement(JavaClassElement declaringElement,
                     VariableElement variableElement,
                     AnnotationMetadata annotationMetadata,
                     JavaVisitorContext visitorContext) {
        this(variableElement, annotationMetadata, visitorContext);
        this.declaringElement = declaringElement;
    }

    @Override
    public ClassElement getGenericType() {
        if (this.genericType == null) {
            if (declaringElement == null) {
                this.genericType = getType();
            } else {
                this.genericType = mirrorToClassElement(
                        variableElement.asType(),
                        visitorContext,
                        declaringElement.getGenericTypeInfo(),
                        false
                );
            }
        }
        return this.genericType;
    }

    @Override
    public boolean isPrimitive() {
        return getType().isPrimitive();
    }

    @Override
    public boolean isArray() {
        return getType().isArray();
    }

    @Override
    public int getArrayDimensions() {
        return getType().getArrayDimensions();
    }

    @NonNull
    @Override
    public ClassElement getType() {
        if (this.typeElement == null) {
            TypeMirror returnType = variableElement.asType();
            this.typeElement = mirrorToClassElement(returnType, visitorContext);
        }
        return this.typeElement;
    }

    @Override
    public ClassElement getDeclaringType() {
        if (resolvedDeclaringClass == null) {

            Element enclosingElement = variableElement.getEnclosingElement();
            if (enclosingElement instanceof TypeElement) {
                TypeElement te = (TypeElement) enclosingElement;
                if (declaringElement.getName().equals(te.getQualifiedName().toString())) {
                    resolvedDeclaringClass = declaringElement;
                } else {
                    resolvedDeclaringClass = mirrorToClassElement(te.asType(), visitorContext, declaringElement.getGenericTypeInfo());
                }
            } else {
                return declaringElement;
            }
        }
        return resolvedDeclaringClass;
    }
}
