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
import io.micronaut.inject.ast.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.WildcardElement;

import javax.lang.model.type.WildcardType;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementation of {@link io.micronaut.inject.ast.WildcardElement} for Java.
 *
 * @author Jonas Konrad
 * @since 3.1.0
 */
@Internal
final class JavaWildcardElement extends JavaClassElement implements WildcardElement {
    private final WildcardType wildcardType;
    private final List<JavaClassElement> upperBounds;
    private final List<JavaClassElement> lowerBounds;

    JavaWildcardElement(ElementAnnotationMetadataFactory elementAnnotationMetadataFactory,
                        @NonNull WildcardType wildcardType,
                        @NonNull List<JavaClassElement> upperBounds,
                        @NonNull List<JavaClassElement> lowerBounds) {
        super(
            upperBounds.get(0).classElement,
            elementAnnotationMetadataFactory,
            upperBounds.get(0).visitorContext,
            upperBounds.get(0).typeArguments,
            upperBounds.get(0).getGenericTypeInfo()
        );
        this.wildcardType = wildcardType;
        this.upperBounds = upperBounds;
        this.lowerBounds = lowerBounds;
    }

    @Override
    public Object getNativeType() {
        return wildcardType;
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
        List<JavaClassElement> upperBounds = this.upperBounds.stream().map(ele -> toJavaClassElement(ele.foldBoundGenericTypes(fold))).collect(Collectors.toList());
        List<JavaClassElement> lowerBounds = this.lowerBounds.stream().map(ele -> toJavaClassElement(ele.foldBoundGenericTypes(fold))).collect(Collectors.toList());
        return fold.apply(upperBounds.contains(null) || lowerBounds.contains(null) ? null : new JavaWildcardElement(elementAnnotationMetadataFactory, wildcardType, upperBounds, lowerBounds));
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
                    .withBoundGenericTypes(element.getBoundGenericTypes());
            }
        }
    }
}
