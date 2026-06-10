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

import io.micronaut.core.annotation.Experimental;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.EnumConstantElement;
import io.micronaut.inject.ast.EnumElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.PropertyElementQuery;
import io.micronaut.python.processing.PythonProcessingEnvironment;

/**
 * Represents a Python enum class element in the Micronaut injection context.
 * <p>
 * This class extends {@link AbstractPythonClassElement} and implements {@link EnumElement}
 * to provide enum-specific functionality for Python enums parsed from AST.
 * </p>
 *
 * @author Micronaut Team
 * @since 5.0.0
 */
@Experimental
public final class PythonEnumElement extends AbstractPythonClassElement implements EnumElement {

    /**
     * Creates a new PythonEnumElement for the given class definition.
     *
     * @param classDef The class definition from Python AST
     * @param environment The Python processing environment
     */
    public PythonEnumElement(ClassDef classDef, PythonProcessingEnvironment environment) {
        super(classDef, environment);
    }

    /**
     * Creates a new PythonEnumElement for the given class definition with array dimensions.
     *
     * @param classDef The class definition from Python AST
     * @param environment The Python processing environment
     * @param arrayDimensions The number of array dimensions
     */
    public PythonEnumElement(ClassDef classDef, PythonProcessingEnvironment environment, int arrayDimensions) {
        super(classDef, environment, arrayDimensions);
    }

    @Override
    public boolean isEnum() {
        return true;
    }

    @Override
    protected ClassElement createWithArrayDimensions(int arrayDimensions) {
        return new PythonEnumElement(getNativeType(), environment, arrayDimensions);
    }

    @Override
    public String toString() {
        return "Python Enum: " + getNativeType().name();
    }

    @Override
    public List<String> values() {
        return getNativeType().values();
    }

    @Override
    public List<EnumConstantElement> elements() {
        return getNativeType().values().stream()
            .map(enumValue -> {
                // Create a synthetic AttributeDef for the enum constant
                AttributeDef constantDef = new AttributeDef(enumValue, getName(), new TypeRef(getName()), null, List.of(), null, true, getNativeType());
                return new PythonEnumConstantElement(constantDef, environment, this, this, environment.metadataFactory());
            })
            .collect(Collectors.toList());
    }

    @Override
    public List<PropertyElement> getBeanProperties() {
        return filterEnumConstants(super.getBeanProperties());
    }

    @Override
    public List<PropertyElement> getBeanProperties(PropertyElementQuery propertyElementQuery) {
        return filterEnumConstants(super.getBeanProperties(propertyElementQuery));
    }

    private List<PropertyElement> filterEnumConstants(List<PropertyElement> properties) {
        Set<String> enumConstants = Set.copyOf(values());
        return properties.stream()
            .filter(property -> !enumConstants.contains(property.getName()))
            .toList();
    }

    @Override
    protected AbstractPythonElement copyThis() {
        return new PythonEnumElement(getNativeType(), environment, arrayDimensions);
    }

    @Override
    public boolean isAssignable(String type) {
        return Object.class.getName().equals(type) || getName().equals(type);
    }
}
