/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.python.imports;

import io.micronaut.core.annotation.Experimental;

import java.util.List;
import java.util.Optional;

/**
 * The view of the class path a curated module is resolved against. The Python processor implements it over
 * its visitor context and the editor stub generators over the class files they read, so both resolve a
 * module to the same names.
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Experimental
public interface ClassIndex {

    /**
     * The types declared in a package, nested types included or not.
     *
     * @param javaPackage The package name
     * @return The types, empty when the package is not on the class path
     */
    List<TypeInfo> types(String javaPackage);

    /**
     * A type by binary name.
     *
     * @param binaryName The binary name
     * @return The type, empty when it is not on the class path
     */
    Optional<TypeInfo> type(String binaryName);

    /**
     * The names of the public static methods a type declares, an overloaded name once or more.
     *
     * @param binaryName The binary name of the type
     * @return The method names
     */
    List<String> staticMethods(String binaryName);

    /**
     * The constants of a type, in declaration order: the constants of an enum, or the public static final
     * fields of a class or an interface.
     *
     * @param binaryName The binary name of the type
     * @return The constant names
     */
    List<String> constants(String binaryName);

    /**
     * What the index knows of a type.
     *
     * @param binaryName The binary name
     * @param kind       The kind of type
     * @param publicType Whether the type is public
     * @param internal   Whether the type is annotated with {@code io.micronaut.core.annotation.Internal}
     */
    record TypeInfo(String binaryName, TypeKind kind, boolean publicType, boolean internal) {

        /**
         * @return The package of the type
         */
        public String packageName() {
            int lastDot = binaryName.lastIndexOf('.');
            return lastDot < 0 ? "" : binaryName.substring(0, lastDot);
        }

        /**
         * @return The simple name of the type, the innermost for a nested type
         */
        public String simpleName() {
            String name = binaryName.substring(binaryName.lastIndexOf('.') + 1);
            return name.substring(name.lastIndexOf('$') + 1);
        }

        /**
         * @return Whether the type is nested in another
         */
        public boolean nested() {
            return binaryName.substring(binaryName.lastIndexOf('.') + 1).contains("$");
        }
    }

    /**
     * The kinds of types, as Python sees them.
     */
    enum TypeKind {
        /**
         * An annotation type: a decorator in Python.
         */
        ANNOTATION,
        /**
         * An enum.
         */
        ENUM,
        /**
         * An interface.
         */
        INTERFACE,
        /**
         * A class or a record.
         */
        CLASS
    }
}
