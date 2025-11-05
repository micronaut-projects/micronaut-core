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

import io.micronaut.context.annotation.ConfigurationInject;
import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.python.processing.PythonProcessingEnvironment;

import java.util.List;

/**
 * Represents a Python constructor as a Micronaut {@link ConstructorElement}.
 * <p>
 * This class wraps a {@link PythonMethodElement} representing a constructor
 * (a method with name "__init__") and provides the {@link ConstructorElement} interface.
 * </p>
 *
 * @author Micronaut Team
 * @since 5.0.0
 */
public final class PythonConstructorElement extends PythonMethodElement implements ConstructorElement {

    /**
     * Constructs a new {@code PythonConstructorElement} from the given {@code FunctionDef}.
     *
     * @param constructorDef the constructor function definition; must not be {@code null}
     * @param environment the Python processing environment; must not be {@code null}
     * @param declaringType the class that declares this constructor; must not be {@code null}
     * @param owningType the class that owns this constructor (may be a subclass); must not be {@code null}
     * @param metadataFactory the annotation metadata factory; must not be {@code null}
     * @throws NullPointerException if any parameter is {@code null}
     */
    public PythonConstructorElement(FunctionDef constructorDef,
                                    PythonProcessingEnvironment environment,
                                    AbstractPythonClassElement declaringType,
                                    AbstractPythonClassElement owningType,
                                    ElementAnnotationMetadataFactory metadataFactory) {
        super(constructorDef, environment, declaringType, owningType, metadataFactory);
        List<DecoratorDef> decorators = constructorDef.declaringClass().decorators();
        for (DecoratorDef decorator : decorators) {
            if (owningType.hasDeclaredStereotype(ConfigurationReader.class) &&
                decorator.name().equals("dataclass")) {
                // data classes usee configuration inject
                annotate(ConfigurationInject.class);
                break;
            }
        }
    }

    @Override
    public boolean isPublic() {
        return true;
    }

    @Override
    public String getName() {
        return "__init__";
    }

    @Override
    public ClassElement getReturnType() {
        // Constructor return type is the declaring class
        return getDeclaringType();
    }
}
