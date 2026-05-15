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
package io.micronaut.python.processing.visitor;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.python.processing.PythonProcessingEnvironment;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of {@link GenericPlaceholderElement} for Python TypeVars.
 *
 * @author Micronaut
 * @since 4.8.0
 */
public final class PythonGenericPlaceholderElement extends AbstractPythonClassElement implements GenericPlaceholderElement {

    private final TypeVar typeVar;
    private final List<PythonClassElement> bounds;
    private Element declaringElement;

    public PythonGenericPlaceholderElement(TypeVar typeVar,
                                           PythonProcessingEnvironment environment,
                                           List<PythonClassElement> bounds) {
        this(typeVar, environment, bounds, null);
    }

    public PythonGenericPlaceholderElement(TypeVar typeVar,
                                           PythonProcessingEnvironment environment,
                                           List<PythonClassElement> bounds,
                                           Element declaringElement) {
        super(new ClassDef(typeVar.name()), environment);
        this.typeVar = typeVar;
        this.bounds = bounds != null ? bounds : Collections.emptyList();
        this.declaringElement = declaringElement;
    }

    @Override
    protected ClassElement createWithArrayDimensions(int arrayDimensions) {
        return new PythonGenericPlaceholderElement(typeVar, environment, bounds, declaringElement);
    }

    @Override
    protected AbstractPythonElement copyThis() {
        return new PythonGenericPlaceholderElement(typeVar, environment, bounds, declaringElement);
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
