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

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.python.processing.PythonProcessingEnvironment;
import io.micronaut.python.processing.util.GraalPyUtil;
import org.graalvm.polyglot.Value;

import java.util.Objects;

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
public final class PythonParameterElement extends AbstractPythonElement implements ParameterElement {
    private final PythonProcessingEnvironment environment;
    private final ClassElement type;

    public PythonParameterElement(ArgumentDef argumentDef,
                                  PythonProcessingEnvironment environment,
                                  ElementAnnotationMetadataFactory metadataFactory) {
        super(
            Objects.requireNonNull(argumentDef, "ArgumentDef cannot be null").name(),
            argumentDef,
            Objects.requireNonNull(metadataFactory, "ElementAnnotationMetadataFactory cannot be null")
        );
        this.environment = Objects.requireNonNull(environment, "PythonProcessingEnvironment cannot be null");

        // Resolve parameter type
        this.type = resolveType(argumentDef);
    }

    @Override
    public ArgumentDef getNativeType() {
        return (ArgumentDef) super.getNativeType();
    }

    @Override
    public ClassElement getType() {
        return type;
    }

    @Override
    public ClassElement getGenericType() {
        return getType(); // Python doesn't have generics in the same way
    }

    private ClassElement resolveType(ArgumentDef argumentDef) {
        if (argumentDef.typeAnnotation() != null) {
            // Use the same type resolution logic as fields
            return GraalPyUtil.resolvePythonTypeToJava(argumentDef.typeAnnotation(), environment.visitorContext());
        }

        // Fall back to Object when no type annotation
        return environment.visitorContext().getClassElement(Object.class).orElse(ClassElement.of(Object.class));
    }

    @Override
    public java.util.Optional<String> getDocumentation(boolean parseContent) {
        return java.util.Optional.ofNullable(getNativeType().documentation());
    }
}
