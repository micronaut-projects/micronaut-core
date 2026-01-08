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
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.annotation.MethodElementAnnotationMetadata;
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate;
import io.micronaut.python.processing.PythonProcessingEnvironment;
import org.jspecify.annotations.NonNull;

/**
 * A synthetic getter method element for Python properties.
 * This allows synthetic property getters to support annotation mutation,
 * unlike MethodElement.of() which creates immutable elements.
 *
 * @author Micronaut Team
 * @since 5.0.0
 */
public final class PythonPropertyGetterMethodElement extends AbstractPythonElement implements MethodElement, ElementProvider {

    private final PythonProcessingEnvironment environment;
    private final ClassElement declaringType;
    private final ClassElement owningType;
    private final ClassElement returnType;
    private final String methodName;
    private final PythonPropertyElement propertyElement;

    public PythonPropertyGetterMethodElement(
            PythonPropertyElement propertyElement,
            PythonProcessingEnvironment environment,
            ClassElement declaringType,
            ClassElement owningType,
            ElementAnnotationMetadataFactory metadataFactory) {
        super(propertyElement.getName(), null, metadataFactory);
        this.methodName = propertyElement.getName();
        this.returnType = propertyElement.getType();
        this.propertyElement = propertyElement;
        this.environment = environment;
        this.declaringType = declaringType;
        this.owningType = owningType;
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
        return new MethodElementAnnotationMetadata(this);
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
        return returnType;
    }

    @Override
    public ClassElement getGenericReturnType() {
        return returnType;
    }

    @Override
    public ParameterElement[] getParameters() {
        return ParameterElement.ZERO_PARAMETER_ELEMENTS; // Getter has no parameters
    }

    @Override
    public String getName() {
        return NameUtils.getterNameFor(
            super.getName(),
            getGenericReturnType().equals(PrimitiveElement.BOOLEAN)
        );
    }

    @Override
    public MethodElement withParameters(ParameterElement... newParameters) {
        if (newParameters.length != 0) {
            throw new IllegalArgumentException("Getter methods cannot have parameters");
        }
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
        PythonPropertyGetterMethodElement that = (PythonPropertyGetterMethodElement) o;
        return methodName.equals(that.methodName) && owningType.equals(that.owningType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(methodName, owningType);
    }

    @Override
    protected AbstractPythonElement copyThis() {
        return new PythonPropertyGetterMethodElement(
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
