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

import io.micronaut.core.annotation.Experimental;
import java.util.Map;
import java.util.Objects;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.python.processing.PythonProcessingEnvironment;
import io.micronaut.python.processing.util.GraalPyUtil;

/**
 * A parameter element representing a Python function parameter.
 * <p>
 * This class wraps parameter information from a Python function argument,
 * providing type resolution and metadata for Micronaut's parameter processing.
 * </p>
 *
 * @author Micronaut Team
 * @since 5.0.0
 */
@Experimental
public final class PythonParameterElement extends AbstractPythonElement implements ParameterElement {
    private static final String ANN_CONSTRAINT = "jakarta.validation.Constraint";
    private static final String ANN_VALID = "jakarta.validation.Valid";
    private static final String ANN_VALIDATED_ELEMENT = "io.micronaut.validation.annotation.ValidatedElement";

    private final PythonProcessingEnvironment environment;
    private final ClassElement type;
    private final PythonMethodElement methodElement;
    private final ArgumentDef argumentDef;

    public PythonParameterElement(ArgumentDef argumentDef,
                                  PythonProcessingEnvironment environment,
                                  PythonMethodElement methodElement,
                                  ElementAnnotationMetadataFactory metadataFactory) {
        super(
            Objects.requireNonNull(argumentDef, "ArgumentDef cannot be null").name(),
            argumentDef,
            Objects.requireNonNull(metadataFactory, "ElementAnnotationMetadataFactory cannot be null")
        );
        this.environment = Objects.requireNonNull(environment, "PythonProcessingEnvironment cannot be null");
        this.methodElement = Objects.requireNonNull(methodElement, "MethodElement cannot be null");

        // Resolve parameter type
        this.type = resolveType(argumentDef);
        this.argumentDef = argumentDef;
        if (hasValidationAnnotation(type)) {
            annotate(ANN_VALIDATED_ELEMENT);
        }
    }

    @Override
    public ArgumentDef getNativeType() {
        return (ArgumentDef) super.getNativeType();
    }

    @Override
    public ClassElement getType() {
        if (methodElement.requiresResolvedParameterType()) {
            ClassElement classElement = resolveType(argumentDef, methodElement.getBoundGenericTypes());
            if (!classElement.getTypeArguments().isEmpty()) {
                classElement = classElement.getRawClassElement();
            }
            if (classElement instanceof AbstractPythonClassElement pythonClassElement) {
                return pythonClassElement.withTypeAnnotationsKey(argumentDef);
            }
            return classElement;
        }
        return type;
    }

    @Override
    public ClassElement getGenericType() {
        ClassElement classElement = resolveType(argumentDef, methodElement.getBoundGenericTypes());
        if (classElement instanceof AbstractPythonClassElement pythonClassElement) {
            return pythonClassElement.withTypeAnnotationsKey(argumentDef);
        }
        return classElement;
    }

    private ClassElement resolveType(ArgumentDef argumentDef) {
        ClassElement classElement = resolveType(argumentDef, Map.of());
        if (classElement instanceof AbstractPythonClassElement pythonClassElement) {
            return pythonClassElement.withTypeAnnotationsKey(argumentDef);
        }
        return classElement;
    }

    private ClassElement resolveType(ArgumentDef argumentDef, Map<String, ClassElement> boundTypes) {
        if (argumentDef.typeAnnotation() != null) {
            // Use the same type resolution logic as fields
            return GraalPyUtil.resolvePythonTypeToJava(argumentDef.typeAnnotation(), environment.visitorContext(), boundTypes);
        }

        // Fall back to Object when no type annotation
        return environment.visitorContext().getClassElement(Object.class).orElse(ClassElement.of(Object.class));
    }

    @Override
    public PythonMethodElement getMethodElement() {
        return methodElement;
    }

    @Override
    public java.util.Optional<String> getDocumentation(boolean parseContent) {
        return java.util.Optional.ofNullable(getNativeType().documentation());
    }

    @Override
    protected AbstractPythonElement copyThis() {
        return new PythonParameterElement(
            getNativeType(),
            environment,
            methodElement,
            getElementAnnotationMetadataFactory()
        );
    }

    @Override
    public ParameterElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return (ParameterElement) super.withAnnotationMetadata(annotationMetadata);
    }

    private static boolean hasValidationAnnotation(ClassElement classElement) {
        AnnotationMetadata annotationMetadata = classElement.getAnnotationMetadata();
        if (annotationMetadata.hasStereotype(ANN_CONSTRAINT) || annotationMetadata.hasAnnotation(ANN_VALID)) {
            return true;
        }
        for (ClassElement typeArgument : classElement.getTypeArguments().values()) {
            if (hasValidationAnnotation(typeArgument)) {
                return true;
            }
        }
        return false;
    }
}
