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
package io.micronaut.ast.groovy.visitor;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ArrayableClassElement;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.WildcardElement;

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
final class GroovyWildcardElement extends GroovyClassElement implements WildcardElement {
    private final List<GroovyClassElement> upperBounds;
    private final List<GroovyClassElement> lowerBounds;

    GroovyWildcardElement(@NonNull List<GroovyClassElement> upperBounds,
                          @NonNull List<GroovyClassElement> lowerBounds,
                          ElementAnnotationMetadataFactory annotationMetadataFactory) {
        super(
            upperBounds.get(0).visitorContext,
            upperBounds.get(0).classNode,
            annotationMetadataFactory,
            upperBounds.get(0).getGenericTypeInfo(),
            0
        );
        this.upperBounds = upperBounds;
        this.lowerBounds = lowerBounds;
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
        List<GroovyClassElement> upperBounds = this.upperBounds.stream().map(ele -> toGroovyClassElement(ele.foldBoundGenericTypes(fold))).collect(Collectors.toList());
        List<GroovyClassElement> lowerBounds = this.lowerBounds.stream().map(ele -> toGroovyClassElement(ele.foldBoundGenericTypes(fold))).collect(Collectors.toList());
        return fold.apply(upperBounds.contains(null) || lowerBounds.contains(null) ? null : new GroovyWildcardElement(upperBounds, lowerBounds, elementAnnotationMetadataFactory));
    }

    private GroovyClassElement toGroovyClassElement(ClassElement element) {
        if (element == null || element instanceof GroovyClassElement) {
            return (GroovyClassElement) element;
        } else {
            if (element.isWildcard() || element.isGenericPlaceholder()) {
                throw new UnsupportedOperationException("Cannot convert wildcard / free type variable to GroovyClassElement");
            } else {
                return (GroovyClassElement) ((ArrayableClassElement) visitorContext.getClassElement(element.getName(), elementAnnotationMetadataFactory)
                    .orElseThrow(() -> new UnsupportedOperationException("Cannot convert ClassElement to GroovyClassElement, class was not found on the visitor context")))
                    .withArrayDimensions(element.getArrayDimensions())
                    .withBoundGenericTypes(element.getBoundGenericTypes());
            }
        }
    }
}
