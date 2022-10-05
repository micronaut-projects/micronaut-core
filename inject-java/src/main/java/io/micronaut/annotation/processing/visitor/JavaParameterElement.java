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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;

import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.util.Map;

/**
 * Implementation of the {@link ParameterElement} interface for Java.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
class JavaParameterElement extends AbstractJavaElement implements ParameterElement {

    private final JavaClassElement owningType;
    private final MethodElement methodElement;
    private final VariableElement variableElement;
    private ClassElement typeElement;
    private ClassElement genericTypeElement;

    /**
     * Default constructor.
     *
     * @param owningType                The owning class
     * @param methodElement             The method element
     * @param element                   The variable element
     * @param annotationMetadataFactory The annotation metadata factory
     * @param visitorContext            The visitor context
     */
    JavaParameterElement(JavaClassElement owningType,
                         MethodElement methodElement,
                         VariableElement element,
                         ElementAnnotationMetadataFactory annotationMetadataFactory,
                         JavaVisitorContext visitorContext) {
        super(element, annotationMetadataFactory, visitorContext);
        this.owningType = owningType;
        this.methodElement = methodElement;
        this.variableElement = element;
    }

    @Override
    protected AbstractJavaElement copyThis() {
        return new JavaParameterElement(owningType, methodElement, variableElement, elementAnnotationMetadataFactory, visitorContext);
    }

    @Override
    public ParameterElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return (ParameterElement) super.withAnnotationMetadata(annotationMetadata);
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

    @Override
    @NonNull
    public ClassElement getType() {
        if (typeElement == null) {
            TypeMirror parameterType = getNativeType().asType();
            this.typeElement = mirrorToClassElement(parameterType, visitorContext);
        }
        return typeElement;
    }

    @NonNull
    @Override
    public ClassElement getGenericType() {
        if (this.genericTypeElement == null) {
            TypeMirror returnType = getNativeType().asType();
            Map<String, Map<String, TypeMirror>> declaredGenericInfo = owningType.getGenericTypeInfo();
            this.genericTypeElement = parameterizedClassElement(returnType, visitorContext, declaredGenericInfo);
        }
        return this.genericTypeElement;
    }

    @Override
    public MethodElement getMethodElement() {
        return methodElement;
    }

    @Override
    public VariableElement getNativeType() {
        return (VariableElement) super.getNativeType();
    }

}
