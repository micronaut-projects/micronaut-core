/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.python.processing.visitor;

import java.util.Objects;
import java.util.Optional;

import javax.lang.model.element.Element;

import io.micronaut.annotation.processing.visitor.ElementProvider;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.annotation.MethodElementAnnotationsHelper;
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate;
import io.micronaut.python.processing.PythonProcessingEnvironment;

/**
 * A synthetic setter method element for Python properties.
 * This allows synthetic property setters to support annotation mutation,
 * unlike MethodElement.of() which creates immutable elements.
 *
 * @author Micronaut Team
 * @since 5.0.0
 */
public final class PythonPropertySetterMethodElement extends AbstractPythonElement implements MethodElement, ElementProvider {

    private final PythonProcessingEnvironment environment;
    private final AbstractPythonClassElement declaringType;
    private final AbstractPythonClassElement owningType;
    private final ClassElement parameterType;
    private final String methodName;
    private final ParameterElement parameter;
    private final MethodElementAnnotationsHelper helper;

    public PythonPropertySetterMethodElement(
            String methodName,
            ClassElement parameterType,
            AnnotationMetadata annotationMetadata,
            PythonProcessingEnvironment environment,
            AbstractPythonClassElement declaringType,
            AbstractPythonClassElement owningType,
            ElementAnnotationMetadataFactory metadataFactory) {
        super(methodName, null, metadataFactory);
        this.methodName = methodName;
        this.parameterType = parameterType;
        this.environment = environment;
        this.declaringType = declaringType;
        this.owningType = owningType;

        // Create the parameter for the setter using ParameterElement.of
        // Note: This creates an immutable parameter, but that's acceptable for synthetic setters
        this.parameter = new PythonPropertyParameterElement(
            methodName,
            parameterType,
            annotationMetadata,
            metadataFactory
        );

        this.helper = new MethodElementAnnotationsHelper(this, metadataFactory);
        this.presetAnnotationMetadata = annotationMetadata;
    }

    @Override
    protected MutableAnnotationMetadataDelegate<?> getAnnotationMetadataToWrite() {
        return helper.getMethodAnnotationMetadata(presetAnnotationMetadata);
    }

    @Override
    public io.micronaut.inject.ast.annotation.ElementAnnotationMetadata getMethodAnnotationMetadata() {
        return helper.getMethodAnnotationMetadata(presetAnnotationMetadata);
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return helper.getAnnotationMetadata(presetAnnotationMetadata);
    }

    @Override
    public boolean isReflectionRequired() {
        return false;
    }

    @Override
    public boolean isReflectionRequired(ClassElement callingType) {
        return false;
    }

    @Override
    public Object getNativeType() {
        return null; // Synthetic element has no native type
    }

    @Override
    public ClassElement getReturnType() {
        return PrimitiveElement.VOID;
    }

    @Override
    public ClassElement getGenericReturnType() {
        return PrimitiveElement.VOID;
    }

    @Override
    public ParameterElement[] getParameters() {
        return new ParameterElement[]{parameter};
    }

    @Override
    public MethodElement withParameters(ParameterElement... newParameters) {
        if (newParameters.length != 1) {
            throw new IllegalArgumentException("Setter methods must have exactly one parameter");
        }
        // For synthetic setters, we don't support changing parameters
        return this;
    }

    @Override
    public boolean isPublic() {
        return true;
    }

    @Override
    public boolean isPrivate() {
        return false;
    }

    @Override
    public boolean isProtected() {
        return false;
    }

    @Override
    public boolean isSynthetic() {
        return true;
    }

    @Override
    public ClassElement getDeclaringType() {
        return declaringType;
    }

    @Override
    public ClassElement getOwningType() {
        return owningType;
    }

    @Override
    public Optional<String> getDocumentation(boolean parseContent) {
        return Optional.empty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PythonPropertySetterMethodElement that = (PythonPropertySetterMethodElement) o;
        return methodName.equals(that.methodName) && owningType.equals(that.owningType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(methodName, owningType);
    }

    @Override
    protected AbstractPythonElement copyThis() {
        return new PythonPropertySetterMethodElement(
            methodName,
            parameterType,
            presetAnnotationMetadata,
            environment,
            declaringType,
            owningType,
            getElementAnnotationMetadataFactory()
        );
    }

    @Override
    public MethodElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return (MethodElement) super.withAnnotationMetadata(annotationMetadata);
    }

    @Override
    public Element element() {
        return environment.originatingElement();
    }
}
