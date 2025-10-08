/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.annotation.processing.visitor;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.annotation.PropertyElementAnnotationMetadata;

import java.util.Optional;

/**
 * Models a {@link PropertyElement} for Java.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
final class JavaPropertyElement extends AbstractJavaMemberElement implements PropertyElement {

    private final ClassElement type;
    private final String name;
    private final AccessKind readAccessKind;
    private final AccessKind writeAccessKind;
    private final ClassElement owningElement;
    @Nullable
    private final MethodElement getter;
    @Nullable
    private final MethodElement setter;
    @Nullable
    private final FieldElement field;
    private final boolean excluded;
    private final PropertyElementAnnotationMetadata annotationMetadata;
    @Nullable
    private final String doc;

    JavaPropertyElement(ClassElement owningElement,
                        ClassElement type,
                        @Nullable MethodElement getter,
                        @Nullable MethodElement setter,
                        @Nullable FieldElement field,
                        ElementAnnotationMetadataFactory annotationMetadataFactory,
                        String name,
                        AccessKind readAccessKind,
                        AccessKind writeAccessKind,
                        boolean excluded,
                        JavaVisitorContext visitorContext,
                        @Nullable String doc) {
        super(selectNativeType(getter, setter, field), annotationMetadataFactory, visitorContext);
        this.type = type;
        this.getter = getter;
        this.setter = setter;
        this.field = field;
        this.name = name;
        this.readAccessKind = readAccessKind;
        this.writeAccessKind = writeAccessKind;
        this.owningElement = owningElement;
        this.excluded = excluded;
        this.annotationMetadata = new PropertyElementAnnotationMetadata(this, getter, setter, field, null, false);
        this.doc = doc;
    }

    @Override
    protected AnnotationMetadata getTypeAnnotationMetadata() {
        // The correct check for the nullability of the property should base on the read / write accessor
        // We might need to introduce new methods to check if reader value / write value can be null
        return type.getTypeAnnotationMetadata();
    }

    @Override
    public Optional<AnnotationMetadata> getReadTypeAnnotationMetadata() {
        return Optional.of(annotationMetadata.getReadAnnotationMetadata());
    }

    @Override
    public Optional<AnnotationMetadata> getWriteTypeAnnotationMetadata() {
        return Optional.of(annotationMetadata.getWriteAnnotationMetadata());
    }

    @Override
    protected AbstractJavaElement copyThis() {
        return new JavaPropertyElement(owningElement, type, getter, setter, field, elementAnnotationMetadataFactory, name, readAccessKind, writeAccessKind, excluded, visitorContext, doc);
    }

    @Override
    public PropertyElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return (PropertyElement) super.withAnnotationMetadata(annotationMetadata);
    }

    private static JavaNativeElement selectNativeType(MethodElement getter,
                                                      MethodElement setter,
                                                      FieldElement field) {
        if (getter != null) {
            return (JavaNativeElement) getter.getNativeType();
        }
        if (setter != null) {
            return (JavaNativeElement) setter.getNativeType();
        }
        if (field != null) {
            return (JavaNativeElement) field.getNativeType();
        }
        throw new IllegalStateException();
    }

    @Override
    public boolean isExcluded() {
        return excluded;
    }

    @Override
    public ElementAnnotationMetadata getElementAnnotationMetadata() {
        return annotationMetadata;
    }

    @Override
    public @NonNull ClassElement getType() {
        return type;
    }

    @Override
    public @NonNull ClassElement getGenericType() {
        return type; // Already generic
    }

    @Override
    public Optional<FieldElement> getField() {
        return Optional.ofNullable(field);
    }

    @Override
    public Optional<MethodElement> getWriteMethod() {
        return Optional.ofNullable(setter);
    }

    @Override
    public Optional<MethodElement> getReadMethod() {
        return Optional.ofNullable(getter);
    }

    @Override
    public boolean isPrimitive() {
        return getType().isPrimitive();
    }

    @Override
    public boolean isArray() {
        return getType().isArray();
    }

    @Override
    public int getArrayDimensions() {
        return getType().getArrayDimensions();
    }

    @Override
    public @NonNull String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public AccessKind getReadAccessKind() {
        return readAccessKind;
    }

    @Override
    public AccessKind getWriteAccessKind() {
        return writeAccessKind;
    }

    @Override
    public boolean isReadOnly() {
        return switch (writeAccessKind) {
            case METHOD -> setter == null;
            case FIELD -> field == null || field.isFinal();
        };
    }

    @Override
    public boolean isWriteOnly() {
        return switch (readAccessKind) {
            case METHOD -> getter == null;
            case FIELD -> field == null;
        };
    }

    @Override
    public ClassElement getDeclaringType() {
        if (field != null) {
            return field.getDeclaringType();
        }
        if (getter != null) {
            return getter.getDeclaringType();
        }
        if (setter != null) {
            return setter.getDeclaringType();
        }
        throw new IllegalStateException();
    }

    @Override
    public ClassElement getOwningType() {
        return owningElement;
    }

    @Override
    public Optional<String> getDocumentation(boolean parse) {
        return !parse || doc == null ? PropertyElement.super.getDocumentation(parse) : Optional.of(doc);
    }
}
