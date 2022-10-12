/*
 * Copyright 2017-2021 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.GenericPlaceholderElement;

import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeVariable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Implementation of {@link io.micronaut.inject.ast.GenericPlaceholderElement} for Java.
 *
 * @author graemerocher
 * @author Jonas Konrad
 * @since 3.1.0
 */
@Internal
final class JavaGenericPlaceholderElement extends JavaClassElement implements GenericPlaceholderElement {
    final TypeVariable realTypeVariable;
    private final List<JavaClassElement> bounds;

    JavaGenericPlaceholderElement(@NonNull TypeVariable realTypeVariable,
                                  @NonNull List<JavaClassElement> bounds,
                                  @NonNull ElementAnnotationMetadataFactory annotationMetadataFactory,
                                  int arrayDimensions) {
        super(
            bounds.get(0).classElement,
            annotationMetadataFactory,
            bounds.get(0).visitorContext,
            bounds.get(0).typeArguments,
            bounds.get(0).getGenericTypeInfo(),
            arrayDimensions,
            true
        );
        this.realTypeVariable = realTypeVariable;
        this.bounds = bounds;
    }

    @Override
    public Object getNativeType() {
        // Native types should be always Element
        return getParameterElement();
    }

    @NonNull
    @Override
    public List<? extends ClassElement> getBounds() {
        return bounds;
    }

    private TypeParameterElement getParameterElement() {
        return (TypeParameterElement) realTypeVariable.asElement();
    }

    @Override
    @NonNull
    public String getVariableName() {
        return getParameterElement().getSimpleName().toString();
    }

    @Override
    public Optional<Element> getDeclaringElement() {
        return Optional.of(mirrorToClassElement(getParameterElement().getGenericElement().asType(), visitorContext, getGenericTypeInfo()));
    }

    @Override
    public ClassElement withArrayDimensions(int arrayDimensions) {
        return new JavaGenericPlaceholderElement(realTypeVariable, bounds, elementAnnotationMetadataFactory, arrayDimensions);
    }

    @Override
    public ClassElement foldBoundGenericTypes(@NonNull Function<ClassElement, ClassElement> fold) {
        Objects.requireNonNull(fold, "Function argument cannot be null");
        return fold.apply(this);
    }

}
