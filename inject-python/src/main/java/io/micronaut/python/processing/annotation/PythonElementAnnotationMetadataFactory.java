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
package io.micronaut.python.processing.annotation;

import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.annotation.AbstractElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.python.processing.visitor.AbstractPythonClassElement;
import io.micronaut.python.processing.visitor.ElementDef;
import io.micronaut.python.processing.visitor.DecoratorDef;
import io.micronaut.python.processing.visitor.FunctionDef;
import io.micronaut.python.processing.visitor.PythonMethodElement;

/**
 * Factory for creating and managing annotation metadata for Python elements.
 * <p>
 * This class extends {@link AbstractElementAnnotationMetadataFactory} and provides
 * support for reading and building annotation metadata based on Python decorators.
 * The factory delegates annotation processing logic to {@link PythonAnnotationMetadataBuilder}.
 * </p>
 * <p>
 * The metadata factory may be used in either a read-only or mutable context, depending
 * on how it is constructed. To enforce immutability, use {@link #readOnly()} to obtain a read-only factory.
 * </p>
 *
 * <p>Typical usage:</p>
 * <pre>
 *     PythonAnnotationMetadataBuilder builder = ...;
 *     PythonElementAnnotationMetadataFactory factory =
 *         new PythonElementAnnotationMetadataFactory(false, builder);
 *     ElementAnnotationMetadata metadata = factory.build(someElement);
 * </pre>
 *
 * @since 5.0.0
 */
public class PythonElementAnnotationMetadataFactory extends AbstractElementAnnotationMetadataFactory<ElementDef, DecoratorDef> {

    /**
     * Constructs a new factory for Python element annotation metadata.
     *
     * @param isReadOnly             Whether the factory should operate in read-only mode (no shared cache modifications).
     * @param annotationMetadataBuilder The annotation metadata builder used to introspect Python decorators.
     */
    public PythonElementAnnotationMetadataFactory(boolean isReadOnly, PythonAnnotationMetadataBuilder annotationMetadataBuilder) {
        super(isReadOnly, annotationMetadataBuilder);
    }

    /**
     * Creates a read-only version of this element annotation metadata factory.
     * No modifications to annotation metadata will be persisted within the shared cache.
     *
     * @return a read-only element annotation metadata factory
     */
    @Override
    public ElementAnnotationMetadataFactory readOnly() {
        return new PythonElementAnnotationMetadataFactory(true, (PythonAnnotationMetadataBuilder) metadataBuilder);
    }

    @Override
    protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookupTypeAnnotationsForClass(ClassElement classElement) {
        if (classElement instanceof AbstractPythonClassElement pythonClassElement) {
            ElementDef typeAnnotationsKey = pythonClassElement.getTypeAnnotationsKey();
            if (typeAnnotationsKey != null) {
                if (typeAnnotationsKey instanceof FunctionDef functionDef) {
                    return metadataBuilder.lookupOrBuild(
                        new FunctionTypeAnnotationKey(classElement.getNativeType(), functionDef.name()),
                        functionDef.returnType()
                    );
                }
                return metadataBuilder.lookupOrBuild(
                    new TypeAnnotationKey(classElement.getNativeType(), typeAnnotationsKey),
                    typeAnnotationsKey
                );
            }

        }
        return super.lookupTypeAnnotationsForClass(classElement);
    }

    @Override
    protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookupForMethod(MethodElement methodElement) {
        if (methodElement instanceof PythonMethodElement pythonMethodElement) {
            return metadataBuilder.lookupOrBuildForMethod(
                getNativeElement(methodElement.getDeclaringType()),
                methodMetadataKey(pythonMethodElement.getNativeType())
            );
        }
        return super.lookupForMethod(methodElement);
    }

    @Override
    protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookupForParameter(ParameterElement parameterElement) {
        if (parameterElement.getMethodElement() instanceof PythonMethodElement pythonMethodElement) {
            return metadataBuilder.lookupOrBuildForParameter(
                getNativeElement(pythonMethodElement.getDeclaringType()),
                getNativeElement(pythonMethodElement),
                getNativeElement(parameterElement)
            );
        }
        return super.lookupForParameter(parameterElement);
    }

    private static FunctionDef methodMetadataKey(FunctionDef functionDef) {
        int arity = functionDef.arguments().arguments().size();
        return new FunctionDef(
            functionDef.name() + "/" + arity,
            functionDef.arguments(),
            functionDef.decorators(),
            functionDef.returnType(),
            functionDef.typeComment(),
            functionDef.typeParams(),
            functionDef.documentation(),
            functionDef.isAbstract(),
            functionDef.isStatic(),
            functionDef.hasReturnValue(),
            functionDef.declaringClass()
        );
    }

    private record TypeAnnotationKey(Object nativeType, Object typeAnnotationsKey) {
    }

    private record FunctionTypeAnnotationKey(Object nativeType, String functionName) {
    }
}
