/*
 * Copyright 2017-2025 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may Obtain a copy of the License at
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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate;

/**
 * A synthetic parameter element for Python property setter methods.
 * This allows synthetic property setter parameters to support annotation mutation,
 * unlike ParameterElement.of() which creates immutable elements.
 *
 * @author Micronaut Team
 * @since 5.0.0
 */
public final class PythonPropertyParameterElement extends AbstractPythonElement implements ParameterElement {

    private final ClassElement type;
    private final String name;
    private final ElementAnnotationMetadataFactory elementFactory;
    private final PythonPropertyElement propertyElement;

    public PythonPropertyParameterElement(
        PythonPropertyElement propertyElement,
        ElementAnnotationMetadataFactory metadataFactory) {
        super(propertyElement.getName(), null, metadataFactory);
        this.propertyElement = propertyElement;
        this.name = propertyElement.getName();
        this.type = propertyElement.getType();
        this.elementFactory = metadataFactory;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return propertyElement.getTargetAnnotationMetadata();
    }

    @Override
    protected ElementAnnotationMetadata getElementAnnotationMetadata() {
        return propertyElement.getElementAnnotationMetadata();
    }

    @Override
    protected MutableAnnotationMetadataDelegate<?> getAnnotationMetadataToWrite() {
        return propertyElement.getAnnotationMetadataToWrite();
    }

    @Override
    public Object getNativeType() {
        return null; // Synthetic element has no native type
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public ClassElement getType() {
        return type;
    }

    @Override
    public ClassElement getGenericType() {
        return type;
    }

    @Override
    public boolean isProtected() {
        return false;
    }

    @Override
    public boolean isPublic() {
        return true;
    }

    @Override
    public ParameterElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return new PythonPropertyParameterElement(propertyElement, elementFactory);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PythonPropertyParameterElement that = (PythonPropertyParameterElement) o;
        return name.equals(that.name) && type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(name, type);
    }

    @Override
    protected PythonPropertyParameterElement copyThis() {
        PythonPropertyParameterElement element = new PythonPropertyParameterElement(
            propertyElement,
            elementFactory
        );
        element.presetAnnotationMetadata = presetAnnotationMetadata;
        return element;
    }
}
