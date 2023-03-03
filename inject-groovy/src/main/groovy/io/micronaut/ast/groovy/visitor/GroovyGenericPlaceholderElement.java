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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.WildcardElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata;
import io.micronaut.inject.ast.annotation.GenericPlaceholderElementAnnotationMetadata;
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate;

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
    private final GroovyClassElement resolved;
    private final List<GroovyClassElement> bounds;
    private final boolean rawType;
    private final ElementAnnotationMetadata typeAnnotationMetadata;
    @Nullable
    private ElementAnnotationMetadata genericTypeAnnotationMetadata;

    GroovyGenericPlaceholderElement(GroovyVisitorContext visitorContext,
                                    Element declaringElement,
                                    GroovyNativeElement placeholderNativeElement,
                                    @Nullable
                                            GroovyClassElement resolved,
                                    List<GroovyClassElement> bounds,
                                    int arrayDimensions,
                                    boolean rawType,
                                    String variableName) {
        this(visitorContext, declaringElement, placeholderNativeElement, variableName, resolved, bounds, selectClassElementRepresentingThisPlaceholder(resolved, bounds), arrayDimensions, rawType);
    }

    GroovyGenericPlaceholderElement(GroovyVisitorContext visitorContext,
                                    Element declaringElement,
                                    GroovyNativeElement placeholderNativeElement,
                                    String variableName,
                                    @Nullable
                                    GroovyClassElement resolved,
                                    List<GroovyClassElement> bounds,
                                    GroovyClassElement classElementRepresentingThisPlaceholder,
                                    int arrayDimensions,
                                    boolean rawType) {
        super(visitorContext,
                classElementRepresentingThisPlaceholder.getNativeType(),
                classElementRepresentingThisPlaceholder.getElementAnnotationMetadataFactory(),
                classElementRepresentingThisPlaceholder.resolvedTypeArguments,
                arrayDimensions);
        this.declaringElement = declaringElement;
        this.placeholderNativeElement = placeholderNativeElement;
        this.variableName = variableName;
        this.resolved = resolved;
        this.bounds = bounds;
        this.rawType = rawType;
        typeAnnotationMetadata = new GenericPlaceholderElementAnnotationMetadata(this, classElementRepresentingThisPlaceholder);

    }

    private static GroovyClassElement selectClassElementRepresentingThisPlaceholder(@Nullable GroovyClassElement resolved,
                                                                                    @NonNull List<GroovyClassElement> bounds) {
        if (resolved != null) {
            return resolved;
        }
        return WildcardElement.findUpperType(bounds, bounds);
    }

    @Override
    public boolean isTypeVariable() {
        return true;
    }

    @Override
    protected MutableAnnotationMetadataDelegate<?> getAnnotationMetadataToWrite() {
        return getGenericTypeAnnotationMetadata();
    }

    @Override
    public MutableAnnotationMetadataDelegate<AnnotationMetadata> getGenericTypeAnnotationMetadata() {
        if (genericTypeAnnotationMetadata == null) {
            genericTypeAnnotationMetadata = elementAnnotationMetadataFactory.buildGenericTypeAnnotations(this);
        }
        return genericTypeAnnotationMetadata;
    }

    @Override
    public MutableAnnotationMetadataDelegate<AnnotationMetadata> getTypeAnnotationMetadata() {
        return typeAnnotationMetadata;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return new AnnotationMetadataHierarchy(true, super.getAnnotationMetadata(), getGenericTypeAnnotationMetadata());
    }

    @Override
    public GroovyNativeElement getGenericNativeType() {
        return placeholderNativeElement;
    }

    @Override
    public boolean isRawType() {
        return rawType;
    }

    @Override
    protected GroovyClassElement copyConstructor() {
        return new GroovyGenericPlaceholderElement(visitorContext, declaringElement, placeholderNativeElement, variableName, resolved, bounds, selectClassElementRepresentingThisPlaceholder(resolved, bounds), getArrayDimensions(), rawType);
    }

    @NonNull
    @Override
    public List<GroovyClassElement> getBounds() {
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
        return new GroovyGenericPlaceholderElement(visitorContext, declaringElement, placeholderNativeElement, variableName, resolved, bounds, selectClassElementRepresentingThisPlaceholder(resolved, bounds), arrayDimensions, rawType);
    }

    @Override
    public ClassElement foldBoundGenericTypes(@NonNull Function<ClassElement, ClassElement> fold) {
        Objects.requireNonNull(fold, "Function argument cannot be null");
        return fold.apply(this);
    }

    @Override
    public Optional<ClassElement> getResolved() {
        return Optional.ofNullable(resolved);
    }

    @Nullable
    public GroovyClassElement getResolvedInternal() {
        return resolved;
    }
}
