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
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
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
        this(
                elementAnnotationMetadataFactory,
                wildcardType,
                findUpperType(upperBounds, lowerBounds),
                upperBounds,
                lowerBounds
        );
    }

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
                mostUpper.getGenericTypeInfo()
        );
        this.wildcardType = wildcardType;
        this.upperBounds = upperBounds;
        this.lowerBounds = lowerBounds;
    }

    @NonNull
    private static JavaClassElement findUpperType(List<JavaClassElement> upperBounds, List<JavaClassElement> lowerBounds) {
        JavaClassElement upper = null;
        for (JavaClassElement lowerBound : lowerBounds) {
            if (upper == null || lowerBound.isAssignable(upper)) {
                upper = lowerBound;
            }
        }
        for (JavaClassElement upperBound : upperBounds) {
            if (upper == null || upperBound.isAssignable(upper)) {
                upper = upperBound;
            }
        }
        return upper;
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
