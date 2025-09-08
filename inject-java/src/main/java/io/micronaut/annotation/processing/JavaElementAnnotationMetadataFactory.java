/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.annotation.processing;

import io.micronaut.annotation.processing.visitor.AbstractJavaElement;
import io.micronaut.annotation.processing.visitor.JavaNativeElement;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.WildcardElement;
import io.micronaut.inject.ast.annotation.AbstractElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;

/**
 * Java element annotation metadata factory.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
public final class JavaElementAnnotationMetadataFactory extends AbstractElementAnnotationMetadataFactory<Element, AnnotationMirror> {

    private static final ElementAnnotationMetadata EMPTY = new ElementAnnotationMetadata() {
    };

    public JavaElementAnnotationMetadataFactory(boolean isReadOnly, JavaAnnotationMetadataBuilder metadataBuilder) {
        super(isReadOnly, metadataBuilder);
    }

    @NonNull
    @Override
    public ElementAnnotationMetadataFactory readOnly() {
        return new JavaElementAnnotationMetadataFactory(true, (JavaAnnotationMetadataBuilder) metadataBuilder);
    }

    @NonNull
    @Override
    public ElementAnnotationMetadata build(io.micronaut.inject.ast.Element element) {
        AbstractJavaElement javaElement = (AbstractJavaElement) element;
        if (!allowedAnnotations(javaElement)) {
            return EMPTY;
        }
        return super.build(element);
    }

    private static boolean allowedAnnotations(AbstractJavaElement javaElement) {
        return javaElement.getNativeType().element() != null;
    }

    @Override
    protected Element getNativeElement(io.micronaut.inject.ast.Element element) {
        return ((AbstractJavaElement) element).getNativeType().element();
    }

    @Override
    protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookupTypeAnnotationsForClass(ClassElement classElement) {
        var clazz = (JavaNativeElement.Class) classElement.getNativeType();
        TypeMirror typeMirror = clazz.typeMirror();
        if (typeMirror == null) {
            return super.lookupTypeAnnotationsForClass(classElement);
        }
        if (typeMirror instanceof ArrayType arrayType) {
            if (!hasJSpecifyAnnotations(arrayType) && !hasJSpecifyAnnotations(arrayType.getComponentType())) {
                // Backward compatibility for Micronaut type annotations support
                typeMirror = arrayType.getComponentType();
            }
        }
        return metadataBuilder.lookupOrBuild(clazz, new AnnotationsElement(typeMirror));
    }

    private boolean hasJSpecifyAnnotations(TypeMirror element) {
        for (AnnotationMirror am : element.getAnnotationMirrors()) {
            if (am.getAnnotationType().toString().startsWith("org.jspecify.annotations")) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookupTypeAnnotationsForGenericPlaceholder(GenericPlaceholderElement placeholderElement) {
        var genericNativeType = (JavaNativeElement.Placeholder) placeholderElement.getGenericNativeType();
        Element placeholderJavaElement;
        TypeVariable placeholderTypeVariable = genericNativeType.typeVariable();
        if (!placeholderTypeVariable.getAnnotationMirrors().isEmpty()) {
            placeholderJavaElement = new AnnotationsElement(placeholderTypeVariable);
        } else {
            placeholderJavaElement = genericNativeType.element();
        }
        return metadataBuilder.lookupOrBuild(genericNativeType, placeholderJavaElement);
    }

    @Override
    protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookupTypeAnnotationsForWildcard(WildcardElement wildcardElement) {
        var wildcard = (WildcardType) wildcardElement.getGenericNativeType();
        return metadataBuilder.lookupOrBuild(wildcard, new AnnotationsElement(wildcard));
    }

}
