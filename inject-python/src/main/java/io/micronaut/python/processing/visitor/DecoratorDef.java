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

import io.micronaut.core.annotation.Nullable;
import org.graalvm.polyglot.Value;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * <p>
 * Represents a Python decorator in the annotation processing pipeline.
 * This record models a decorator instance (e.g., {@code @mydeco} or {@code @mydeco(param=42)}),
 * typically encountered while traversing Python AST nodes from user-provided source code.
 * </p>
 *
 * <p>
 * Each {@code DecoratorDef} consists of:
 * </p>
 * <ul>
 *     <li>{@code name}: The qualified name of the decorator function as a string.
 *         For example, {@code "singleton"}, {@code "named"}, or {@code "micronaut.inject"}.</li>
 *     <li>{@code members}: A mapping of argument names to their values, as extracted from keyword arguments
 *         or call-time arguments (if any) in the Python decorator usage. Values are represented using GraalVM
 *         {@link org.graalvm.polyglot.Value} to support interop between Python and Java data models.</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <p>
 * Given the Python decorator usage:
 * <pre>
 * &#64;named("example")
 * def myfunc(): ...
 * </pre>
 * This results in a {@code DecoratorDef} with name {@code "named"} and {@code members} possibly containing
 * {"name": Value.asString("example")} if arguments are parsed.
 * </p>
 *
 * <h2>Usage in Processing Pipeline</h2>
 * <p>
 * {@code DecoratorDef} instances are typically produced during AST traversal
 * (e.g., by the embedded Python {@code MicronautAstVisitor}) and are used to construct
 * higher-level Java elements such as {@link ClassDef} and {@link FunctionDef}.
 * </p>
 *
 * <h2>Null Safety</h2>
 * <ul>
 *     <li>Both {@code name} and {@code members} are required and validated non-null.</li>
 *     <li>When constructing from name only, an empty member map is used.</li>
 * </ul>
 *
 * @param name           The identifier of the decorator, must not be null.
 * @param annotationName The Micronaut annotation name associated with this decorator
 * @param repeatedName   The repeated name for repeatable annotations
 * @param members        The argument mapping for the decorator; must not be null, but may be empty.
 * @param stereotypes    Stereotypes are decorators applied to decorators
 */
public record DecoratorDef(
    String name,
    String annotationName,
    @Nullable String repeatedName,
    @Nullable Map<String, Value> members,
    @Nullable List<DecoratorDef> stereotypes) {

    /**
     * Simplified constructor with just the name.
     *
     * @param name           The decorator name
     * @param annotationName The micronaut annotation name
     */
    public DecoratorDef(String name, String annotationName) {
        this(name, annotationName, null, Map.of(), List.of());
    }

    /**
     * Simplified constructor with just the name and members.
     *
     * @param name           The decorator name
     * @param annotationName The micronaut annotation name
     * @param members        The members
     */
    public DecoratorDef(String name, String annotationName, Map<String, Value> members) {
        this(name, annotationName, null, members, List.of());
    }

    public DecoratorDef {
        Objects.requireNonNull(name, "Decorator name cannot be null");
        Objects.requireNonNull(annotationName, "Decorator annotation name cannot be null");
        if (members == null) {
            members = Map.of();
        } else {
            members = Collections.unmodifiableMap(members);
        }
        if (stereotypes == null) {
            stereotypes = List.of();
        } else {
            stereotypes = Collections.unmodifiableList(stereotypes);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DecoratorDef that = (DecoratorDef) o;
        return Objects.equals(name, that.name) && Objects.equals(annotationName, that.annotationName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, annotationName);
    }
}
