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
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    @Override
    public ElementAnnotationMetadataFactory readOnly() {
        return new JavaElementAnnotationMetadataFactory(true, (JavaAnnotationMetadataBuilder) metadataBuilder);
    }

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
        Element use = genericNativeType.use();
        if (use != null) {
            placeholderJavaElement = new AnnotationsElement(placeholderTypeVariable, typeUseAnnotations(use, placeholderTypeVariable));
        } else if (!placeholderTypeVariable.getAnnotationMirrors().isEmpty() || !genericNativeType.declaration()) {
            // Only the declaration reports the annotations of the type parameter
            placeholderJavaElement = new AnnotationsElement(placeholderTypeVariable);
        } else {
            placeholderJavaElement = genericNativeType.element();
        }
        return metadataBuilder.lookupOrBuild(genericNativeType, placeholderJavaElement);
    }

    /**
     * The annotations of a type variable used as the type of a field, method or parameter.
     * An annotation of the declaration that is also applicable to type uses applies to the type (JLS 9.7.4).
     * Javac keeps it on a class type, but not on a type variable.
     *
     * @param use The field, method or parameter
     * @param typeVariable The type variable
     * @return The annotations
     */
    private static List<AnnotationMirror> typeUseAnnotations(Element use, TypeVariable typeVariable) {
        List<? extends AnnotationMirror> typeAnnotations = typeVariable.getAnnotationMirrors();
        List<AnnotationMirror> annotations = new ArrayList<>(typeAnnotations.size() + 1);
        for (AnnotationMirror annotation : use.getAnnotationMirrors()) {
            if (isApplicableToTypeUse(annotation) && typeAnnotations.stream().noneMatch(a -> a.getAnnotationType().equals(annotation.getAnnotationType()))) {
                annotations.add(annotation);
            }
        }
        annotations.addAll(typeAnnotations);
        return annotations;
    }

    private static boolean isApplicableToTypeUse(AnnotationMirror annotation) {
        Target target = annotation.getAnnotationType().asElement().getAnnotation(Target.class);
        return target != null && Arrays.asList(target.value()).contains(ElementType.TYPE_USE);
    }

    @Override
    protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookupTypeAnnotationsForWildcard(WildcardElement wildcardElement) {
        var wildcard = (WildcardType) wildcardElement.getGenericNativeType();
        return metadataBuilder.lookupOrBuild(wildcard, new AnnotationsElement(wildcard));
    }

}
