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
import java.util.Map;
import java.util.Objects;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate;
import io.micronaut.python.processing.PythonProcessingEnvironment;
import io.micronaut.python.processing.model.ArgumentDef;
import org.jspecify.annotations.Nullable;

/**
 * A parameter element representing a Python function parameter.
 * <p>
 * This class wraps parameter information from a Python function argument,
 * providing type resolution and metadata for Micronaut's parameter processing.
 * </p>
 *
 * @author Micronaut Team
 * @since 5.2.0
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
    // The signature inherited from the parameter of the Java method the Python method overrides
    // (see withInheritedType and withInheritedAnnotationMetadata)
    private @Nullable ClassElement inheritedType;
    private @Nullable AnnotationMetadata inheritedAnnotationMetadata;
    private @Nullable ElementAnnotationMetadata mergedAnnotationMetadata;
    private boolean validatedElementResolved;

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
    }

    /**
     * The annotation metadata read from this parameter: the annotations declared on the Python parameter,
     * seen through those inherited from the overridden Java parameter, as for the parameters of an overriding
     * Java method (the inherited ones are visible to {@code hasAnnotation} but not to {@code hasDeclaredAnnotation}).
     */
    @Override
    protected ElementAnnotationMetadata getElementAnnotationMetadata() {
        if (inheritedAnnotationMetadata == null) {
            return getOwnAnnotationMetadata();
        }
        if (mergedAnnotationMetadata == null) {
            mergedAnnotationMetadata = elementAnnotationMetadataFactory.buildMutable(
                new AnnotationMetadataHierarchy(inheritedAnnotationMetadata, getOwnAnnotationMetadata())
            );
        }
        return mergedAnnotationMetadata;
    }

    /**
     * Annotations added by a visitor go to the metadata of the Python parameter itself, which is cached for the
     * parameter: every view of the parameter (each query of the enclosed methods answers a new copy) sees them,
     * the inherited metadata being a read-only view over it.
     */
    @Override
    protected MutableAnnotationMetadataDelegate<?> getAnnotationMetadataToWrite() {
        return getOwnAnnotationMetadata();
    }

    private ElementAnnotationMetadata getOwnAnnotationMetadata() {
        ElementAnnotationMetadata annotationMetadata = super.getElementAnnotationMetadata();
        if (!validatedElementResolved) {
            validatedElementResolved = true;
            if (hasValidationAnnotation(type) && !annotationMetadata.hasAnnotation(ANN_VALIDATED_ELEMENT)) {
                annotationMetadata.annotate(ANN_VALIDATED_ELEMENT);
            }
        }
        return annotationMetadata;
    }

    @Override
    public ArgumentDef getNativeType() {
        return (ArgumentDef) super.getNativeType();
    }

    @Override
    public boolean hasDefault() {
        // The Java signature a Python override adopts has no defaults: its arguments are the Java ones
        return inheritedType == null && getNativeType().hasDefaultValue();
    }

    @Override
    public ClassElement getType() {
        if (inheritedType != null) {
            return inheritedType;
        }
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
        if (inheritedType != null) {
            return inheritedType;
        }
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
        ClassElement classElement;
        if (argumentDef.typeAnnotation() != null) {
            // Use the same type resolution logic as fields
            classElement = environment.visitorContext().getTypeResolver().resolve(argumentDef.typeAnnotation(), boundTypes);
        } else {
            // Fall back to Object when no type annotation
            classElement = environment.visitorContext().getClassElement(Object.class).orElse(ClassElement.of(Object.class));
        }
        if (argumentDef.variadic()) {
            // `*args: T` collects the remaining positional arguments: Java sees a `T...` varargs array
            return classElement.toArray();
        }
        return classElement;
    }

    /**
     * @return Whether this is the variadic ({@code *args}) parameter of its function
     */
    public boolean isVariadic() {
        return argumentDef.variadic();
    }

    @Override
    public PythonMethodElement getMethodElement() {
        return methodElement;
    }

    @Override
    public java.util.Optional<String> getDocumentation(boolean parseContent) {
        return java.util.Optional.ofNullable(getNativeType().documentation());
    }

    /**
     * Returns a copy of this parameter that reports the given type, the type of the corresponding parameter
     * of the Java method the Python method overrides, in place of the one resolved from the Python hint. The
     * copy remains a parameter of the Python method: it keeps the Python name, the method element and the
     * annotation metadata.
     *
     * @param type The type of the overridden Java parameter
     * @return The copy
     */
    public PythonParameterElement withInheritedType(ClassElement type) {
        PythonParameterElement copy = (PythonParameterElement) makeCopy();
        copy.inheritedType = Objects.requireNonNull(type, "Type cannot be null");
        return copy;
    }

    /**
     * Returns a copy of this parameter that inherits the given annotation metadata, the annotations of the
     * corresponding parameter of a method the Python method overrides (the constraints of a Java interface
     * method). They are read through this parameter as inherited annotations, in addition to any inherited
     * earlier, while annotations added to the copy go to the parameter's own metadata, so a visitor that
     * annotates the parameter while inheriting annotations itself (the validation visitor) works on it.
     *
     * @param annotationMetadata The annotation metadata of the overridden parameter
     * @return The copy
     */
    public PythonParameterElement withInheritedAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        Objects.requireNonNull(annotationMetadata, "Annotation metadata cannot be null");
        PythonParameterElement copy = (PythonParameterElement) makeCopy();
        copy.inheritedAnnotationMetadata = inheritedAnnotationMetadata == null
            ? annotationMetadata
            : new AnnotationMetadataHierarchy(inheritedAnnotationMetadata, annotationMetadata);
        return copy;
    }

    @Override
    protected AbstractPythonElement copyThis() {
        PythonParameterElement copy = new PythonParameterElement(
            getNativeType(),
            environment,
            methodElement,
            getElementAnnotationMetadataFactory()
        );
        copy.inheritedType = inheritedType;
        copy.inheritedAnnotationMetadata = inheritedAnnotationMetadata;
        return copy;
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
