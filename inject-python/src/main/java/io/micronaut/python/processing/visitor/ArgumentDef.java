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
 * An ArgumentDef represents a single function parameter definition.
 * <p>
 * This record captures the details of a Python function argument including its name,
 * type annotation, default value, decorators, and documentation.
 * </p>
 *
 * @param name The parameter name.
 * @param annotation The full type annotation string.
 * @param typeAnnotation The extracted type annotation (e.g., TypeRef for "List[str]").
 * @param defaultValue The default value as a GraalPy Value, or null if no default.
 * @param hasDefaultValue Whether the parameter declaration includes a default value, including an explicit {@code None}.
 * @param decorators The decorators applied to this parameter.
 * @param documentation The parameter documentation string.
 * @param declaringFunction The function that declares this argument.
 * @author Micronaut Team
 * @since 5.2.0
 */
@Experimental
public record ArgumentDef(
    String name,
    String annotation,
    TypeRef typeAnnotation,
    Object defaultValue,
    boolean hasDefaultValue,
    List<DecoratorDef> decorators,
    String documentation,
    FunctionDef declaringFunction
) implements ElementDef {

    public ArgumentDef(String name, TypeRef typeAnnotation) {
        this(name, typeAnnotation != null ? typeAnnotation.name() : null, typeAnnotation, null, false, List.of(), null, null);
    }

    public ArgumentDef(String name, String annotation, TypeRef typeAnnotation, Object defaultValue, List<DecoratorDef> decorators, String documentation) {
        this(name, annotation, typeAnnotation, defaultValue, defaultValue != null, decorators, documentation, null);
    }

    public ArgumentDef(String name,
                       String annotation,
                       TypeRef typeAnnotation,
                       Object defaultValue,
                       List<DecoratorDef> decorators,
                       String documentation,
                       FunctionDef declaringFunction) {
        this(name, annotation, typeAnnotation, defaultValue, defaultValue != null, decorators, documentation, declaringFunction);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ArgumentDef that = (ArgumentDef) o;
        return Objects.equals(name, that.name) && Objects.equals(declaringFunction, that.declaringFunction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, declaringFunction);
    }

    /**
     * Creates an argument definition.
     *
     * @param name The parameter name
     * @param typeAnnotation The type annotation (nullable)
     * @return A new ArgumentDef
     */
    public static ArgumentDef of(String name, TypeRef typeAnnotation) {
        return new ArgumentDef(name, typeAnnotation != null ? typeAnnotation.name() : null, typeAnnotation, null, false, List.of(), null, null);
    }

    /**
     * Creates an argument definition with name only.
     *
     * @param name The parameter name
     * @return A new ArgumentDef
     */
    public static ArgumentDef of(String name) {
        return new ArgumentDef(name, null, null, null, false, List.of(), null, null);
    }

    /**
     * Creates an argument definition with name, type, and default value.
     *
     * @param name The parameter name
     * @param typeAnnotation The type annotation (nullable)
     * @param defaultValue The default value (nullable)
     * @return A new ArgumentDef
     */
    public static ArgumentDef of(String name, TypeRef typeAnnotation, Object defaultValue) {
        return new ArgumentDef(name, typeAnnotation != null ? typeAnnotation.name() : null, typeAnnotation, defaultValue, defaultValue != null, List.of(), null, null);
    }

    /**
     * Creates an argument definition with name, type, default value, and documentation.
     *
     * @param name The parameter name
     * @param typeAnnotation The type annotation (nullable)
     * @param defaultValue The default value (nullable)
     * @param documentation The parameter documentation (nullable)
     * @return A new ArgumentDef
     */
    public static ArgumentDef of(String name, TypeRef typeAnnotation, Object defaultValue, String documentation) {
        return new ArgumentDef(name, typeAnnotation != null ? typeAnnotation.name() : null, typeAnnotation, defaultValue, defaultValue != null, List.of(), documentation, null);
    }

    /**
     * Creates an argument definition with annotation, type, decorators, and documentation.
     *
     * @param name The parameter name
     * @param annotation The full annotation string (nullable)
     * @param typeAnnotation The extracted type annotation (nullable)
     * @param defaultValue The default value (nullable)
     * @param decorators The decorators (nullable)
     * @param documentation The parameter documentation (nullable)
     * @return A new ArgumentDef
     */
    public static ArgumentDef of(String name, String annotation, TypeRef typeAnnotation, Object defaultValue, List<DecoratorDef> decorators, String documentation) {
        return new ArgumentDef(name, annotation, typeAnnotation, defaultValue, defaultValue != null, decorators, documentation, null);
    }

    /**
     * Creates an argument definition with explicit default presence metadata.
     *
     * @param name The parameter name
     * @param annotation The full annotation string (nullable)
     * @param typeAnnotation The extracted type annotation (nullable)
     * @param defaultValue The default value (nullable)
     * @param hasDefaultValue Whether a default value was declared
     * @param decorators The decorators (nullable)
     * @param documentation The parameter documentation (nullable)
     * @return A new ArgumentDef
     */
    public static ArgumentDef of(String name,
                                 String annotation,
                                 TypeRef typeAnnotation,
                                 Object defaultValue,
                                 boolean hasDefaultValue,
                                 List<DecoratorDef> decorators,
                                 String documentation) {
        return new ArgumentDef(name, annotation, typeAnnotation, defaultValue, hasDefaultValue, decorators, documentation, null);
    }

    /**
     * Creates a new ArgumentDef with the given declaring function.
     *
     * @param declaringFunction The function that declares this argument
     * @return A new ArgumentDef with the declaring function set
     */
    public ArgumentDef withDeclaringFunction(FunctionDef declaringFunction) {
        return new ArgumentDef(name, annotation, typeAnnotation, defaultValue, hasDefaultValue, decorators, documentation, declaringFunction);
    }
}
