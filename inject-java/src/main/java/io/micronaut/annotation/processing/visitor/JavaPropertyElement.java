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
import org.jspecify.annotations.Nullable;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
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
    @Nullable
    private final AccessKind readAccessKind;
    @Nullable
    private final AccessKind writeAccessKind;
    private final ClassElement owningElement;
    @Nullable
    private final MethodElement getter;
    @Nullable
    private final MethodElement setter;
    @Nullable
    private final FieldElement field;
    @Nullable
    private final MemberElement sourceMember;
    @Nullable
    private final AnnotationMetadata propertyComponentAnnotationMetadata;
    private final boolean excluded;
    private final boolean constructorWriteAccess;
    private final PropertyElementAnnotationMetadata annotationMetadata;
    @Nullable
    private final String doc;

    @SuppressWarnings("checkstyle:ParameterNumber")
    JavaPropertyElement(ClassElement owningElement,
                        ClassElement type,
                        @Nullable MethodElement getter,
                        @Nullable MethodElement setter,
                        @Nullable FieldElement field,
                        @Nullable MemberElement sourceMember,
                        @Nullable AnnotationMetadata propertyComponentAnnotationMetadata,
                        ElementAnnotationMetadataFactory annotationMetadataFactory,
                        String name,
                        @Nullable AccessKind readAccessKind,
                        @Nullable AccessKind writeAccessKind,
                        boolean excluded,
                        boolean constructorWriteAccess,
                        JavaVisitorContext visitorContext,
                        @Nullable String doc) {
        super(selectNativeType(getter, setter, field, sourceMember), annotationMetadataFactory, visitorContext);
        this.type = type;
        this.getter = getter;
        this.setter = setter;
        this.field = field;
        this.sourceMember = sourceMember;
        this.propertyComponentAnnotationMetadata = propertyComponentAnnotationMetadata;
        this.name = name;
        this.readAccessKind = readAccessKind;
        this.writeAccessKind = writeAccessKind;
        this.owningElement = owningElement;
        this.excluded = excluded;
        this.constructorWriteAccess = constructorWriteAccess;
        this.annotationMetadata = new PropertyElementAnnotationMetadata(
            this,
            annotationMethod(getter, sourceMember),
            setter,
            annotationField(field, sourceMember),
            null,
            propertyComponentAnnotationMetadata,
            false
        );
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
        if (readAccessKind == null) {
            return Optional.empty();
        }
        return Optional.of(annotationMetadata.getReadAnnotationMetadata());
    }

    @Override
    public Optional<AnnotationMetadata> getWriteTypeAnnotationMetadata() {
        if (writeAccessKind == null && !constructorWriteAccess) {
            return Optional.empty();
        }
        return Optional.of(annotationMetadata.getWriteAnnotationMetadata());
    }

    @Override
    protected AbstractJavaElement copyThis() {
        return new JavaPropertyElement(
            owningElement,
            type,
            getter,
            setter,
            field,
            sourceMember,
            propertyComponentAnnotationMetadata,
            elementAnnotationMetadataFactory,
            name,
            readAccessKind,
            writeAccessKind,
            excluded,
            constructorWriteAccess,
            visitorContext,
            doc
        );
    }

    @Override
    public PropertyElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return (PropertyElement) super.withAnnotationMetadata(annotationMetadata);
    }

    private static JavaNativeElement selectNativeType(@Nullable MethodElement getter,
                                                      @Nullable MethodElement setter,
                                                      @Nullable FieldElement field,
                                                      @Nullable MemberElement sourceMember) {
        if (getter != null) {
            return (JavaNativeElement) getter.getNativeType();
        }
        if (setter != null) {
            return (JavaNativeElement) setter.getNativeType();
        }
        if (field != null) {
            return (JavaNativeElement) field.getNativeType();
        }
        if (sourceMember != null) {
            return (JavaNativeElement) sourceMember.getNativeType();
        }
        throw new IllegalStateException();
    }

    @Nullable
    private static MethodElement annotationMethod(@Nullable MethodElement getter, @Nullable MemberElement sourceMember) {
        if (getter != null) {
            return getter;
        }
        if (sourceMember instanceof MethodElement methodElement) {
            return methodElement;
        }
        return null;
    }

    @Nullable
    private static FieldElement annotationField(@Nullable FieldElement field, @Nullable MemberElement sourceMember) {
        if (field != null) {
            return field;
        }
        if (sourceMember instanceof FieldElement fieldElement) {
            return fieldElement;
        }
        return null;
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
    public ClassElement getType() {
        return type;
    }

    @Override
    public ClassElement getGenericType() {
        return type; // Already generic
    }

    @Override
    public Optional<FieldElement> getField() {
        return Optional.ofNullable(field);
    }

    @Override
    public Optional<MethodElement> getWriteMethod() {
        if (writeAccessKind != AccessKind.METHOD) {
            return Optional.empty();
        }
        return Optional.ofNullable(setter);
    }

    @Override
    public Optional<MethodElement> getReadMethod() {
        if (readAccessKind != AccessKind.METHOD) {
            return Optional.empty();
        }
        return Optional.ofNullable(getter);
    }

    @Override
    public Optional<? extends MemberElement> getReadMember() {
        if (readAccessKind == null) {
            return Optional.empty();
        }
        return PropertyElement.super.getReadMember();
    }

    @Override
    public Optional<ClassElement> getReadType() {
        if (readAccessKind == null) {
            return Optional.empty();
        }
        return PropertyElement.super.getReadType();
    }

    @Override
    public Optional<? extends MemberElement> getWriteMember() {
        if (writeAccessKind == null) {
            return Optional.empty();
        }
        return PropertyElement.super.getWriteMember();
    }

    @Override
    public Optional<ClassElement> getWriteType() {
        if (constructorWriteAccess) {
            return Optional.of(type);
        }
        if (writeAccessKind == null) {
            return Optional.empty();
        }
        return PropertyElement.super.getWriteType();
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
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public AccessKind getReadAccessKind() {
        return readAccessKind == null ? AccessKind.METHOD : readAccessKind;
    }

    @Override
    public AccessKind getWriteAccessKind() {
        return writeAccessKind == null ? AccessKind.METHOD : writeAccessKind;
    }

    @Override
    public boolean isReadOnly() {
        if (constructorWriteAccess) {
            return false;
        }
        if (writeAccessKind == null) {
            return true;
        }
        return switch (writeAccessKind) {
            case METHOD -> setter == null;
            case FIELD -> field == null || field.isFinal();
        };
    }

    @Override
    public boolean isWriteOnly() {
        if (readAccessKind == null) {
            return true;
        }
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
        if (sourceMember != null) {
            return sourceMember.getDeclaringType();
        }
        throw new IllegalStateException();
    }

    @Override
    public ClassElement getOwningType() {
        return owningElement;
    }

    @Override
    public Optional<String> getDocumentation(boolean parse) {
        if (!parse) {
            return JavaPropertyElement.super.getDocumentation(parse);
        }
        return doc == null ? PropertyElement.super.getDocumentation(parse) : Optional.of(doc);
    }
}
