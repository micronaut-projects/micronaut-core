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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ArrayableClassElement;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.WildcardElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate;
import io.micronaut.inject.ast.annotation.WildcardElementAnnotationMetadata;

import javax.lang.model.type.WildcardType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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
    private final ElementAnnotationMetadata typeAnnotationMetadata;
    @Nullable
    private ElementAnnotationMetadata genericTypeAnnotationMetadata;

    JavaWildcardElement(ElementAnnotationMetadataFactory elementAnnotationMetadataFactory,
                        @NonNull WildcardType wildcardType,
                        @NonNull JavaClassElement mostUpper,
                        @NonNull List<JavaClassElement> upperBounds,
                        @NonNull List<JavaClassElement> lowerBounds,
                        @Nullable String doc) {
        super(
            mostUpper.getNativeType(),
            elementAnnotationMetadataFactory,
            mostUpper.visitorContext,
            mostUpper.typeArguments,
            mostUpper.getTypeArguments(),
            doc
        );
        this.wildcardType = wildcardType;
        this.upperBound = mostUpper;
        this.upperBounds = upperBounds;
        this.lowerBounds = lowerBounds;
        typeAnnotationMetadata = new WildcardElementAnnotationMetadata(this, upperBound);
    }

    @Override
    public Optional<ClassElement> getResolved() {
        return Optional.of(upperBound);
    }

    @NonNull
    @Override
    public MutableAnnotationMetadataDelegate<AnnotationMetadata> getGenericTypeAnnotationMetadata() {
        if (genericTypeAnnotationMetadata == null) {
            genericTypeAnnotationMetadata = elementAnnotationMetadataFactory.buildGenericTypeAnnotations(this);
        }
        return genericTypeAnnotationMetadata;
    }

    @Override
    protected MutableAnnotationMetadataDelegate<?> getAnnotationMetadataToWrite() {
        return getGenericTypeAnnotationMetadata();
    }

    @NonNull
    @Override
    public MutableAnnotationMetadataDelegate<AnnotationMetadata> getTypeAnnotationMetadata() {
        return typeAnnotationMetadata;
    }

    @NonNull
    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return new AnnotationMetadataHierarchy(true, super.getAnnotationMetadata(), getGenericTypeAnnotationMetadata());
    }

    @NonNull
    @Override
    public Object getGenericNativeType() {
        return wildcardType;
    }

    @Override
    public boolean isTypeVariable() {
        return true;
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
        List<JavaClassElement> upperBounds = this.upperBounds.stream()
            .map(ele -> toJavaClassElement(ele.foldBoundGenericTypes(fold)))
            .toList();
        List<JavaClassElement> lowerBounds = this.lowerBounds.stream()
            .map(ele -> toJavaClassElement(ele.foldBoundGenericTypes(fold)))
            .toList();
        return fold.apply(upperBounds.contains(null) || lowerBounds.contains(null) ? null : new JavaWildcardElement(elementAnnotationMetadataFactory, wildcardType, upperBound, upperBounds, lowerBounds, doc));
    }

    private JavaClassElement toJavaClassElement(ClassElement element) {
        if (element == null) {
            return null;
        }
        if (element instanceof JavaClassElement classEl) {
            return classEl;
        }
        if (element.isWildcard() || element.isGenericPlaceholder()) {
            throw new UnsupportedOperationException("Cannot convert wildcard / free type variable to JavaClassElement");
        }
        return (JavaClassElement) ((ArrayableClassElement) visitorContext.getClassElement(element.getName(), elementAnnotationMetadataFactory)
            .orElseThrow(() -> new UnsupportedOperationException("Cannot convert ClassElement to JavaClassElement, class was not found on the visitor context")))
            .withArrayDimensions(element.getArrayDimensions())
            .withTypeArguments((Collection<ClassElement>) element.getBoundGenericTypes());
    }

}
