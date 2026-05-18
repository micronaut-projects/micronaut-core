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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A ClassDef node represents a class definition.
 * <p>
 * ClassDef(identifier name, list[expr] bases, list[keyword] keywords, list[stmt] body, list[FunctionDef] decorator_list, list[TypeVar] type_params, list[AttributeDef] attributes, list[PropertyDef] properties)
 * </p>
 *
 * @param name The name of the class.
 * @param packageName The package name of the class.
 * @param bases The base classes with their type arguments.
 * @param decorators The decorators.
 * @param typeParams The type parameters.
 * @param functions The functions defined in the class.
 * @param attributes The attributes defined in the class.
 * @param properties The properties defined in the class.
 * @param constructor The constructor function (__init__) if present.
 * @param isEnum Whether this class is an enum.
 * @param values The enum values if this is an enum.
 * @param documentation The class documentation string.
 * @see <a href="https://docs.python.org/3/library/ast.html#ast.ClassDef">Python AST ClassDef</a>
 */
public record ClassDef(
    String name,
    String packageName,
    List<TypeRef> bases,
    List<DecoratorDef> decorators,
    List<TypeVar> typeParams,
    List<FunctionDef> functions,
    List<AttributeDef> attributes,
    List<PropertyDef> properties,
    FunctionDef constructor,
    boolean isEnum,
    List<String> values,
    String documentation
) implements ElementDef {

    public ClassDef {
        Objects.requireNonNull(name, "Decorator name cannot be null");
        if (bases == null) {
            bases = List.of();
        } else {
            bases = List.copyOf(bases);
        }
        if (decorators == null) {
            decorators = List.of();
        } else {
            decorators = List.copyOf(decorators);
        }
        if (typeParams == null) {
            typeParams = List.of();
        } else {
            typeParams = List.copyOf(typeParams);
        }

        if (functions == null) {
            functions = List.of();
        } else {
            functions = List.copyOf(functions);
        }

        if (attributes == null) {
            attributes = List.of();
        } else {
            attributes = List.copyOf(attributes);
        }

        if (properties == null) {
            properties = List.of();
        } else {
            properties = List.copyOf(properties);
        }

        if (values == null) {
            values = List.of();
        } else {
            values = List.copyOf(values);
        }
    }

    public ClassDef(String name) {
        this(name, "", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, false, List.of(), null);
    }

    public ClassDef(String name, List<DecoratorDef> decoratorList) {
        this(name, "", List.of(), decoratorList, List.of(), List.of(), List.of(), List.of(), null, false, List.of(), null);
    }

    public ClassDef withConstructor(FunctionDef constructor) {
        return new ClassDef(
            name,
            packageName,
            bases,
            decorators, typeParams,
            functions,
            attributes,
            properties, constructor.withClassDef(this), isEnum, values, documentation);
    }

    public ClassDef withFunction(FunctionDef function) {
        Objects.requireNonNull(function, "Function cannot be null");
        function = function.withClassDef(this);
        List<FunctionDef> functions = new ArrayList<>(this.functions);
        functions.add(function);
        return new ClassDef(
            name,
            packageName,
            bases,
            decorators,
            typeParams,
            functions,
            attributes,
            properties,
            constructor,
            isEnum,
            values,
            documentation
        );
    }

    public ClassDef withAttribute(AttributeDef attribute) {
        Objects.requireNonNull(attribute, "Attribute cannot be null");
        // Set the declaring class on the attribute
        AttributeDef attributeWithDeclaringClass = attribute.withDeclaringClass(this);
        List<AttributeDef> attributes = new ArrayList<>(this.attributes);
        attributes.add(attributeWithDeclaringClass);
        return new ClassDef(name, packageName, bases, decorators, typeParams, functions, attributes, properties, constructor, isEnum, values, documentation);
    }

    public ClassDef withProperty(PropertyDef property) {
        Objects.requireNonNull(property, "Property cannot be null");
        // Set the declaring class on the property
        PropertyDef propertyWithDeclaringClass = property.withDeclaringClass(this);
        List<PropertyDef> properties = new ArrayList<>(this.properties);
        properties.add(propertyWithDeclaringClass);
        return new ClassDef(name, packageName, bases, decorators, typeParams, functions, attributes, properties, constructor, isEnum, values, documentation);
    }

    public ClassDef withEnum(boolean isEnum, List<String> values) {
        return new ClassDef(name, packageName, bases, decorators, typeParams, functions, attributes, properties, constructor, isEnum, values, documentation);
    }

    public String qualifiedName() {
        return packageName + "." + name;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ClassDef classDef = (ClassDef) o;
        return Objects.equals(name, classDef.name) && Objects.equals(packageName, classDef.packageName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, packageName);
    }
}
