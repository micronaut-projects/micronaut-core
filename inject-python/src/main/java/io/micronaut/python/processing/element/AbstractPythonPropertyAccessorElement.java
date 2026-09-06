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

import io.micronaut.annotation.processing.visitor.ElementProvider;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate;
import io.micronaut.python.processing.PythonProcessingEnvironment;
import org.jspecify.annotations.NonNull;

import javax.lang.model.element.Element;
import java.util.Objects;
import java.util.Optional;

/**
 * What the synthetic getter and setter of a Python property have in common: they are public,
 * synthetic, carry the property's annotations under the owning type's, and need no reflection.
 *
 * @author Micronaut Team
 * @since 5.2.0
 */
@Experimental
abstract sealed class AbstractPythonPropertyAccessorElement extends AbstractPythonElement implements MethodElement, ElementProvider
    permits PythonPropertyGetterMethodElement, PythonPropertySetterMethodElement {

    protected final PythonProcessingEnvironment environment;
    protected final ClassElement declaringType;
    protected final ClassElement owningType;
    protected final String methodName;
    protected final PythonPropertyElement propertyElement;

    protected AbstractPythonPropertyAccessorElement(
            PythonPropertyElement propertyElement,
            PythonProcessingEnvironment environment,
            ClassElement declaringType,
            ClassElement owningType,
            ElementAnnotationMetadataFactory metadataFactory) {
        super(propertyElement.getName(), null, metadataFactory);
        this.propertyElement = propertyElement;
        this.methodName = propertyElement.getName();
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
        AnnotationMetadata targetAnnotationMetadata = propertyElement.getTargetAnnotationMetadata();
        if (!owningType.getAnnotationMetadata().isEmpty()) {
            // Synthetic Python property accessors are generated from PropertyElement, not a
            // Python function. Keep owning type metadata visible here so class-level AOP and
            // executable metadata apply to generated accessors the same way they apply to
            // regular Python methods.
            targetAnnotationMetadata = new AnnotationMetadataHierarchy(owningType, targetAnnotationMetadata);
        }
        return targetAnnotationMetadata;
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
        AbstractPythonPropertyAccessorElement that = (AbstractPythonPropertyAccessorElement) o;
        return methodName.equals(that.methodName) && owningType.equals(that.owningType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(methodName, owningType);
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
