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

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.EnumConstantElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.python.processing.PythonProcessingEnvironment;

/**
 * Represents a Python enum constant as a Micronaut {@link EnumConstantElement}.
 * <p>
 * This class wraps an {@link AttributeDef} node representing an enum constant
 * and provides the {@link EnumConstantElement} interface implementation.
 * </p>
 *
 * @author Micronaut Team
 * @since 5.0.0
 */
public final class PythonEnumConstantElement extends AbstractPythonElement implements EnumConstantElement {

    private final PythonEnumElement declaringType;
    private final PythonEnumElement owningType;

    /**
     * Constructs a new {@code PythonEnumConstantElement} from the given {@code AttributeDef}.
     *
     * @param attributeDef the attribute definition representing the enum constant; must not be {@code null}
     * @param environment the Python processing environment; must not be {@code null}
     * @param declaringType the enum class that declares this constant; must not be {@code null}
     * @param owningType the enum class that owns this constant (may be a subclass); must not be {@code null}
     * @param metadataFactory the annotation metadata factory; must not be {@code null}
     * @throws NullPointerException if any parameter is {@code null}
     */
    public PythonEnumConstantElement(AttributeDef attributeDef,
                                     PythonProcessingEnvironment environment,
                                     PythonEnumElement declaringType,
                                     PythonEnumElement owningType,
                                     ElementAnnotationMetadataFactory metadataFactory) {
        super(
            Objects.requireNonNull(attributeDef, "AttributeDef cannot be null").name(),
            attributeDef,
            Objects.requireNonNull(metadataFactory, "ElementAnnotationMetadataFactory cannot be null")
        );
        this.declaringType = Objects.requireNonNull(declaringType, "Declaring type cannot be null");
        this.owningType = Objects.requireNonNull(owningType, "Owning type cannot be null");
    }

    @Override
    public AttributeDef getNativeType() {
        return (AttributeDef) super.getNativeType();
    }

    @Override
    public ClassElement getType() {
        // Enum constants have the type of their declaring enum
        return declaringType;
    }

    @Override
    public ClassElement getGenericType() {
        return getType();
    }

    @Override
    public Object getConstantValue() {
        AttributeDef attr = getNativeType();
        if (attr.value() != null) {
            // Try to convert Python literals to Java types
            return convertPythonValueToJava(attr.value());
        }
        // For enum constants without explicit values, return the name
        return getName();
    }

    @Override
    public boolean isStatic() {
        return true; // Enum constants are always static
    }

    @Override
    public boolean isFinal() {
        return true; // Enum constants are always final
    }

    @Override
    public ClassElement getDeclaringType() {
        return declaringType;
    }

    @Override
    public ClassElement getOwningType() {
        return owningType;
    }

    private Object convertPythonValueToJava(org.graalvm.polyglot.Value pythonValue) {
        if (pythonValue == null) {
            return null;
        }
        // For enum constants, we might want to return the Python value directly
        // or convert it to a suitable Java representation
        return pythonValue;
    }
}
