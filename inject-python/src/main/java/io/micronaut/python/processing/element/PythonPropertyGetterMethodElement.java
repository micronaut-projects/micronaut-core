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
package io.micronaut.python.processing.element;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.annotation.MethodElementAnnotationMetadata;
import io.micronaut.python.processing.PythonProcessingEnvironment;

/**
 * A synthetic getter method element for Python properties.
 * This allows synthetic property getters to support annotation mutation,
 * unlike MethodElement.of() which creates immutable elements.
 *
 * @author Micronaut Team
 * @since 5.2.0
 */
@Experimental
public final class PythonPropertyGetterMethodElement extends AbstractPythonPropertyAccessorElement {

    private final ClassElement returnType;

    public PythonPropertyGetterMethodElement(
            PythonPropertyElement propertyElement,
            PythonProcessingEnvironment environment,
            ClassElement declaringType,
            ClassElement owningType,
            ElementAnnotationMetadataFactory metadataFactory) {
        super(propertyElement, environment, declaringType, owningType, metadataFactory);
        this.returnType = propertyElement.getType();
    }

    @Override
    protected ElementAnnotationMetadata getElementAnnotationMetadata() {
        return new MethodElementAnnotationMetadata(this);
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
    protected AbstractPythonElement copyThis() {
        return new PythonPropertyGetterMethodElement(
            propertyElement,
            environment,
            declaringType,
            owningType,
            getElementAnnotationMetadataFactory()
        );
    }
}
