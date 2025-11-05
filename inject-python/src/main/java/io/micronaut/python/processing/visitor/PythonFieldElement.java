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

import io.micronaut.inject.visitor.VisitorContext;
import org.graalvm.polyglot.Value;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.python.processing.PythonProcessingEnvironment;
import io.micronaut.python.processing.util.GraalPyUtil;

/**
 * A field element returning data from a Python {@link AttributeDef}.
 *
 * @author Micronaut Team
 * @since 5.0.0
 */
public final class PythonFieldElement extends AbstractPythonElement implements FieldElement {
    private final PythonProcessingEnvironment environment;
    private final AbstractPythonClassElement declaringType;
    private final AbstractPythonClassElement owningType;
    private final ClassElement type;

    public PythonFieldElement(AttributeDef attributeDef,
                              PythonProcessingEnvironment environment,
                              AbstractPythonClassElement declaringType,
                              AbstractPythonClassElement owningType,
                              ElementAnnotationMetadataFactory metadataFactory) {
        super(
            Objects.requireNonNull(attributeDef, "AttributeDef cannot be null").name(),
            attributeDef,
            Objects.requireNonNull(metadataFactory, "ElementAnnotationMetadataFactory cannot be null")
        );
        this.environment = Objects.requireNonNull(environment, "PythonProcessingEnvironment cannot be null");
        this.declaringType = Objects.requireNonNull(declaringType, "Declaring type cannot be null");
        this.owningType = Objects.requireNonNull(owningType, "Owning type cannot be null");

        this.type = resolveType(attributeDef);
    }

    @Override
    public AttributeDef getNativeType() {
        return (AttributeDef) super.getNativeType();
    }

    @Override
    public ClassElement getType() {
        return type;
    }

    @Override
    public ClassElement getGenericType() {
        return getType(); // Python doesn't have generics in the same way
    }

    @Override
    public Object getConstantValue() {
        AttributeDef attr = getNativeType();
        if (attr.value() != null) {
            // Try to convert Python literals to Java types
            return convertPythonValueToJava(attr.value(), environment.visitorContext());
        }
        return null;
    }

    @Override
    public boolean isStatic() {
        return getNativeType().isStatic();
    }

    @Override
    public boolean isFinal() {
        // Check if annotated with typing.Final
        return hasStereotype("typing.Final") || hasStereotype("Final");
    }

    @Override
    public ClassElement getDeclaringType() {
        return declaringType;
    }

    @Override
    public ClassElement getOwningType() {
        return owningType;
    }

    private ClassElement resolveType(AttributeDef attributeDef) {
        if (attributeDef.annotation() != null) {
            // Try to resolve the type annotation
            String annotation = attributeDef.annotation();
            return GraalPyUtil.resolvePythonTypeToJava(annotation, environment.visitorContext());
        }
        // Infer from value if no annotation
        if (attributeDef.value() != null) {
            return inferTypeFromValue(attributeDef.value(), environment.visitorContext());
        }
        return environment.visitorContext().getClassElement(Object.class).orElse(null);
    }

    private ClassElement inferTypeFromValue(Value value, PythonVisitorContext visitorContext) {
        if (value == null) {
            return environment.visitorContext().getClassElement(Object.class).orElse(null);
        }
        Object javaValue = GraalPyUtil.convertValueToJava(value, visitorContext);
        if (javaValue instanceof Integer) {
            return environment.visitorContext().getClassElement(int.class).orElse(null);
        } else if (javaValue instanceof Double || javaValue instanceof Float) {
            return environment.visitorContext().getClassElement(double.class).orElse(null);
        } else if (javaValue instanceof String) {
            return environment.visitorContext().getClassElement(String.class).orElse(null);
        } else if (javaValue instanceof Boolean) {
            return environment.visitorContext().getClassElement(boolean.class).orElse(null);
        }
        return environment.visitorContext().getClassElement(Object.class).orElse(null);
    }

    private Object convertPythonValueToJava(Value pythonValue, VisitorContext visitorContext) {
        if (pythonValue == null) {
            return null;
        }
        return GraalPyUtil.convertValueToJava(pythonValue, visitorContext);
    }

    @Override
    public java.util.Optional<String> getDocumentation(boolean parseContent) {
        String doc = getNativeType().documentation();
        if (doc == null) {
            return java.util.Optional.empty();
        }
        if (parseContent) {
            // Parse Python docstring to extract main description
            return java.util.Optional.of(GraalPyUtil.parsePythonDocstring(doc));
        }
        return java.util.Optional.of(doc);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PythonFieldElement that = (PythonFieldElement) o;

        return that.getNativeType().name().equals(getNativeType().name()) &&
            owningType.equals(that.owningType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getNativeType().name(), owningType);
    }

    @Override
    protected AbstractPythonElement copyThis() {
        return new PythonFieldElement(
            getNativeType(),
            environment,
            declaringType,
            owningType,
            getElementAnnotationMetadataFactory()
        );
    }

    @Override
    public FieldElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return (FieldElement) super.withAnnotationMetadata(annotationMetadata);
    }
}
