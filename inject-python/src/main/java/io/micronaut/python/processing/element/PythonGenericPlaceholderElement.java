/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.python.processing.element;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata;
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate;
import io.micronaut.python.processing.PythonProcessingEnvironment;
import io.micronaut.python.processing.model.ClassDef;
import io.micronaut.python.processing.model.TypeRef;
import io.micronaut.python.processing.model.TypeVar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Implementation of {@link GenericPlaceholderElement} for Python TypeVars.
 *
 * @author Micronaut
 * @since 5.2.0
 */
@Experimental
public final class PythonGenericPlaceholderElement extends AbstractPythonClassElement implements GenericPlaceholderElement {

    private final TypeVar typeVar;
    private final List<ClassElement> bounds;
    private Element declaringElement;
    private final @Nullable ElementAnnotationMetadata typeUseAnnotationMetadata;

    public PythonGenericPlaceholderElement(TypeVar typeVar,
                                           PythonProcessingEnvironment environment,
                                           List<ClassElement> bounds) {
        this(typeVar, environment, bounds, null);
    }

    public PythonGenericPlaceholderElement(TypeVar typeVar,
                                           PythonProcessingEnvironment environment,
                                           List<ClassElement> bounds,
                                           Element declaringElement) {
        this(typeVar, environment, bounds, declaringElement, null);
    }

    private PythonGenericPlaceholderElement(TypeVar typeVar,
                                            PythonProcessingEnvironment environment,
                                            List<ClassElement> bounds,
                                            Element declaringElement,
                                            @Nullable ElementAnnotationMetadata typeUseAnnotationMetadata) {
        super(new ClassDef(typeVar.name()), environment);
        this.typeVar = typeVar;
        this.bounds = bounds != null ? bounds : Collections.emptyList();
        this.declaringElement = declaringElement;
        this.typeUseAnnotationMetadata = typeUseAnnotationMetadata;
    }

    /**
     * The type variable used with type-use annotations, such as {@code Annotated[E, NotNull()]} as a type argument.
     * The copy stays a {@link GenericPlaceholderElement}, so the type argument keeps naming the type variable.
     *
     * @param typeUseAnnotationMetadata The type-use annotation metadata of the type node
     * @return The annotated type variable
     */
    public PythonGenericPlaceholderElement withTypeUseAnnotationMetadata(ElementAnnotationMetadata typeUseAnnotationMetadata) {
        PythonGenericPlaceholderElement copy = new PythonGenericPlaceholderElement(typeVar, environment, bounds, declaringElement, typeUseAnnotationMetadata);
        copyValues(copy);
        return copy;
    }

    @Override
    protected ClassElement createWithArrayDimensions(int arrayDimensions) {
        return new PythonGenericPlaceholderElement(typeVar, environment, bounds, declaringElement, typeUseAnnotationMetadata);
    }

    @Override
    protected AbstractPythonElement copyThis() {
        return new PythonGenericPlaceholderElement(typeVar, environment, bounds, declaringElement, typeUseAnnotationMetadata);
    }

    @Override
    protected MutableAnnotationMetadataDelegate<?> getAnnotationMetadataToWrite() {
        if (typeUseAnnotationMetadata == null) {
            return super.getAnnotationMetadataToWrite();
        }
        return typeUseAnnotationMetadata;
    }

    @NonNull
    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        if (typeUseAnnotationMetadata == null || presetAnnotationMetadata != null) {
            return super.getAnnotationMetadata();
        }
        return new AnnotationMetadataHierarchy(true, super.getAnnotationMetadata(), typeUseAnnotationMetadata);
    }

    @Override
    public @NonNull MutableAnnotationMetadataDelegate<AnnotationMetadata> getTypeAnnotationMetadata() {
        if (typeUseAnnotationMetadata == null) {
            return super.getTypeAnnotationMetadata();
        }
        return typeUseAnnotationMetadata;
    }

    @Override
    public @NonNull MutableAnnotationMetadataDelegate<AnnotationMetadata> getGenericTypeAnnotationMetadata() {
        if (typeUseAnnotationMetadata == null) {
            return GenericPlaceholderElement.super.getGenericTypeAnnotationMetadata();
        }
        return typeUseAnnotationMetadata;
    }

    /**
     * The bound and the constraints of a PEP 695 type parameter ({@code [S: Book]}) as Java types, the
     * bounds of the type variable the generated Java declaration carries. {@code object} is left out:
     * {@link Object} is the implicit bound of a Java type variable.
     *
     * @param typeVar       The type parameter
     * @param environment   The processing environment resolving the bound
     * @param selfReference Whether a bound names the element declaring the type parameter, which has no
     *                      Java counterpart and is left out
     * @return The bounds, empty for an unbounded type parameter
     */
    static List<ClassElement> resolveBounds(TypeVar typeVar,
                                            PythonProcessingEnvironment environment,
                                            Predicate<TypeRef> selfReference) {
        List<ClassElement> bounds = new ArrayList<>();
        addBound(bounds, typeVar.bound(), environment, selfReference);
        for (Object constraint : typeVar.constraints()) {
            addBound(bounds, constraint, environment, selfReference);
        }
        return bounds;
    }

    private static void addBound(List<ClassElement> bounds,
                                 Object bound,
                                 PythonProcessingEnvironment environment,
                                 Predicate<TypeRef> selfReference) {
        if (bound == null) {
            return;
        }
        TypeRef typeRef = bound instanceof TypeRef typeReference ? typeReference : new TypeRef(bound.toString());
        if (selfReference.test(typeRef)) {
            return;
        }
        ClassElement boundElement = environment.visitorContext().getTypeResolver().resolve(typeRef, Map.of());
        if (!Object.class.getName().equals(boundElement.getName())) {
            bounds.add(boundElement);
        }
    }

    @Override
    public boolean isTypeVariable() {
        return true;
    }

    @Override
    public boolean isRawType() {
        return false;
    }

    @NonNull
    @Override
    public List<? extends ClassElement> getBounds() {
        if (bounds.isEmpty()) {
            return List.of(ClassElement.of(Object.class));
        }
        return bounds;
    }

    @NonNull
    @Override
    public String getVariableName() {
        return typeVar.name();
    }

    @Override
    public Optional<Element> getDeclaringElement() {
        return Optional.ofNullable(declaringElement);
    }

    @Override
    public boolean isAssignable(String type) {
        return Object.class.getName().equals(type) || getName().equals(type);
    }

    @Override
    public String toString() {
        return "Python Generic Placeholder: " + getVariableName();
    }
}
