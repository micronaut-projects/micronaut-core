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
package io.micronaut.python.processing.model;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.python.processing.util.PythonValues;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a parsed Python decorator and its resolved metadata.
 *
 * @param name The decorator name
 * @param annotationName The mapped Micronaut annotation name
 * @param repeatedName The repeatable annotation container name
 * @param members The decorator member values
 * @param stereotypes Stereotype decorators applied to this decorator
 * @param memberDecorators Decorators applied to annotation members
 * @param memberTypes Resolved annotation member types
 */
@Experimental
public record DecoratorDef(
    String name,
    String annotationName,
    @Nullable String repeatedName,
    @Nullable Map<String, Object> members,
    @Nullable List<DecoratorDef> stereotypes,
    @Nullable Map<String, List<DecoratorDef>> memberDecorators,
    @Nullable Map<String, TypeRef> memberTypes) {

    private static final Set<String> TARGET_PARAMETER_NAMES = Set.of("func", "cls", "bean");

    /**
     * Simplified constructor with just the name.
     *
     * @param name           The decorator name
     * @param annotationName The micronaut annotation name
     */
    public DecoratorDef(String name, String annotationName) {
        this(name, annotationName, null, Map.of(), List.of(), Map.of(), Map.of());
    }

    /**
     * Simplified constructor with just the name and members.
     *
     * @param name           The decorator name
     * @param annotationName The micronaut annotation name
     * @param members        The members
     */
    public DecoratorDef(String name, String annotationName, Map<String, Object> members) {
        this(name, annotationName, null, members, List.of(), Map.of(), Map.of());
    }

    /**
     * Constructor without member decorators.
     *
     * @param name           The decorator name
     * @param annotationName The micronaut annotation name
     * @param repeatedName   The repeatable annotation container name
     * @param members        The members
     * @param stereotypes    The stereotypes
     */
    public DecoratorDef(String name,
                        String annotationName,
                        @Nullable String repeatedName,
                        @Nullable Map<String, Object> members,
                        @Nullable List<DecoratorDef> stereotypes) {
        this(name, annotationName, repeatedName, members, stereotypes, Map.of(), Map.of());
    }

    /**
     * Constructor without member types.
     *
     * @param name             The decorator name
     * @param annotationName   The micronaut annotation name
     * @param repeatedName     The repeatable annotation container name
     * @param members          The members
     * @param stereotypes      The stereotypes
     * @param memberDecorators Decorators applied to annotation members
     */
    public DecoratorDef(String name,
                        String annotationName,
                        @Nullable String repeatedName,
                        @Nullable Map<String, Object> members,
                        @Nullable List<DecoratorDef> stereotypes,
                        @Nullable Map<String, List<DecoratorDef>> memberDecorators) {
        this(name, annotationName, repeatedName, members, stereotypes, memberDecorators, Map.of());
    }

    public DecoratorDef {
        Objects.requireNonNull(name, "Decorator name cannot be null");
        Objects.requireNonNull(annotationName, "Decorator annotation name cannot be null");
        if (members == null) {
            members = Map.of();
        } else {
            // Values arrive from the Python processor as polyglot values; the model holds plain Java values.
            members = Collections.unmodifiableMap(PythonValues.toJava(members));
        }
        if (stereotypes == null) {
            stereotypes = List.of();
        } else {
            stereotypes = Collections.unmodifiableList(stereotypes);
        }
        if (memberDecorators == null) {
            memberDecorators = Map.of();
        } else {
            memberDecorators = Collections.unmodifiableMap(memberDecorators);
        }
        if (memberTypes == null) {
            memberTypes = Map.of();
        } else {
            memberTypes = Collections.unmodifiableMap(memberTypes);
        }
    }

    /**
     * Whether a parameter of the decorator is the decorated target rather than an annotation member: a decorator
     * written as {@code def my_ann(func)} takes the decorated function, class or bean as its only parameter, with
     * no type, no default and no decorators, and declares no member at all.
     *
     * @param memberName The parameter name
     * @return {@code true} if the parameter is the decorated target
     * @since 5.3.0
     */
    public boolean isTargetMember(String memberName) {
        return memberTypes.size() <= 1
            && members.size() <= 1
            && members.get(memberName) == null
            && memberTypes.get(memberName) == null
            && memberDecorators.getOrDefault(memberName, List.of()).isEmpty()
            && TARGET_PARAMETER_NAMES.contains(memberName);
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
