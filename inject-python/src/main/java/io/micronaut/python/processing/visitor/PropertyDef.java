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
import java.util.Objects;

/**
 * A PropertyDef node represents a Python property definition.
 * <p>
 * PropertyDef(identifier name, FunctionDef getter, FunctionDef setter, FunctionDef deleter, AttributeDef field, list[DecoratorDef] decorators, ClassDef declaringClass)
 * </p>
 *
 * @param name The name of the property.
 * @param getter The getter method (@property decorated function).
 * @param setter The setter method (@property.setter decorated function).
 * @param deleter The deleter method (@property.deleter decorated function).
 * @param field The associated field if this property is field-backed.
 * @param decorators The decorators applied to this property.
 * @param declaringClass The class that declares this property.
 * @see <a href="https://docs.python.org/3/library/functions.html#property">Python Property</a>
 */
@Experimental
public record PropertyDef(
    String name,
    FunctionDef getter,
    FunctionDef setter,
    FunctionDef deleter,
    AttributeDef field,
    List<DecoratorDef> decorators,
    ClassDef declaringClass
) implements ElementDef {

    public PropertyDef {
        Objects.requireNonNull(name, "Property name cannot be null");
        if (decorators == null) {
            decorators = List.of();
        }
    }

    public PropertyDef(String name) {
        this(name, null, null, null, null, List.of(), null);
    }

    public PropertyDef(String name, FunctionDef getter) {
        this(name, getter, null, null, null, List.of(), null);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PropertyDef that = (PropertyDef) o;
        return Objects.equals(name, that.name) && Objects.equals(declaringClass, that.declaringClass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, declaringClass);
    }

    /**
     * Returns true if this property has a getter method.
     */
    public boolean hasGetter() {
        return getter != null;
    }

    /**
     * Returns true if this property has a setter method.
     */
    public boolean hasSetter() {
        return setter != null;
    }

    /**
     * Returns true if this property has a deleter method.
     */
    public boolean hasDeleter() {
        return deleter != null;
    }

    /**
     * Returns true if this property is backed by a field.
     */
    public boolean hasField() {
        return field != null;
    }

    /**
     * Returns true if this is a read-only property (has getter but no setter).
     */
    public boolean isReadOnly() {
        return hasGetter() && !hasSetter();
    }

    /**
     * Returns true if this is a write-only property (no getter but has setter).
     */
    public boolean isWriteOnly() {
        return !hasGetter() && hasSetter();
    }

    /**
     * Creates a new PropertyDef with the given getter.
     */
    public PropertyDef withGetter(FunctionDef getter) {
        return new PropertyDef(name, getter, setter, deleter, field, decorators, declaringClass);
    }

    /**
     * Creates a new PropertyDef with the given setter.
     */
    public PropertyDef withSetter(FunctionDef setter) {
        return new PropertyDef(name, getter, setter, deleter, field, decorators, declaringClass);
    }

    /**
     * Creates a new PropertyDef with the given deleter.
     */
    public PropertyDef withDeleter(FunctionDef deleter) {
        return new PropertyDef(name, getter, setter, deleter, field, decorators, declaringClass);
    }

    /**
     * Creates a new PropertyDef with the given field.
     */
    public PropertyDef withField(AttributeDef field) {
        return new PropertyDef(name, getter, setter, deleter, field, decorators, declaringClass);
    }

    /**
     * Creates a new PropertyDef with the given decorators.
     */
    public PropertyDef withDecorators(List<DecoratorDef> decorators) {
        return new PropertyDef(name, getter, setter, deleter, field, decorators, declaringClass);
    }

    /**
     * Creates a new PropertyDef with the given declaring class.
     *
     * @param declaringClass The class that declares this property
     * @return A new PropertyDef with the declaring class set
     */
    public PropertyDef withDeclaringClass(ClassDef declaringClass) {
        return new PropertyDef(name, getter, setter, deleter, field, decorators, declaringClass);
    }
}
