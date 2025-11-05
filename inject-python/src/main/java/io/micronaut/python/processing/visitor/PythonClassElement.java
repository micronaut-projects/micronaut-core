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

import java.util.List;
import java.util.Optional;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.python.processing.PythonProcessingEnvironment;

public final class PythonClassElement extends AbstractPythonClassElement {

    public PythonClassElement(ClassDef classDef, PythonProcessingEnvironment environment) {
        super(classDef, environment);
    }

    public PythonClassElement(ClassDef classDef, PythonProcessingEnvironment environment, int arrayDimensions) {
        super(classDef, environment, arrayDimensions);
    }

    @Override
    protected AbstractPythonElement copyThis() {
        return new PythonClassElement(getNativeType(), environment, arrayDimensions);
    }

    public boolean isPythonSource() {
        return environment.classes().containsKey(getNativeType().name());
    }

    @Override
    protected ClassElement createWithArrayDimensions(int arrayDimensions) {
        return new PythonClassElement(getNativeType(), environment, arrayDimensions);
    }

    @Override
    public String toString() {
        return "Python Class: " + getNativeType().name();
    }

    @Override
    public Optional<MethodElement> getPrimaryConstructor() {
        FunctionDef constructor = getNativeType().constructor();
        if (constructor != null) {
            return Optional.of(new PythonConstructorElement(constructor, environment, this, this, environment.metadataFactory()));
        }
        return Optional.empty();
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

        for (ClassElement anInterface : getInterfaces()) {
            if (anInterface.isAssignable(type)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Optional<ClassElement> getSuperType() {
        List<String> bases = getNativeType().bases();
        if (!bases.isEmpty()) {
            for (String base : bases) {
                ClassElement baseElement = environment.classes().get(base);
                if (baseElement != null) {
                    return Optional.of(baseElement);
                }
            }
        }
        return Optional.empty();
    }
}
