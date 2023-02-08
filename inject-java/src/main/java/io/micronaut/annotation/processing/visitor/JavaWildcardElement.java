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
import io.micronaut.inject.ast.ArrayableClassElement;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.WildcardElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate;

import javax.lang.model.type.WildcardType;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * Implementation of {@link io.micronaut.inject.ast.WildcardElement} for Java.
 *
 * @author Jonas Konrad
 * @since 3.1.0
 */
@Internal
final class JavaWildcardElement extends JavaClassElement implements WildcardElement {
    private final WildcardType wildcardType;
    private final JavaClassElement upperBound;
    private final List<JavaClassElement> upperBounds;
    private final List<JavaClassElement> lowerBounds;

    JavaWildcardElement(ElementAnnotationMetadataFactory elementAnnotationMetadataFactory,
                        @NonNull WildcardType wildcardType,
                        @NonNull JavaClassElement mostUpper,
                        @NonNull List<JavaClassElement> upperBounds,
                        @NonNull List<JavaClassElement> lowerBounds) {
        super(
                mostUpper.classElement,
                elementAnnotationMetadataFactory,
                mostUpper.visitorContext,
                mostUpper.typeArguments,
                mostUpper.getTypeArguments()
        );
        this.wildcardType = wildcardType;
        this.upperBound = mostUpper;
        this.upperBounds = upperBounds;
        this.lowerBounds = lowerBounds;
    }

    @Override
    public MutableAnnotationMetadataDelegate<?> getAnnotationMetadata() {
        return upperBound.getAnnotationMetadata();
    }

    @Override
    public boolean isTypeVariable() {
        return true;
    }

    @Override
    public Object getNativeType() {
        return wildcardType;
    }

    @Override
    public int hashCode() {
        return wildcardType.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        io.micronaut.inject.ast.Element that = (io.micronaut.inject.ast.Element) o;
        if (that instanceof JavaWildcardElement wildcardElement) {
            return wildcardElement.wildcardType.equals(wildcardType);
        }
        return false;
    }

    @NonNull
    @Override
    public List<? extends ClassElement> getUpperBounds() {
        return upperBounds;
    }

    @NonNull
    @Override
    public List<? extends ClassElement> getLowerBounds() {
        return lowerBounds;
    }

    @Override
    public ClassElement withArrayDimensions(int arrayDimensions) {
        if (arrayDimensions != 0) {
            throw new UnsupportedOperationException("Can't create array of wildcard");
        }
        return this;
    }

    @Override
    public ClassElement foldBoundGenericTypes(@NonNull Function<ClassElement, ClassElement> fold) {
        List<JavaClassElement> upperBounds = this.upperBounds.stream().map(ele -> toJavaClassElement(ele.foldBoundGenericTypes(fold))).toList();
        List<JavaClassElement> lowerBounds = this.lowerBounds.stream().map(ele -> toJavaClassElement(ele.foldBoundGenericTypes(fold))).toList();
        return fold.apply(upperBounds.contains(null) || lowerBounds.contains(null) ? null : new JavaWildcardElement(elementAnnotationMetadataFactory, wildcardType, upperBound, upperBounds, lowerBounds));
    }

    private JavaClassElement toJavaClassElement(ClassElement element) {
        if (element == null || element instanceof JavaClassElement) {
            return (JavaClassElement) element;
        } else {
            if (element.isWildcard() || element.isGenericPlaceholder()) {
                throw new UnsupportedOperationException("Cannot convert wildcard / free type variable to JavaClassElement");
            } else {
                return (JavaClassElement) ((ArrayableClassElement) visitorContext.getClassElement(element.getName(), elementAnnotationMetadataFactory)
                        .orElseThrow(() -> new UnsupportedOperationException("Cannot convert ClassElement to JavaClassElement, class was not found on the visitor context")))
                        .withArrayDimensions(element.getArrayDimensions())
                        .withTypeArguments((Collection<ClassElement>) element.getBoundGenericTypes());
            }
        }
    }
}
