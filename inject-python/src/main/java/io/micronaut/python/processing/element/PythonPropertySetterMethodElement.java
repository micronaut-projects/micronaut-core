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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.ast.annotation.AbstractElementAnnotationMetadata;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate;
import io.micronaut.python.processing.PythonProcessingEnvironment;

/**
 * A synthetic setter method element for Python properties.
 * This allows synthetic property setters to support annotation mutation,
 * unlike MethodElement.of() which creates immutable elements.
 *
 * @author Micronaut Team
 * @since 5.2.0
 */
@Experimental
public final class PythonPropertySetterMethodElement extends AbstractPythonPropertyAccessorElement {

    private final ParameterElement parameter;

    public PythonPropertySetterMethodElement(
            PythonPropertyElement propertyElement,
            PythonProcessingEnvironment environment,
            ClassElement declaringType,
            ClassElement owningType,
            ElementAnnotationMetadataFactory metadataFactory) {
        super(propertyElement, environment, declaringType, owningType, metadataFactory);
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
    protected PythonPropertySetterMethodElement copyThis() {
        return new PythonPropertySetterMethodElement(
            propertyElement,
            environment,
            declaringType,
            owningType,
            getElementAnnotationMetadataFactory()
        );
    }
}
