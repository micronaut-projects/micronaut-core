/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.inject.visitor;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.type.TypeHierarchy;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;

import java.util.List;

/**
 * What the record of the generic hierarchy of a type says and leaves out, as reported by
 * {@link VisitorContext#recordTypeHierarchy(ClassElement, Element...)} when the record is written. The record itself
 * is found at run time with {@link io.micronaut.core.type.TypeHierarchies#find(Class)}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public sealed interface TypeHierarchyRecord permits DefaultTypeHierarchyRecord {

    /**
     * @return The name of the generated class of the record
     */
    String getClassName();

    /**
     * The super types the record leaves out because it cannot name them from its package, the same erased names the
     * record lists at run time in {@link TypeHierarchy#getOmittedSuperTypeNames()}, in the order of the closure.
     *
     * @return The names of the omitted super types, empty when the record names every super type
     */
    List<String> getOmittedSuperTypeNames();

    /**
     * @return Whether the record names every super type of the type
     */
    default boolean isComplete() {
        return getOmittedSuperTypeNames().isEmpty();
    }

    /**
     * Creates a record description.
     *
     * @param className             The name of the generated class of the record
     * @param omittedSuperTypeNames The names of the super types the record leaves out, in the order of the closure
     * @return The description
     */
    static TypeHierarchyRecord of(String className, List<String> omittedSuperTypeNames) {
        return new DefaultTypeHierarchyRecord(className, List.copyOf(omittedSuperTypeNames));
    }
}
