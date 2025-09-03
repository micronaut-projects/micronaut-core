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
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.WildcardElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.annotation.GenericPlaceholderElementAnnotationMetadata;
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate;

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
    private final JavaNativeElement.Placeholder genericNativeType;
    private final Element declaredElement;
    @Nullable
    private final JavaClassElement resolved;
    private final List<JavaClassElement> bounds;
    private final boolean isRawType;
    private final ElementAnnotationMetadata typeAnnotationMetadata;
    @Nullable
    private ElementAnnotationMetadata genericTypeAnnotationMetadata;

    JavaGenericPlaceholderElement(JavaNativeElement.Placeholder genericNativeType,
                                  TypeVariable realTypeVariable,
                                  @NonNull Element declaredElement,
                                  @Nullable JavaClassElement resolved,
                                  @NonNull List<JavaClassElement> bounds,
                                  @NonNull ElementAnnotationMetadataFactory annotationMetadataFactory,
                                  int arrayDimensions,
                                  boolean isRawType,
                                  String doc) {
        this(genericNativeType,
            realTypeVariable,
            declaredElement,
            resolved,
            bounds,
            selectClassElementRepresentingThisPlaceholder(resolved, bounds),
            annotationMetadataFactory,
            arrayDimensions,
            isRawType,
            doc);
    }

    JavaGenericPlaceholderElement(JavaNativeElement.Placeholder genericNativeType,
                                  TypeVariable realTypeVariable,
                                  @NonNull Element declaredElement,
                                  @Nullable JavaClassElement resolved,
                                  @NonNull List<JavaClassElement> bounds,
                                  JavaClassElement classElementRepresentingThisPlaceholder,
                                  @NonNull ElementAnnotationMetadataFactory annotationMetadataFactory,
                                  int arrayDimensions,
                                  boolean isRawType,
                                  String doc) {
        super(
            classElementRepresentingThisPlaceholder.getNativeType(),
            annotationMetadataFactory,
            classElementRepresentingThisPlaceholder.visitorContext,
            classElementRepresentingThisPlaceholder.typeArguments,
            classElementRepresentingThisPlaceholder.getTypeArguments(),
            arrayDimensions,
            doc
        );
        this.genericNativeType = genericNativeType;
        this.declaredElement = declaredElement;
        this.realTypeVariable = realTypeVariable;
        this.resolved = resolved;
        this.bounds = bounds;
        this.isRawType = isRawType;
        typeAnnotationMetadata = new GenericPlaceholderElementAnnotationMetadata(this, classElementRepresentingThisPlaceholder);
    }

    private static JavaClassElement selectClassElementRepresentingThisPlaceholder(@Nullable JavaClassElement resolved,
                                                                                  @NonNull List<JavaClassElement> bounds) {
        if (resolved != null) {
            return resolved;
        }
        return WildcardElement.findUpperType(bounds, bounds);
    }

    @Override
    protected MutableAnnotationMetadataDelegate<?> getAnnotationMetadataToWrite() {
        return getGenericTypeAnnotationMetadata();
    }

    @NonNull
    @Override
    public MutableAnnotationMetadataDelegate<AnnotationMetadata> getGenericTypeAnnotationMetadata() {
        if (genericTypeAnnotationMetadata == null) {
            genericTypeAnnotationMetadata = elementAnnotationMetadataFactory.buildGenericTypeAnnotations(this);
        }
        return genericTypeAnnotationMetadata;
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
    public JavaNativeElement.Placeholder getGenericNativeType() {
        return genericNativeType;
    }

    @Override
    public boolean isTypeVariable() {
        return true;
    }

    @Override
    public boolean isRawType() {
        return isRawType;
    }

    @NonNull
    @Override
    public List<JavaClassElement> getBounds() {
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
        return Optional.of(declaredElement);
    }

    @Override
    public ClassElement withArrayDimensions(int arrayDimensions) {
        return new JavaGenericPlaceholderElement(genericNativeType, realTypeVariable, declaredElement, resolved, bounds, elementAnnotationMetadataFactory, arrayDimensions, isRawType, doc);
    }

    @Override
    protected JavaClassElement copyThis() {
        return new JavaGenericPlaceholderElement(genericNativeType, realTypeVariable, declaredElement, resolved, bounds, elementAnnotationMetadataFactory, arrayDimensions, isRawType, doc);
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
    public JavaClassElement getResolvedInternal() {
        return resolved;
    }

}
