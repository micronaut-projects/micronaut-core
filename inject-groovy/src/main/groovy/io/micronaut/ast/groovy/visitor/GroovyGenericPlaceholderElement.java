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
import io.micronaut.inject.ast.WildcardElement;

import java.util.Collections;
import java.util.List;
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

    private final GroovyNativeElement placeholderNativeElement;
    private final Element declaringElement;
    private final String variableName;
    private final GroovyClassElement mostUpper;
    private final List<GroovyClassElement> bounds;
    private final boolean rawType;

    GroovyGenericPlaceholderElement(GroovyVisitorContext visitorContext,
                                    Element declaringElement,
                                    GroovyNativeElement placeholderNativeElement,
                                    List<GroovyClassElement> bounds,
                                    boolean rawType,
                                    String variableName) {
        this(visitorContext, declaringElement, placeholderNativeElement, variableName, WildcardElement.findUpperType(bounds, Collections.emptyList()), bounds, 0, rawType);
    }

    GroovyGenericPlaceholderElement(GroovyVisitorContext visitorContext,
                                    Element declaringElement,
                                    GroovyNativeElement placeholderNativeElement,
                                    String variableName, GroovyClassElement mostUpper,
                                    List<GroovyClassElement> bounds,
                                    int arrayDimensions,
                                    boolean rawType) {
        super(visitorContext, mostUpper.getNativeType(), mostUpper.elementAnnotationMetadataFactory, mostUpper.resolvedTypeArguments, arrayDimensions);
        this.declaringElement = declaringElement;
        this.placeholderNativeElement = placeholderNativeElement;
        this.variableName = variableName;
        this.mostUpper = mostUpper;
        this.bounds = bounds;
        this.rawType = rawType;
    }

    @Override
    public Object getGenericNativeType() {
        return placeholderNativeElement;
    }

    @Override
    public boolean isRawType() {
        return rawType;
    }

    @Override
    protected GroovyClassElement copyConstructor() {
        return new GroovyGenericPlaceholderElement(visitorContext, declaringElement, placeholderNativeElement, variableName, mostUpper, bounds, getArrayDimensions(), rawType);
    }

    @NonNull
    @Override
    public List<? extends GroovyClassElement> getBounds() {
        return bounds;
    }

    @NonNull
    @Override
    public String getVariableName() {
        return variableName;
    }

    @Override
    public Optional<Element> getDeclaringElement() {
        return Optional.ofNullable(declaringElement);
    }

    @Override
    public ClassElement withArrayDimensions(int arrayDimensions) {
        return new GroovyGenericPlaceholderElement(visitorContext, declaringElement, placeholderNativeElement, variableName, mostUpper, bounds, arrayDimensions, rawType);
    }

    @Override
    public ClassElement foldBoundGenericTypes(@NonNull Function<ClassElement, ClassElement> fold) {
        Objects.requireNonNull(fold, "Function argument cannot be null");
        return fold.apply(this);
    }
}
