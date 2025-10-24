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

import io.micronaut.inject.ast.ArrayableClassElement;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.python.processing.PythonProcessingEnvironment;
import io.micronaut.python.processing.util.GraalPyUtil;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class PythonClassElement extends AbstractPythonClassElement {
    public PythonClassElement(ClassDef classDef, PythonProcessingEnvironment environment) {
        super(classDef, environment);
    }

    public PythonClassElement(ClassDef classDef, PythonProcessingEnvironment environment, int arrayDimensions) {
        super(classDef, environment, arrayDimensions);
    }

    @Override
    protected ClassElement createWithArrayDimensions(int arrayDimensions) {
        return new PythonClassElement(getNativeType(), environment, arrayDimensions);
    }

    @Override
    public ClassDef getNativeType() {
        return (ClassDef) super.getNativeType();
    }

    @Override
    public String toString() {
        return "Python Class: " + getNativeType().name();
    }

    @Override
    public <T extends Element> List<T> getEnclosedElements(ElementQuery<T> query) {
        return List.of();
    }

    @Override
    public Optional<MethodElement> getPrimaryConstructor() {
        FunctionDef constructor = getNativeType().constructor();
        if (constructor != null) {
            return Optional.of(new PythonMethodElement(constructor, environment, this, environment.metadataFactory()));
        }
        return Optional.empty();
    }

    @Override
    public String getPackageName() {
        return getNativeType().packageName();
    }

    @Override
    public boolean isAssignable(String type) {
        if (getName().equals(type)) {
            return true;
        }
        if (getNativeType().bases().contains(type)) {
            return true;
        }
        for (String base : getNativeType().bases()) {
            ClassElement baseElement = environment.classes().get(base);
            if (baseElement != null && baseElement.isAssignable(type)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Optional<String> getDocumentation(boolean parseContent) {
        String doc = getNativeType().documentation();
        if (doc == null) {
            return Optional.empty();
        }
        if (parseContent) {
            // Parse Python docstring to extract main description
            return Optional.of(GraalPyUtil.parsePythonDocstring(doc));
        }
        return Optional.of(doc);
    }
}
