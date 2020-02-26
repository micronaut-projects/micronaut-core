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
package io.micronaut.cli.profile.commands.io

import io.micronaut.cli.io.support.Resource

/**
 * Utility methods exposed to scripts for interacting with resources (found on the file system or jars) and the file system
 *
 * @author Graeme Rocher
 * @since 1.0
 */
interface FileSystemInteraction {

    /**
     * Makes a directory
     *
     * @param path The path to the directory
     */
    FileSystemInteraction mkdir(path)
    /**
     * Deletes a file
     *
     * @param path The path to the file
     */
    FileSystemInteraction delete(path)
    /**
     * Allows Gradle style simple copy specs
     *
     * @param callable The callable
     * @return this
     */
    FileSystemInteraction copy(@DelegatesTo(CopySpec) Closure callable)
    /**
     * Copies a resource to the target destination
     *
     * @param path The path
     * @param destination The destination
     */
    FileSystemInteraction copy(path, destination)
    /**
     * Copies resources to the target destination
     *
     * @param path The path
     * @param destination The destination
     */
    FileSystemInteraction copyAll(Iterable resources, destination)
    /**
     * Copy a Resource from the given location to the given directory or location
     *
     * @param from The resource to copy
     * @param to The location to copy to
     * @return The {@FileSystemInteraction} instance
     */
    FileSystemInteraction copy(Resource from, File to)
    /**
     * Obtain a file for the given path
     *
     * @param path The path
     * @return The file
     */
    File file(Object path)
    /**
     * @return The target build directory
     */
    File getBuildDir()
    /**
     * @return The directory where classes are compiled to
     */
    File getClassesDir()
    /**
     * Finds a source file for the given class name
     * @param className The class name
     * @return The source resource
     */
    Resource source(String className)
    /**
     * Obtain a resource for the given path
     * @param path The path
     * @return The resource
     */
    Resource resource(Object path)
    /**
     * Obtain resources for the given pattern
     *
     * @param pattern The pattern
     * @return The resources
     */
    Collection<Resource> resources(String pattern)
    /**
     * Obtain the path of the resource relative to the current project
     *
     * @param path The path to inspect
     * @return The relative path
     */
    String projectPath(Object path)

    /**
     * The class name of the given resource
     *
     * @param resource The resource
     * @return The class name
     */
    String className(Resource resource)

    /**
     * Get files matching the given pattern
     *
     * @param pattern The pattern
     * @return the files
     */
    Collection<File> files(String pattern)

    static class CopySpec {
        def from
        def into

        void from(path) {
            this.from = path
        }

        void into(path) {
            this.into = path
        }
    }
}