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
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.ast.annotation.AbstractElementAnnotationMetadata;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.annotation.MethodElementAnnotationMetadata;
import io.micronaut.inject.ast.annotation.MethodElementAnnotationsHelper;
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate;
import io.micronaut.python.processing.PythonProcessingEnvironment;
import org.jspecify.annotations.NonNull;

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
    private final String methodName;
    private final ParameterElement parameter;
    private final PythonPropertyElement propertyElement;

    public PythonPropertySetterMethodElement(
            PythonPropertyElement propertyElement,
            PythonProcessingEnvironment environment,
            AbstractPythonClassElement declaringType,
            AbstractPythonClassElement owningType,
            ElementAnnotationMetadataFactory metadataFactory) {
        super(propertyElement.getName(), null, metadataFactory);
        this.propertyElement = propertyElement;
        this.methodName = propertyElement.getName();
        this.environment = environment;
        this.declaringType = declaringType;
        this.owningType = owningType;

        // Create the parameter for the setter using ParameterElement.of
        // Note: This creates an immutable parameter, but that's acceptable for synthetic setters
        this.parameter = new PythonPropertyParameterElement(
            propertyElement,
            metadataFactory
        );
    }

    @Override
    public String getName() {
        return NameUtils.setterNameFor(
            super.getName()
        );
    }

    @Override
    public @NonNull MutableAnnotationMetadataDelegate<AnnotationMetadata> getMethodAnnotationMetadata() {
        return propertyElement.getElementAnnotationMetadata();
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return propertyElement.getTargetAnnotationMetadata();
    }

    @Override
    protected ElementAnnotationMetadata getElementAnnotationMetadata() {
        return new AbstractElementAnnotationMetadata() {

            @Override
            protected AnnotationMetadata getReturnInstance() {
                return propertyElement.getTargetAnnotationMetadata();
            }

            @Override
            protected MutableAnnotationMetadataDelegate<?> getAnnotationMetadataToWrite() {
                return propertyElement.getAnnotationMetadataToWrite();
            }
        };
    }

    @Override
    protected MutableAnnotationMetadataDelegate<?> getAnnotationMetadataToWrite() {
        return getElementAnnotationMetadata();
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
    protected PythonPropertySetterMethodElement copyThis() {
        return new PythonPropertySetterMethodElement(
            propertyElement,
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
