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
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementModifier;
import io.micronaut.inject.ast.EnumConstantElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;

import java.util.Set;

/**
 * Implements the {@link io.micronaut.inject.ast.EnumElement} interface for Java.
 *
 * @since 3.6.0
 */
@Internal
final class JavaEnumConstantElement extends AbstractJavaElement implements EnumConstantElement {

    private final JavaEnumElement declaringEnum;

    /**
     * @param declaringEnum             The declaring enum element
     * @param nativeElement             The native element
     * @param annotationMetadataFactory The annotation metadata factory
     * @param visitorContext            The visitor context
     */
    JavaEnumConstantElement(JavaEnumElement declaringEnum,
                            JavaNativeElement.Variable nativeElement,
                            ElementAnnotationMetadataFactory annotationMetadataFactory,
                            JavaVisitorContext visitorContext) {
        super(nativeElement, annotationMetadataFactory, visitorContext);
        this.declaringEnum = declaringEnum;
    }

    @Override
    public JavaNativeElement.Variable getNativeType() {
        return (JavaNativeElement.Variable) super.getNativeType();
    }

    @Override
    protected AbstractJavaElement copyThis() {
        return new JavaEnumConstantElement(declaringEnum, getNativeType(), elementAnnotationMetadataFactory, visitorContext);
    }

    @Override
    public FieldElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return (FieldElement) super.withAnnotationMetadata(annotationMetadata);
    }

    @Override
    public ClassElement getDeclaringType() {
        return declaringEnum;
    }

    @Override
    public ClassElement getType() {
        return declaringEnum;
    }

    @Override
    public Set<ElementModifier> getModifiers() {
        return ENUM_CONSTANT_MODIFIERS;
    }

    @Override
    public boolean isPackagePrivate() {
        return false;
    }

    @Override
    public boolean isAbstract() {
        return false;
    }

    @Override
    public boolean isStatic() {
        return true;
    }

    @Override
    public boolean isPublic() {
        return true;
    }

    @Override
    public boolean isPrivate() {
        return false;
    }

    @Override
    public boolean isFinal() {
        return true;
    }

    @Override
    public boolean isProtected() {
        return false;
    }

    @Override
    public boolean isPrimitive() {
        return false;
    }

    @Override
    public boolean isArray() {
        return false;
    }
}
