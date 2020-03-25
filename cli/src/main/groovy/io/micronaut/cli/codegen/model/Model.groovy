/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.cli.codegen.model

/**
 * The model of the naming conventions of a class used for Codegen
 *
 * @author Graeme Rocher
 * @since 1.0
 */
interface Model {

    /**
     * @return The class name excluding package
     */
    String getClassName()

    /**
     * @return The class name including package
     */
    String getFullName()

    /**
     * @return The package name
     */
    String getPackageName()

    /**
     * @return The package name
     */
    String getPackagePath()

    /**
     * @return The class name without package
     */
    String getSimpleName()

    /**
     * A property name version of the class name. For example 'FooBar' becomes 'fooBar'
     *
     * @return The property name version of the class name
     */
    String getPropertyName()

    /**
     * A property name version of the class name. For example 'FooBar' becomes 'fooBar'
     *
     * @return The property name version of the class name
     */
    String getModelName()

    /**
     * A lower case version of the class name separated by hyphens. For example 'FooBar' becomes 'foo-bar'
     *
     * @return Lower case version of the class name
     */
    String getLowerCaseName()

    /**
     * Returns the convention of this model for the given name. For example given a {@link #getSimpleName()} of "Foo" this method will return "FooController" where the name argument is "Controller"
     * @param conventionName The name
     * @param conventionName The convention name
     * @return The convention for the given convention name
     */
    String convention(String name, String conventionName)

    /**
     * Returns given name without the convention if present. For example given a name of "FooController" this method will return "Foo" where the name argument is "Controller"
     * @param name The name
     * @param conventionName The convention name
     * @return The given name without the convention
     */
    String trimConvention(String name, String conventionName)

    /**
     * @return The model as a map
     */
    Map<String, ?> asMap()
}