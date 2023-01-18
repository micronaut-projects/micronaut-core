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
package io.micronaut.annotation.processing;

import io.micronaut.core.annotation.Internal;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVisitor;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;

/**
 * Custom {@link DeclaredType} that preserves annotations while new type is created using {@link javax.lang.model.util.Types#getDeclaredType(TypeElement, TypeMirror...)}.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
final class PreservedAnnotationsTypeMirror implements DeclaredType {

    private final TypeElement typeElement;
    private final TypeMirror typeMirror;
    private final DeclaredType declaredType;

    PreservedAnnotationsTypeMirror(TypeElement typeElement, TypeMirror typeMirror, DeclaredType declaredType) {
        this.typeElement = typeElement;
        this.typeMirror = typeMirror;
        this.declaredType = declaredType;
    }

    @Override
    public TypeKind getKind() {
        return typeMirror.getKind();
    }

    @Override
    public List<? extends AnnotationMirror> getAnnotationMirrors() {
        return typeMirror.getAnnotationMirrors();
    }

    @Override
    public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
        return typeMirror.getAnnotation(annotationType);
    }

    @Override
    public <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationType) {
        return typeMirror.getAnnotationsByType(annotationType);
    }

    @Override
    public <R, P> R accept(TypeVisitor<R, P> v, P p) {
        throw new IllegalStateException("Not supported");
    }

    @Override
    public Element asElement() {
        return new TypeElement() {
            @Override
            public TypeMirror asType() {
                return typeElement.asType();
            }

            @Override
            public List<? extends Element> getEnclosedElements() {
                return typeElement.getEnclosedElements();
            }

            @Override
            public NestingKind getNestingKind() {
                return typeElement.getNestingKind();
            }

            @Override
            public Name getQualifiedName() {
                return typeElement.getQualifiedName();
            }

            @Override
            public Name getSimpleName() {
                return typeElement.getSimpleName();
            }

            @Override
            public TypeMirror getSuperclass() {
                return typeElement.getSuperclass();
            }

            @Override
            public List<? extends TypeMirror> getInterfaces() {
                return typeElement.getInterfaces();
            }

            @Override
            public List<? extends TypeParameterElement> getTypeParameters() {
                return typeElement.getTypeParameters();
            }

            @Override
            public Element getEnclosingElement() {
                return typeElement.getEnclosingElement();
            }

            @Override
            public ElementKind getKind() {
                return typeElement.getKind();
            }

            @Override
            public Set<Modifier> getModifiers() {
                return typeElement.getModifiers();
            }

            @Override
            public List<? extends AnnotationMirror> getAnnotationMirrors() {
                return typeMirror.getAnnotationMirrors();
            }

            @Override
            public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
                return typeMirror.getAnnotation(annotationType);
            }

            @Override
            public <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationType) {
                return typeMirror.getAnnotationsByType(annotationType);
            }

            @Override
            public <R, P> R accept(ElementVisitor<R, P> v, P p) {
                throw new IllegalStateException("Not supported");
            }
        };
    }

    @Override
    public TypeMirror getEnclosingType() {
        return declaredType.getEnclosingType();
    }

    @Override
    public List<? extends TypeMirror> getTypeArguments() {
        return declaredType.getTypeArguments();
    }
}
