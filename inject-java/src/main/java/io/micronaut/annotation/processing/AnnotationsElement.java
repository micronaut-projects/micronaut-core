/*
 * Copyright 2017-2023 original authors
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
import javax.lang.model.type.TypeMirror;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A custom Java Lang Model element that provides the annotations from the type mirror.
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
final class AnnotationsElement implements Element {
    private final TypeMirror typeMirror;

    public AnnotationsElement(TypeMirror clazz) {
        this.typeMirror = clazz;
    }

    @Override
    public TypeMirror asType() {
        return typeMirror;
    }

    @Override
    public ElementKind getKind() {
        return ElementKind.OTHER;
    }

    @Override
    public Set<Modifier> getModifiers() {
        return Collections.emptySet();
    }

    @Override
    public Name getSimpleName() {
        throw notSupportedMethod();
    }

    @Override
    public Element getEnclosingElement() {
        return null;
    }

    @Override
    public List<? extends Element> getEnclosedElements() {
        return Collections.emptyList();
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
        return v.visit(this);
    }

    private static IllegalStateException notSupportedMethod() {
        return new IllegalStateException("Not supported method");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AnnotationsElement that = (AnnotationsElement) o;
        return Objects.equals(typeMirror, that.typeMirror);
    }

    @Override
    public int hashCode() {
        return typeMirror.hashCode();
    }
}
