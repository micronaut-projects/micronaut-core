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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.micronaut.annotation.processing.visitor.ElementProvider;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.annotation.MethodElementAnnotationsHelper;
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate;
import io.micronaut.python.processing.PythonProcessingEnvironment;
import io.micronaut.python.processing.util.GraalPyUtil;
import org.jetbrains.annotations.Nullable;

import javax.lang.model.element.Element;

/**
 * Represents a Python method/function as a Micronaut {@link MethodElement}.
 * <p>
 * This class wraps a {@link FunctionDef} node of the Python AST, providing full
 * MethodElement interface implementation including parameters, return types, and visibility.
 * </p>
 *
 * @author Micronaut Team
 * @since 5.0.0
 * @see FunctionDef
 * @see <a href="https://docs.python.org/3/library/ast.html#ast.FunctionDef">Python AST FunctionDef</a>
 */
public sealed class PythonMethodElement extends AbstractPythonElement implements MethodElement, ElementProvider permits PythonConstructorElement {
    private final PythonProcessingEnvironment environment;
    private final AbstractPythonClassElement declaringType;
    private final AbstractPythonClassElement owningType;
    private final ClassElement returnType;
    private final ParameterElement[] parameters;
    private final MethodElementAnnotationsHelper helper;

    /**
     * Constructs a new {@code PythonMethodElement} from the given {@code FunctionDef}.
     *
     * @param functionDef the function definition node; must not be {@code null}
     * @param environment the Python processing environment; must not be {@code null}
     * @param declaringType the class that declares this method; must not be {@code null}
     * @param owningType the class that owns this method (may be a subclass); must not be {@code null}
     * @param metadataFactory the annotation metadata factory; must not be {@code null}
     * @throws NullPointerException if any parameter is {@code null}
     */
    public PythonMethodElement(FunctionDef functionDef,
                               PythonProcessingEnvironment environment,
                               AbstractPythonClassElement declaringType,
                               AbstractPythonClassElement owningType,
                               ElementAnnotationMetadataFactory metadataFactory) {
        super(Objects.requireNonNull(functionDef, "FunctionDef cannot be null").name(), functionDef, metadataFactory);
        this.environment = Objects.requireNonNull(environment, "PythonProcessingEnvironment cannot be null");
        this.declaringType = Objects.requireNonNull(declaringType, "Declaring type cannot be null");
        this.owningType = Objects.requireNonNull(owningType, "Owning type cannot be null");

        // Resolve return type
        this.returnType = resolveReturnType(functionDef);

        // Create parameter elements
        this.parameters = createParameters(functionDef);
        this.helper = new MethodElementAnnotationsHelper(this, metadataFactory);
    }

    @Override
    protected MutableAnnotationMetadataDelegate<?> getAnnotationMetadataToWrite() {
        return helper.getMethodAnnotationMetadata(presetAnnotationMetadata);
    }

    @Override
    public ElementAnnotationMetadata getMethodAnnotationMetadata() {
        return helper.getMethodAnnotationMetadata(presetAnnotationMetadata);
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return helper.getAnnotationMetadata(presetAnnotationMetadata);
    }

    @Override
    public boolean isReflectionRequired() {
        // since we are in charge of Python stub generation, this doesn't make sense
        return false;
    }



    @Override
    public boolean isReflectionRequired(ClassElement callingType) {
        // since we are in charge of Python stub generation, this doesn't make sense
        return false;
    }

    /**
     * Returns the native {@link FunctionDef} object that backs this element.
     *
     * @return the underlying {@code FunctionDef} node
     */
    @Override
    public FunctionDef getNativeType() {
        return (FunctionDef) super.getNativeType();
    }

    @Override
    public ClassElement getReturnType() {
        return returnType;
    }

    @Override
    public ParameterElement[] getParameters() {
        return parameters.clone();
    }

    @Override
    public MethodElement withParameters(ParameterElement... newParameters) {
        // Since PythonMethodElement is based on parsed Python code,
        // we create a synthetic MethodElement with the new parameters
        return MethodElement.of(
            getOwningType(),
            getAnnotationMetadata(),
            getReturnType(),
            getGenericReturnType(),
            getName(),
            newParameters
        );
    }

    @Override
    public boolean isPublic() {
        // Python considers methods/attributes starting with '_' as private
        return !getName().startsWith("_");
    }

    @Override
    public boolean isPrivate() {
        return getName().startsWith("_");
    }

    @Override
    public ClassElement getDeclaringType() {
        return declaringType;
    }

    @Override
    public ClassElement getOwningType() {
        return owningType;
    }

    private ClassElement resolveReturnType(FunctionDef functionDef) {
        ReturnDef returnDef = functionDef.returnType();
        if (returnDef != null && returnDef.typeAnnotation() != null) {
            ClassElement baseType = GraalPyUtil.resolvePythonTypeToJava(returnDef.typeAnnotation(), environment.visitorContext());

            // If there are decorators, create a ClassElement with annotation metadata
            if (!returnDef.decorators().isEmpty()) {
                io.micronaut.core.annotation.AnnotationMetadata annotationMetadata =
                    environment.visitorContext().getAnnotationMetadataBuilder().buildDeclared(returnDef);
                return baseType.withAnnotationMetadata(annotationMetadata);
            }

            return baseType;
        }
        // Fall back to void/Object
        return environment.visitorContext().getClassElement(Object.class).orElse(ClassElement.of(Object.class));
    }

    private ParameterElement[] createParameters(FunctionDef functionDef) {
        List<ArgumentDef> arguments = functionDef.arguments().arguments();
        ParameterElement[] parameters = new ParameterElement[arguments.size()];

        for (int i = 0; i < arguments.size(); i++) {
            ArgumentDef argDef = arguments.get(i);
            parameters[i] = new PythonParameterElement(argDef, environment, this, getElementAnnotationMetadataFactory());
        }

        return parameters;
    }

    @Override
    public Optional<String> getDocumentation(boolean parseContent) {
        String doc = getNativeType().documentation();
        if (doc == null) {
            return Optional.empty();
        }
        if (parseContent) {
            // Parse Python docstring to extract main description
            return Optional.of(GraalPyUtil.parsePythonDocstring(doc));
        }
        return Optional.of(doc);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PythonMethodElement that = (PythonMethodElement) o;

        return that.getNativeType().name().equals(getNativeType().name()) &&
            owningType.equals(that.owningType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getNativeType().name(), owningType);
    }

    @Override
    protected AbstractPythonElement copyThis() {
        return new PythonMethodElement(
            getNativeType(),
            environment,
            declaringType,
            owningType,
            getElementAnnotationMetadataFactory()
        );
    }

    @Override
    public MethodElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return (MethodElement) super.withAnnotationMetadata(annotationMetadata);
    }

    @Override
    public @Nullable Element element() {
        return environment.originatingElement();
    }
}
