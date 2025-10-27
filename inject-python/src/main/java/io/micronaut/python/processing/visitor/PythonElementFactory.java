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
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.ElementFactory;
import io.micronaut.inject.ast.EnumConstantElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.python.processing.PythonProcessingEnvironment;

/**
 * Python implementation of {@link ElementFactory} for creating Python AST-based elements.
 * <p>
 * This factory creates Micronaut elements from Python AST definition objects:
 * - {@link ClassDef} for classes
 * - {@link FunctionDef} for methods and constructors
 * - {@link AttributeDef} for fields and enum constants
 * </p>
 *
 * @author Micronaut Team
 * @since 5.0.0
 */
public final class PythonElementFactory implements ElementFactory<ElementDef, ClassDef, FunctionDef, AttributeDef> {

    private final PythonProcessingEnvironment environment;

    /**
     * Creates a new PythonElementFactory with the given processing environment.
     *
     * @param environment The Python processing environment
     */
    public PythonElementFactory(PythonProcessingEnvironment environment) {
        this.environment = environment;
    }

    @NonNull
    @Override
    public ClassElement newClassElement(@NonNull ClassDef classDef,
                                        @NonNull ElementAnnotationMetadataFactory annotationMetadataFactory) {
        if (classDef.isEnum()) {
            return new PythonEnumElement(classDef, environment);
        } else {
            return new PythonClassElement(classDef, environment);
        }
    }

    @NonNull
    @Override
    public ClassElement newSourceClassElement(@NonNull ClassDef classDef,
                                              @NonNull ElementAnnotationMetadataFactory annotationMetadataFactory) {
        // For Python, source and regular class elements are the same
        return newClassElement(classDef, annotationMetadataFactory);
    }

    @NonNull
    @Override
    public MethodElement newSourceMethodElement(@NonNull ClassElement owningClass,
                                                @NonNull FunctionDef method,
                                                @NonNull ElementAnnotationMetadataFactory annotationMetadataFactory) {
        return newMethodElement(owningClass, method, annotationMetadataFactory);
    }

    @NonNull
    @Override
    public MethodElement newMethodElement(@NonNull ClassElement owningClass,
                                          @NonNull FunctionDef method,
                                          @NonNull ElementAnnotationMetadataFactory annotationMetadataFactory) {
        if (!(owningClass instanceof AbstractPythonClassElement)) {
            throw new IllegalArgumentException("Owning class must be a Python class element");
        }
        AbstractPythonClassElement pythonClass = (AbstractPythonClassElement) owningClass;
        return new PythonMethodElement(method, environment, pythonClass, pythonClass, annotationMetadataFactory);
    }

    @NonNull
    @Override
    public ConstructorElement newConstructorElement(@NonNull ClassElement owningClass,
                                                    @NonNull FunctionDef constructor,
                                                    @NonNull ElementAnnotationMetadataFactory annotationMetadataFactory) {
        // Python constructors are represented as FunctionDef with name "__init__"
        // We create a PythonConstructorElement that implements ConstructorElement
        if (!(owningClass instanceof AbstractPythonClassElement)) {
            throw new IllegalArgumentException("Owning class must be a Python class element");
        }
        AbstractPythonClassElement pythonClass = (AbstractPythonClassElement) owningClass;
        return new PythonConstructorElement(constructor, environment, pythonClass, pythonClass, annotationMetadataFactory);
    }

    @NonNull
    @Override
    public EnumConstantElement newEnumConstantElement(@NonNull ClassElement owningClass,
                                                      @NonNull AttributeDef enumConstant,
                                                      @NonNull ElementAnnotationMetadataFactory annotationMetadataFactory) {
        if (!(owningClass instanceof PythonEnumElement)) {
            throw new IllegalArgumentException("Owning class must be a PythonEnumElement for enum constants");
        }
        PythonEnumElement enumClass = (PythonEnumElement) owningClass;
        return new PythonEnumConstantElement(enumConstant, environment, enumClass, enumClass, annotationMetadataFactory);
    }

    @NonNull
    @Override
    public FieldElement newFieldElement(@NonNull ClassElement owningClass,
                                        @NonNull AttributeDef field,
                                        @NonNull ElementAnnotationMetadataFactory annotationMetadataFactory) {
        if (!(owningClass instanceof AbstractPythonClassElement)) {
            throw new IllegalArgumentException("Owning class must be a Python class element");
        }
        AbstractPythonClassElement pythonClass = (AbstractPythonClassElement) owningClass;
        return new PythonFieldElement(field, environment, pythonClass, pythonClass, annotationMetadataFactory);
    }
}
