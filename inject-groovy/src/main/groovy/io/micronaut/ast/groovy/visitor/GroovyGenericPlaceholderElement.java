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
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import org.codehaus.groovy.ast.ClassNode;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Implementation of {@link io.micronaut.inject.ast.GenericPlaceholderElement} for Groovy.
 *
 * @author Jonas Konrad
 * @since 3.1.0
 */
@Internal
final class GroovyGenericPlaceholderElement extends GroovyClassElement implements GenericPlaceholderElement {

    private final List<GroovyClassElement> bounds;
    private final boolean rawType;

    GroovyGenericPlaceholderElement(GroovyVisitorContext visitorContext,
                                    ClassNode classNode,
                                    ElementAnnotationMetadataFactory annotationMetadataFactory,
                                    Map<String, ClassElement> genericInfo,
                                    int arrayDimensions,
                                    List<GroovyClassElement> bounds, boolean rawType) {
        super(visitorContext, classNode, annotationMetadataFactory, genericInfo, arrayDimensions);
        this.bounds = bounds;
        this.rawType = rawType;
    }

    @Override
    public boolean isRawType() {
        return rawType;
    }

    @Override
    protected GroovyClassElement copyConstructor() {
        return new GroovyGenericPlaceholderElement(visitorContext, classNode, elementAnnotationMetadataFactory, resolvedTypeArguments, getArrayDimensions(), bounds, rawType);
    }

    @NonNull
    @Override
    public List<? extends GroovyClassElement> getBounds() {
        return bounds;
    }

    @NonNull
    @Override
    public String getVariableName() {
        return classNode.getUnresolvedName();
    }

    @Override
    public Optional<Element> getDeclaringElement() {
        return Optional.empty();
    }

    @Override
    public ClassElement withArrayDimensions(int arrayDimensions) {
        return new GroovyGenericPlaceholderElement(visitorContext, classNode, elementAnnotationMetadataFactory, resolvedTypeArguments, arrayDimensions, bounds, rawType);
    }

    @Override
    public ClassElement foldBoundGenericTypes(@NonNull Function<ClassElement, ClassElement> fold) {
        Objects.requireNonNull(fold, "Function argument cannot be null");
        return fold.apply(this);
    }
}
