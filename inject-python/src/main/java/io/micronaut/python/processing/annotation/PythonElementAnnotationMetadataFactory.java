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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.annotation.AbstractElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.python.processing.element.AbstractPythonClassElement;
import io.micronaut.python.processing.model.ElementDef;
import io.micronaut.python.processing.model.DecoratorDef;
import io.micronaut.python.processing.model.FunctionDef;
import io.micronaut.python.processing.element.PythonMethodElement;
import io.micronaut.python.processing.element.PythonScriptElement;
import io.micronaut.python.processing.model.ScriptDef;
import io.micronaut.python.processing.model.TypeRef;

import java.util.IdentityHashMap;
import java.util.Map;

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
 * @since 5.2.0
 */
@Experimental
public class PythonElementAnnotationMetadataFactory extends AbstractElementAnnotationMetadataFactory<ElementDef, DecoratorDef> {

    private final Map<TypeRef, ElementAnnotationMetadata> typeUseAnnotations = new IdentityHashMap<>();

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

    /**
     * The type-use annotation metadata of one type node of a Python source: the decorators of an
     * {@code Annotated[...]} type argument, or the synthetic nullability of a union. Every resolution of the same
     * node answers the same mutable metadata, so an annotation a visitor adds to a type argument (the validation
     * visitor marks the constrained element type of a collection parameter) is still there when the argument
     * metadata is written, like the type-use annotations of a Java type argument.
     *
     * @param typeNode       The type node the metadata belongs to
     * @param typeUseElement The synthetic element carrying the decorators of the node
     * @return The metadata
     */
    public ElementAnnotationMetadata buildTypeUseAnnotations(TypeRef typeNode, ElementDef typeUseElement) {
        return typeUseAnnotations.computeIfAbsent(typeNode, node -> new AbstractElementAnnotationMetadata() {

            @Override
            protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookup() {
                return metadataBuilder.lookupOrBuild(new TypeUseAnnotationKey(node), typeUseElement);
            }

            @Override
            public String toString() {
                return node.toString();
            }
        });
    }

    @Override
    protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookupTypeAnnotationsForClass(ClassElement classElement) {
        if (classElement instanceof PythonScriptElement scriptElement) {
            ScriptDef scriptDef = scriptElement.getNativeType();
            return metadataBuilder.lookupOrBuild(
                new TypeAnnotationKey(classElement.getNativeType(), scriptDef),
                scriptDef
            );
        }
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
            functionDef.isAsync(),
            functionDef.hasReturnValue(),
            functionDef.hasPlaceholderBody(),
            functionDef.declaringClass(),
            null,
            functionDef.isGenerator()
        );
    }

    private record TypeAnnotationKey(Object nativeType, Object typeAnnotationsKey) {
    }

    /**
     * Identifies a type node by identity: two spellings of the same type in one source ({@code Annotated[str,
     * NotBlank]} as the key and the value of a dict) are equal records but distinct type uses.
     */
    private static final class TypeUseAnnotationKey {

        private final TypeRef typeNode;

        TypeUseAnnotationKey(TypeRef typeNode) {
            this.typeNode = typeNode;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof TypeUseAnnotationKey key && key.typeNode == typeNode;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(typeNode);
        }
    }

    private record FunctionTypeAnnotationKey(Object nativeType, String functionName) {
    }
}
