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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.annotation.AbstractAnnotationElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;

abstract sealed class AbstractPythonElement extends AbstractAnnotationElement implements Element permits AbstractPythonClassElement, PythonEnumConstantElement, PythonFieldElement, PythonMethodElement, PythonParameterElement, PythonPropertyElement {
    private final String name;
    private final Object nativeType;

    protected AbstractPythonElement(String name, Object nativeType, ElementAnnotationMetadataFactory metadataFactory) {
        super(metadataFactory);
        this.name = name;
        this.nativeType = nativeType;
    }

    /**
     * @return copy of this element
     */
    protected abstract AbstractPythonElement copyThis();

    /**
     * @param element the values to be copied to
     */
    protected void copyValues(AbstractPythonElement element) {
        element.presetAnnotationMetadata = presetAnnotationMetadata;
    }

    protected final AbstractPythonElement makeCopy() {
        AbstractPythonElement element = copyThis();
        copyValues(element);
        return element;
    }

    @Override
    public io.micronaut.inject.ast.Element withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        AbstractPythonElement abstractPythonElement = makeCopy();
        abstractPythonElement.presetAnnotationMetadata = annotationMetadata;
        return abstractPythonElement;
    }

    @Override
    public String getName() {
        return name;
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
    public boolean equals(Object o) {
        if (!(o instanceof AbstractPythonElement that)) {
            return false;
        }
        return Objects.equals(name, that.name) && Objects.equals(nativeType, that.nativeType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, nativeType);
    }

    @Override
    public Object getNativeType() {
        return nativeType;
    }
}
