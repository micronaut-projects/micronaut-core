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

import java.util.Optional;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.python.processing.PythonProcessingEnvironment;

/**
 * A property element representing a Python property (either a @property decorated method or a regular attribute).
 * <p>
 * This class implements the {@link PropertyElement} interface to provide property support for Python classes.
 * Properties can be created from:
 * <ul>
 * <li>@property decorated methods with optional @property.setter decorators</li>
 * <li>Regular Python attributes (fields)</li>
 * </ul>
 * </p>
 *
 * @author Micronaut Team
 * @since 5.0.0
 */
public final class PythonPropertyElement extends AbstractPythonElement implements PropertyElement {
    private final PythonProcessingEnvironment environment;
    private final AbstractPythonClassElement declaringType;
    private final AbstractPythonClassElement owningType;
    private final PropertyDef propertyDef;
    private final ClassElement type;
    private final AccessKind readAccessKind;
    private final AccessKind writeAccessKind;
    private final FieldElement field;
    private final MethodElement readMethod;
    private final MethodElement writeMethod;

    public PythonPropertyElement(PropertyDef propertyDef,
                                 PythonProcessingEnvironment environment,
                                 AbstractPythonClassElement declaringType,
                                 AbstractPythonClassElement owningType,
                                 ElementAnnotationMetadataFactory metadataFactory) {
        super(
            propertyDef.name(),
            propertyDef,
            metadataFactory
        );
        this.propertyDef = propertyDef;
        this.environment = environment;
        this.declaringType = declaringType;
        this.owningType = owningType;

        // Initialize field, read method, and write method
        this.field = propertyDef.hasField() ? new PythonFieldElement(
            propertyDef.field(),
            environment,
            declaringType,
            owningType,
            metadataFactory
        ) : null;

        this.readMethod = propertyDef.hasGetter() ? new PythonMethodElement(
            propertyDef.getter(),
            environment,
            declaringType,
            owningType,
            metadataFactory
        ) : null;

        this.writeMethod = propertyDef.hasSetter() ? new PythonMethodElement(
            propertyDef.setter(),
            environment,
            declaringType,
            owningType,
            metadataFactory
        ) : null;

        // Determine access kinds and type
        if (propertyDef.hasGetter()) {
            this.readAccessKind = AccessKind.METHOD;
            this.writeAccessKind = propertyDef.hasSetter() ? AccessKind.METHOD : null;
        } else {
            // Field-based property
            this.readAccessKind = AccessKind.FIELD;
            this.writeAccessKind = propertyDef.hasField() && !propertyDef.field().isStatic() ? AccessKind.FIELD : null;
        }

        this.type = resolveType();
    }

    @Override
    public PropertyDef getNativeType() {
        return propertyDef;
    }

    @Override
    public ClassElement getType() {
        return type;
    }

    @Override
    public ClassElement getGenericType() {
        return getType();
    }

    @Override
    public Optional<FieldElement> getField() {
        return Optional.ofNullable(field);
    }

    @Override
    public Optional<MethodElement> getReadMethod() {
        return Optional.ofNullable(readMethod);
    }

    @Override
    public Optional<MethodElement> getWriteMethod() {
        return Optional.ofNullable(writeMethod);
    }

    @Override
    public AccessKind getReadAccessKind() {
        return readAccessKind != null ? readAccessKind : AccessKind.FIELD;
    }

    @Override
    public AccessKind getWriteAccessKind() {
        return writeAccessKind;
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
    public boolean isReadOnly() {
        return getWriteMember().isEmpty();
    }

    @Override
    public boolean isWriteOnly() {
        return getReadMember().isEmpty();
    }

    @Override
    public Optional<? extends MemberElement> getReadMember() {
        if (getReadAccessKind() == AccessKind.METHOD) {
            return getReadMethod();
        }
        return getField();
    }

    @Override
    public Optional<? extends MemberElement> getWriteMember() {
        if (getWriteAccessKind() == AccessKind.METHOD) {
            return getWriteMethod();
        }
        return getField().filter(fieldElement -> !fieldElement.isFinal());
    }

    @Override
    public Optional<ClassElement> getReadType() {
        if (getReadAccessKind() == AccessKind.METHOD) {
            return getReadMethod().map(MethodElement::getGenericReturnType);
        }
        return getField().map(field -> field.getGenericType());
    }

    @Override
    public Optional<ClassElement> getWriteType() {
        if (getWriteAccessKind() == AccessKind.METHOD) {
            return getWriteMethod().flatMap(methodElement -> {
                if (methodElement.getParameters().length > 0) {
                    return Optional.of(methodElement.getParameters()[0].getGenericType());
                }
                return Optional.empty();
            });
        }
        return getField().filter(fieldElement -> !fieldElement.isFinal()).map(field -> field.getGenericType());
    }

    @Override
    public Optional<AnnotationMetadata> getReadTypeAnnotationMetadata() {
        return getReadMember().map(MemberElement::getAnnotationMetadata);
    }

    @Override
    public Optional<AnnotationMetadata> getWriteTypeAnnotationMetadata() {
        return getWriteMember().map(MemberElement::getAnnotationMetadata);
    }

    @Override
    public boolean overrides(PropertyElement overridden) {
        // Python doesn't have method overriding in the same way as Java
        // For now, just check if names match
        return overridden.getName().equals(getName());
    }

    private ClassElement resolveType() {
        // First try to get type from field if it exists
        if (propertyDef.hasField()) {
            PythonFieldElement fieldElement = new PythonFieldElement(
                propertyDef.field(),
                environment,
                declaringType,
                owningType,
                environment.metadataFactory()
            );
            ClassElement fieldType = fieldElement.getType();
            if (fieldType != null && !"java.lang.Object".equals(fieldType.getName())) {
                return fieldType;
            }
        }

        // Then try to get type from getter return type
        if (propertyDef.hasGetter()) {
            String returnType = propertyDef.getter().returnTypeAnnotation();
            if (returnType != null && !returnType.isEmpty()) {
                ClassElement resolvedType = resolvePythonTypeToJava(returnType);
                if (resolvedType != null) {
                    return resolvedType;
                }
            }
        }

        // Fallback to Object
        return environment.visitorContext().getClassElement(Object.class).orElse(null);
    }

    private ClassElement resolvePythonTypeToJava(String pythonType) {
        return io.micronaut.python.processing.util.GraalPyUtil.resolvePythonTypeToJava(
            pythonType,
            environment.visitorContext()
        );
    }

    @Override
    public Optional<String> getDocumentation(boolean parseContent) {
        // Try to get documentation from getter first
        if (propertyDef.hasGetter()) {
            Optional<String> getterDoc = getReadMethod().flatMap(method ->
                method.getDocumentation(parseContent));
            if (getterDoc.isPresent()) {
                return getterDoc;
            }
        }

        // Then try field documentation
        if (propertyDef.hasField()) {
            Optional<String> fieldDoc = getField().flatMap(field ->
                field.getDocumentation(parseContent));
            if (fieldDoc.isPresent()) {
                return fieldDoc;
            }
        }

        // Finally try setter documentation
        if (propertyDef.hasSetter()) {
            Optional<String> setterDoc = getWriteMethod().flatMap(method ->
                method.getDocumentation(parseContent));
            if (setterDoc.isPresent()) {
                return setterDoc;
            }
        }

        return Optional.empty();
    }
}
