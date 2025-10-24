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
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.python.processing.PythonProcessingEnvironment;

import java.util.List;

public sealed abstract class AbstractPythonClassElement extends AbstractPythonElement implements ArrayableClassElement permits PythonClassElement, PythonEnumElement {
    protected final int arrayDimensions;
    protected final PythonProcessingEnvironment environment;

    protected AbstractPythonClassElement(ClassDef classDef, PythonProcessingEnvironment environment) {
        this(classDef, environment, 0);
    }

    protected AbstractPythonClassElement(ClassDef classDef, PythonProcessingEnvironment environment, int arrayDimensions) {
        super(
            classDef.packageName().isEmpty() ? classDef.name() : classDef.packageName() + "." + classDef.name(),
            classDef,
            environment.metadataFactory()
        );
        this.environment = environment;
        this.arrayDimensions = arrayDimensions;
    }

    @Override
    public ClassElement withArrayDimensions(int arrayDimensions) {
        return createWithArrayDimensions(arrayDimensions);
    }

    protected abstract ClassElement createWithArrayDimensions(int arrayDimensions);

    @Override
    public boolean isArray() {
        return arrayDimensions > 0;
    }

    @Override
    public int getArrayDimensions() {
        return arrayDimensions;
    }
}
