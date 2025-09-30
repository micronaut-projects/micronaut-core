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
package io.micronaut.inject.visitor;

import io.micronaut.core.annotation.Experimental;

/**
 * The query allows modifying what {@link TypeElementVisitor} visits.
 *
 * @author Denis Stepanov
 * @since 4.9
 */
@Experimental
public interface TypeElementQuery {

    TypeElementQuery DEFAULT = DefaultTypeElementQuery.DEFAULT;

    /**
     * Only visit the class and the methods.
     *
     * @return this query
     */
    static TypeElementQuery onlyMethods() {
        return DEFAULT.excludeAll().includeMethods();
    }

    /**
     * Only visit the class.
     *
     * @return this query
     */
    static TypeElementQuery onlyClass() {
        return DEFAULT.excludeAll();
    }

    /**
     * Include the methods.
     *
     * @return this query
     */
    TypeElementQuery includeMethods();

    /**
     * Exclude the methods.
     *
     * @return this query
     */
    TypeElementQuery excludeMethods();

    /**
     * Include the constructors.
     *
     * @return this query
     */
    TypeElementQuery includeConstructors();

    /**
     * Exclude the constructors.
     *
     * @return this query
     */
    TypeElementQuery excludeConstructors();

    /**
     * Include the fields.
     *
     * @return this query
     */
    TypeElementQuery includeFields();

    /**
     * Exclude the fields.
     *
     * @return this query
     */
    TypeElementQuery excludeFields();

    /**
     * If the unresolved interfaces should be visited.
     *
     * @return this query
     */
    TypeElementQuery visitUnresolvedInterfaces();

    /**
     * Include the enum constants.
     *
     * @return this query
     */
    TypeElementQuery includeEnumConstants();

    /**
     * Exclude the enum constants.
     *
     * @return this query
     */
    TypeElementQuery excludeEnumConstants();

    /**
     * Include all.
     *
     * @return this query
     */
    TypeElementQuery includeAll();

    /**
     * Exclude all.
     *
     * @return this query
     */
    TypeElementQuery excludeAll();

    /**
     * @return Is includes methods?
     */
    boolean includesMethods();

    /**
     * @return Is includes fields?
     */
    boolean includesFields();

    /**
     * @return Is includes constructors?
     */
    boolean includesConstructors();

    /**
     * @return Is enum constants?
     */
    boolean includesEnumConstants();

    /**
     * @return Visits unresolved interfaces
     */
    boolean visitsUnresolvedInterfaces();
}
